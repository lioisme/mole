package com.lioisme.modev.domain.config

import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.ProfileImportOutcome
import com.lioisme.modev.core.model.ProfileResolution
import kotlinx.coroutines.flow.StateFlow

/**
 * 设备画像库契约(V47 T2,R-3「支持用户 DIY」的类型级落点)。
 *
 * **画像库是伪装配置的唯一可变入口**:UI 画像页、每应用绑定、MCP `spoof_profile_*`
 * 工具与注入侧展开全部经本契约,与 [KeywordLibraryService] 同族
 * (AGENTS #18 方法层契约族;`SingleWriteEntryPointTest` 把实现侧登记为唯一写者)。
 *
 * 三个不得退化的语义:
 *  1. [resolve] 返回三态 ⇒ 「未绑定」与「绑了但库里没有」必须可区分,禁静默回落;
 *  2. [synchronizeBuiltins] 幂等且守墓碑 ⇒ 升级只补新档,用户改过与删过的永覆盖不回来;
 *  3. 校验一律走 `ProfileFieldCatalog` ⇒ 非法值**拒绝写入**,不钳制、不静默丢弃。
 */
interface SpoofProfileService {

    /** 画像全量快照(创建序;墓碑行已排除)。 */
    val profiles: StateFlow<List<DeviceProfileEntry>>

    /** 内置档账本同步(首装补档 / 升级补新 / 受管辖行原地更新 / 墓碑与已改档不动)。 */
    suspend fun synchronizeBuiltins()

    /** 新建 DIY 画像;名称空白或画像全空(零字段无可伪装)返回 null。 */
    suspend fun create(name: String, summary: String, profile: DeviceProfile): DeviceProfileEntry?

    /** 按 id 更新画像;id 不存在返回 false。改内置档会同时令其脱离账本管辖。 */
    suspend fun update(id: String, name: String, summary: String, profile: DeviceProfile): Boolean

    /** 复制为 DIY 画像(内置档定制的标准路径);源不存在返回 null。 */
    suspend fun duplicate(id: String, name: String): DeviceProfileEntry?

    /** 删除画像;内置档留下墓碑 ⇒ 升级不再复活。返回是否确有一行被删。 */
    suspend fun delete(id: String): Boolean

    /** 取条目(null = id 为空或不存在)。 */
    fun find(id: String?): DeviceProfileEntry?

    /** 绑定解析(三态,禁回落)。 */
    fun resolve(id: String?): ProfileResolution

    /** 导出为传输信封 JSON;ids 为空 = 全量导出。 */
    fun exportJson(ids: List<String> = emptyList()): String

    /**
     * 导入信封 JSON([ProfileImportOutcome] 逐条给结论);[dryRun] 只校验不落库。
     *
     * [defaultName] 由调用方(UI 取四语资源、MCP 给固定英文键值)提供:
     * **数据层不得内嵌用户可见字面量**(与 `HardcodedStringConventionTest` 同一红线,
     * 只是本条落在 data 侧)。
     */
    suspend fun importJson(text: String, dryRun: Boolean = false, defaultName: String): ProfileImportOutcome
}
