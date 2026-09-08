# TEST-V42：V42 整改全量压力测试任务书（雷电模拟器 LDPlayer x64）

| 项 | 值 |
|---|---|
| 版本 | V42-STRESS（基于 V42 工作树，2026-09-08） |
| 范围 | V42 五项整改（搜索槽长按门禁 / AppIcon 底图 / 端口可用性校验 / 主题色板回退 / 日韩本地化）+ 全量回归（宿主单测 / 设备插桩 / MCP 矩阵 / 注入矩阵 / 冷启动冒烟） |
| 环境 | LDPlayer x64（`D:\leidian\LDPlayer14`），adb 独立端口 5038，设备序列 `127.0.0.1:5555`（单序列锁定，禁 connectedAndroidTest 双序列踩踏） |
| 编排器 | `scripts/test-integration/25-run-v42-stress.ps1`（单键执行 G0–G8，JSON 落盘 `%TEMP%\modev-tt\v42-stress-round.json`） |
| 通过标准 | **单轮次 G0–G8 全部 PASS**（一次连续执行内全绿，方判战役通过） |
| 失败处理 | 失败循环协议（§3）：根因三分类 → 修复 → **全战役从 G0 完整重跑** → 循环至单轮全绿 |

---

## 0. 战役前置（缺口补齐，已随本任务书落地）

探查确认的 V42 设备侧覆盖缺口已补齐并随 G1 构建验证：

| 缺口 | 补齐 |
|---|---|
| 端口校验 UI 零覆盖 | `ServicesScreenTest` +7：范围非法/特权 80/冲突 1510/占用(测试进程 ServerSocket 占位)/空闲保存/自身端口豁免/`wire-bind-error-row` 失败渲染与恢复（端口迁移占位注入法） |
| ColorSheet 零覆盖且无锚点 | 主源补 testTag `theme-color-sheet`/`color-swatch`（最小可测性变更）+ 新增 `ColorSheetDeviceTest` 4 例（20 色点渲染/选择即写持久/壁纸行恢复/返回键收口） |
| AppIcon 无设备锚点 | 新增 `AppIconDeviceTest`（真实 Coil 终态：`app-row` 在 → `app-icon-placeholder` 归零） |
| ja/ko UI 点选未覆盖 | `LanguageHotSwitchTest` +1（面板点选 日本語/한국어 → localeFlow 同步 → 标题随语） |
| 实机长按探针缺失 | `MonitorListScreensTest` +1（Appbar 内长按 `records-clear-type` 不展开搜索、单击仍弹危险确认） |
| 稳定性压力探针缺失 | 新增 `V42StabilityProbeTest` 3 例：语言热切 ×10（零重建）/ 端口 sheet 校验循环 ×10 / 色板点选 ×10 |
| 探针常量副本漂移风险 | 主源 `SLOT_EXPAND_GRACE_MS` private→internal（androidTest 直引同源） |
| RecordSinkTest 轮询抖动（V42 T6 实测） | 轮询窗 10s→30s 确定性加固（测试侧，零产品风险） |

---

## 1. 门组测试清单（任务 / 方式 / 预期结果）

### G0 环境门

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G0-1 | 设备在线 | LDPlayer adb（`ANDROID_ADB_SERVER_PORT=5038`）`adb devices` | `127.0.0.1:5555` 或 `emulator-5554` 状态 `device`；战役锁定单一序列 |
| G0-2 | 设备可用性 | `adb -s <seq> shell uptime` / getprop | 命令回显正常（VM 未假死） |
| G0-3 | 冻结器禁用 | `settings put global cached_apps_freezer disabled` | 设置成功（注入矩阵前置） |

### G1 构建门

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G1-1 | 主源 APK | `:app:assembleModernDebug` | BUILD SUCCESSFUL，产出 `app-modern-x86_64-debug.apk` |
| G1-2 | androidTest APK | `:app:assembleModernDebugAndroidTest` | BUILD SUCCESSFUL（含全部 V42 新增用例编译） |
| G1-3 | 靶程序 APK | `:testtarget:assembleDebug` | BUILD SUCCESSFUL |

### G2 安装与注入恢复门

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G2-1 | 三 APK 安装 | `adb install -r`（host/androidTest/testtarget） | 三包 Success |
| G2-2 | **整机重启**（install -r 后 lspd 模块表按 inode 缓存失效，唯一恢复法） | `dnconsole quit --index 0` + `dnplayer.exe -index 0` → WaitDevice | 设备回在线，`uptime` 归零可证 |
| G2-3 | Vector 作用域恢复 | `11-vector-scope.ps1`（modules enabled=1 + scope 行 + 注入冒烟 L-CRY-01） | testtarget 作用域行存在 + HookInstaller「探针已安装」日志命中 |

### G3 宿主单测门

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G3-1 | 全量 JVM/Robolectric 单测 | `:app:testModernDebugUnitTest`（103+ 类，含 PortValidatorTest 7 例 / AppIconPlaceholderTest 2 例 / 四语 parity / plurals 门禁 / V42 加固后 RecordSinkTest） | XML 汇总 `failures=0 errors=0`（BUILD SUCCESSFUL） |

### G4 冷启动冒烟门（注入侧回归；V42 零触 hook/**，属回归防线）

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G4-1 | 全功能 ON 冷启动 ×5 App | `23-run-smoke.ps1`（testtarget + 4 第三方作用域 App，崩溃缓冲 + ANR 检查） | 每应用 cold-start PASS |
| G4-2 | D-17 帧语义探针 | L-SMOKE-01（forNameSingle/forNameExplicit/instantiate 全 ok） | PASS（钩子不破坏调用方帧） |

### G5 设备插桩门（V42 主战场）

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G5-1 | 全量插桩单次连续执行 | `adb force-stop 宿主` → `adb -s <seq> shell am instrument -w com.lioisme.modev.test/androidx.test.runner.AndroidJUnitRunner`（30+ 类，~120 用例，含 V42 新增 17 例） | XML `failures=0 errors=0` |
| G5-2 | V42 定向复核（失败归因辅助，非独立门） | 同上失败时按类过滤重放 | 仅归因用 |

**G5 关键用例与预期**（V42 行为锚点）：

| 用例 | 预期 |
|---|---|
| `SearchInputComponentsTest.longPress_onActionSlot/FilterSlot/LeadingSlot_doesNotExpand` ×3 | 长按三槽零展开意图（expansions==0） |
| `SearchInputComponentsTest.tap_onQueryText_stillExpands_afterSlotGate` | 文本区单击仍展开（MD3 标准保持） |
| `MonitorListScreensTest.inApp_longPressOnClearTypeSlot_doesNotExpandSearch` | 实机长按清空槽零展开 + 单击仍弹危险确认 |
| `ServicesScreenTest.portSheet_outOfRange/privileged/conflict/inUse_hardBlocked` ×4 | 行内分型错误 + sheet 保持打开 + 偏好零写入 |
| `ServicesScreenTest.portSheet_freePort_savesAndDismisses` | 空闲端口保存 + sheet 关闭 |
| `ServicesScreenTest.portSheet_selfPort_exemptFromProbe` | 重存当前端口直接放行 |
| `ServicesScreenTest.wireBindError_rowRendersOnFailure_andClearsOnRecovery` | 占位注入 → 错误行渲染；解除 → 行消失 |
| `ColorSheetDeviceTest` ×4 | 20 色点渲染 / 点选即写 ThemeConfig / 壁纸恢复 / 返回键收口 |
| `AppIconDeviceTest.appsList_afterRealLoad_placeholderGone_everywhere` | 真实加载终态占位归零 |
| `LanguageHotSwitchTest.jaKo_*` ×2 | ja→ko 热切零重建 + UI 点选生效 |
| `V42StabilityProbeTest` ×3 | 三链路 ×10 循环状态一致 |

### G6 MCP 矩阵门

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G6-1 | MCP 全矩阵 22 断言（协议 6 / 记录 7 / 配置 2 / 资产 2 / 服务 2 / 应用作用域危险 3） | `24-run-mcp.ps1`（自含构建+安装+前转；`adb forward tcp:1510`） | `=== MCP matrix: 22 PASS / 0 FAIL ===`（scope 组 `unavailable` 属 Vector 2.2 合法态） |

### G7 注入矩阵门

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G7-1 | 监听矩阵 L（11 域 81 kind 正向 + 全域负向） | `20-run-listen.ps1` | 正向 kind 计数全 >0；负向零记录；干净模拟器掩蔽类 SKIP 属预期 |
| G7-2 | 增强矩阵 E（ON/OFF 两态行为断言） | `21-run-enhance.ps1` | 两态差异全 PASS（E-SEC 截屏非黑比、E-EXIT 存活等） |
| G7-3 | 联动矩阵 C（12 项：闸门/栈滤/PathGate/节流/离线 spool 重放） | `22-run-linkage.ps1` | 12 项全 PASS |

### G8 汇总裁决门

| # | 任务 | 方式 | 预期结果 |
|---|---|---|---|
| G8-1 | 五轮 JSON 聚合报告 | `99-report.ps1` → `docs/test-reports/round-<ts>.md` | 报告生成，PASS 率 100%（SKIP 剔除） |
| G8-2 | 战役总裁决 | 25 号编排器汇总 | **单轮次 G0–G8 全 PASS → 「一次性测试结果全量符合预期」成立** |

---

## 2. 执行顺序与纪律

```
G0 → G1 → G2(整机重启!) → G3 → G4 → G5 → G7 → G6 → G8   (单轮次顺序执行)
```

- **单序列纪律**：全部 adb 命令 `-s <锁定序列>`；禁用 `connectedModernDebugAndroidTest`（双序列踩踏坑）。
- **force-stop 前置**：每次 `am instrument` 前 force-stop 宿主（残留进程致 attach 失败/segfault）。
- **顺序纪律（关键）**：`24-run-mcp.ps1` 内部 `install -r` 宿主 APK → lspd 模块表按 inode 缓存失效 → **注入矩阵必须先于 MCP 矩阵执行**（G7 → G6）；androidTest 走手动 `am instrument`（不卸载 APK、不依赖注入），与注入链路无耦合。
- **测后卫生**：G5 产生的种子/偏好由各测试类 @After 自净 + G6/G7 各矩阵 ResetState 自净。
- **装配位置**：G2 整机重启后 lspd 已重载，G4/G7 注入链路有效；若 G7 中途出现零捕获假失败，按 AGENTS 坑序列整机重启后重跑整轮。

---

## 3. 失败循环协议（硬性要求）

任一门任何断言 FAIL：

1. **根因审计**（三分类，引用 file:line 与 XML 断言消息/TT_RESULT 原文）：
   - **产品缺陷** → 修主源，随附最小回归测试（单测/插桩），更新 TASK-V42-AUDIT.md 备注；
   - **测试缺陷**（断言口径/时序/锚点错误）→ 修测试，不改产品；
   - **环境抖动**（adb reset/VM 假死/剪贴板丢弃等 AGENTS 已知坑）→ 按 AGENTS 坑序列处置（重跑/自愈/整机重启），记录处置证据。
2. **修复后全战役重跑**：从 G0 完整重跑单轮次（禁止「单门补测」充当通过——用户要求一次性全量符合预期）。
3. **循环**：重复 1–2 直至单轮次 G0–G8 全部 PASS；无上限轮次，每轮记录于 §4。

---

## 4. 执行轮次日志（追加式）

| 轮次 | 日期 | 门组结果 | 失败与根因 | 修复 | 结论 |
|---|---|---|---|---|---|
| （待执行） | | | | | |

---

## 5. 风险登记册

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| RK-1 | RecordSinkTest 等轮询用例负载抖动 | 低 | 已预加固 30s 窗；若仍抖动按环境抖动处置并记录 |
| RK-2 | LDPlayer 剪贴板静默丢弃（DiagnosticsScreenTest 复制断言） | 中 | 用例内置哨兵降级（Snackbar 证实路径），预期 SKIP 降级非 FAIL |
| RK-3 | G7 注入矩阵历史基线在 V42 工作树下漂移 | 中 | V42 零触 hook/**（全为宿主 UI/domain 层）；若 FAIL 按协议审计根因 |
| RK-4 | 设备离线/VM 假死 | 中 | tt-lib WaitDevice 420s 自愈 + 8 连失联冷重启；G0 门前置拦截 |
| RK-5 | wire bindError 用例的端口迁移时序（G2-3 后 1511 占用态残留） | 低 | 用例 finally + @After 双层恢复默认端口；编排器在 G5 前重置偏好 |
| RK-6 | 新增用例在低速 x64 镜像下的 5–20s 轮询窗不足 | 低 | 全部关键等待均为有界轮询（10s/15s/20s 分级），非固定 sleep |
| RK-7 | 四语切换用例与 ja/ko 字面量断言（日本語/한국어 自名）跨 locale 稳定性 | 低 | 自名键为 IDENTICAL_ALLOWED 面（四 locale 同文），字面量断言稳定 |

---

*本文档由 V42 压力测试战役规划产出；执行裁决以 25 号编排器 JSON 与 G8 汇总报告为准。*
