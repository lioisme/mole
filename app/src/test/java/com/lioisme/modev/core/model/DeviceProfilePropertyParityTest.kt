package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V47 T1 漂移围栏:[ProfileField] 目录与 [DeviceProfile] 属性集的**双向一一对账**。
 *
 * 生产代码零反射(编译期穷尽 `when` 保证同步),本测试用反射从**外部视角**证明
 * 「目录声明的字段」与「模型真实存在的属性」完全等价 —— 单侧新增字段必红:
 * 加属性不加枚举 ⇒ 属性无对应字段;加枚举不加属性 ⇒ jsonKey 反查不到属性。
 *
 * 同时锁死 Gson 安全前提:**全参数默认值 ⇒ Kotlin 生成无参构造器**
 * (AGENTS 实锤:无 no-arg 构造器时 Gson 走 `Unsafe.allocateInstance` 跳过默认值表达式,
 * 使「可选字段缺省」静默等价 null)。
 */
class DeviceProfilePropertyParityTest {

    private val propertyNames: Set<String> =
        DeviceProfile::class.java.declaredFields
            .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

    @Test
    fun `model declares exactly the catalog fields`() {
        assertEquals(ProfileField.entries.size, propertyNames.size)
        assertEquals(41, propertyNames.size)
    }

    @Test
    fun `every catalog json key resolves to a real model property`() {
        val unknown = ProfileField.entries
            .map { ProfileFieldCatalog.jsonKeyOf(it) }
            .filterNot { it in propertyNames }
        assertTrue("目录键无对应模型属性: $unknown", unknown.isEmpty())
    }

    @Test
    fun `every model property is covered by exactly one catalog field`() {
        val covered = ProfileField.entries.map { ProfileFieldCatalog.jsonKeyOf(it) }.toSet()
        val orphan = propertyNames - covered
        assertTrue("模型属性未登记进目录: $orphan", orphan.isEmpty())
    }

    @Test
    fun `model keeps a no-arg constructor for gson default-value safety`() {
        assertTrue(
            "DeviceProfile 失去无参构造器 ⇒ 存在无默认值的字段,Gson 反序列化将跳过默认值表达式",
            DeviceProfile::class.java.constructors.any { it.parameterCount == 0 },
        )
    }

    @Test
    fun `every model property is a primitive or String so Gson never boxes null`() {
        val offenders = DeviceProfile::class.java.declaredFields
            .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .filter { it.type != String::class.java && !it.type.isPrimitive }
            .map { "${it.name}:${it.type.name}" }
        assertTrue("画像字段必须是值类型或 String(null 会击穿不生效语义): $offenders", offenders.isEmpty())
    }
}
