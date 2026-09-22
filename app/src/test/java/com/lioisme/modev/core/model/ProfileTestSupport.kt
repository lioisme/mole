package com.lioisme.modev.core.model

/**
 * V47 T1 测试夹具:每个画像字段的**合法样本值**。
 *
 * 样本必须同时满足三条:通过 [ProfileFieldCatalog.validate]、使 [ProfileField.isSetIn] 为真、
 * 且经 [ProfileField.withText] 写入后 [ProfileField.stringOf] 原样回读(编辑态往返稳定)。
 */
internal fun legalSample(field: ProfileField): String = when (field) {
    ProfileField.BRAND -> "google"
    ProfileField.MANUFACTURER -> "Google"
    ProfileField.MODEL -> "Pixel 7 Pro"
    ProfileField.PRODUCT -> "raven"
    ProfileField.DEVICE -> "raven"
    ProfileField.BOARD -> "raven"
    ProfileField.HARDWARE -> "raven"
    ProfileField.BUILD_ID -> "UP1A.231105.001"
    ProfileField.FINGERPRINT -> "google/raven/raven:14/UP1A.231105.001/10754064:user/release-keys"
    ProfileField.ANDROID_VERSION -> "14"
    ProfileField.SDK_INT -> "34"
    ProfileField.DENSITY_DPI -> "420"
    ProfileField.CPU_CORES -> "8"
    ProfileField.NETWORK_TYPE -> "1"
    ProfileField.WIFI_SSID -> "CafeNet"
    ProfileField.WIFI_BSSID -> "02:00:00:00:00:02"
    ProfileField.WIFI_MAC_ADDRESS -> "02:00:00:00:00:02"
    ProfileField.SIM_OPERATOR -> "46000"
    ProfileField.SIM_OPERATOR_NAME -> "China Mobile"
    ProfileField.SIM_COUNTRY -> "CN"
    ProfileField.ANDROID_ID -> "a1b2c3d4e5f60718"
    ProfileField.IMEI -> "490171581696172"
    ProfileField.PHONE_NUM -> "13800000000"
    ProfileField.LAC -> "45"
    ProfileField.CID -> "123"
    ProfileField.LONGITUDE -> "13.4"
    ProfileField.LATITUDE -> "52.5"
    ProfileField.RANDOM_OFFSET -> "true"
    ProfileField.WIFI_LOCATION_HIDE -> "true"
    ProfileField.CELL_LOCATION_HIDE -> "true"
    ProfileField.LANGUAGE -> "zh_CN"
    ProfileField.TIME_ZONE -> "Asia/Shanghai"
    ProfileField.VERSION_CODE -> "100"
    ProfileField.VERSION_NAME -> "1.2.3"
    ProfileField.BATTERY_LEVEL -> "66"
    ProfileField.SCREENSHOTS_FLAG -> "0"
    ProfileField.PASS_CONTACTS -> "true"
    ProfileField.PASS_PHOTO -> "true"
    ProfileField.PASS_VIDEO -> "true"
    ProfileField.PASS_AUDIO -> "true"
    ProfileField.PASS_APPLICATIONS -> "true"
}
