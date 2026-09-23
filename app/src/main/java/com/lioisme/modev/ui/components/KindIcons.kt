package com.lioisme.modev.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.IntegrationInstructions
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.WebViewKind

/**
 * 类别图标映射(V27 M1-T2):记录列表 leading 徽章的 kind→图标单一事实源。
 * 同族内不重复;新增类别必须同步登记。
 */
object KindIcons {

    /** 算法族(CIPHER 按方向区分锁形:解密开锁/其余闭锁)。 */
    fun crypto(
        kind: CryptoKind,
        direction: Int? = null,
    ): ImageVector =
        when (kind) {
            CryptoKind.MESSAGE_DIGEST -> Icons.Outlined.Tag
            CryptoKind.CIPHER ->
                if (direction == 2) Icons.Outlined.LockOpen else Icons.Outlined.EnhancedEncryption
            CryptoKind.MAC -> Icons.Outlined.Fingerprint
            CryptoKind.SIGNATURE -> Icons.Outlined.HistoryEdu
            CryptoKind.KEY_PARAM -> Icons.Outlined.Password
            CryptoKind.KEY_GENERATION -> Icons.Outlined.Code
            CryptoKind.KEY_FACTORY -> Icons.Outlined.Construction
            CryptoKind.KEY_STORE -> Icons.Outlined.Security
            CryptoKind.KEY_AGREEMENT -> Icons.Outlined.SwapHoriz
            CryptoKind.RANDOM -> Icons.Outlined.Casino
            CryptoKind.BASE64 -> Icons.Outlined.DataObject
            CryptoKind.WEB_CRYPTO -> Icons.Outlined.Public
        }

    /** 数据库族。 */
    fun sql(kind: SqlKind): ImageVector =
        when (kind) {
            SqlKind.OPEN -> Icons.Outlined.Storage
            SqlKind.QUERY -> Icons.Outlined.ManageSearch
            SqlKind.DML -> Icons.Outlined.Edit
            SqlKind.EXEC -> Icons.Outlined.Terminal
            SqlKind.TRANSACTION -> Icons.Outlined.SwapHoriz
        }

    /** 行为族(V27)。 */
    fun behavior(kind: BehaviorKind): ImageVector =
        when (kind) {
            BehaviorKind.FILE_READ -> Icons.Outlined.Description
            BehaviorKind.FILE_WRITE -> Icons.Outlined.Save
            BehaviorKind.FILE_DELETE -> Icons.Outlined.Delete
            BehaviorKind.FILE_RENAME -> Icons.Outlined.DriveFileRenameOutline
            BehaviorKind.FILE_LIST -> Icons.Outlined.FolderOpen
            BehaviorKind.ASSET_READ -> Icons.Outlined.Layers
            BehaviorKind.SHELL_EXEC -> Icons.Outlined.IntegrationInstructions
        }

    /** 偏好族(V28)。 */
    fun prefs(kind: PrefsKind): ImageVector =
        when (kind) {
            PrefsKind.OPEN -> Icons.Outlined.Storage
            PrefsKind.GET -> Icons.Outlined.Visibility
            PrefsKind.PUT -> Icons.Outlined.EditNote
            PrefsKind.REMOVE -> Icons.Outlined.RemoveCircleOutline
            PrefsKind.CLEAR -> Icons.Outlined.DeleteSweep
        }

    /** WebView 族(V29 一等公民)。 */
    fun webview(kind: WebViewKind): ImageVector =
        when (kind) {
            WebViewKind.PAGE_LOAD -> Icons.Outlined.Public
            WebViewKind.EVALUATE_JS -> Icons.Outlined.Code
            WebViewKind.JS_BRIDGE -> Icons.Outlined.SwapHoriz
            WebViewKind.DEBUG -> Icons.Outlined.Visibility
            WebViewKind.JS_CRYPTO -> Icons.Outlined.EnhancedEncryption
        }

    /** 网络环境监听族(V30 一等公民)。 */
    fun network(kind: NetworkKind): ImageVector =
        when (kind) {
            NetworkKind.SSL_CONTEXT,
            NetworkKind.SSL_TRUST_MANAGER,
            NetworkKind.SSL_OKHTTP_PINNER,
            NetworkKind.SSL_OKHTTP_VERIFIER,
            NetworkKind.SSL_WEBVIEW,
            NetworkKind.SSL_NETWORK_CONFIG -> Icons.Outlined.Security
            NetworkKind.PROXY_SYSTEM_PROP,
            NetworkKind.PROXY_CONNECTIVITY,
            NetworkKind.PROXY_SETTINGS,
            NetworkKind.PROXY_OKHTTP -> Icons.Outlined.Public
            NetworkKind.VPN_CAPABILITIES,
            NetworkKind.VPN_INTERFACE,
            NetworkKind.VPN_NETWORK_INFO,
            NetworkKind.VPN_LINK_PROPERTIES -> Icons.Outlined.Visibility
            NetworkKind.REQUEST -> Icons.Outlined.ManageSearch
        }

    /** 设备环境监听族(V31 一等公民)。 */
    fun deviceEnv(kind: DeviceEnvKind): ImageVector =
        when (kind) {
            DeviceEnvKind.ROOT_FILE, DeviceEnvKind.ROOT_EXEC,
            DeviceEnvKind.ROOT_PACKAGE_MANAGER, DeviceEnvKind.ROOT_PROPERTY -> Icons.Outlined.Security
            DeviceEnvKind.XPOSED_CLASS, DeviceEnvKind.XPOSED_STACK,
            DeviceEnvKind.XPOSED_MAPS -> Icons.Outlined.Visibility
            DeviceEnvKind.EMULATOR_BUILD, DeviceEnvKind.EMULATOR_FILE,
            DeviceEnvKind.EMULATOR_SENSOR, DeviceEnvKind.EMULATOR_NETWORK,
            DeviceEnvKind.EMULATOR_TELEPHONY, DeviceEnvKind.EMULATOR_CPU,
            DeviceEnvKind.EMULATOR_BATTERY -> Icons.Outlined.Construction
            DeviceEnvKind.SYSTEM_PROPERTIES -> Icons.Outlined.Code
            DeviceEnvKind.PROC_STATUS, DeviceEnvKind.PROC_MOUNT_INFO,
            DeviceEnvKind.PROC_CGROUP, DeviceEnvKind.PROC_NET -> Icons.Outlined.Description
            DeviceEnvKind.DETECT_ROOT, DeviceEnvKind.DETECT_EMULATOR -> Icons.Outlined.ManageSearch
            // 画像强制档:改写而非遮掩,故与条件档不同图标
            DeviceEnvKind.PROFILE_BUILD_IDENTITY, DeviceEnvKind.PROFILE_BUILD_VERSION -> Icons.Outlined.PhoneAndroid
        }

    /** UI 交互监听族(V33 一等公民)。 */
    fun uiInteraction(kind: UiInteractionKind): ImageVector =
        when (kind) {
            UiInteractionKind.DIALOG_SHOW,
            UiInteractionKind.DIALOG_KEYWORD -> Icons.Outlined.Visibility
            UiInteractionKind.WIDGET_TEXT -> Icons.Outlined.EditNote
            UiInteractionKind.ACTIVITY_START,
            UiInteractionKind.ACTIVITY_RESUME -> Icons.Outlined.SwapHoriz
        }

    /** 系统信息监听族(V33 一等公民)。 */
    fun system(kind: SystemKind): ImageVector =
        when (kind) {
            SystemKind.LOGCAT -> Icons.Outlined.Terminal
            SystemKind.APP_INFO -> Icons.Outlined.ManageSearch
            SystemKind.SIGNATURE -> Icons.Outlined.Security
        }

    /** 进程控制监听族(V33 一等公民)。 */
    fun process(kind: ProcessKind): ImageVector =
        when (kind) {
            ProcessKind.EXIT_INTERCEPT -> Icons.Outlined.DeleteSweep
            ProcessKind.CRASH_PREVENT -> Icons.Outlined.HistoryEdu
            ProcessKind.SCREEN_RECORD -> Icons.Outlined.Visibility
        }
}
