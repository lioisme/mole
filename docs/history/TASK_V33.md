# Modev V33 — 全量功能扩展任务文档

> 状态追踪文件 | 创建时间: 2026-08-28 | 版本: V33

## 一、需求总览

| # | 原始需求 | 建议命名 | 类型归属 | 干预性质 | 状态 |
|---|---------|---------|---------|---------|------|
| 1 | 弹窗定位 + 返回键关闭 | `DialogLocate` | UI_INTERACTION | 记录+干预 | ⬜ 待开发 |
| 2 | 拦截包含关键字的弹窗 | `DialogIntercept` | UI_INTERACTION | 干预(条件拦截) | ⬜ 待开发 |
| 3 | 控件文本赋值记录 | `WidgetTextTrack` | UI_INTERACTION | 仅记录 | ⬜ 待开发 |
| 4 | Activity 跳转 + Intent 参数记录 | `ActivityTrack` | UI_INTERACTION | 仅记录 | ⬜ 待开发 |
| 5 | Logcat 日志捕获 | `LogcatCapture` | SYSTEM | 仅记录 | ⬜ 待开发 |
| 6 | Application 信息 | `AppInfoCapture` | SYSTEM | 仅记录 | ⬜ 待开发 |
| 7 | 拦截应用退出 | `ExitIntercept` | PROCESS_CONTROL | 干预(阻断) | ⬜ 待开发 |
| 8 | 防止应用闪退崩溃 | `CrashPrevent` | PROCESS_CONTROL | 干预(兜底) | ⬜ 待开发 |
| 9 | 解除屏幕录屏限制 | `ScreenRecordBypass` | PROCESS_CONTROL | 干预(伪装) | ⬜ 待开发 |
| 10 | 读取应用签名监听 | `SignatureMonitor` | SYSTEM | 仅记录 | ⬜ 待开发 |

## 二、架构设计

### 2.1 记录类型扩展

| RecordType | wire | 迁移版本 | 说明 |
|-----------|------|---------|------|
| `UI_INTERACTION` | `"ui"` | v12 | UI 交互监听(Dialog/Widget/Activity) |
| `SYSTEM` | `"system"` | v12 | 系统信息监听(Logcat/AppInfo/Signature) |
| `PROCESS_CONTROL` | `"process"` | v12 | 进程控制(Exit拦截/Crash兜底/录屏绕过) |

### 2.2 Kind 枚举

#### UI_INTERACTION (5 kinds)
| Kind | 默认 | 噪点 | 说明 |
|------|------|------|------|
| `DIALOG_SHOW` | ON | SIGNAL | Dialog.show() 记录 |
| `DIALOG_KEYWORD` | ON | SIGNAL | 关键字弹窗拦截记录 |
| `WIDGET_TEXT` | OFF | NOISE_HIGH | TextView.setText() 记录 |
| `ACTIVITY_START` | ON | SIGNAL | Activity 启动记录 |
| `ACTIVITY_RESUME` | OFF | NOISE | Activity 恢复记录 |

#### SYSTEM (3 kinds)
| Kind | 默认 | 噪点 | 说明 |
|------|------|------|------|
| `LOGCAT` | OFF | NOISE_HIGH | Logcat 输出捕获 |
| `APP_INFO` | ON | SIGNAL | Application 元数据 |
| `SIGNATURE` | ON | SIGNAL | 签名读取监听 |

#### PROCESS_CONTROL (3 kinds)
| Kind | 默认 | 噪点 | 说明 |
|------|------|------|------|
| `EXIT_INTERCEPT` | OFF | SIGNAL | System.exit/killProcess 拦截 |
| `CRASH_PREVENT` | OFF | SIGNAL | 未捕获异常兜底 |
| `SCREEN_RECORD` | OFF | NOISE | 录屏限制解除 |

## 三、实施顺序

- [x] Phase 1: 核心模型层（无依赖）
- [x] Phase 2: 编解码层（依赖 Phase 1）
- [x] Phase 3: 数据库层（依赖 Phase 1）
- [x] Phase 4: 注入层（依赖 Phase 1-3）
- [x] Phase 5: 宿主层（依赖 Phase 1-4）
- [x] Phase 6: UI 层（依赖 Phase 1-5）
- [ ] Phase 7: 构建验证 + 测试

## 四、变更日志

### Phase 1: 核心模型层
（实施后填写）

### Phase 2: 编解码层
（实施后填写）

### Phase 3: 数据库层
（实施后填写）

### Phase 4: 注入层
（实施后填写）

### Phase 5: 宿主层
（实施后填写）

### Phase 6: UI 层
（实施后填写）

### Phase 7: 构建验证
（实施后填写）
