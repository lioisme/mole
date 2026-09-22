package com.lioisme.modev.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * 真机目录数据层测试(V47,复刻基线 `DeviceCatalogRepository` 模型 + `DevicePreset` 写入语义的验收面):
 *  ① 随包资产**真读真解**(不是夹具):版本 / 上游修订号与 devices.NOTICE.txt 对账、
 *    品牌与机型计数、已知品牌可认回(含 key / buildBrand / 呈现名三种形态);
 *  ② `resolvedDevice` 的 alias → device → code 优先级(含 null、空串与纯空白串);
 *  ③ `applyDevicePreset` 只写 MODEL 与 DEVICE,PRODUCT / BOARD / HARDWARE 原值必须活着;
 *  ④ `applyBrandIdentity` 的制造商条件跟随三分支(空白 / 等于上一 BRAND / 无关值);
 *  ⑤ 解码闸:垃圾 / 空白 / 零品牌 / 高于当前档的 schemaVersion 一律 null。
 *
 * 纯 JUnit 零 Robolectric —— 本层的全部价值就是「不依赖 android 也能算对」,
 * 资产经文件系统直读(路径定位形态沿用 `architecture/RecordRowContainerContractTest`)。
 */
class DeviceCatalogTest {

    private val assetRelative = "src/main/assets"

    private fun asset(name: String): File {
        val userDir = System.getProperty("user.dir") ?: error("user.dir unavailable")
        return listOf(
            File(userDir, "$assetRelative/$name"),
            File(userDir, "app/$assetRelative/$name"),
            File(userDir, "../app/$assetRelative/$name"),
        ).firstOrNull { it.isFile }
            ?: error("随包资产未定位($name, user.dir=$userDir)")
    }

    /** NOTICE 里「Upstream data revision:」锚点后的 40 位十六进制串。 */
    private fun noticeDataRevision(): String {
        val lines = asset("devices.NOTICE.txt").readLines().map { it.trim() }
        val anchor = lines.indexOfFirst { it.startsWith("Upstream data revision:") }
        assertTrue("NOTICE 缺少上游数据修订号锚点", anchor >= 0)
        val revision = lines.drop(anchor + 1).firstOrNull { it.isNotEmpty() }.orEmpty()
        assertTrue("NOTICE 修订号形态异常: $revision", Regex("^[0-9a-f]{40}${'$'}").matches(revision))
        return revision
    }

    private val bundled: DeviceCatalog by lazy {
        parseDeviceCatalog(asset("devices.json").readText())
            ?: error("随包 devices.json 解析失败(资产破损或解码闸误拒)")
    }

    // ------------------------------------------------------------ ① 资产真身

    @Test
    fun bundledAssetParsesWithPinnedProvenance() {
        val catalog = bundled
        assertEquals(DeviceCatalog.SCHEMA_VERSION, catalog.schemaVersion)
        assertEquals(noticeDataRevision(), catalog.sourceRevision)
        // 本修订号实测口径 = 26 品牌 / 8533 条机型记录;阈值取宽下界,防换版静默缩水。
        assertTrue("品牌数应 > 20,实为 ${catalog.brands.size}", catalog.brands.size > 20)
        val models = catalog.brands.sumOf { it.models.size }
        assertTrue("机型记录数应 > 3000,实为 $models", models > 3000)
        assertTrue("品牌键应恒为小写目录键", catalog.brands.all { it.key == it.key.lowercase() })
    }

    @Test
    fun bundledBrandsResolveCaseInsensitively() {
        val xiaomi = checkNotNull(bundled.brandByKey("xiaomi")) { "目录应含 xiaomi 品牌" }
        assertTrue("xiaomi 应带机型记录", xiaomi.models.isNotEmpty())
        assertSame(xiaomi, bundled.brandByKey("XIAOMI"))
        assertEquals("Xiaomi", xiaomi.buildBrand)
        assertEquals("小米", xiaomi.displayName)

        // google 这条是「key 与 buildBrand 同形、manufacturer 才首字母大写」的对照样本
        val google = checkNotNull(bundled.brandByKey("google")) { "目录应含 google 品牌" }
        assertEquals("google", google.buildBrand)
        assertEquals("Google", google.manufacturer)
        assertSame(google, bundled.brandForConfiguredValue("Google"))
        assertSame(xiaomi, bundled.brandForConfiguredValue("Xiaomi"))
        assertSame(xiaomi, bundled.brandForConfiguredValue("小米"))
        // letv:buildBrand 是 LeEco,与 key 不同形 ⇒ 只有 buildBrand 回落分支能认回
        val letv = checkNotNull(bundled.brandByKey("letv")) { "目录应含 letv 品牌" }
        assertEquals("LeEco", letv.buildBrand)
        assertSame(letv, bundled.brandForConfiguredValue("LeEco"))
        assertSame(letv, bundled.brandForConfiguredValue("leeco"))
        assertNull(bundled.brandForConfiguredValue("NoSuchBrand"))
    }

    // ------------------------------------------------------------ ② resolvedDevice 与呈现名

    @Test
    fun resolvedDevicePrefersAliasThenDeviceThenCode() {
        fun entry(alias: String?, device: String, code: String?): DeviceModelEntry =
            DeviceModelEntry(model = "M1", device = device, code = code, alias = alias)

        assertEquals("alias-x", entry(" alias-x ", "dev", "code").resolvedDevice())
        assertEquals("dev", entry(null, " dev ", "code").resolvedDevice())
        assertEquals("dev", entry("   ", "dev", "code").resolvedDevice())
        assertEquals("code", entry(null, "  ", " code ").resolvedDevice())
        assertEquals("code", entry("", "", "code").resolvedDevice())
        assertEquals("", entry(null, "", null).resolvedDevice())
        assertEquals("", entry("  ", " ", null).resolvedDevice())
    }

    @Test
    fun labelRendersModelWithMarketingNameWithoutStrayWhitespace() {
        assertEquals("2211133C (Xiaomi 13)", DeviceModelEntry(model = "2211133C", name = "Xiaomi 13").label())
        assertEquals(
            "2211133C (Xiaomi 13)",
            DeviceModelEntry(model = "  2211133C ", name = "  Xiaomi 13  ").label(),
        )
        assertEquals("2211133C", DeviceModelEntry(model = "2211133C", name = "   ").label())
        assertEquals("Xiaomi 13", DeviceModelEntry(model = "", name = "Xiaomi 13").label())
        assertEquals("", DeviceModelEntry(model = " ", name = "").label())
    }

    // ------------------------------------------------------------ ③ 机型预设写入

    @Test
    fun devicePresetWritesModelAndDeviceOnly() {
        val base = DeviceProfile.EMPTY.copy(
            model = "old-model",
            device = "old-device",
            product = "keep-product",
            board = "keep-board",
            hardware = "keep-hardware",
        )
        val applied = applyDevicePreset(base, "2211133C:fuxi")
        assertEquals("2211133C", applied.model)
        assertEquals("fuxi", applied.device)
        // 目录不提供 PRODUCT / BOARD / HARDWARE ⇒ 拿 MODEL 或 DEVICE 凑值就是伪造(基线同处注释)
        assertEquals("keep-product", applied.product)
        assertEquals("keep-board", applied.board)
        assertEquals("keep-hardware", applied.hardware)

        // 冒号后为空(该机型 alias / device / code 全缺)⇒ DEVICE 清空,PRODUCT 仍不碰
        val noDevice = applyDevicePreset(base, "2211133C:")
        assertEquals("2211133C", noDevice.model)
        assertEquals("", noDevice.device)
        assertEquals("keep-product", noDevice.product)

        // 串里没有冒号:整串按 MODEL 处理,DEVICE 回落空(基线 missingDelimiterValue 同形态)
        val noColon = applyDevicePreset(base, "2211133C")
        assertEquals("2211133C", noColon.model)
        assertEquals("", noColon.device)
    }

    // ------------------------------------------------------------ ④ 品牌身份条件跟随

    @Test
    fun brandIdentityUpdatesManufacturerOnlyWhenItHadNoOwnValue() {
        val nothing = DeviceBrandEntry(
            key = "nothing",
            displayName = "Nothing",
            buildBrand = "Nothing",
            manufacturer = "Nothing Things",
        )
        // 1) 原制造商空白 ⇒ 跟随品牌
        val blank = applyBrandIdentity(DeviceProfile.EMPTY.copy(brand = "samsung"), nothing)
        assertEquals("Nothing", blank.brand)
        assertEquals("Nothing Things", blank.manufacturer)

        // 2) 原制造商等于上一 BRAND(忽略大小写) ⇒ 跟随品牌
        val followed = applyBrandIdentity(
            DeviceProfile.EMPTY.copy(brand = "Xiaomi", manufacturer = "xiaomi"),
            nothing,
        )
        assertEquals("Nothing", followed.brand)
        assertEquals("Nothing Things", followed.manufacturer)

        // 3) 用户自填的无关制造商 ⇒ 只改 BRAND,制造商原值活着
        val kept = applyBrandIdentity(
            DeviceProfile.EMPTY.copy(brand = "samsung", manufacturer = "Sony Mobile"),
            nothing,
        )
        assertEquals("Nothing", kept.brand)
        assertEquals("Sony Mobile", kept.manufacturer)

        // 品牌动作不越界碰机型三兄弟
        assertEquals("", kept.model)
        assertEquals("", kept.device)
        assertEquals("", kept.product)
    }

    // ------------------------------------------------------------ ⑤ 解码闸

    @Test
    fun parseRefusesGarbageBlankAndNewerSchema() {
        assertNull(parseDeviceCatalog(""))
        assertNull(parseDeviceCatalog("   \n  "))
        assertNull(parseDeviceCatalog("这不是 JSON"))
        assertNull(parseDeviceCatalog("{"))
        assertNull(parseDeviceCatalog("null"))
        // 零品牌目录与「读不到」对用户等价 ⇒ 不许返回一个能让选择器画空表的对象
        assertNull(parseDeviceCatalog("""{"schemaVersion": 1, "brands": []}"""))
        // 不认识的版本不猜(与 ProfileCodec.decodeEnvelope 同一红线)
        assertNull(
            parseDeviceCatalog(
                """{"schemaVersion": ${DeviceCatalog.SCHEMA_VERSION + 1}, "brands": [{"key": "a"}]}""",
            ),
        )
    }

    @Test
    fun parseAcceptsMinimalCatalogAndIgnoresUnknownKeys() {
        val catalog = checkNotNull(
            parseDeviceCatalog(
                """
                {
                  "schemaVersion": 1,
                  "futureField": 7,
                  "brands": [
                    {
                      "key": "acme",
                      "displayName": "阿卡米",
                      "buildBrand": "ACME-Phone",
                      "manufacturer": "ACME",
                      "unknownBrandField": { "a": 1 },
                      "models": [
                        { "model": "M1", "name": "One", "type": "mob", "unknownModelField": true }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        ) { "最小合法目录应被接受" }
        assertEquals(1, catalog.brands.size)
        val acme = catalog.brands.first()
        assertEquals("ACME-Phone", acme.buildBrand)
        assertEquals(1, acme.models.size)
        assertNull(acme.models.first().versionName)
        assertEquals("M1 (One)", acme.models.first().label())
        // 该记录没有 device / code / alias ⇒ 可解析的 DEVICE 为空串(选择器出串形如 "M1:")
        assertEquals("", acme.models.first().resolvedDevice())

        // 回落链:key(忽略大小写) → buildBrand(忽略大小写) → 呈现名(精确比对)
        assertSame(acme, catalog.brandByKey("ACME"))
        assertSame(acme, catalog.brandForConfiguredValue("ACME-PHONE"))
        assertSame(acme, catalog.brandForConfiguredValue("阿卡米"))
        assertNull(catalog.brandForConfiguredValue("acme牌"))
        assertNull(catalog.brandForConfiguredValue("阿卡米牌"))
    }

    @Test
    fun emptyCatalogAnswersWithoutThrowing() {
        assertEquals(DeviceCatalog.SCHEMA_VERSION, DeviceCatalog.EMPTY.schemaVersion)
        assertTrue(DeviceCatalog.EMPTY.brands.isEmpty())
        assertNull(DeviceCatalog.EMPTY.brandByKey("xiaomi"))
        assertNull(DeviceCatalog.EMPTY.brandForConfiguredValue("Xiaomi"))
    }
}
