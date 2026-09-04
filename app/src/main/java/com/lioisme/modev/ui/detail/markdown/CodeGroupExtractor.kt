package com.lioisme.modev.ui.detail.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

/**
 * 围栏索引与代码组聚合(V26,纯 JVM 可测):
 *
 * 详情 markdown 由本工程 [RecordMarkdownFactory] 生成,围栏恒为文档顶层块。
 * 本文件在渲染前对解析树做一次线性扫描,产出「文档序围栏清单 + 代码组归属」,
 * 供自定义 codeFence 组件(mikepenz `markdownComponents(codeFence=)` 扩展点):
 * - 组内首围栏渲染整个代码组(标签页);后续成员消费掉不重复发射;
 * - 孤立围栏(单表示 / 非组语言)退化为单个代码卡;
 * - ordinal 与工厂生成顺序严格一致,是 [DetailFenceHandle] 全量文本句柄的寻址键。
 */

/** 单个围栏的解析信息(镜像库 MarkdownCodeFence 的偏移提取逻辑,纯函数化)。 */
@Immutable
class FenceInfo(
    val language: String?,
    val code: String,
)

/** 代码组归属:runFirstOrdinal = 组首围栏的全局序;indexInRun/runSize 定位标签页。 */
@Immutable
data class CodeRunPlacement(
    val runFirstOrdinal: Int,
    val indexInRun: Int,
    val runSize: Int,
)

/** 文档序围栏条目。 */
@Stable
class FenceEntry(
    val ordinal: Int,
    val node: ASTNode,
    val info: FenceInfo?,
    val placement: CodeRunPlacement?,
)

/** 全部围栏索引(一次线性扫描的结果)。 */
@Stable
class MarkdownFenceIndex(entries: List<FenceEntry>) {
    private val entries: List<FenceEntry> = entries
    val size: Int get() = entries.size

    /** 偏移量→条目映射,locate() O(1) 查找(Long key 避免 Pair boxing)。 */
    private val offsetMap: Map<Long, FenceEntry> =
        entries.associateBy {
            (it.node.startOffset.toLong() shl 32) or (it.node.endOffset.toLong() and 0xFFFFFFFFL)
        }

    operator fun get(ordinal: Int): FenceEntry? = entries.getOrNull(ordinal)

    /** 按节点偏移定位(渲染回调只拿得到当前 node)。 */
    fun locate(node: ASTNode): FenceEntry? =
        offsetMap[(node.startOffset.toLong() shl 32) or (node.endOffset.toLong() and 0xFFFFFFFFL)]
}

private val GROUP_LANGUAGES = setOf(MarkdownLangs.TEXT, MarkdownLangs.BASE64, MarkdownLangs.HEX)

fun isCodeFence(node: ASTNode): Boolean = node.type == MarkdownElementTypes.CODE_FENCE

/** 围栏语言(FENCE_LANG 子节点文本;无 info string = null)。 */
fun fenceLanguage(
    node: ASTNode,
    content: String,
): String? = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)?.toString()

/**
 * 提取围栏代码体(与库 MarkdownCodeFence 同一偏移规则:
 * children[2].startOffset .. children[size-2].endOffset,replaceIndent 去公共缩进);
 * 结构非法(children < 3,库同样跳过)返回 null。
 */
fun fenceInfo(
    node: ASTNode,
    content: String,
): FenceInfo? {
    if (!isCodeFence(node)) return null
    val language = fenceLanguage(node, content)
    if (node.children.size < 3) return null
    val start = node.children[2].startOffset
    val minCount = if (language != null && node.children.size > 3) 3 else 2
    val end = node.children[(node.children.size - 2).coerceAtLeast(minCount)].endOffset
    if (end < start) return null
    return FenceInfo(language, content.subSequence(start, end).toString().replaceIndent())
}

private fun isEolLike(node: ASTNode): Boolean =
    node.type == MarkdownTokenTypes.EOL || node.type == MarkdownTokenTypes.WHITE_SPACE

/**
 * 一次性构建文档围栏索引:
 * - 仅扫描顶层(生成器契约:围栏不出现在列表/引用内);
 * - 相邻判定忽略 EOL/空白叶节点(生成器在围栏之间只发空行);
 * - 运行链 = 语言 ∈ [GROUP_LANGUAGES] 且与前一「块级兄弟」为同链围栏的极大连续段;
 *   非组语言围栏自成孤立条目(placement = null,渲染为单代码卡)。
 */
fun buildFenceIndex(
    root: ASTNode,
    content: String,
): MarkdownFenceIndex {
    data class Scan(
        val ordinal: Int,
        val node: ASTNode,
        val info: FenceInfo?,
        val eligible: Boolean,
        val adjacentToPrev: Boolean,
    )

    // 第一遍:块级线性扫描(EOL/空白不中断相邻链)
    val scans = ArrayList<Scan>()
    var ordinal = 0
    var prevBlockWasEligibleFence = false
    root.children.forEach { child ->
        if (isEolLike(child)) return@forEach
        if (isCodeFence(child)) {
            val info = fenceInfo(child, content)
            val lang = info?.language
            val eligible = lang != null && lang in GROUP_LANGUAGES
            scans += Scan(ordinal, child, info, eligible, prevBlockWasEligibleFence)
            ordinal++
            prevBlockWasEligibleFence = eligible
        } else {
            prevBlockWasEligibleFence = false
        }
    }

    // 第二遍:极大连续运行段 → 每围栏一条 FenceEntry(eligible 且成组才有 placement;
    // 孤立 eligible 围栏 placement.runSize == 1,渲染层退化为单代码卡)
    val built = ArrayList<FenceEntry>(scans.size)
    var index = 0
    while (index < scans.size) {
        if (!scans[index].eligible) {
            built += FenceEntry(scans[index].ordinal, scans[index].node, scans[index].info, null)
            index++
            continue
        }
        var end = index
        while (end + 1 < scans.size && scans[end + 1].eligible && scans[end + 1].adjacentToPrev) end++
        for (position in index..end) {
            built +=
                FenceEntry(
                    scans[position].ordinal,
                    scans[position].node,
                    scans[position].info,
                    CodeRunPlacement(
                        runFirstOrdinal = scans[index].ordinal,
                        indexInRun = position - index,
                        runSize = end - index + 1,
                    ),
                )
        }
        index = end + 1
    }
    return MarkdownFenceIndex(built)
}
