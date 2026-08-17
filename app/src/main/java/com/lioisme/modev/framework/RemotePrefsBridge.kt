package com.lioisme.modev.framework

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.StateFlow

/**
 * 现代框架远程偏好桥:任意进程(宿主或注入)注册 [XposedServiceHelper] 监听,
 * 获取 [XposedService] 并访问 RemotePreferences(框架 DB)。
 *
 * - 宿主进程([ModevApp])写入配置镜像与令牌;注入进程([ModevModule])只读配置 + 变更监听;
 * - legacy 变体不含 service 库(compileOnly),全部访问经 [runCatching] 兜底后自然降级为 null;
 * - 配置 key 统一前缀 [KEY_SWITCH_PREFIX],令牌 key 见 [TOKEN_KEY]。
 */
object RemotePrefsBridge {

    const val PREFS_KEY = "modev_config"
    const val TOKEN_KEY = "token"
    const val KEY_SWITCH_PREFIX = "switch_"

    /** ��ǰ���õĿ�ܷ���(�ɿ� = ���ִ���ܷ���)�� */
    val serviceFlow: StateFlow<XposedService?> = FrameworkBridge.serviceFlow

    private val registered = AtomicBoolean(false)

    /** �ݵ�ע��(�ظ����ð�ȫ,���״���Ч)�� */
    fun ensureRegistered() {
        // ͳһ�� FrameworkBridge ע�� XposedServiceHelper ������
        // (libxposed-service ���������� 1 �� listener,�����˸��ŵ��)
        if (registered.getAndSet(true)) return
        FrameworkBridge.ensureRegistered()
    }

    /** ��ǰ���̵Ŀ�ܷ���(�ɿ�)�� */
    fun currentService(): XposedService? = FrameworkBridge.serviceFlow.value

    /** 当前进程可用的远程偏好;无服务或访问失败时返回 null。 */
    fun prefs(): SharedPreferences? =
        currentService()?.let { svc -> runCatching { svc.getRemotePreferences(PREFS_KEY) }.getOrNull() }

    /** 握手令牌(host 生成写入;无服务时 null)。 */
    fun token(): String? = prefs()?.getString(TOKEN_KEY, null)

    /** 包名 → 远程配置 key。 */
    fun switchKey(packageName: String): String = KEY_SWITCH_PREFIX + packageName
}