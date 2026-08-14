package com.lioisme.modev.data.repo

import com.google.gson.Gson
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.db.CryptoRecordDao
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.server.ConfigServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 日志仓库:接收注入侧记录 → 入库(IO 线程)→ 容量治理 → 供 UI 订阅。
 */
class LogRepository(
    private val dao: CryptoRecordDao,
    server: ConfigServer,
    private val maxRecords: Int = MAX_RECORDS,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val writeMutex = Mutex()

    /** 自增计数,每写入 [trimEvery] 条执行一次容量清理。 */
    private var inserted = 0L

    /** 每写入 [trimEvery] 条执行一次容量清理。 */
    private val trimEvery = 500L

    init {
        server.onRecord = { record ->
            scope.launch { insert(record) }
        }
    }

    suspend fun insert(record: CryptoRecord) {
        writeMutex.withLock {
            dao.insert(record.toEntity(gson))
            inserted++
            if (inserted % trimEvery == 0L) {
                dao.trim(maxRecords)
            }
        }
    }

    fun observe(
        packageName: String? = null,
        kind: CryptoKind? = null,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<CryptoRecord>> =
        dao.observe(packageName, kind?.name, limit).map { list -> list.map { it.toModel(gson) } }

    fun observePackages(): Flow<List<String>> = dao.observePackages()

    suspend fun clear(packageName: String) = dao.clearPackage(packageName)

    suspend fun clearAll() = dao.clearAll()

    private fun CryptoRecord.toEntity(gson: Gson) = CryptoRecordEntity(
        packageName = packageName,
        kind = kind.name,
        operation = operation.name,
        algorithm = algorithm,
        provider = provider,
        direction = direction,
        keyHex = key?.hex,
        keyText = key?.text,
        keyLength = key?.totalLength,
        keyTruncated = key?.truncated ?: false,
        ivHex = iv?.hex,
        ivText = iv?.text,
        ivLength = iv?.totalLength,
        ivTruncated = iv?.truncated ?: false,
        keyAlias = keyAlias,
        keyExportable = keyExportable,
        inputHex = input?.hex,
        inputText = input?.text,
        inputLength = input?.totalLength,
        inputTruncated = input?.truncated ?: false,
        outputHex = output?.hex,
        outputText = output?.text,
        outputLength = output?.totalLength,
        outputTruncated = output?.truncated ?: false,
        contextId = contextId,
        threadName = threadName,
        stackJson = if (stack.isEmpty()) null else gson.toJson(stack),
        timestamp = timestamp,
    )

    private fun CryptoRecordEntity.toModel(gson: Gson) = CryptoRecord(
        id = id,
        packageName = packageName,
        kind = enumOf(CryptoKind::class.java, kind),
        operation = enumOf(CryptoOperation::class.java, operation),
        algorithm = algorithm,
        provider = provider,
        direction = direction,
        key = view(keyHex, keyText, keyLength, keyTruncated),
        iv = view(ivHex, ivText, ivLength, ivTruncated),
        keyAlias = keyAlias,
        keyExportable = keyExportable,
        input = view(inputHex, inputText, inputLength, inputTruncated),
        output = view(outputHex, outputText, outputLength, outputTruncated),
        contextId = contextId,
        threadName = threadName,
        stack = stackJson?.let { runCatching { gson.fromJson(it, Array<String>::class.java).toList() }.getOrNull() } ?: emptyList(),
        timestamp = timestamp,
    )

    private fun view(hex: String?, text: String?, length: Int?, truncated: Boolean): BytesView? =
        if (hex == null) null else BytesView(hex = hex, text = text, totalLength = length ?: 0, truncated = truncated)

    private inline fun <reified T : Enum<T>> enumOf(clazz: Class<T>, name: String): T =
        runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrDefault(clazz.enumConstants.first())

    companion object {
        private const val MAX_RECORDS = 20_000
        private const val DEFAULT_LIMIT = 2_000
    }
}