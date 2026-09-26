# Modev 架构审计任务文档

**审计日期**: 2026-08-30
**审计范围**: 全量源代码架构审计
**质量标准**: Apple/Google 大厂验收标准

---

## 一、审计概览

### 1.1 项目概况
- **定位**: Kotlin/Compose (Material3) Xposed 模块 — 全维度监听引擎
- **代码规模**: ~12,000 行 Kotlin, 10 种 RecordType, ~80 个 Hooker, 10 个 Probe
- **架构**: 手动 DI (AppContainer) + TCP IPC + Room + Paging3 + Navigation Compose

### 1.2 架构分层
```
┌─────────────────────────────────────────────────────────┐
│  core/model + core/codec (纯 Kotlin, 零 Android 依赖)   │
├─────────────────────────────────────────────────────────┤
│  hook/ (注入进程侧, 目标 App 进程内)                     │
├─────────────────────────────────────────────────────────┤
│  data/ (宿主进程侧: db/repo/mapping/server)             │
├─────────────────────────────────────────────────────────┤
│  domain/ (抽象层: capability/scope/server)               │
├─────────────────────────────────────────────────────────┤
│  ui/ (Compose UI 层)                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 二、任务清单

### Phase 1: 基础解耦

#### 任务 4: UI-数据解耦 (domain/record 层抽取) [待完成]
**目标**: 将 `RecordService`/`RecordFilter`/`RecordPage` 从 `data.repo` 迁移到 `domain.record`，使 UI 和未来 MCP 层仅依赖 domain 抽象。

**变更文件**:
- 新增 `domain/record/RecordService.kt`
- 新增 `domain/record/RecordFilter.kt`
- 新增 `domain/record/RecordPage.kt`
- 新增 `domain/record/RecordTypeCount.kt`
- 修改 `data/repo/LogRepository.kt` (实现 domain.record.RecordService)
- 修改所有导入路径

**验收标准**: `PackageBoundaryTest` 通过，UI 层无 data.db 导入

---

#### 任务 5: 应用列表解耦 (PackageId 模型) [待完成]
**目标**: 抽取最小包名信息模型 `PackageId`，使 MCP 层仅需 packageName，无需 label/scoped/connected 等 UI 信息。

**变更文件**:
- 新增 `domain/app/PackageId.kt`
- 修改 `data/repo/AppCatalogSource.kt` (新增 packageIds 流)
- 修改 `data/repo/AppCatalogRepository.kt` (实现 packageIds)
- 修改 `ui/apps/AppsViewModel.kt` (使用 PackageId)

**验收标准**: MCP 可独立使用 PackageId 列表

---

### Phase 2: 功能分离

#### 任务 1: 监听/增强 UI 分离 [待完成]
**目标**: 将 `PerAppListenSettingsScreen` 拆分为监听功能区和增强功能区，视觉上明确区分纯监听与干预型 hook。

**变更文件**:
- 修改 `core/model/CategoryTaxonomy.kt` (新增 FunctionDomain 枚举)
- 重构 `ui/apps/PerAppListenSettingsScreen.kt` (Tab 或 Section 分离)
- 修改 `ui/navigation/ModevRoutes.kt` (新增路由)
- 修改 `ui/ModevNav.kt` (注册新路由)
- 修改 `values/strings.xml` / `values-zh/strings.xml` (新增字符串)

**验收标准**: 监听区与增强区视觉分离，87 个开关正确分组

---

#### 任务 2: 默认启停优化 (回报比驱动) [待完成]
**目标**: 基于回报比 (逆向价值/噪声开销) 审查所有功能默认状态，确保高价值功能默认启用，高噪声功能默认停用。

**变更文件**:
- 修改 `core/model/CategoryTaxonomy.kt` (调整 KEY_AGREEMENT 默认状态)
- 修改 `core/model/HookSwitch.kt` (同步默认值)
- 更新相关测试断言

**验收标准**: 所有默认状态与 CategoryTaxonomy 一致，测试通过

---

### Phase 3: 架构整洁

#### 任务 3: 工厂制分离架构 [待完成]
**目标**: 将硬编码的实例化逻辑抽取为独立工厂类，保持架构整洁。

**变更文件**:
- 新增 `core/factory/RecordCodecFactory.kt`
- 新增 `core/factory/ProbeFactory.kt`
- 新增 `core/factory/RowPresentationFactory.kt`
- 修改 `data/repo/LogRepository.kt` (使用 RecordCodecFactory)
- 修改 `hook/HookInstaller.kt` (使用 ProbeFactory)
- 修改 `ui/components/RecordRow.kt` (使用 RowPresentationFactory)

**验收标准**: 工厂类独立于业务逻辑，可独立测试

---

#### 任务 7: 记录列表与详情性能优化 [待完成]
**目标**: 优化搜索 debounce、LazyColumn 性能、邻居查询节流，杜绝卡顿和闪烁。

**变更文件**:
- 修改 `ui/records/RecordsViewModel.kt` (添加 debounce)
- 修改 `ui/records/RecordTimelineList.kt` (LazyColumn 参数优化)
- 修改 `ui/logs/RecordDetailViewModel.kt` (邻居查询节流)

**验收标准**: 搜索无卡顿，列表滚动 60fps

---

### Phase 4: 体验优化

#### 任务 6: 应用列表横向加载指示器 [待完成]
**目标**: 在应用列表加载时显示横向 LinearProgressIndicator。

**变更文件**:
- 修改 `ui/apps/AppsScreen.kt` (添加 LinearProgressIndicator)

**验收标准**: 加载时顶部显示进度条

---

## 三、进度跟踪

| 任务 | 状态 | 完成时间 | 备注 |
|------|------|---------|------|
| 任务 4: UI-数据解耦 | ⬜ 待完成 | - | - |
| 任务 5: 应用列表解耦 | ⬜ 待完成 | - | - |
| 任务 1: 监听/增强分离 | ⬜ 待完成 | - | - |
| 任务 2: 默认启停优化 | ⬜ 待完成 | - | - |
| 任务 3: 工厂制分离 | ⬜ 待完成 | - | - |
| 任务 7: 性能优化 | ⬜ 待完成 | - | - |
| 任务 6: 加载指示器 | ⬜ 待完成 | - | - |

---

## 四、验收门禁

### 4.1 静态验收
- [ ] `.\gradlew.bat :app:lintModernRelease` 零新增 warning
- [ ] `.\gradlew.bat :app:testModernDebugUnitTest` 全绿
- [ ] `PackageBoundaryTest` 通过

### 4.2 动态验收
- [ ] 应用列表加载横向进度条可见
- [ ] 监听设置页与增强设置页完全分离
- [ ] 记录列表搜索 debounce 生效
- [ ] 记录详情页 markdown 渲染流畅
