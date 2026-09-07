# V39 设备实测清单(雷电 x64,LDPlayer14 Android 14 + Vector)

> 日期:2026-09-07。前置:V39 改动已全部实施并通过 JVM 门禁(单测 657 全绿、
> lint 49/0、androidTest 编译、assembleModernDebug)。本文档 = 设备侧验收计划与
> 预期结果;实施按 M0→M4 顺序,结果记录至 `docs/test-reports/`。

## 环境

| 项 | 值 |
|----|-----|
| 设备 | `127.0.0.1:5555`(雷电 9 实例 0,Android 14,adb server 端口 5038) |
| 宿主 | `com.lioisme.modev`(modern x86_64 debug,V39) |
| 靶程序 | `com.lioisme.modev.testtarget`(debuggable,run-as) |
| 框架 | Vector(lspd modules_config.db;模块启用 + 作用域行) |
| 端口约定 | wire(注入通道)1511 / MCP 1510(均宿主回环) |

## M0 构建安装与环境恢复

| # | 用例 | 命令/动作 | 预期 |
|---|------|-----------|------|
| M0-1 | 构建双 APK | `gradlew :app:assembleModernDebug :testtarget:assembleDebug` | BUILD SUCCESSFUL |
| M0-2 | 安装 | `adb install -r` 两 APK | Success ×2 |
| M0-3 | 模块启用 + 作用域 | lspd DB `modules.enabled=1` + scope 行(testtarget) | sqlite 查询含 testtarget |
| M0-4 | Vector 重载 | 重启模拟器(lspd 缓存模块表,AGENTS 已知坑) | 重启后注入生效 |
| M0-5 | 宿主拉起 | `am start` Modev 主Activity | 进程存活,MCP 随进程常驻 |

## M1 端口 / IP(需求 1、2)

| # | 用例 | 动作 | 预期 |
|---|------|------|------|
| M1-1 | MCP 默认端口 1510 | 宿主启动后 `netstat -tln`(su)或 MCP `server_status` | `127.0.0.1:1510` LISTEN;`server_status.mcp.port=1510` |
| M1-2 | wire 默认端口 1511 | netstat | `127.0.0.1:1511` LISTEN(ConfigServer) |
| M1-3 | 绑定地址可观测 | `server_status.mcp.boundAddress` | `127.0.0.1`(回环默认态) |
| M1-4 | 端口自定义改绑 | UI/MCP 改 `mcp_port`=1520 → restart | netstat 出现 `127.0.0.1:1520`,1510 消失;改回 1510 |
| M1-5 | 外部访问改绑 0.0.0.0 | 服务页开 MCP 外部访问(级联开令牌) | netstat `0.0.0.0:1510`;boundAddress=`0.0.0.0`;@复测后关回 |
| M1-6 | 撞号防线 | (可选)wire 与 MCP 同端口设置 | 后启动方 bind 失败不崩溃,`bindError` 有值 |

## M2 令牌矩阵(需求 3)

| # | 用例 | 动作 | 预期 |
|---|------|------|------|
| M2-1 | 默认停用回环直通 | 无 Authorization 头 POST /mcp initialize | HTTP 200 + 会话头 |
| M2-2 | 启用认证后回环强制 | 服务页/仓库开 `mcp_token_auth` → 无头 initialize | **HTTP 401** |
| M2-3 | 启用即派生 | 同上动作后查令牌列表 | ≥1 枚(启用不锁死) |
| M2-4 | 携带正确令牌 | `Authorization: Bearer <令牌>` initialize | HTTP 200 |
| M2-5 | 错误令牌 | `Bearer wrong` | HTTP 401 |
| M2-6 | 新建第二枚令牌 | UI 创建「label2」 | 列表 2 行;两枚均可用(各 initialize 200) |
| M2-7 | 撤销即时生效 | 撤销第一枚 → 旧令牌请求 | **401**(原地生效,无需重启宿主);第二枚仍 200 |
| M2-8 | 撤销最后一枚 | 撤销至空列表 | 认证开关仍开,全部请求 401(UI 提示空集全拒) |
| M2-9 | 关认证级联关外部 | 关 `mcp_token_auth` | 外部访问自动置 false,绑回 127.0.0.1 |
| M2-10 | 令牌不外泄 | `server_status`/`diagnostics` 输出 | 无任何令牌值字段 |
| M2-11 | 端口自定义保持 | M1-4 后 `mcp_port` 持久化 | 宿主重启后仍 1520→1510(复测恢复默认) |

## M3 UI 验证(需求 4–8;含既有 androidTest 抽样)

| # | 用例 | 动作 | 预期 |
|---|------|------|------|
| M3-1 | 三入口 | 设置页 | 数据管理 / 服务 / 诊断 三行(旧「服务与诊断」不存在) |
| M3-2 | 服务页两分区 | 进 `settings/services` | 通信服务(外部访问+端口)/ MCP(启用/外部/令牌认证/端口/令牌入口/错误行) |
| M3-3 | 端口 sheet 唯一 | 两端口行各点击 | 同一 `ModevPortSheet` 形态,1..65535 校验,非法值报错不关 |
| M3-4 | 诊断页瘦身 | 进 `settings/diagnostics` | 框架作用域/在线目标/管道统计/复制;无外部访问开关、无端口编辑、无 MCP 开关、无令牌行 |
| M3-5 | 令牌页 | 进 `settings/mcp-tokens` | 认证开关 + 列表(掩码默认、点击显隐、复制/撤销按钮、空态文案) |
| M3-6 | 对话框无图标 | 任一危险确认(如令牌撤销/清空记录) | 居中 AlertDialog 无图标、确认红字、取消/确认 |
| M3-7 | 记录页清空入条 | 任一记录列表页 | Appbar 仅 SearchBar 无按钮;搜索条内有删除图标(点击弹 M3-6 确认) |
| M3-8 | 运行按钮最右 | 应用列表行 | trailing = Checkbox + 最右 PlayArrow;不可启动应用无 PlayArrow;点击启动不触发行导航 |
| M3-9 | 双击退出 | 根页按返回 | 首按 Snackbar「再按一次退出程序」且不退出;2s 内再按退出;>2s 再按仅再提示 |
| M3-10 | 既有插桩抽样 | connectedModernDebugAndroidTest 抽 DiagnosticsScreenTest/ServicesScreenTest/FourScreensNavTest/MonitorListScreensTest | 全绿(测后执行 AGP 卸载恢复序列) |

## M4 注入侧连通性(wire 1511)

| # | 用例 | 动作 | 预期 |
|---|------|------|------|
| M4-1 | 通道建立 | 触发靶程序记录(RunCase)后 `server_status.wire.connectedPackages` | 含 testtarget |
| M4-2 | 记录落库 | `query-records`/sqlite 计数 | crypto_records 有新行(seq 连续,无覆盖丢失) |
| M4-3 | 配置推送 | ResetState 推配置 → 重启靶 | 靶侧按新配置采集(记录 kind 匹配) |
| M4-4 | 冒烟门禁 | `23-run-smoke.ps1` | 全绿(冷启动 + L-SMOKE-01 + ANR 检查) |
| M4-5 | MCP 矩阵 | `24-run-mcp.ps1` | 42 子断言全 PASS(M-SRV-01a/b 改 1510、M-PROTO-06 默认免 Token) |

## 执行顺序与恢复注意

1. M0 → M4-1..3 → M1 → M2 → M3(UI 手工/插桩)→ M4-4/5(全矩阵)。
2. **connectedAndroidTest 排最后**(AGP 测后卸载宿主 APK → Vector modules 行删除,
   需按 `11-vector-scope.ps1` + 重启 VM 恢复,AGENTS 已知坑)。
3. 雷电剪贴板静默丢弃:令牌复制断言走 Snackbar 哨兵,内容断言由 JVM 单测兜底。
4. 全程用 `ANDROID_ADB_SERVER_PORT=5038` + 雷电自带 adb,避免 server 互杀。

## 执行完成标注(2026-09-07 实测)

| 项 | 状态 | 工作内容/证据 |
|----|------|---------------|
| M0-1..5 | ✅ 完成 | 双 APK 构建安装 Success;`modules.enabled=1` + scope 含 testtarget;宿主拉起。发现 `install -r` 后 lspd 不注入新进程,需 dnconsole 整机重启(已沉淀 AGENTS)。 |
| M1-1..6 | ✅ 完成 | netstat 实测 MCP `127.0.0.1:1510` + wire `127.0.0.1:1511`;`server_status.boundAddress=127.0.0.1`;UI 端口 sheet 改 1520 即时改绑 + 1520 直通 200 + 改回 1510;外部访问开 → `[::]:1510` 重启持久 + 无头 401/持令牌 200。 |
| M2-1..11 | ✅ 完成 | 默认停用回环直通 200;启用后回环 401;启用即派生;正确/错误令牌 200/401;双令牌并存;UI 撤销即时 401(不重启);关认证级联关外部;令牌不外泄;端口持久。**M2-8 补测**:撤销至空集 → 无头 401 + 旧令牌 401 + UI「空集全拒」文案,后恢复默认。 |
| M3-1..9 | ✅ 完成 | 三入口/服务页两分区/令牌页/无图标确认框/搜索条内清空(Appbar 右缘无按钮)/运行按钮最右(点击拉起不导航)/双击退出全分支(uiautomator 驱动)。 |
| M4-1..5 | ✅ 完成 | `server_status.wire.connectedPackages` 含 testtarget;`24-run-mcp.ps1` **42/42**;`23-run-smoke.ps1` **6/6**;记录落库对账。 |
| 全量 connectedAndroidTest | ✅ 完成(V39 零回归) | 单设备 `am instrument` 82 跑;V39 首跑 19 失败 → 修复 2 项真实回归(ModevChoiceSheet 缺 verticalScroll、DiagnosticsDetailSheet 复制误调 onDismiss)→ 终跑 **18 失败**;v38 基线对比证明 18 项全部预存在(17 完全重合 + WebViewBridge 注入时序,v38 亦失败),**V39 净新增失败 = 0**。 |
| 测后恢复 | ✅ 完成 | 重装 V39 双 APK + `enabled=1` + scope + VM 重启;注入复验 records=25、MCP 健康。 |
| 最终 JVM 门禁 | ✅ 完成 | 单测全绿;lint **49 warning / 0 error**(回基线)。 |

> 18 项预存在失败为雷电模拟器环境性失败(mikepenz markdown 异步解析竞态、
> 记录列表导航 uiautomator 脆弱性、WebView 桥时序),与 V39 改动无关,
> 详见 `docs/test-reports/round-20260907-v39-device.md` 基线对比。
