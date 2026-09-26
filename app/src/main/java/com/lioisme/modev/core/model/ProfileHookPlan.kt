package com.lioisme.modev.core.model

/**
 * 画像干预计划编译器(V47 T3,基线 `HookPlan.activeHookFeatures` 同族):
 * 把一份 [DeviceProfile] 编译成**最小干预组集合**。
 *
 * 三条设计约束:
 *  1. **纯函数、零 Android、零 IO** ⇒ JVM 直测,注入侧与宿主诊断面共用同一份裁决;
 *  2. **未选中的组连对象都不构造**(消费方按本结果装配,不再各自判空),
 *     这样「装了但没生效」与「生效了但没装」两种漂移都失去立足点;
 *  3. 互斥裁决集中在本文件,**禁止散进各钩子** —— 同一语义只允许一个实现方在场
 *     (基线把「隐藏优先于伪装」写进 HookPlan 与 WifiHook 两处是反面教材)。
 */
object ProfileHookPlan {

    /**
     * 编译生效组。
     *
     * 组激活判据 = 该组承载的字段中**任一项**处于生效态([ProfileField.isSetIn])。
     */
    fun activeGroups(profile: DeviceProfile): List<ProfileKind> {
        val active = ProfileKind.entries.filter { kind ->
            ProfileFieldCatalog.byKind.getValue(kind).any { it.isSetIn(profile) }
        }.toMutableList()

        // R-B:隐藏 Wi-Fi 定位来源优先于 Wi-Fi 标识伪装(两者写同一批 API,并存即双写)。
        if (ProfileKind.PROFILE_WIFI_LOCATION_HIDE in active) {
            active -= ProfileKind.PROFILE_WIFI
        }
        // R-C:隐藏基站定位优先于基站参数伪装。
        if (ProfileKind.PROFILE_CELL_LOCATION_HIDE in active) {
            active -= ProfileKind.PROFILE_CELL
        }
        return active
    }

    /**
     * R-D:自适应条件档是否让位。
     *
     * 画像显式改写了 `Build` 身份 ⇒ 强制档在场,条件档(「只在脏值上掩蔽」)对该字段族退让,
     * 避免两套实现先后写同一批静态字段。条件档仍继续负责画像**留空**的字段族。
     *
     * V49 R-1 把 `PROFILE_BUILD` 拆成身份与版本两族(它们仍是同一批 `android.os.Build`
     * 静态字段的读者),所以本裁决**恒覆盖两族** —— 只认其一会出现「改了 Build.ID 但
     * Build.MODEL 仍被条件档先后覆写」的半让位形态。
     */
    fun adaptiveMaskShouldYield(profile: DeviceProfile): Boolean =
        buildFieldFamilies.any { family ->
            ProfileFieldCatalog.byKind.getValue(family).any { it.isSetIn(profile) }
        }

    /**
     * R-D 的按族形:条件档与画像强制档**共享同一读口**时,画像填了该族 ⇒ 条件档让位。
     *
     * 与上面 Build 那一对的区别只是族数:核数族(`Runtime.availableProcessors`)与密度族各自
     * 只与一个条件档同伴相撞,键空间不重叠的(电池:画像只占电量百分比,条件档管计数器/电流/
     * 充电状态)**不该**整族让位 —— 那种情形由两侧各读自己的键解决,见 `ProfileBatteryHooker`。
     */
    fun adaptiveMaskShouldYield(profile: DeviceProfile, family: ProfileKind): Boolean =
        ProfileFieldCatalog.byKind.getValue(family).any { it.isSetIn(profile) }

    /** 写同一批 `android.os.Build` 静态字段的两族(本文件私有,不构成第二条分类轴)。 */
    private val buildFieldFamilies = listOf(
        ProfileKind.PROFILE_BUILD_IDENTITY,
        ProfileKind.PROFILE_BUILD_VERSION,
    )

    /**
     * 逐组自检:同组内是否同时存在「伪装」与「隐藏该伪装」的矛盾配置。
     *
     * 返回非空即配置自相矛盾(UI 应提示,而不是悄悄择一生效)。
     * 当前裁决表里的两组互斥是**优先级压制**而非矛盾,故不在此报告。
     */
    fun conflicts(profile: DeviceProfile): List<ProfileConflict> {
        val active = activeGroups(profile)
        val found = mutableListOf<ProfileConflict>()
        // 坐标伪装要求经纬度齐备,只填一半属于半配置(合成 Location 无法成立)。
        val hasLon = ProfileField.LONGITUDE.isSetIn(profile)
        val hasLat = ProfileField.LATITUDE.isSetIn(profile)
        if (hasLon != hasLat) {
            found += ProfileConflict.COORDINATE_HALF_SET
        }
        // 抖动开关没有坐标可抖 ⇒ 无效开关(静默无害,但用户会以为已生效)。
        // 注意不能用「LOCATION 组是否激活」判:抖动位自身就属于该组,组恒激活。
        val hasCoordinates = hasLon || hasLat
        if (ProfileField.RANDOM_OFFSET.isSetIn(profile) && !hasCoordinates) {
            found += ProfileConflict.OFFSET_WITHOUT_COORDINATES
        }
        // 空白通行证的应用列表项与标识伪装无关,但单独勾选应用列表却未勾任何内容项时,
        // 用户通常想要的是「隐藏媒体」而非「隐藏包名」—— 提示而非拒绝。
        if (ProfileField.PASS_APPLICATIONS.isSetIn(profile) &&
            ProfileKind.PROFILE_BLANK_CONTENT !in active
        ) {
            found += ProfileConflict.APP_LIST_WITHOUT_CONTENT
        }
        return found
    }
}

/** 画像配置矛盾类型(标签四语在 ui 侧映射)。 */
enum class ProfileConflict {
    /** 只填了经度或只填了纬度,合成定位无法成立。 */
    COORDINATE_HALF_SET,

    /** 开了坐标抖动但没有坐标可抖。 */
    OFFSET_WITHOUT_COORDINATES,

    /** 只勾了应用列表空白通行证,内容四项全空。 */
    APP_LIST_WITHOUT_CONTENT,
}
