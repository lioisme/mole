package com.lioisme.modev.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.ImageRequest
import coil.request.Options

/**
 * 应用图标(V40 T5.4 Coil 化,手搓 LruCache/drawable→Bitmap 转换退役;
 * V42 T2 底图状态驱动):[AsyncImage] + PackageManager Fetcher——Coil 内存
 * 缓存(MemoryCache)承担去重(滚动列表零重复 binder 调用)。
 * 占位首字母块 = **加载中/失败态限定**(AsyncImage onState 驱动,Success 即
 * 移除)——不规则/透明图标加载完成后不再透出底层色块(V42 R2 主诉);
 * 包不存在/加载失败 = 占位即终态,语义与 V13 形态一致。
 * 彩色启动图标由 [AsyncImage] 直绘 Drawable 保证(不经单色 Icon 管线,
 * 无 LocalContentColor 蒙层问题——V13 已知坑),**恒原样绘制不裁切**
 * (尊重不规则图标原生形态)。
 *
 * @param loader 测试注入点(默认 null = 进程级共享加载器,生产零感知)。
 */
@Composable
fun AppIcon(
    packageName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    loader: ImageLoader? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val effectiveLoader = loader ?: remember(context) { appIconLoader(context) }
    val sizePx = with(density) { size.roundToPx() } * 2
    // 占位可见性按包名重置(Lazy 列表行复用切换包名时回到加载态)。
    var showPlaceholder by remember(packageName) { mutableStateOf(true) }
    Box(modifier = modifier.size(size)) {
        if (showPlaceholder) {
            PlaceholderIconUnderlay(packageName, size)
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(PackageIcon(packageName))
                .size(sizePx, sizePx)
                .build(),
            contentDescription = null,
            imageLoader = effectiveLoader,
            onState = { state -> showPlaceholder = state !is AsyncImagePainter.State.Success },
            modifier = Modifier.size(size),
        )
    }
}

/** 占位首字母块(垫底层;仅加载中/失败组合,加载成功即随 [AppIcon] 状态移除)。 */
@Composable
private fun PlaceholderIconUnderlay(packageName: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .testTag("app-icon-placeholder")
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

/** PackageManager 图标键(Coil [Keyer] 缓存键 = 包名)。 */
internal data class PackageIcon(val packageName: String)

/** 缓存键:PackageIcon → "icon:{pkg}"(跨 size 复用同一解码源)。 */
internal object PackageIconKeyer : Keyer<PackageIcon> {
    override fun key(data: PackageIcon, options: Options): String = "icon:${data.packageName}"
}

/**
 * Coil Fetcher:PackageIcon → PackageManager Drawable(主进程内零拷贝,
 * drawable 尺寸交 Coil 缩放;包不存在抛 NameNotFound → AsyncImage 空态)。
 */
internal object PackageIconFetcherFactory : Fetcher.Factory<PackageIcon> {
    override fun create(data: PackageIcon, options: Options, imageLoader: ImageLoader): Fetcher =
        object : Fetcher {
            override suspend fun fetch(): DrawableResult {
                val drawable: Drawable =
                    options.context.packageManager.getApplicationIcon(data.packageName)
                return DrawableResult(drawable, isSampled = false, dataSource = DataSource.DISK)
            }
        }
}

/** 共享加载器(进程级单例,装配 Keyer/Fetcher + 默认内存缓存)。 */
internal fun appIconLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(PackageIconKeyer)
            add(PackageIconFetcherFactory)
        }
        .build()
        .also { Coil.setImageLoader(it) }
