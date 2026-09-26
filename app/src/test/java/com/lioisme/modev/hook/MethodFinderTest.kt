package com.lioisme.modev.hook

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 反射定位门面契约(T4.1):注入侧所有钩子装配都经 [MethodFinder] 取 `Method`/`Constructor`,
 * 它的三条语义决定了「装不上就静默跳过、绝不误伤宿主」是否成立 ——
 * ①**只按声明类精确定位**(`getDeclaredMethod`,不含继承来的方法);
 * ②**只走 bootstrap 装载器**(`Class.forName(name, false, null)`,目标恒为框架类);
 * ③**命中即缓存同一实例**(热路径重复解析零反射)。
 * 三条此前无任何测试锁定;本类同时把「参数类型缺省即不出键」的归一口径钉住。
 */
class MethodFinderTest {

    @Test
    fun resolves_declared_method_with_primitive_parameter() {
        val m = MethodFinder.find("java.lang.String", "charAt", Int::class.javaPrimitiveType)
        assertNotNull("charAt(int) 必须可精确定位", m)
        assertEquals("charAt", m!!.name)
        assertEquals(1, m.parameterCount)
    }

    @Test
    fun hit_is_cached_as_the_same_reflective_instance() {
        val first = MethodFinder.find("java.lang.String", "length")
        val second = MethodFinder.find("java.lang.String", "length")
        assertNotNull(first)
        // 缓存的意义 = 安装期与运行期拿到同一对象(反射枚举不重复发生)。
        assertSame("同键二次解析必须命中缓存实例", first, second)
    }

    @Test
    fun null_param_types_are_dropped_from_the_lookup_key() {
        // 平台可空标注(`Int::class.javaPrimitiveType` 之类)传 null 时等价于「该参数不存在」,
        // 归一后必须与显式无参解析同键同结果。
        val withNull = MethodFinder.find("java.lang.String", "length", null, null)
        val bare = MethodFinder.find("java.lang.String", "length")
        assertNotNull(bare)
        assertSame("null 参数必须被滤除而非参与签名", bare, withNull)
    }

    @Test
    fun distinct_overloads_do_not_share_cache_entries() {
        val byInt = MethodFinder.find("java.lang.String", "valueOf", Int::class.javaPrimitiveType)
        val byObject = MethodFinder.find("java.lang.String", "valueOf", Any::class.java)
        assertNotNull(byInt)
        assertNotNull(byObject)
        // 键含参数类型全名:两个重载若撞键,后者会被前者静默顶替(钩错方法且零异常)。
        assertSame(byInt, MethodFinder.find("java.lang.String", "valueOf", Int::class.javaPrimitiveType))
        assertSame(byObject, MethodFinder.find("java.lang.String", "valueOf", Any::class.java))
        assertEquals(1, byInt!!.parameterCount)
        assertEquals(1, byObject!!.parameterCount)
        assertEquals(Int::class.javaPrimitiveType, byInt.parameterTypes[0])
        assertEquals(Any::class.java, byObject.parameterTypes[0])
    }

    @Test
    fun inherited_method_is_not_resolved_on_the_declaring_class() {
        // `wait` 声明在 Object 而非 String ⇒ getDeclaredMethod 语义下必须为 null。
        // 生产契约:钩子必须挂在**真实声明类**上(挂子类会静默不触发)。
        assertNull(MethodFinder.find("java.lang.String", "wait"))
    }

    @Test
    fun missing_method_returns_null_without_throwing() {
        assertNull(MethodFinder.find("java.lang.String", "modevNoSuchMethod"))
    }

    @Test
    fun missing_class_returns_null_and_is_repeatable() {
        assertNull(MethodFinder.find("com.lioisme.modev.hook.NoSuchFrameworkClass", "anything"))
        assertNull("缺席类不得因缓存写坏而二次抛错", MethodFinder.find("com.lioisme.modev.hook.NoSuchFrameworkClass", "anything"))
    }

    @Test
    fun bootstrap_only_lookup_skips_app_classloader_targets() {
        // 模块自身类不在 bootstrap 装载器上 ⇒ 恒 null(需要 app dex 类的路径走
        // ProbeContext.appClassLoader,不经本门面)。
        assertNull(MethodFinder.find("com.lioisme.modev.hook.MethodFinder", "find"))
    }

    @Test
    fun resolves_private_declared_constructor() {
        val ctor = MethodFinder.findCtor("java.lang.Math")
        assertNotNull("私有声明构造器也必须可定位(getDeclared 语义)", ctor)
        assertEquals(0, ctor!!.parameterCount)
        assertNull("必须走 getDeclared 而非 getConstructor ⇒ 该构造器确为私有", if (Modifier.isPublic(ctor.modifiers)) ctor else null)
    }

    @Test
    fun constructor_cache_is_keyed_by_param_types() {
        val seqCtor = MethodFinder.findCtor("java.util.ArrayList", Int::class.javaPrimitiveType)
        val emptyCtor = MethodFinder.findCtor("java.util.ArrayList")
        assertNotNull(seqCtor)
        assertNotNull(emptyCtor)
        assertSame(seqCtor, MethodFinder.findCtor("java.util.ArrayList", Int::class.javaPrimitiveType))
        assertEquals(1, seqCtor!!.parameterCount)
        assertEquals(0, emptyCtor!!.parameterCount)
    }

    @Test
    fun missing_constructor_returns_null_without_throwing() {
        assertNull(MethodFinder.findCtor("java.lang.Math", Void.TYPE))
    }
}
