package com.lioisme.modev.ui.settings.profiles

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lioisme.modev.ui.navigation.ModevRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 画像库 → 参数编辑器的**返回栈**回归(V47 WP-5 强约束 #7)。
 *
 * 「新屏是压栈目的地且能返回父级」不是一条落地 URI 断言能证明的:路由串相同而栈语义不同
 * (例如编辑器被并进同一条 `settings/profiles` 分支 = 进去即替换而非压栈),
 * 在落地断言下完全隐形。故本测试直接持 [NavHostController],以与生产同款的
 * 路由常量与参数声明驱动,逐点断言**栈深**与真实 pop 的结果。
 *
 * 注:`NavHostController.canPop` 在本仓锁定的 navigation 2.8.9 上不存在
 * (javap 实锤:NavController 只有 popBackStack / currentBackStack:StateFlow<List<NavBackStackEntry>>),
 * 故 [depth] 取 `currentBackStack` 长度 —— 与 canPop 同一个事实,且更的诊断信息(能看到几层)。
 *
 * 内置档主键带 `builtin:` 前缀 ⇒ 编码往返必须成立(编解码不往返 = 打开内置档会落到
 * 一条不存在的目的地,这条一并钉住)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileBackStackRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private var controller: NavHostController? = null

    /** 栈深(= canPop 的等价判据:深度 > 1 才可返回父级)。 */
    private fun NavHostController.depth(): Int = currentBackStack.value.size

    private fun host(start: String) {
        compose.setContent {
            val nav = rememberNavController()
            controller = nav
            NavHost(navController = nav, startDestination = start) {
                composable(ModevRoutes.SPOOF_PROFILES) {
                    Text("library-anchor", modifier = Modifier.testTag("nav-library"))
                }
                composable(
                    route = ModevRoutes.PROFILE_EDITOR,
                    arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
                ) { entry ->
                    val id = ModevRoutes.dec(entry.arguments?.getString("profileId"))
                    Text("editor:$id", modifier = Modifier.testTag("nav-editor"))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun openingTheEditor_pushesAndCanBePopped() {
        host(ModevRoutes.SPOOF_PROFILES)
        val nav = requireNotNull(controller)
        // navigation 2.8 起 NavHost 为自身插入一条空导航器根条目 ⇒ 库页栈深实测 2。
        // 断言全部相对起点栈深做算术:要证的是「压栈 +1 / 弹栈复原」这层语义,
        // 把框架内部条目数写进期望值只会随升级假红。
        val startDepth = nav.depth()
        assertTrue("库页自身必须在栈上", startDepth >= 1)

        nav.navigate(ModevRoutes.profileEditor("builtin:pixel-7-pro"))
        compose.waitForIdle()
        assertEquals("编辑器必须是压栈目的地(可返回画像库)", startDepth + 1, nav.depth())
        compose.onAllNodesWithText("editor:builtin:pixel-7-pro")
            .fetchSemanticsNodes().let { assertTrue("编辑器目的地未组合", it.isNotEmpty()) }

        // 真实返回路径:popGuarded 底层即 popBackStack;回落后库页重新在屏且栈回到一层。
        assertTrue(nav.popBackStack())
        compose.waitForIdle()
        assertEquals("返回后不得仍留在编辑器栈上", startDepth, nav.depth())
        compose.onAllNodesWithText("library-anchor").fetchSemanticsNodes()
            .let { assertTrue("返回后库页未回到屏上", it.isNotEmpty()) }
    }

    @Test
    fun openingTheEditorTwice_stacksPerProfileId() {
        host(ModevRoutes.SPOOF_PROFILES)
        val nav = requireNotNull(controller)
        val startDepth = nav.depth()
        nav.navigate(ModevRoutes.profileEditor("aaa"))
        compose.waitForIdle()
        nav.navigate(ModevRoutes.profileEditor("builtin:xiaomi-13"))
        compose.waitForIdle()
        assertEquals(startDepth + 2, nav.depth()) // 库页 + 两份编辑器
        assertTrue(nav.popBackStack())
        compose.waitForIdle()
        assertEquals(startDepth + 1, nav.depth())
        // 弹一次回到第一份画像的编辑器,而不是直接掉回库页(逐屏返回)。
        compose.onAllNodesWithText("editor:aaa").fetchSemanticsNodes()
            .let { assertTrue("逐屏返回应停在上一份画像的编辑器", it.isNotEmpty()) }
    }

    @Test
    fun routeEncoding_roundTripsTheLibraryKey() {
        val id = "builtin:galaxy-s23-ultra"
        val route = ModevRoutes.profileEditor(id)
        assertEquals("settings/profiles/builtin%3Agalaxy-s23-ultra", route)
        assertEquals(id, ModevRoutes.dec(route.substringAfterLast('/')))
    }

    /**
     * 草稿路由(新建):名称随主键位携带,前缀把它与真实画像(32 位十六进制 /
     * `builtin:<seedId>`)隔开 —— 库里因此**永远不存在**一行「草稿画像」。
     */
    @Test
    fun draftRoute_roundTripsTheNameAndIsRecognisedAsADraft() {
        val name = "我的 档:第二份"
        val route = ModevRoutes.profileDraft(name)
        val id = ModevRoutes.dec(route.substringAfterLast('/'))
        assertEquals("draft:我的 档:第二份", id)
        assertEquals(name, ModevRoutes.draftNameOf(id))
        assertNull("DIY 主键不得被认成草稿", ModevRoutes.draftNameOf("0f4c1a2b3c4d5e6f7a8b9c0d1e2f3a4b"))
        assertNull("内置主键不得被认成草稿", ModevRoutes.draftNameOf("builtin:pixel-7-pro"))
        // 草稿与真实画像共用同一条目的地路由(单段参数 ⇒ 不会与库页本身撞车)。
        assertTrue(route.startsWith("settings/profiles/"))
        assertEquals(2, route.count { it == '/' })
    }

    @Test
    fun diyAndBuiltinKeysProduceDistinctRoutes() {
        val diy = ModevRoutes.profileEditor("0f4c1a2b3c4d5e6f7a8b9c0d1e2f3a4b")
        val builtin = ModevRoutes.profileEditor("builtin:xiaomi-13")
        assertFalse(diy == builtin)
        assertTrue(diy.startsWith("settings/profiles/"))
        assertTrue(builtin.startsWith("settings/profiles/"))
    }

    /** 生产路由常量必须是本测试驱动的那两条(改常量而测试未跟着走 = 静默失效)。 */
    @Test
    fun productionRouteStringsMatchTheDrivenGraph() {
        assertEquals("settings/profiles", ModevRoutes.SPOOF_PROFILES)
        assertEquals("settings/profiles/{profileId}", ModevRoutes.PROFILE_EDITOR)
    }
}
