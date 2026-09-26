# 工具链坑(实测沉淀)

> 从 `AGENTS.md` 拆出。这一节讲的不是「本产品的设计」,而是**编译器 / 测试框架 / 设备框架本身
> 会怎样静默骗过你**:每一条都对应一次「表面全绿、实际没生效」的事故。写新代码或排查怪现象前先扫一眼。
> 条目按「症状 → 根因 → 判据 / 处置」组织,可独立引用。


## 已知工具链坑

- **Kotlin `-no-jdk` 下 `java.text.NumberFormat.getCompactNumberInstance` 编译期
  Unresolved**(内嵌 JDK 11 类元数据;AGP `--system jdkImage` 同坑)。
  方案:紧凑数字用 `android.icu.text.CompactDecimalFormat`(API 24+);
  相关单测须 Robolectric(纯 JVM 下 android.icu 是桩)。
- 新增 Java 源:AGP javac 同样受 `--system jdkImage` 限制,勿依赖 JDK 12+ 平台 API。
- **`ListItem` 内部槽位的自定义 testTag 在合并语义树里查不到(V46 实测量)**:Robolectric 下
  实测 `merged(identity)=0 / unmerged=1` —— 挂在 `headlineContent`/`supportingContent` 内
  任意节点的 `Modifier.testTag` 会被并给父合并节点,`onNode(hasTestTag(...))` 直接零命中
  (`fetchSemanticsNodes().first()` 抛 `NoSuchElementException`)。凡对 ListItem 槽位内节点做
  锚定/几何断言,一律 `useUnmergedTree = true`;行容器自身的 testTag(挂在 ListItem 外的 Box 上)
  在两棵树都可见,不必切树。
- **`assertAny` 只有集合接收者、`SemanticsNode` 无 `density`(V46 编译实锤)**:
  `SemanticsNodeInteractionCollection.assertAny(matcher)` 是唯一形态,单节点交互器上
  不存在该扩展 → 「某锚点内含有文本 X」一律写
  `onNode(hasTestTag(t)).assert(hasAnyDescendant(hasText(x)))`;几何换算的密度取
  `ComposeTestRule.density`(节点对象上没有 `density` 属性)。缺件清单再加
  `performTextClearance`(本仓无先例,预填输入框的用例改为**种子空值**规避清文本)。
- **Robolectric 打不动 `ModalBottomSheet` 里的按钮(V47 WP-5 控制实验实锤)**:弹层内容
  在语义树里可见(锚点存在性、行内文案都能断言),但对其按钮 `performClick` 后
  `onSubmit`/`onDismiss` **恒零触发**(推时钟 + `shadowOf(mainLooper).idle()` 均无效)。
  判据不是猜的:对 V39 起全 App 共用、真机正常的 `ModevInputSheet` 本体做同一操作,
  结果 `submitted=[] dismissed=0` ⇒ **harness 限制**,非产品缺陷。故弹层交互一律归
  `app/src/androidTest`(如 `ProfileEditorFlowTest`),Robolectric 档只测「一屏画什么」
  (整屏结构 + 派生态渲染,经 VM 写入面驱动)——两侧合起来才是完整证据,单侧绿不算。
  另注意:同一用例**只能一次 `setContent`**(换参数须拆用例);测「编辑既有条目」的
  夹具必须在库里种下那一行,否则跑到的是「条目缺失 ⇒ 弹栈」分支,屏上零节点。
- **`compose.waitForIdle()` 不等跨线程落盘,Robolectric 里会伪装成「VM 没写」(V49 WP-C 实锤)**:
  `ViewModel` 的写路径是 `viewModelScope.launch { repo.setOverride(...) }`,而仓库内部
  `withContext(Dispatchers.IO)` 写偏好**之后**才更新 `StateFlow`。测试里
  `runBlocking { vm.setX(...) }` + `compose.waitForIdle()` 只排空主 looper 与组合,**不等待 IO 线程**
  ⇒ 紧接着读 `repo.overrideFor(pkg)` 恒是写前快照,断言炸成「VM 未落盘」的假故障
  (本轮为此先怀疑解码/校验/状态流,绕了一圈)。处置 = 轮询式 flush
  (`repeat(30) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }` 后再
  `waitForIdle()`),形态同 `KeywordLibraryRepositoryTest` / `ConfigServer*Test`;
  且**先断数据面再断呈现面**(两者同败 = 写路径问题;只有呈现败 = 锚点/语义树问题)。
- **`RecordSink` 未 `start()` ⇒ 审计断言恒绿在空列表上(V49 WP-D2 实锤,已污染过一条 D1 结论)**:
  `RecordSink.emit` 首句 `if (!running) return`,而 `start()` 由装配层调用 ⇒ 测试夹具里直接构造的
  sink **静默丢弃全部记录**。`ProfileBuildHookerTest` 因此把「记录数 = 0」读成「JVM 拒写
  `static final`」,并把这个假结论写进了本文件(见下一条的更正)。凡断言「发没发记录」,夹具必须
  `.also { it.start() }`,且**捕获列表用 `CopyOnWriteArrayList` + 有界等待**(出口在工作线程上
  异步回调,0 条的断言没有沉淀窗就是恒真)。判据:同一用例里既数记录又读字段现值,两边必须一
  一对应 —— 只有一边时,另一边才是被测对象。
- **反射查不到方法 = 永不生效的死钩子,而文档会替它撒谎(V49 WP-D2/D3 两连实锤,登记 V49-DFC-13/14;V50 第三形态)**:
  三种查不到的形态都真实存在过 ——①`java.io.File.getMethod("readText", Charset)`:`readText` 是
  `kotlin.io` 的**扩展函数**(编译成静态 `TextStreamsKt.readText(File, Charset)`),`File` 上没有这个成员;
  ②`CellIdentity` 的四个 mcc/mnc 取口与 `SubscriptionManager.getPhoneNumber()` 无参形:它们是
  **@SystemApi/@hide**,公开 android.jar 里根本没有(靶程序也调不到 ⇒ 钩了零读者)。
  **③(V50 新增)`DetectionMonitorHooker` 的 `BUILD_FIELDS = ["getFINGERPRINT","getMODEL",…]`
  经 `Build.getMethod(field)` 解析 —— `android.os.Build` 的这些成员是 `public static final` 字段,
  从来没有同名 getter**(第 3 号形态的特征:名字是**凭印象加的 "get" 前缀**,连 `NoSuchMethodException`
  都必然发生,却因 `?: continue` 在循环里静默跳过,比 ①② 更隐蔽:整段监控代码零安装而表面看不出来)。
  三者的共同点都是 `getOrNull() ?: return`/`?: continue` 咽掉异常,而 AGENTS/§2.2 写着"已掩蔽/已监控"。
  收口 = `DeviceEnvBaseHooker.unresolvedHookPoints`(记录每个解析失败的 `类#方法#参数`)
  + 每族一条「列出的钩点必须全部解析得到」断言;**且**该观测面必须覆盖**私有 `runCatching` 分支**
  (V50 实锤:`monitorBuildFields` 走私有 runCatching,根本不进 `unresolvedHookPoints`,
  所以「列出的钩点全部解析得到」的既有断言看不见它);照抄上游钩点名单前,先在测试里证明它存在。
  **静态字段直读(`GETSTATIC`)在 ART 上不可钩是物理上限** —— 想观测「读 Build 字段」只能钩
  `java.lang.reflect.Field.get`(反射通路,V50 `monitorBuildFieldReflection` 即此),
  字段改写型干预则由 `EmulatorBuildHooker` 承担,**禁**伪造一个观测不到的观测点。
- **注入侧「改 `static final` 平台字段」在 JVM 档**大部分真的改得动**(V49 WP-D2 更正,原条目为假):
  原记「`Field.set` 对 `static final` 恒抛 `IllegalAccessException` ⇒ 实测 0/11 落地」,那是上一条
  未启动 sink 造出来的**零记录**,与写入成功与否无关。启动后实测:`Build` 身份八项 + `VERSION.RELEASE`
  + `VERSION.SDK_INT` **11 项里 10 项落地**,仅 `Build.VERSION.ID` 被拒;且落地的每一项恰发一条
  `BLOCKED` 审计、被拒的那项零记录(对应关系由 `appliedWritesAreAuditedForExactlyTheFieldsThatLanded`
  钉住)。**结论不变但理由换了**:JVM 绿仍然不等于靶程序生效(ART 侧另有缓存/内联/直读 `GETSTATIC`
  漂移),所以生效判据依旧是靶程序读回(WP-F);改这类钩子时**别再用「记录条数」当写入证据**,
  要读字段现值对账。
- **插桩档四种「静默不生效」形态(V48 设备档实锤,四种都红过一整轮才被抓出)**:
  ① **懒列表点不到** —— `ModevSearchableChoiceSheet` = LazyColumn + `heightIn(max=420.dp)`
  + 120 行截断 ⇒ 第 8 行之后的行**压根没组合**,`performScrollTo` 对未组合节点无能为力
  (报「could not find any node」而非「off screen」)。写选择器端到端用例一律先用 sheet
  自带搜索框(`sheet-search-input`)过滤再点 —— 这也是产品给长名单设计的通路,测试绕开它
  等于替产品掩盖问题。
  ② **遮罩吞点击** —— DropdownMenu/Dialog 退场动画期间的 `performClick` 打在 scrim 上,
  零异常、零效果,失败炸在下一句。跨弹层动作前先 `awaitUntilGone(<弹层内锚点>)`
  (V48 实锤形态:筛完类别 → 立刻点折叠搜索条 → 展开「不生效」,真因是菜单还没收干净)。
  ③ **文案断言必须与被测形态同源** —— 「每应用 × 类型」列表的清空确认走
  `danger_clear_page_records_msg`(默认参),「类型 × 全部应用」全局列表才走
  `danger_clear_<type>_msg`;在单应用屏断全局键 = 恒红,而消息只说「not displayed」。
  ④ **枚举字面量不得抄死在断言里** —— 探针/模型改枚举名(V12 `WEB_CRYPTO`/`ENCRYPT`
  → V29 `JS_CRYPTO`/`JS_ENCRYPT`)后,抄字面量的设备用例当场变假红且**消息为空**;
  断言一律 `"kind=${WebViewKind.JS_CRYPTO}"` 这样由枚举派生 + 必带说明文案。
  另:挂在 `ListItem` 槽位(`trailingContent`/`supportingContent`)里的 testTag 只在
  `useUnmergedTree = true` 可见(同坑见上一条 Robolectric 条目),设备档一样成立。
- **Material3 1.4.0 `ListItem` 无 `shape` 形参(javap 实锤)**:圆角/自绘背景/可见波纹
  只能在容器层做(见 #13 行容器契约);另注意 `ListItem` 布局会按 `trailingContent`
  宽度**永久削减文本列宽度**(`TrailingContentStartPadding` 常量),尾栏放短内容 =
  其下方死空间 + 正文提前截断。
- 本 Compose 测试库无 `hasToggleableState`/`waitUntil`/`hasText(Regex)`/
  `assertExists`/`performScrollBy` 与 `SemanticsProperties.OnClick`
  (用 `SemanticsActions.OnClick`);`onRoot`/`printToLog`/`onFirst` 是扩展需
  显式 import;`onRoot()` 在弹窗打开时有 2 个 root 会报错,须
  `onAllNodes(isPopup())`;节点存在断言用
  `onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty()`。
- **单一 LazyColumn item(整段 `SettingsSection` 为一个 item)内,段内行
  `performScrollToNode` 不可靠**(实测滚动停在 161px、目标行仍在组合窗口外,
  仅跨 item 滚动稳定);整段 item 会整段组合,段内行断言存在即可;段尾行先
  滚动到相邻 item 再断言存在/可见。
- 断言计数/数值文案须资源驱动:zh 设备渲染 "2 条"(`settings_total_records_value`
  zh 带后缀),裸数字精确匹配失败;存储值断言防「Room 首查才建库文件」竞态
  (生产 `remember` 快照取 0 B)——测试先 `openOrCreateDatabase` 预建文件。
- 模拟器偶发 ADB `Connection reset`/`Failed to start Emulator console`(测试
  本身 XML 全绿但 Gradle 报驱动失败):重跑即可;跑前 `adb devices` 确认设备在线。
- 并行跑两个 gradle 任务可能触发守护进程 OOM(「daemon disappeared」),lint 等
  重任务串行执行。多项目/IDE 守护进程滞留(Xmx8G 等)会压垮页面文件
  (OS errno 1455/daemon 崩溃):assemble 前清理闲置守护进程,
  重任务用 `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2048m` 逐变体
  串行执行。**Kotlin 编译守护进程同样会被系统杀掉**(V45 实锤:
  `Connection to the Kotlin daemon has been unexpectedly lost`,特征 = 报
  `compile…Kotlin FAILED` 而**无任何 `e:` 诊断**)——此时先
  `-Dkotlin.compiler.execution.strategy=in-process` 重跑再归因源码,
  否则会把环境故障误读成编译错误。
  **OOM 崩溃还会撕裂 KSP 增量缓存**(V47 实锤:commit charge 38.6/40.6 GB 下测试执行器
  `Chunk::new` malloc 失败被杀,之后 `ksp…AndroidTestKotlin` 恒报
  `StreamCorruptedException: invalid stream header: 00000000`,`--rerun-tasks --no-build-cache`
  **也修不好**;缓存目录被 `--stop` 前的 daemon 句柄持住 ⇒ `Device or resource busy`)。
  处置 = `gradlew --stop`(只停闲置)→ 删 `app/build/kspCaches/<variant>`(纯生成物)→ 重跑。
  另:测试执行器被杀时 gradle 可能仍报 `BUILD FAILED` 而**无任何失败用例**,后台通知的
  「exit code 0」在这一族故障里同样不可信,判据是日志里 `BUILD` 行与结果 XML 的 mtime。
- **Robolectric 双模式均不支持 `EXPLAIN QUERY PLAN`**(legacy/native shadow
  都剥离 EXPLAIN 前缀直接执行原查询,返回普通结果集)——查询计划级验证留待
  真机插桩;JVM 门禁用「sqlite_master 索引存在性 + 行为对账」组合
  (`ExplainQueryPlanProbeTest`)。
- **验收编排器四坑(V45 首轮实锤,写/改 `run-acceptance.ps1` 一类编排脚本必读)**:
  ① 子进程**禁止** `& powershell -File … | Tee-Object`:子脚本会拉起 gradle/adb 守护进程,
  它们继承 stdout 句柄 ⇒ 管道永不 EOF ⇒ 编排器在子进程已退出后**永久挂死**
  (特征 = 子脚本日志已到 `install done` 而编排器不打印步结论);一律改
  `Start-Process -RedirectStandardOutput/-RedirectStandardError -PassThru` +
  `WaitForExit(ms)` 轮询(顺带才真正拥有 `-TimeoutMin`)。
  ② PS 5.1 `@(Get-Content -Raw | ConvertFrom-Json)` 对**JSON 数组**得到的是
  「1 个元素 = 数组本身」,`.Status` 变成 `"PASS PASS PASS…"` 全不匹配 ⇒
  逐断言计数**恒为 0 的假绿**;必须先 `| ForEach-Object { $_ }` 解除嵌套。
  ③ `Run-Child` 的透传参数曾名 `$Args`(= 自动 `$args`,大小写不敏感)⇒
  `-Args @("-SkipBuild")` 一类实参**不绑定**,子脚本按默认档静默跑
  (preflight 误跑全档 → 整轮 BLOCKED;MCP 步丢掉 -SkipBuild → 重复构建)。
  自动变量禁用清单见下文「PowerShell 参数名/脚本变量」条目。
  ④ `-Reboot` 步的**假就绪**(V48 实锤):`dnconsole.exe` 在本机**无 stdout、退出码恒 0**
  (直调还会抛 `ApplicationFailedException`),旧实装 `quit` 后仅 `Start-Sleep 8` 就
  `WaitDevice` —— VM 根本没关过,`boot_completed` 立刻为 1 ⇒ 打印「设备已就绪」,
  而后续注入矩阵对着 lspd 的陈旧模块表全红。修法 = **判据只能是失联**:`cmd.exe /c` 包装
  `quit` 后轮询 `sys.boot_completed` 读到非 `1`(90s 窗)才认定关机,否则整轮 BLOCKED。
- **验收量具自己就是掉线制造者 + 假红工厂(V50 实锤,四条同批修)**:
  ① `WaitDevice` 旧阈值「连续 8 次 adb 级报错(≈40s)」⇒ 直接 `Stop-Process dnplayer -Force`
  冷启动,**而宿主满载时单次 `adb shell` 就能卡过这个窗口** —— 量具把一台活 VM 打死,
  再把后半程零产出全记成产品缺陷(R8 一轮 8 次)。阈值提到 24(≈2min 持续报错)且
  **必须把末次真实报错打进日志**,否则「掉线」这个词只是情绪。
  ② 掉线自愈过去只打一行黄字、编排器毫不知情 ⇒ 一整轮 **21 条功能红**里真因只有掉线。
  现在:子进程按步写标记文件(`TT_VM_LOST_MARK`),编排器见标记 ⇒ **该步计数清零作废 +
  整轮 BLOCKED** —— 刚冷重启的设备上靶进程已消失、注入未必恢复(lspd 缓存模块表),
  任何 PASS 都不是证据。**归因纪律:环境故障登记成产品缺陷,比产品缺陷更难发现。**
  ③ Z4 要的是「默认 server 上**恰 1 条**序列」,不是「必须是 `emulator-5554`」。旧实装
  无条件 `disconnect` TCP 序列再要求恰 1 条,而本机 LDPlayer 在默认 server 上**只注册 TCP
  一条** ⇒ disconnect 后计数归 0,`ui`/`mcp` 两步**每轮都被 Z4 自己拦死**(R8 即此,
  台账只留一行「序列面非恰 1 条」,看着像产品问题)。现在:两种序列同时在场才去重,
  零序列则主动 `connect` 回来。
  ④ `env-runtime`(Twilight 进程存活 / `spool.dat` 存在 / 已入库记录 >0)排在 `smoke`
  **之前** ⇒ 它断言的是「冒烟跑过之后才存在」的产物,连续两轮恒 3 红且与代码零关系。
  写运行态断言前先问:**这一条的前序动作在本步之前还是之后**。挪对一次后仍剩 1 红,
  才看清第二层:`spool.dat` 是**宿主不可达时的磁盘兜底**(V36 D-1),而本步的 `EnsureHost`
  恰恰保证宿主在线 ⇒ 断「在线态下兜底文件存在」是方向写反,该能力的正档在
  `26-run-module-off-negative`;`已入库记录 >0` 则要等监听矩阵才可能为真(冒烟用例
  L-SMOKE-01/L-PERF-01 只验帧语义与耗时,**不产业务记录**)。
  ⑤ **数记录必须走设备侧活库,不许 `cp modev.db` 拉回本地查**:Room 是显式 WAL(#18),
  未 checkpoint 的行全在 `modev.db-wal` 里,单拷主库文件必然少读甚至读到 0 行 ⇒
  `00-env-verify` 的 `Modev has records (0)` 恒红,而**同一时刻**矩阵用 tt-lib 的 `Q`
  (`run-as … sqlite3 databases/modev.db`)看得见同样这些记录。判据:同一事实两条通路
  结果不同 ⇒ 先怀疑通路,别急着认定功能坏。
  ⑥ **「VM 掉线」绝大多数不是 VM 死了,而是雷电自己重挂 adb 端口转发**(V50 铁证,
  推翻本项目历轮对掉线的所有归因):`D:\leidian\LDPlayer14\crash_copyx86\leidian0_vbox.log`
  里逐行写着 `vbox::VBoxClientImpl::_onCallPropNotify(1908): app crash` →
  `autoAdbReconnect(1691): adb auto reconnect` → `remove natnetwork port forward tcp_5555_0`
  → `add natnetwork port forward tcp_5555_0`。**Guest 内任一 App 崩溃**(压力矩阵里天天发生)
  都会让雷电重挂 5555 转发,期间 adb 侧序列凭空消失 = `device '127.0.0.1:5555' not found`,
  而 VM 活得好好的。旧 `WaitDevice` 把这判成 VM 死亡并 `Stop-Process dnplayer -Force`
  ⇒ **量具亲手把健康设备上的现场(靶进程/spool/lspd 内存态)毁掉**,再把后半程零产出记成产品缺陷。
  修法 = 恢复分两级:一级只 `adb kill-server` + 重连并把窗口给到 300s(覆盖 guest 框架重启 +
  端口重挂 + `boot_completed` 回 1),只有真回不来才二级冷启动;且冷启动必须落标记文件,
  由编排器作废本步计数 + 整轮 BLOCKED。
  **连带结论**:此机制同时解释了"db 五类恒 `count=0`"——设备失联期间 `Q` 把 adb 报错文本
  **当数据行**返回,`KindCountsAll` 见计数列非数字就静默跳行 ⇒ 整表读成零记录(撒谎的只有这一条
  通路,`KindCount` 走 `CountChecked` 拿不到数字直接 `throw`,所以 enhance 档从不撒这个谎)。
- **整域零产出必须先测量再改代码(V50 反例,五种假设烧掉五轮)**:db 域五类 kind
  连着两轮全 `count=0`。台账只给「五个 0」这一种信息,于是只能猜 —— 游标类死钩、
  `recordType` 字符串不匹配(`RecordTypes.DATABASE` 恰为 `"db"`,匹配)、JSON 键没绑
  (`dbListen` 有 `FIELD_DB` 解析臂,绑了)、配置默认值(`dbListen`/`dbQuery` 恒 `true`)、
  探针未注册(`HookInstaller:64` 在列)—— **五条全部排除,但没有一条是设备证据否掉的**,
  每条都要跑一轮才能证伪。修法不是继续猜第六个,而是给量具补上缺的那一次测量:
  整域零产出时自动打印「该包在 `crypto_records` 里究竟有哪些 recordType|kind」
  + `logcat -d -s HookInstaller` 的装钩自报。**判据:一轮 80 分钟的设备档,只能用来
  区分已经互相冲突的假设,不能用来海选。**
- **框架守护进程整场缺席(V48 实锤,注入类断言全红的根因,改代码前先看这条)**:
  LSPosed 与 Vector 都是 **Zygisk 模块** —— Magisk 没跑,两者一起不加载,与本项目代码零关系。
  判据(全在设备侧,无需重跑矩阵):`/sbin` 与 `/dev/.magisk` 不存在 +
  `grep -c zygisk /proc/$(pidof zygote64)/maps` == 0 + `ps -A` 无 `magisk` +
  `/data/adb/lspd/log/*` 的 mtime 停在若干天前;`su` **仍然可用**(雷电自带 `ldinit`),
  所以「adb 能 root」不等于「框架在跑」。此时 `modules_config.db` 的
  `apk_path`/`enabled`/`scope` 三项即使全对,靶进程 `/proc/<pid>/maps` 里也只有自身 APK
  ⇒ 注入矩阵与 MCP 记录组必然红。**冷重启不恢复**(V48 实测一次无效),
  恢复属宿主 VM 的 Magisk/root 档位改动 = 共享设备侧操作,须先与使用者确认。
  **但反向同样错(V49 WP-D3 实锤):凭上面四条 grep 判「框架死了」不可靠。** 同一台机器在使用者
  调整档位后,`11-vector-scope.ps1` 报 scope 2/0、`listen` 70/0、`smoke` 10/0,注入确实发生;
  而其中 `ps -A | grep -c magisk` 会匹配到**管理器 App** `io.github.huskydg.magisk`(不是 `magiskd`),
  `/dev/.magisk` 与 zygote maps 在 shell 的挂载命名空间里也可能不可见。
  **唯一裁判是框架自身证据**:scope 步的模块映射 + 靶进程真的产出了记录。
  写「环境阻塞 ⛔」之前必须拿 scope 步说话,并先问使用者,不许拿手搓探测当结论(本轮就这么误判了一次,
  还把 ⛔ 写进了任务书)。
- **PowerShell 管道处理含中文源码会毁文件**:`Get-Content | -replace |
  Set-Content` 以系统 ANSI 解码 UTF-8,中文注释乱码且尾字节可吞换行把下一行
  代码并入注释(V23 实锤)。改文件一律用 edit 工具或 .NET API 字节级读写
  (`[IO.File]::ReadAllBytes/WriteAllBytes + Encoding.UTF8`)。
- androidx.sqlite `SimpleSQLiteQuery` 不解析 `:name` 命名参数(手写 SQL 直绑
  需用 `?` 占位);Robolectric 下 `?N` 编号占位触发 SQLITE_MISMATCH,用纯 `?`
  按序绑定。
- **mikepenz markdown-renderer(V26)**:①默认在 Dispatchers.Default 异步解析
  (Loading 态渲染空 Box),Robolectric 下与 waitForIdle 竞态——一律走核心
  `rememberMarkdownState(content=…, immediate=true)` + m3 的 state 重载
  (**m3 门面未透传 immediate**,直接 content 重载传不进去);②GFM 表格单元格
  解析器保留管道前空格为尾随字符 → 序列化用紧凑 `|Cell|`;③文档级围栏索引按
  content 字符串 remember 缓存,节点定位用 start/endOffset(跨解析实例稳定)。
- 详情页锚点滚动静默失效的形态:**LazyColumn 漏传 `state = listState`** 时
  animateScrollToItem 打在孤立状态上零异常零移动(firstVisible 恒 0);
  回归测试 `AnchorScrollRoboTest`(Robolectric 全装配驱动,秒级)。
- 截断标记/任何引用块插在连续围栏之间会打断代码组聚合(CodeGroupExtractor
  以块级相邻为准)→ 工厂恒将标记后置于全部围栏之后。
- **KDoc 注释内禁止书写 `get*/put*` 形态**:`*/` 会提前终结块注释,余文被当
  顶层声明解析出成串 Syntax error;且 KSP2 只报 `[MissingType] Element
  'CryptoRecordEntity' references a type that is not present`(掩盖真实行号)。
  定位法:临时摘除 `ksp(libs.androidx.room.compiler)` 跑 compile 看真实诊断,
  修毕恢复。写法用「get/put/remove 系列」(V43 T2 再实证:`emit*/ensure*` 同坑)。
- **KDoc/块注释内禁止出现 `/*` 序列**(V45 T3.5 实锤):Kotlin 块注释**可嵌套**,注释里写
  `ui/**`、`/**` 之类会开一层内注释,外层 `*/` 只关掉内层 → 其后代码整体被吞进注释,
  诊断表现为几百行外「Expecting member declaration / Unresolved reference」连锁。
  定位法:`awk` 逐行统计 `{}` 深度到 0 的行,或 grep `^\s*\*.*[^\\]/\*`;写法用「ui 子树」。
- **raw string 结尾不能紧跟引号**:`Regex("""name\s*=\s*"([a-z_]+)"""")` 里 `""""` 会被
  词法器当「三个引号收尾 + 游离引号」(Kotlin 原始串以首个 `"""` 结束,无贪心),
  报 `Syntax error: Expecting member declaration`(位置在下一行,掩盖真因)。
  需要末尾引号时用普通串拼接:`Regex("name\\s*=\\s*\"([a-z_]+)\"")`。
- **Gson 反序列化 Kotlin 数据类会跳过全部默认值**(V45 T3.2 实锤):Gson 无 no-arg 构造器时
  走 `Unsafe.allocateInstance`,于是「可选字段缺省」等价 null——非空集合字段(`stack`/`kinds`)
  直接 NPE、非空 Long(`timestamp`)落 0(记录停在 1970)。解法 = `registerTypeAdapter`
  一个 `InstanceCreator`,以全 1 mask 调 Kotlin 合成的默认值构造器
  (`(..., int $mask, DefaultConstructorMarker)`),非默认参数须给**占位值**
  (真实构造器对非空参数有 `checkNotNullParameter`,传 null 直接抛异常)。
  可空性不可反射(`@NotNull` 是 BINARY 保留,`RuntimeInvisibleParameterAnnotations` 反射读不到),
  故「无默认值的非空字段」必须显式登记清单并由缺值检查点名。
- **legacy Xposed API 82 的 MethodHookParam 无 `hasResult()`**(V43 T3 实锤):
  判「before 已 setResult」只能用 `param.result != null`(before 阶段 result
  恒 null,after 阶段 = 现读)。跨适配器 `Invocation.consumed` 语义须双实现
  同测(libxposed 用 shortCircuited||pendingThrowable,legacy 用
  result!=null||hasThrowable)。
- **管道命令吞 gradle 退出码**(V43 实施纪律):`gradlew.bat ... | grep/tail`
  后 `echo $?` 取到的是管道尾进程状态,构建失败被伪装成 exit 0——批处理
  脚本判 PASS 必须 `set -o pipefail` 或取 `${PIPESTATUS[0]}`,或直接看 BUILD
  SUCCESSFUL 行;后台任务通知的「exit code 0」同理不可信。
- **Gson 树序列化默认省略 JsonNull 成员**(gson 2.13 实测):SQL NULL 必须
  `GsonBuilder().serializeNulls()` 才落 `"value": null`,否则键静默丢失。
- mikepenz 0.38.1 的 MarkdownComponents 无 textLink 钩子;markdown 链接语义
  节点为 0×0 bounds(测试坐标点击不可达)→ 页内导航一律走原生可点击行
  (见强约束 #21 D2.1),勿依赖链接点击链路。
- lint NewerVersionAvailable 按声明依赖逐条计数:直依赖锁定旧版必 +1;
  仅传递使用的库不要显式声明(高亮引擎经 `dev.snipme:highlights` 直依赖并锁
  1.1.0,Kotlin 2.2.0 构建 metadata 安全;禁随上游盲升)。
- **PowerShell 参数名/脚本变量禁用自动变量名 `$args`/`$pid`(V38 实锤,大小写不敏感
  即冲突)**:`function F([object]$args)` 实参不绑定(自动变量遮蔽),`$pid = ...`
  静默失败(只读)——集成脚本中表现为「arguments 恒空」「存活判定拿 shell 自身 PID
  恒真 = 假 PASS」。一律改 `$toolArgs`/`$pidOut`;新写脚本函数先 `Get-Help` 心查
  自动变量表(完整列表见 about_Automatic_Variables)。
- **PS 5.1 `Invoke-WebRequest` 无 `-SkipHttpErrorCheck`**(PS 6+ 参数):参数绑定
  失败 = 语句终止错误,响应变量为 null。非 2xx 断言一律 try/catch +
  `$_.Exception.Response.GetResponseStream()` 读体。
- **雷电 x64 镜像剪贴板静默丢弃写入**(V38 实锤):`ClipboardManager.setPrimaryClip`
  前台执行后 `hasPrimaryClip` 仍 false(直写亦然)。剪贴板内容断言必须经
  `clipboardFunctional()` 哨兵探测降级(Snackbar 证处理器执行),内容同源由
  DiagnosticsReport JVM 单测 + MCP diagnostics 工具双端兜底;真机自动恢复全断言。
- **connectedAndroidTest 测后 AGP 卸载宿主 APK → Vector modules 行删除 + enabled
  复位**(V38 实锤):后续注入矩阵全 FAIL(记录零捕获)且无崩溃日志,极难归因。
  恢复序列:重装 APK → 等 Vector 重注册 → `UPDATE modules SET enabled=1` +
  scope 行(`11-vector-scope.ps1`)→ **重启 VM**(lspd 缓存模块表,不重启不生效)→
  HookInstaller 日志验证。androidTest 抽样必须排在设备集成矩阵之后,或测后即恢复。
- **注入早于 Application 时,任何构造期一次性求值的 Context 依赖初始化都会静默退化**
  (V38 seq 续号 bug 根因):`RecordSink.seqCounter` 构造期 `restoreSeq()` 时
  `ActivityThread.currentApplication()` 为 null → 每次重启 seq 从 1 重发 → 宿主
  `(packageName,processName,seq)` 唯一索引 REPLACE 静默覆盖上一代记录(跨重启数据
  丢失,矩阵表现为「记录凭空消失」)。规约:Context 依赖初始化一律惰性到首次使用
  且路径可见才定档;spoolProvider 同款懒重试。回归 `RecordSinkTest`
  「file becomes visible after construction」。
- **现代路径 after-setResult 曾被拦截器丢弃(V38 修复,功能域全局影响)**:
  `LibxposedPlatform.intercept` 恒 `return result` → after 钩子里的 setResult
  (maps 掩蔽/栈过滤/传感器伪装/VPN·DNS 隐藏/SSL 结果替换)全部空操作。
  after 短路值必须优先返回。新增结果改写型 Hooker 后必须以对应二态断言验证
  (裸挂钩不触发 = 假修复)。
- **Android 14 握手验证只经 Platform 包私有桥(V38 dexdump 实锤)**:
  `Platform.checkServerTrusted(X509TrustManager,X509Certificate[],String,
  AbstractConscryptSocket|ConscryptEngine)` 是 static 包私有(access 0x0008 无
  PUBLIC 位),`getMethod` 不可见必须 `getDeclaredMethod+setAccessible`;参数类型
  为 hide 类需运行时反射解析。TrustManagerImpl 公开重载不在 native 握手路径上。
- **app dex 类必须经 app classloader 显式装载(V38 三重实锤)**:①ART 同 loader
  内类引用解析走 native fast path,`ClassLoader.loadClass` 监视器看不到(okhttp
  全系类);②模块注入类的 forName 委派链不含 app dex;③模块与 app 各带一份
  kotlin-stdlib,`Function0` 等双份 Class 按 `==` 比较 → getDeclaredMethod 必败。
  统一解法:`ProbeContext.appClassLoader` + `Class.forName(name,false,appLoader)`
  (initialize=false 零副作用),以 SSLContext.init/握手回调为补装触发点。
- **Vector 2.2 同一方法的第二个 hook 不执行**(V38 观察):两个 hooker 各自对
  `ClassLoader.loadClass` 注册钩子时,仅先注册者生效——需要类加载观察的 hooker
  共享单一监视器或改用显式预载,禁止各自重复注册同一方法。
- **`install -r` 覆盖安装宿主 APK 后 lspd 不再向新进程注入**(V39 设备实测,与
  connectedAndroidTest 卸载同源):模块表按 APK inode/路径缓存,覆盖安装后冷启动进程
  无 `Loading module` 日志、记录零捕获。唯一恢复 = `dnconsole quit --index 0` +
  `dnplayer -index 0` **整机重启**(受限环境下 `Stop-Process` 拒绝访问,tt-lib
  WaitDevice 自愈同样失效);重启后 `uptime` 归零可证。
- **设备侧改宿主 SharedPreferences 三定律**(V39 令牌矩阵实锤):①必须
  `am force-stop` 后再写,运行中直写会被内存态在下次 persist 时刷回;②XML 内 JSON
  值是 `&quot;` 转义,sed 匹配须按转义形态;③on-device `sed` 替换文本中的 `&` 会
  展开为整个匹配 → 一律「`run-as cat` 拉取 → 本地改 → `run-as` 回写」,禁 on-device
  复杂 sed。
- **`am instrument` 前必须 force-stop 宿主**:残留宿主进程导致测试进程
  `failed to attach` / 原生 segfault(INSTRUMENTATION_CODE 0)。
- **验收轮进行中，任何人(含 agent 自己)并行发 adb 命令 = 整轮判据作废(V50 实锤)**:
  设备矩阵的等待/计数全靠对 `127.0.0.1:5555` 的连续 `adb shell` 轮询;编排器跑着时另开一条
  `adb devices` / `adb -s … shell` 会造成连接**中途掉离 server**,症状不是断言失败而是
  `adb.exe: device '127.0.0.1:5555' not found` 抛出 → **整步在日志中途截断**(enhance 28 行只剩 12 行),
  台账 JSON 不落盘 ⇒ 编排器按「无台账 = 不可判定」记 FAIL(该机制本身是对的,救了我们没把崩掉的步读成绿)。
  同因连带把 `env-runtime` 从 3 红放大到 **11 红**(连 `Root available` 都红,而手工查是 uid=0)。
  **判据**:一次红先别归因产品 —— 看该步 err 日志有没有 `not found` / `device offline`,
  以及 FAIL 集合是否是「root/adb 类断言齐红」这种**环境连通性形态**;静默重跑同一阶段,
  连红数回落到基线即为争用。实测:quiet 重跑 env-runtime 11→3(与基线逐条同名)。
  **纪律**:矩阵跑着的时候,agent 不许对设备发任何命令;要查状态就读日志文件,别开 adb。
- **雷电同一设备双序列**(`127.0.0.1:5555` + `emulator-5554`):Gradle
  connectedAndroidTest 会对两序列各跑一遍、互相踩踏(共享 prefs/前台/记录),表现为
  大面积假失败(实锤形态:`Failed to inject touch input` 成片、用例数虚增到 2×);
  单设备回归改用 `adb -s <单序列> shell am instrument -w
  <testPkg>/androidx.test.runner.AndroidJUnitRunner`。
  **V45 再实锤机制**:独立 server 端口是靠 `$env:ANDROID_ADB_SERVER_PORT` 设的
  (`tt-env.ps1` dot-source 时一次设定、**永不清理**),该变量会被同进程的
  `gradlew` 继承 ⇒ AGP 也走 5038 = 天然双序列,且编排器的 `disconnect`/`devices`
  检查打在同一个 server 上(看见双序列仍照常开跑 = 假门禁)。插桩档必须先
  `Remove-Item Env:ANDROID_ADB_SERVER_PORT` 回到默认 server,再断言序列数 == 1,
  不等即 BLOCKED(不许「带着踩踏跑完整轮」)。
- **视口外节点 `performClick()` 静默丢弃**(V45 前端轮 8 例红的唯一根因):Compose 测试的
  `performClick` 是**按节点中心坐标注入真实触摸**,节点若在当前滚动容器视口之外,坐标落在
  窗口外 ⇒ 手势被系统丢弃,**零异常、零导航**,失败几秒后在下一屏的断言上炸开,失败信息只给
  一份与根因无关的容器语义转储(几何猜由此诞生)。整屏 `Modifier.verticalScroll` 页
  (监控页 Hero/统计区之下的入口行、设置页分区尾行)与视口高/文案长度相关,呈「有的类绿、
  有的类红」的刀口形态。写法一律:先 `performScrollTo()`(视口内节点上部分版本会抛断言错,
  需吞掉)或容器 `performScrollToNode(matcher)`,再点;跨屏导航后必须**有界等待目标屏的
  testTag 锚点**(`awaitAnchor`),失败信息必须自带「当前停在哪一屏」。
- **Gradle 构建缓存可交出跨产物 ABI 不一致的陈旧 dex**(V45 实锤,难归因):宿主 APK 是
  新产物,而 `androidTest` APK 内调用点仍是**旧构造器 synthetic** ⇒ 只在设备运行期炸
  `NoSuchMethodError`(JVM 单测同批编译源码故恒绿);`:app:clean` 也不足以复位——命中旧缓存
  key 的 task 会把陈旧产物原样交回。判据用 `dexdump -d classesN.dex | grep "<init>:"` 逐
  产物对签名,处置是 `--rerun-tasks --no-build-cache`;**验收轮的 gradle 调用必须带
  `--no-build-cache`**(从源码可复现优先于构建提速),装机步另加**产物 MD5 == 设备
  `base.apk` MD5** 对账断言(`install` 文本 Success 与 `dumpsys versionName` 都不校验字节)。
- **靶程序 launcher 入口是 `.TriggerActivity`**(非 MainActivity);
  uiautomator dump 在高负载偶发挂起/截断,解析用 perl(宿主 grep 是 ugrep 别名,
  `-o` 行为不同)。

## PowerShell 5.1 按系统 ANSI 解码「无 BOM 的 UTF-8 .ps1」

含中文的 `.ps1` 若无 UTF-8 BOM,PS 5.1 会按 GBK 解码 ⇒ 中文字符串字面量被拆坏,
报 `TerminatorExpectedAtEndOfString`,位置还落在**最后一个含中文的 Write-Host** 上
(前面几行看起来完全正常,于是会把「解析失败」误读成「运行到一半出错」)。
判据:`Get-Content -TotalCount 1 -Encoding Byte` 看前三字节是否 `EF BB BF`。
本仓 `scripts/*.ps1` 全部带 BOM 是**既有惯例**,新脚本必须照做;
写盘工具默认输出无 BOM UTF-8,所以由 agent/脚本新生成的 `.ps1` 一定会踩这一条。

## 忽略规则的尾斜杠只挡目录,不挡同名散件

`docs/test-reports/v45-*/` 这条规则看起来在防验收产物入库,实际一条也挡不住:
带尾斜杠 = 只匹配**目录**,而 `run-acceptance.ps1` 除了写轮次目录,还往同一层写
`v45-<utc>.json` / `.md` 两个散件。结果 78 个台账进了索引(36 个含 `D:\Users\...` 本机路径),
另有 401 个未跟踪散件堆在工作树里。
判据:**任何"防产物入库"的规则,必须用一次真实运行来验**,而不是看规则写没写。
本轮的处置是不再逐目录设防:产物只有一个落点 `dev-local/`,那里整棵子树不入库。

## `--no-daemon` + 冷构建会把 KSP worker 炸成 `NoClassDefFoundError`(与被测代码无关)

为躲 commit-charge OOM,本仓的提交前纪律是 `--no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2048m`。
在**已有 build 目录**的热工作树上这样跑没问题;但在**全新克隆**上跑同一条命令,会在
`:app:kspModernDebugKotlin` 直接炸:

```
e: [ksp] java.lang.NoClassDefFoundError: com/google/devtools/ksp/impl/KotlinSymbolProcessing$execute$7
> A failure occurred while executing com.google.devtools.ksp.gradle.KspAAWorkerAction
Failed to notify build listener.
> org/jetbrains/kotlin/gradle/plugin/statistics/BuildFinishMetrics$reportGlobalMetrics$1
```

根因是单使用 daemon 的类加载路径与 KSP 的 worker 动作、Kotlin 插件的构建结束监听器不匹配,
**症状完全不像构建配置问题,像代码问题** —— 于是会被追半天"是不是我改坏了"。
判据:同一条命令在同一份代码上,**带 daemon 就绿**。

处置分工(别把两者混成一条命令):
- **本机省内存验收**:保留 `--no-daemon`,但只在已有 build 目录的工作树上跑;
  冷构建首轮允许用 daemon(`-Dorg.gradle.jvmargs=-Xmx2560m`,不开 --no-daemon)。
- **CI 与"陌生人可用"判据**:一律用默认 daemon 路径(GitHub Actions 就是这么跑的),
  这样测出来的才是别人会得到的结果。
