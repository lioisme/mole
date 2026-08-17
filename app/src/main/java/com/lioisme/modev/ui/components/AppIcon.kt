package com.lioisme.modev.ui.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用图标缓存:滚动列表避免反复 PackageManager binder 调用与重复位图转换。
 */
object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(128)

    fun get(pm: PackageManager, packageName: String, sizePx: Int): ImageBitmap? =
        cache.get(packageName) ?: runCatching {
            pm.getApplicationIcon(packageName).toBitmap(sizePx)
        }.getOrNull()?.also { cache.put(packageName, it) }

    private fun Drawable.toBitmap(sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, sizePx, sizePx)
        draw(canvas)
        return bitmap.asImageBitmap()
    }
}

/**
 * 应用图标:后台加载 + 缓存;加载中/失败时显示占位首字母块。
 */
@Composable
fun AppIcon(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            AppIconCache.get(context.packageManager, packageName, sizePx)
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Icon(
            bitmap = bitmap,
            contentDescription = null,
            // 彩色启动图标必须 Color.Unspecified,否则 LocalContentColor 默认值
            // (深色主题≈白)会对位图叠加单色蒙层,导致图标全白不可读。
            tint = Color.Unspecified,
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                packageName.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}