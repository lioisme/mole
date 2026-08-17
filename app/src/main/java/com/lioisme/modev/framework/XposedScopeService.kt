package com.lioisme.modev.framework

import com.lioisme.modev.domain.scope.ScopeRequestCallback
import com.lioisme.modev.domain.scope.ScopeService
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [XposedService] → [ScopeService] 适配器:隔离框架类,仓库层零框架依赖。
 */
class XposedScopeService(private val delegate: XposedService) : ScopeService {

    override val frameworkName: String
        get() = delegate.frameworkName

    override val frameworkVersion: String
        get() = delegate.frameworkVersion

    override suspend fun getScope(): List<String> = withContext(Dispatchers.IO) {
        delegate.scope.toList()
    }

    override fun requestScope(packages: List<String>, callback: ScopeRequestCallback) {
        delegate.requestScope(packages, object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                callback.onApproved()
            }

            override fun onScopeRequestFailed(message: String) {
                callback.onFailed(message)
            }
        })
    }

    override suspend fun removeScope(packages: List<String>) = withContext(Dispatchers.IO) {
        delegate.removeScope(packages)
    }
}