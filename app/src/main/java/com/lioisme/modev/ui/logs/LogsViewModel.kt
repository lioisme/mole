package com.lioisme.modev.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.data.repo.LogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 日志页:包/类别/关键字三级过滤,支持详情(含堆栈)与清空。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModel(
    private val logs: LogRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedKind = MutableStateFlow<CryptoKind?>(null)
    private val selectedPackage = MutableStateFlow<String?>(null)

    /** 出现过的包列表(日志页下拉过滤)。 */
    val packages: StateFlow<List<String>> = logs.observePackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 过滤后的记录列表。 */
    val records: StateFlow<List<CryptoRecord>> = combine(
        selectedPackage.flatMapLatest { pkg -> logs.observe(pkg, null, LIMIT) },
        query,
        selectedKind,
    ) { records, q, kind ->
        records
            .filter { kind == null || it.kind == kind }
            .filter { q.isBlank() || matches(it, q) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun matches(record: CryptoRecord, q: String): Boolean =
        record.algorithm.contains(q, ignoreCase = true) ||
            record.operation.name.contains(q, ignoreCase = true) ||
            record.key?.text?.contains(q, ignoreCase = true) == true ||
            record.key?.hex?.contains(q, ignoreCase = true) == true ||
            record.input?.text?.contains(q, ignoreCase = true) == true ||
            record.input?.hex?.contains(q, ignoreCase = true) == true ||
            record.output?.text?.contains(q, ignoreCase = true) == true ||
            record.output?.hex?.contains(q, ignoreCase = true) == true ||
            record.threadName.contains(q, ignoreCase = true)

    fun setQuery(value: String) {
        query.value = value
    }

    fun setKind(kind: CryptoKind?) {
        selectedKind.value = kind
    }

    fun setPackage(packageName: String?) {
        selectedPackage.value = packageName
    }

    fun clearAll() {
        viewModelScope.launch { logs.clearAll() }
    }

    companion object {
        private const val LIMIT = 2_000
    }
}

class LogsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LogsViewModel(container.logs) as T
    }
}