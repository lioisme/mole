package com.lioisme.modev.ui.apps

import com.lioisme.modev.R
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.WebViewKind

/**
 * kind id → 设置页类别开关标签资源(单一事实源,V37 T3.4 自监听页提取;
 * 监听页 LISTEN 类别与功能页 FEATURE 类别共用)。
 * 偏好 OPEN/CLEAR 与 SQL kind 名碰撞,必须前置匹配(契约 22)。
 */
internal object KindLabelRes {

    fun resolve(kindId: String): Int = when (kindId) {
        // 偏好类别(V28):OPEN/CLEAR 与 SQL kind 名碰撞,必须前置匹配
        PrefsKind.OPEN.name -> R.string.kind_prefs_open
        PrefsKind.GET.name -> R.string.kind_prefs_get
        PrefsKind.PUT.name -> R.string.kind_prefs_put
        PrefsKind.REMOVE.name -> R.string.kind_prefs_remove
        PrefsKind.CLEAR.name -> R.string.kind_prefs_clear
        "MESSAGE_DIGEST" -> R.string.switch_message_digest
        "CIPHER" -> R.string.switch_cipher
        "MAC" -> R.string.switch_mac
        "SIGNATURE" -> R.string.switch_signature
        "KEY_PARAM" -> R.string.switch_key_param
        "KEY_GENERATION" -> R.string.switch_key_generation
        "KEY_FACTORY" -> R.string.switch_key_factory
        "KEY_STORE" -> R.string.switch_key_store
        "KEY_AGREEMENT" -> R.string.switch_key_agreement
        "RANDOM" -> R.string.switch_random
        "BASE64" -> R.string.switch_base64
        "WEB_CRYPTO" -> R.string.switch_webview
        "OPEN" -> R.string.switch_db_open
        "QUERY" -> R.string.switch_db_query
        "DML" -> R.string.switch_db_dml
        "EXEC" -> R.string.switch_db_exec
        "TRANSACTION" -> R.string.switch_db_transaction
        // 行为类别(V27):复用 kind 标签单一事实源(设置页行标签 = 筛选/详情同文案)
        BehaviorKind.FILE_READ.name -> R.string.kind_behavior_file_read
        BehaviorKind.FILE_WRITE.name -> R.string.kind_behavior_file_write
        BehaviorKind.FILE_DELETE.name -> R.string.kind_behavior_file_delete
        BehaviorKind.FILE_RENAME.name -> R.string.kind_behavior_file_rename
        BehaviorKind.FILE_LIST.name -> R.string.kind_behavior_file_list
        BehaviorKind.ASSET_READ.name -> R.string.kind_behavior_asset_read
        BehaviorKind.SHELL_EXEC.name -> R.string.kind_behavior_shell_exec
        // WebView 类别(V29 一等公民)
        WebViewKind.PAGE_LOAD.name -> R.string.kind_wv_page_load
        WebViewKind.EVALUATE_JS.name -> R.string.kind_wv_evaluate_js
        WebViewKind.JS_BRIDGE.name -> R.string.kind_wv_js_bridge
        WebViewKind.DEBUG.name -> R.string.kind_wv_debug
        WebViewKind.JS_CRYPTO.name -> R.string.kind_wv_js_crypto
        // 网络环境类别(V30 一等公民)
        NetworkKind.SSL_CONTEXT.name -> R.string.kind_net_ssl_context
        NetworkKind.SSL_TRUST_MANAGER.name -> R.string.kind_net_ssl_trust_manager
        NetworkKind.SSL_OKHTTP_PINNER.name -> R.string.kind_net_ssl_okhttp_pinner
        NetworkKind.SSL_OKHTTP_VERIFIER.name -> R.string.kind_net_ssl_okhttp_verifier
        NetworkKind.SSL_WEBVIEW.name -> R.string.kind_net_ssl_webview
        NetworkKind.SSL_NETWORK_CONFIG.name -> R.string.kind_net_ssl_network_config
        NetworkKind.PROXY_SYSTEM_PROP.name -> R.string.kind_net_proxy_system_prop
        NetworkKind.PROXY_CONNECTIVITY.name -> R.string.kind_net_proxy_connectivity
        NetworkKind.PROXY_SETTINGS.name -> R.string.kind_net_proxy_settings
        NetworkKind.PROXY_OKHTTP.name -> R.string.kind_net_proxy_okhttp
        NetworkKind.VPN_CAPABILITIES.name -> R.string.kind_net_vpn_capabilities
        NetworkKind.VPN_INTERFACE.name -> R.string.kind_net_vpn_interface
        NetworkKind.VPN_NETWORK_INFO.name -> R.string.kind_net_vpn_network_info
        NetworkKind.VPN_LINK_PROPERTIES.name -> R.string.kind_net_vpn_link_properties
        NetworkKind.REQUEST.name -> R.string.kind_net_request
        // 设备环境类别(V31 一等公民;V35 补全 momo 增强 9 项)
        DeviceEnvKind.ROOT_FILE.name -> R.string.kind_device_env_ROOT_FILE
        DeviceEnvKind.ROOT_EXEC.name -> R.string.kind_device_env_ROOT_EXEC
        DeviceEnvKind.ROOT_PACKAGE_MANAGER.name -> R.string.kind_device_env_ROOT_PACKAGE_MANAGER
        DeviceEnvKind.ROOT_PROPERTY.name -> R.string.kind_device_env_ROOT_PROPERTY
        DeviceEnvKind.XPOSED_CLASS.name -> R.string.kind_device_env_XPOSED_CLASS
        DeviceEnvKind.XPOSED_STACK.name -> R.string.kind_device_env_XPOSED_STACK
        DeviceEnvKind.XPOSED_MAPS.name -> R.string.kind_device_env_XPOSED_MAPS
        DeviceEnvKind.EMULATOR_BUILD.name -> R.string.kind_device_env_EMULATOR_BUILD
        DeviceEnvKind.EMULATOR_FILE.name -> R.string.kind_device_env_EMULATOR_FILE
        DeviceEnvKind.EMULATOR_SENSOR.name -> R.string.kind_device_env_EMULATOR_SENSOR
        DeviceEnvKind.EMULATOR_NETWORK.name -> R.string.kind_device_env_EMULATOR_NETWORK
        DeviceEnvKind.EMULATOR_TELEPHONY.name -> R.string.kind_device_env_EMULATOR_TELEPHONY
        DeviceEnvKind.EMULATOR_CPU.name -> R.string.kind_device_env_EMULATOR_CPU
        DeviceEnvKind.EMULATOR_BATTERY.name -> R.string.kind_device_env_EMULATOR_BATTERY
        DeviceEnvKind.SYSTEM_PROPERTIES.name -> R.string.kind_device_env_SYSTEM_PROPERTIES
        DeviceEnvKind.PROC_STATUS.name -> R.string.kind_device_env_PROC_STATUS
        DeviceEnvKind.PROC_MOUNT_INFO.name -> R.string.kind_device_env_PROC_MOUNT_INFO
        DeviceEnvKind.PROC_CGROUP.name -> R.string.kind_device_env_PROC_CGROUP
        DeviceEnvKind.PROC_NET.name -> R.string.kind_device_env_PROC_NET
        DeviceEnvKind.DETECT_ROOT.name -> R.string.kind_device_env_DETECT_ROOT
        DeviceEnvKind.DETECT_EMULATOR.name -> R.string.kind_device_env_DETECT_EMULATOR
        // UI 交互类别(V33 一等公民)
        UiInteractionKind.DIALOG_SHOW.name -> R.string.kind_ui_DIALOG_SHOW
        UiInteractionKind.DIALOG_KEYWORD.name -> R.string.kind_ui_DIALOG_KEYWORD
        UiInteractionKind.WIDGET_TEXT.name -> R.string.kind_ui_WIDGET_TEXT
        UiInteractionKind.ACTIVITY_START.name -> R.string.kind_ui_ACTIVITY_START
        UiInteractionKind.ACTIVITY_RESUME.name -> R.string.kind_ui_ACTIVITY_RESUME
        // 系统信息类别(V33 一等公民)
        SystemKind.LOGCAT.name -> R.string.kind_system_LOGCAT
        SystemKind.APP_INFO.name -> R.string.kind_system_APP_INFO
        SystemKind.SIGNATURE.name -> R.string.kind_system_SIGNATURE
        // 进程控制类别(V33 一等公民)
        ProcessKind.EXIT_INTERCEPT.name -> R.string.kind_process_EXIT_INTERCEPT
        ProcessKind.CRASH_PREVENT.name -> R.string.kind_process_CRASH_PREVENT
        ProcessKind.SCREEN_RECORD.name -> R.string.kind_process_SCREEN_RECORD
        else -> R.string.unknown_error
    }
}
