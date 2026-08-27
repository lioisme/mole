package com.lioisme.modev.ui.theme

import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.DeviceEnvKind

fun deviceEnvKindColor(kind: DeviceEnvKind): Color = when (kind) {
    DeviceEnvKind.ROOT_FILE,
    DeviceEnvKind.ROOT_EXEC,
    DeviceEnvKind.ROOT_PACKAGE_MANAGER,
    DeviceEnvKind.ROOT_PROPERTY -> Color(0xFFE53935)

    DeviceEnvKind.XPOSED_CLASS,
    DeviceEnvKind.XPOSED_STACK,
    DeviceEnvKind.XPOSED_MAPS -> Color(0xFFFF9800)

    DeviceEnvKind.EMULATOR_BUILD,
    DeviceEnvKind.EMULATOR_FILE,
    DeviceEnvKind.EMULATOR_SENSOR,
    DeviceEnvKind.EMULATOR_NETWORK -> Color(0xFFFFC107)

    DeviceEnvKind.DETECT_ROOT,
    DeviceEnvKind.DETECT_EMULATOR -> Color(0xFF9E9E9E)
}
