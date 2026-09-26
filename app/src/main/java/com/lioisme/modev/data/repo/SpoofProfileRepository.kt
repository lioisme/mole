package com.lioisme.modev.data.repo

import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.BuiltinProfileSpec
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.ProfileCodec
import com.lioisme.modev.core.model.ProfileEnvelope
import com.lioisme.modev.core.model.ProfileImportOutcome
import com.lioisme.modev.core.model.ProfileImportVerdict
import com.lioisme.modev.core.model.ProfileResolution
import com.lioisme.modev.core.model.ProfileTransferItem
import com.lioisme.modev.data.db.SpoofProfileDao
import com.lioisme.modev.data.db.SpoofProfileEntity
import com.lioisme.modev.domain.config.SpoofProfileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 设备画像库仓库:**`spoof_profile` 表唯一写者**(AGENTS #18 单一写入口径,
 * 由 `RecordDaoAccessGateTest` 把 `spoofProfileDao` 列入本类白名单强制)。
 *
 * - DAO 全量流 → 内存快照([profiles]),UI 列表、每应用绑定解析、MCP 出参同一份;
 * - **每次写后立即同步回读**([refresh]),不等 Flow 发射 ⇒ 「创建完立刻解析」
 *   不存在读写竞态(Flow 只负责外部改动的后续对齐);
 * - [synchronizeBuiltins] 互斥且幂等:守墓碑、守「用户改过」,并**收养**内容完全等价的
 *   既有 DIY 行而非重复建档(基线 `BundledTemplateManager` 同族语义);
 * - 写入面一律先过 [ProfileCodec.isValid]:非法值**拒绝**而非钳制
 *   (与端口写 `PortWriteGuard` 同一纪律)。
 */
class SpoofProfileRepository(
    private val dao: SpoofProfileDao,
    scope: CoroutineScope,
    private val timeMillis: () -> Long = System::currentTimeMillis,
) : SpoofProfileService {

    private val _profiles = MutableStateFlow<List<DeviceProfileEntry>>(emptyList())

    override val profiles: StateFlow<List<DeviceProfileEntry>> = _profiles.asStateFlow()

    private val syncMutex = Mutex()

    @Volatile
    private var builtinSynchronized = false

    init {
        // 收集器容错收场(与 KeywordLibraryRepository 同法):Robolectric 下仓库可能随
        // 宿主沙箱一起消亡(DB 已关而 arch_disk_io 线程仍在),一次发射失败不得向全局
        // 异常通道泄漏并污染同 JVM 后续用例。
        scope.launch {
            runCatching {
                dao.observeAll().collect { rows -> _profiles.value = rows.map { it.toEntry() } }
            }
        }
    }

    // ---------------------------------------------------------------- 读

    override fun find(id: String?): DeviceProfileEntry? {
        val key = id?.trim().orEmpty()
        if (key.isEmpty()) return null
        return _profiles.value.firstOrNull { it.id == key }
    }

    override fun resolve(id: String?): ProfileResolution {
        val key = id?.trim().orEmpty()
        if (key.isEmpty()) return ProfileResolution.Unbound
        return find(key)?.let { ProfileResolution.Bound(it) } ?: ProfileResolution.Missing(key)
    }

    // ---------------------------------------------------------------- 写

    override suspend fun create(name: String, summary: String, profile: DeviceProfile): DeviceProfileEntry? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || profile.isEmpty || !ProfileCodec.isValid(profile)) return null
        val now = timeMillis()
        val entry = DeviceProfileEntry(
            id = newId(),
            name = trimmedName,
            summary = summary.trim(),
            profile = profile,
            createTime = now,
            updateTime = now,
        )
        dao.insert(entry.toEntity())
        refresh()
        return entry
    }

    override suspend fun update(
        id: String,
        name: String,
        summary: String,
        profile: DeviceProfile,
    ): Boolean {
        val existing = dao.getById(id) ?: return false
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || !ProfileCodec.isValid(profile)) return false
        // 内置档一旦被用户改动即脱离账本管辖:seedId 保留(呈现名仍按 seedId 走资源),
        // managed 置 false ⇒ 升级永不再覆盖这一行。
        val editedBuiltin = existing.builtinSeedId != null
        dao.update(
            existing.copy(
                name = trimmedName,
                summary = summary.trim(),
                profileJson = ProfileCodec.encode(profile),
                managed = if (editedBuiltin) false else existing.managed,
                updateTime = timeMillis(),
            ),
        )
        refresh()
        return true
    }

    override suspend fun duplicate(id: String, name: String): DeviceProfileEntry? {
        val source = find(id) ?: return null
        return create(name = name, summary = source.summary, profile = source.profile)
    }

    override suspend fun delete(id: String): Boolean {
        val existing = dao.getById(id) ?: return false
        if (existing.builtinSeedId != null) {
            // 内置档删为墓碑:行留、deleted 置位 ⇒ applyBuiltin 看见即不再补档。
            dao.update(existing.copy(deleted = true, managed = false, updateTime = timeMillis()))
        } else {
            dao.deleteById(id)
        }
        refresh()
        return true
    }

    // ---------------------------------------------------------------- 内置档账本

    override suspend fun synchronizeBuiltins() {
        if (builtinSynchronized) return
        syncMutex.withLock {
            if (!builtinSynchronized) {
                BuiltinDeviceProfiles.BUILTINS.forEach { spec -> applyBuiltin(spec) }
                builtinSynchronized = true
            }
        }
        refresh()
    }

    /** 装配/测试可用:清幂等位后重算内置档(墓碑仍守约)。 */
    internal suspend fun resynchronizeBuiltins() {
        builtinSynchronized = false
        synchronizeBuiltins()
    }

    /** 单档落位:墓碑不动;已脱管不动;受管辖且内容或版本变了原地更新;缺档先收养再新建。 */
    private suspend fun applyBuiltin(spec: BuiltinProfileSpec) {
        val fingerprint = ProfileCodec.fingerprint(spec.profile)
        val json = ProfileCodec.encode(spec.profile)
        dao.getBySeedId(spec.seedId)?.let { existing ->
            when {
                existing.deleted -> return
                !existing.managed -> return
                existing.builtinFingerprint == fingerprint && existing.builtinVersion == spec.version -> return
                else -> dao.update(
                    existing.copy(
                        profileJson = json,
                        builtinVersion = spec.version,
                        builtinFingerprint = fingerprint,
                        updateTime = timeMillis(),
                    ),
                )
            }
            return
        }
        // 收养:库里已有内容完全等价的行 ⇒ 认作内置档,不重复建档。
        val adopt = dao.getAll().firstOrNull { row ->
            ProfileCodec.decode(row.profileJson)
                ?.let { ProfileCodec.sameContent(it, spec.profile) } == true
        }
        if (adopt != null) {
            dao.update(
                adopt.copy(
                    builtinSeedId = spec.seedId,
                    builtinVersion = spec.version,
                    builtinFingerprint = fingerprint,
                    managed = true,
                    deleted = false,
                    updateTime = timeMillis(),
                ),
            )
            return
        }
        val now = timeMillis()
        dao.insert(
            SpoofProfileEntity(
                id = spec.entryId,
                name = "",
                summary = "",
                profileJson = json,
                builtinSeedId = spec.seedId,
                builtinVersion = spec.version,
                builtinFingerprint = fingerprint,
                managed = true,
                deleted = false,
                createTime = now,
                updateTime = now,
            ),
        )
    }

    // ---------------------------------------------------------------- 导入导出

    override fun exportJson(ids: List<String>): String {
        val live = _profiles.value
        val chosen = if (ids.isEmpty()) live else live.filter { it.id in ids }
        return Json.gson.toJson(chosen.toEnvelope())
    }

    private fun List<DeviceProfileEntry>.toEnvelope(): ProfileEnvelope = ProfileEnvelope(
        schemaVersion = ProfileEnvelope.SCHEMA_VERSION,
        exportedAt = timeMillis(),
        items = map { ProfileTransferItem(it.name, it.summary, it.profile) },
    )

    override suspend fun importJson(
        text: String,
        dryRun: Boolean,
        defaultName: String,
    ): ProfileImportOutcome {
        val envelope = ProfileCodec.decodeEnvelope(text)
            ?: return ProfileImportOutcome(schemaRejected = true)
        // 超上限整包拒绝:部分导入会让用户以为「包已生效」,是不可见的半成品状态。
        if (envelope.items.size > ProfileEnvelope.MAX_ITEMS) {
            return ProfileImportOutcome(
                rejected = envelope.items.size,
                verdicts = List(envelope.items.size) { ProfileImportVerdict.REJECTED_LIMIT },
            )
        }
        var imported = 0
        var skipped = 0
        var rejected = 0
        val verdicts = mutableListOf<ProfileImportVerdict>()
        val seen = _profiles.value.map { ProfileCodec.fingerprint(it.profile) }.toMutableSet()
        envelope.items.forEach { item ->
            val fingerprint = if (item.profile.isEmpty) null else ProfileCodec.fingerprint(item.profile)
            val verdict = when {
                item.profile.isEmpty || fingerprint == null -> {
                    rejected++
                    ProfileImportVerdict.REJECTED_EMPTY
                }

                !ProfileCodec.isValid(item.profile) -> {
                    rejected++
                    ProfileImportVerdict.REJECTED_INVALID
                }

                fingerprint in seen -> {
                    skipped++
                    ProfileImportVerdict.DUPLICATE
                }

                else -> {
                    val usedFallback = item.name.isBlank()
                    val created = if (dryRun) {
                        true
                    } else {
                        create(
                            name = item.name.trim().ifEmpty { defaultName },
                            summary = item.summary,
                            profile = item.profile,
                        ) != null
                    }
                    if (created) {
                        imported++
                        seen += fingerprint
                        when {
                            dryRun -> ProfileImportVerdict.DRY_RUN_OK
                            usedFallback -> ProfileImportVerdict.IMPORTED_NAME_FALLBACK
                            else -> ProfileImportVerdict.IMPORTED
                        }
                    } else {
                        rejected++
                        ProfileImportVerdict.REJECTED_INVALID
                    }
                }
            }
            verdicts += verdict
        }
        return ProfileImportOutcome(
            imported = imported,
            skipped = skipped,
            rejected = rejected,
            schemaRejected = false,
            verdicts = verdicts,
        )
    }

    // ---------------------------------------------------------------- 内部

    private suspend fun refresh() {
        _profiles.value = dao.getAll().map { it.toEntry() }
    }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun SpoofProfileEntity.toEntry(): DeviceProfileEntry = DeviceProfileEntry(
        id = id,
        name = name,
        summary = summary,
        profile = ProfileCodec.decode(profileJson) ?: DeviceProfile.EMPTY,
        builtinSeedId = builtinSeedId,
        builtinVersion = builtinVersion,
        managed = managed,
        createTime = createTime,
        updateTime = updateTime,
    )

    private fun DeviceProfileEntry.toEntity(): SpoofProfileEntity = SpoofProfileEntity(
        id = id,
        name = name,
        summary = summary,
        profileJson = ProfileCodec.encode(profile),
        builtinSeedId = null,
        builtinVersion = null,
        builtinFingerprint = null,
        managed = true,
        deleted = false,
        createTime = createTime,
        updateTime = updateTime,
    )
}
