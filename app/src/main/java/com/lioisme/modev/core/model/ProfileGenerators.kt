package com.lioisme.modev.core.model

import kotlin.random.Random

/**
 * 画像值生成器(V47 T3,基线 `Randoms.kt` 的形态纪律移植):
 * 生成的是**「真实设备可能出现的值」**,不是随机字符。
 *
 * 为什么值得单独立文件并全部接受可注入的 [Random]:
 *  1. 形态不合法就是自我暴露 —— 例如随机 MAC 若落在厂商 OUI 段,或 IMEI 不过 Luhn,
 *     检测方一眼就能判伪,伪装反而比不伪装更可疑;
 *  2. 本仓 DFC-04 的实锤形态就是「假 MAC 前缀 `02:42:ac:11:` 恰好命中自身
 *     模拟器判据的 Docker/桥接 OUI」—— 用生成器统一产出可以从根上杜绝这类自打;
 *  3. 全部纯函数 + 可注入随机源 ⇒ JVM 可复现直测,注入侧与宿主 UI 共用同一份实现
 *     (两侧各写一遍必然漂移)。
 */
object ProfileGenerators {

    private const val HEX_LOWER = "0123456789abcdef"
    private const val ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val DECIMALS = "0123456789"

    /** 指定长度字母数字串。 */
    fun randomString(length: Int, random: Random = Random.Default): String =
        buildString(length) {
            repeat(length) { append(ALNUM[random.nextInt(ALNUM.length)]) }
        }

    /** 无横线 UUID 形态(32 位十六进制;画像库主键用)。 */
    fun uuidNoDash(random: Random = Random.Default): String =
        buildString(32) {
            repeat(32) { append(HEX_LOWER[random.nextInt(16)]) }
        }

    /**
     * 合法「本地管理单播」MAC:首字节低位 bit1 置 1(本地管理)、bit0 清 0(单播),
     * 因此永不冒充真实厂商 OUI。
     */
    fun randomMacAddress(random: Random = Random.Default): String {
        val octets = ByteArray(6) { random.nextInt(256).toByte() }
        // 置 bit1(本地管理)并清 bit0(单播);0xFE 即低字节的 ~0x01。
        octets[0] = (((octets[0].toInt() and 0xFF) or 0x02) and 0xFE).toByte()
        return octets.joinToString(":") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** 15 位 IMEI:前 14 位随机 + 第 15 位为 Luhn 校验位(与 [ProfileFieldCatalog.isLuhnValidImei] 互逆)。 */
    fun randomImei(random: Random = Random.Default): String {
        val body = buildString(14) {
            repeat(14) { append(DECIMALS[random.nextInt(10)]) }
        }
        return body + luhnCheckDigit(body)
    }

    /** 16 位小写十六进制 SSAID。 */
    fun randomAndroidId(random: Random = Random.Default): String =
        buildString(16) {
            repeat(16) { append(HEX_LOWER[random.nextInt(16)]) }
        }

    /** 中国大陆形态手机号:1 + 10 位数字。 */
    fun randomPhoneNum(random: Random = Random.Default): String =
        buildString(11) {
            append('1')
            repeat(10) { append(DECIMALS[random.nextInt(10)]) }
        }

    /**
     * 与 Android 大版本关联的 Build ID 前缀(基线同表):
     * 版本号与 Build ID 世代不符是常见的画像矛盾点。
     */
    fun buildIdPrefix(androidVersion: String): String = when (androidVersion) {
        "10" -> "QP1A"
        "11" -> "RP1A"
        "12" -> "SP1A"
        "12L" -> "SP1A"
        "13" -> "TP1A"
        "14" -> "UP1A"
        "15" -> "AP3A"
        "16" -> "BP2A"
        "17" -> "CP1A"
        else -> "UP1A"
    }

    /** 该大版本对应的发布年份下界(用于 Build ID 的日期段,避免出现「未来构建」)。 */
    fun buildIdBaseYear(androidVersion: String): Int = when (androidVersion) {
        "10" -> 2019
        "11" -> 2020
        "12" -> 2021
        "12L" -> 2022
        "13" -> 2022
        "14" -> 2023
        "15" -> 2024
        "16" -> 2025
        "17" -> 2026
        else -> 2023
    }

    /** 形如 `UP1A.231105.001` 的 Build ID(月 1..12、日 1..28 限定,序号三位)。 */
    fun randomBuildId(androidVersion: String, random: Random = Random.Default): String {
        val year = buildIdBaseYear(androidVersion) + random.nextInt(0, 3)
        val yc = (year % 100).toString().padStart(2, '0')
        return "%s.%s%02d%02d.%03d".format(
            buildIdPrefix(androidVersion),
            yc,
            random.nextInt(1, 13),
            random.nextInt(1, 29),
            random.nextInt(1000),
        )
    }

    /**
     * 由身份三段 + 版本 + Build ID 派生 fingerprint,骨架恒为
     * `brand/product/device:release/buildId/serial:user/release-keys`。
     *
     * 各段做清洗:空白与 `:` 换 `_`,`/` 换 `_`(这三个字符是指纹的分段符)。
     */
    fun deriveFingerprint(
        brand: String,
        product: String,
        device: String,
        release: String,
        buildId: String,
        random: Random = Random.Default,
    ): String {
        val serial = buildString(7) {
            repeat(7) { append(DECIMALS[random.nextInt(10)]) }
        }
        return "%s/%s/%s:%s/%s/%s:user/release-keys".format(
            cleanFingerprintPart(brand, "generic"),
            cleanFingerprintPart(product, "generic"),
            cleanFingerprintPart(device, "device"),
            cleanFingerprintPart(release, "14"),
            cleanFingerprintPart(buildId, "UP1A.231005.00B"),
            serial,
        )
    }

    private fun cleanFingerprintPart(raw: String, fallback: String): String {
        val cleaned = raw.trim().map { if (it == '/' || it == ':' || it.isWhitespace()) '_' else it }
            .joinToString("")
            .trim('_')
        return cleaned.ifEmpty { fallback }
    }

    /** 0..100 电量。 */
    fun randomBatteryLevel(random: Random = Random.Default): Int = random.nextInt(0, 101)

    /** 六位小数的全球合法坐标(负零归一,避免 `-0.0` 这种形态可疑的输出)。 */
    fun randomLatitude(random: Random = Random.Default): Double =
        round6(random.nextDouble(-90.0, 90.0))

    /** 六位小数的全球合法经度。 */
    fun randomLongitude(random: Random = Random.Default): Double =
        round6(random.nextDouble(-180.0, 180.0))

    private fun round6(value: Double): Double {
        val scaled = (value * 1_000_000.0).toLong() / 1_000_000.0
        return if (scaled == 0.0) 0.0 else scaled
    }

    /**
     * 对 14 位 IMEI 主体求 Luhn 校验位(与 [ProfileFieldCatalog.isLuhnValidImei] 互逆,
     * 二者由同一份测试钉死)。
     */
    fun luhnCheckDigit(body: String): Char {
        // 校验位补在末尾后,从右起第二位开始倍增;等价于对长度+1 的序列做标准 Luhn。
        var sum = 0
        val total = body.length + 1
        for (index in body.indices) {
            val digit = body[index] - '0'
            // 补位后该位的 1 基序号 = total - index
            val doubled = (total - index) % 2 == 0
            sum += if (doubled) {
                val twice = digit * 2
                if (twice > 9) twice - 9 else twice
            } else {
                digit
            }
        }
        return '0' + (10 - sum % 10) % 10
    }
}
