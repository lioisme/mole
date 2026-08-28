# Task: 映欲 App 全量抓包与接口存档 (V32)

> **版本**: V32  
> **日期**: 2026-08-28  
> **状态**: 进行中  

---

## 总览

使用 **Modev + PinProxy** 双工具协作,对"映欲"App 的所有功能进行全量抓包,按 HAR 1.2 格式存档每个接口,同步记录全流程算法处理。

### 架构分工

```
┌─────────────────────────────────────────────────────────────────┐
│                    映欲 App (目标应用)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│  │  API 请求    │  │  加密/签名   │  │  SSL 连接    │            │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘            │
└─────────┼────────────────┼────────────────┼────────────────────┘
          │                │                │
          ▼                ▼                ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  PinProxy       │  │  Modev          │  │  Modev          │
│  (流量捕获)      │  │  (算法监听)      │  │  (SSL 绕过)      │
│                 │  │                 │  │                 │
│  - HTTPS 解密   │  │  - AES/MD5/RSA  │  │  - TrustAll     │
│  - HAR 导出     │  │  - 密钥/IV 捕获  │  │  - 证书固定绕过  │
│  - 请求/响应体  │  │  - 调用栈追踪    │  │  - 混淆 OkHttp  │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  HAR 文件       │  │  算法记录 JSON   │  │  网络记录 JSON   │
│  (每接口一个)    │  │  (Modev DB)     │  │  (Modev DB)     │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              ▼
                    ┌─────────────────┐
                    │  存档关联       │
                    │  (时间戳匹配)   │
                    │                 │
                    │  HAR + 算法文档  │
                    └─────────────────┘
```

### 双重目标
1. **Modev 压力测试**: 验证算法监听 + SSL 绕过在真实场景下的完整性和稳定性
2. **接口资产收集**: 产出可直接使用的 HAR 文件 + 算法处理文档

---

## 进度追踪

### Phase 1: 环境验证与配置

- [ ] T1.1: 设备连接确认 — LDPlayer/雷电模拟器状态
- [ ] T1.2: Modev 安装确认 — Xposed 模块激活 + 作用域配置
- [ ] T1.3: PinProxy 安装确认 — 代理工具就绪
- [ ] T1.4: 映欲 App 安装确认 — 目标应用就绪
- [ ] T1.5: 监听开关配置 — 映欲 App 的 Modev 开关设置

### Phase 2: 映欲 App 子工具遍历

- [ ] T2.1: 主界面子工具清单梳理 — 列出所有可访问项
- [ ] T2.2: 不可访问/广告按钮标记 — 规避列表
- [ ] T2.3: 子工具 1 遍历 — 操作 + PinProxy 抓包 + Modev 记录
- [ ] T2.4: 子工具 2 遍历
- [ ] T2.5: 子工具 N 遍历 (逐项)
- [ ] T2.6: 分页/滚动触发 — 确保列表数据完整加载

### Phase 3: 数据收集与关联

- [ ] T3.1: PinProxy HAR 导出 — 按端点导出 .har 文件
- [ ] T3.2: Modev 算法记录导出 — 从 Modev DB 查询算法记录
- [ ] T3.3: 时间戳关联 — 匹配 HAR 请求与 Modev 算法记录
- [ ] T3.4: 接口清单整理 — 端点表 (URL/方法/参数/算法)

### Phase 4: 存档产出

- [ ] T4.1: HAR 文件归档 — 每个端点一个 .har 文件
- [ ] T4.2: 算法文档生成 — 每个端点的算法处理文档
- [ ] T4.3: 端点清单索引 — archive.json
- [ ] T4.4: 交付文档 — README + 已知问题 + 规避列表

---

## 环境配置指南

### PinProxy 配置

1. 启动 PinProxy,设置代理端口 (默认 8080)
2. 在 PinProxy 中添加映欲 App 到抓包白名单
3. 启用 HTTPS 解密 (安装 CA 证书到设备)

### Modev 配置

在 Modev 中为映欲 App 配置监听开关:

| 监听类型 | 推荐状态 | 理由 |
|----------|----------|------|
| 算法监听 - CIPHER | ✅ 开 | 捕获 AES/RSA 加解密 |
| 算法监听 - MESSAGE_DIGEST | ✅ 开 | 捕获 MD5/SHA 签名 |
| 算法监听 - MAC | ✅ 开 | 捕获 HMAC |
| 算法监听 - SIGNATURE | ✅ 开 | 捕获数字签名 |
| 网络监听 - REQUEST | ✅ 开 | 捕获请求元数据 + 堆栈 |
| 网络监听 - SSL_CONTEXT | ✅ 开 | SSL 绕过 |
| 网络监听 - SSL_OKHTTP_PINNER | ✅ 开 | OkHttp 证书固定绕过 |
| 偏好监听 | ⚠️ 可选 | 捕获 SharedPreferences |
| 行为监听 | ⚠️ 可选 | 捕获文件操作 |

### 执行流程

```
1. 启动 PinProxy (代理模式)
2. 启动映欲 App
3. 逐项操作子工具
4. 每完成一个子工具:
   a. PinProxy 导出该会话的 HAR
   b. Modev 查看算法记录
5. 全部完成后:
   a. 整理 HAR 文件
   b. 生成算法文档
   c. 关联匹配
```

---

## 存档目录结构

```
docs/mappingyu/
├── README.md                    # 项目说明
├── archive.json                 # 端点清单索引
├── har/                         # PinProxy 导出的 HAR 文件
│   ├── 01-<端点名>.har
│   ├── 02-<端点名>.har
│   └── ...
├── algorithms/                  # 算法处理文档
│   ├── 01-<端点名>-algorithm.md
│   ├── 02-<端点名>-algorithm.md
│   └── ...
├── modev-records/               # Modev 导出的算法记录
│   ├── 01-<端点名>-crypto.json
│   └── ...
└── notes/
    ├── inaccessible.md          # 不可访问/广告按钮记录
    └── known-issues.md          # 已知问题
```

---

## 算法文档模板

每个端点的算法文档格式:

```markdown
# <端点名> 算法处理文档

## 1. 接口信息
- **URL**: POST https://api.example.com/xxx
- **方法**: POST
- **Content-Type**: application/x-www-form-urlencoded

## 2. 请求签名
- **算法**: MD5
- **签名格式**: `param1=value1&param2=value2&...&盐`
- **盐值**: `<从 Modev 算法记录提取>`

## 3. 请求加密
- **算法**: AES-256-CBC/PKCS5Padding
- **密钥**: `<从 Modev 算法记录提取>`
- **IV**: `<从 Modev 算法记录提取>`

## 4. 响应解密
- **信封格式**: `{"suffix":"...","data":"<base64>"}`
- **解密 IV**: `固定前缀 + 响应内 suffix`

## 5. Modev 算法记录
| 时间戳 | 算法 | 操作 | 输入 | 输出 |
|--------|------|------|------|------|
| ... | ... | ... | ... | ... |

## 6. HAR 请求/响应示例
(从 PinProxy HAR 文件提取)
```

---

## 验收标准

| # | 标准 | 验证方式 |
|---|------|----------|
| 1 | 每个可访问子工具均有对应 HAR 文件 | 文件清单核对 |
| 2 | HAR 文件符合 HTTP Archive 1.2 标准 | 结构校验 |
| 3 | 每个端点均有算法处理文档 | 文档完整性 |
| 4 | 算法文档中的密钥/IV/签名与 Modev 捕获一致 | 交叉验证 |
| 5 | 不可访问/广告按钮已记录并标记规避 | notes/inaccessible.md |
| 6 | Modev 无崩溃,记录数 ≥ 预期 | logcat + DB 查询 |
| 7 | lint 零新增 warning | lintModernRelease |
| 8 | 单元测试全部通过 | testModernDebugUnitTest |

---

## 执行日志

### 2026-08-28: 任务启动 + 架构澄清

**关键澄清**: Modev 不实现抓包功能,是搭配独立的 PinProxy 软件一起使用。

- **Modev 定位**: 算法监听 (AES/MD5/RSA/HMAC) + SSL 绕过 (TrustAll/证书固定绕过)
- **PinProxy 定位**: 流量捕获 + HAR 导出 (独立代理工具)
- **协作方式**: PinProxy 代理抓包 → Modev SSL 绕过放行 → 双方各自记录 → 时间戳关联

**已完成工作**:
- ✅ 任务文档创建 (TASK-MAPPINGYU-CAPTURE.md)
- ✅ 架构分工图绘制
- ✅ 环境配置指南编写
- ✅ 存档目录结构设计
- ✅ 算法文档模板定义
- ✅ 验收标准制定

**代码状态**:
- ✅ NetworkRequestHooker 已恢复原状 (V30,无增强)
- ✅ NetworkRecordMappers 已恢复原状 (无 HAR 导出)
- ✅ RecordDetailScreen 已恢复原状 (无 HAR 导出入口)
- ✅ 构建通过,零新增 warning

### 2026-08-28: Phase 1 环境验证完成

**设备**: LDPlayer 模拟器 (emulator-5554)
**目标应用**: com.taobao.taobao_a120 (映欲 v1.2.1)
**Modev**: 已安装,配置服务器运行中 (端口 1510)
**PinProxy**: 已安装 (com.network.proxy)

**Modev 记录统计**:
- 总记录数: 555+ (持续增长)
- 算法监听: 325 条 MESSAGE_DIGEST + 106 条 CIPHER
- 偏好监听: 27 条 PUT + 10 条 OPEN
- 数据库监听: 26 条 QUERY + 9 条 EXEC + 4 条 DML
- 行为监听: 24 条 FILE_WRITE + 14 条 FILE_RENAME + 8 条 FILE_DELETE

**发现的加密算法**:
1. **AES/CBC/PKCS7Padding** (主加密)
   - Key: `d494f748847f494d` (16 bytes, hex)
   - IV: `0a010b05040f070917030106080c0d5b` (16 bytes)
   - 用途: 页面追踪数据加密

2. **AES/CBC/PKCS5Padding** (API 响应解密)
   - Key: `she9s1co3826dhs4` (16 bytes)
   - IV: `83ks7v6ehs830kl2` (16 bytes)
   - 用途: API 响应数据解密

3. **AES/CBC/PKCS7Padding** (视频列表 API)
   - Key: `VnEyMzR6QmVTZEdnWVh6VlRFZm5uampkbWFUa2s3QTQ=` (base64)
   - IV: `LXA5Qlt+UG5QczcyMzVhOA==` (base64)
   - 用途: 视频列表请求/响应加密

**API 响应结构** (已解密):
```json
{
  "code": 0,
  "msg": "操作成功",
  "data": {
    "total": 2540,
    "per_page": 100,
    "current_page": 1,
    "last_page": 26,
    "data": [
      {
        "id": 483395,
        "title": "MFK-0088.想成为旁观者.",
        "status": 1,
        "thumb": "https://imgsmd.n123dx.xyz/doudou/ms/...",
        ...
      }
    ]
  }
}
```

### 2026-08-28: Phase 2 子工具遍历进行中

**主界面子工具清单** (从 UI 层级提取):
| # | 名称 | 状态 | 说明 |
|---|------|------|------|
| 1 | iOS下载 | ⬜ 待测试 | 外部链接 |
| 2 | 聚合直播 | ⬜ 待测试 | 外部链接 |
| 3 | 香蕉视频 | ⬜ 待测试 | 外部链接 |
| 4 | 麻豆TV | ✅ 已测试 | 视频列表,2540 条内容 |
| 5 | Fulao2 | ⬜ 待测试 | 外部链接 |
| 6 | 新葡京 | ⬜ 待测试 | 外部链接 (广告?) |
| 7 | 91制片厂 | ⬜ 待测试 | 外部链接 |
| 8 | 撸先疯 | ⬜ 待测试 | 外部链接 |
| 9 | 91短视 | ⬜ 待测试 | 外部链接 |
| 10 | 91AV | ⬜ 待测试 | 外部链接 |
| 11 | 开云体 | ⬜ 待测试 | 外部链接 (广告?) |
| 12 | 黑料 | ⬜ 待测试 | 外部链接 |
| 13 | XVideo | ⬜ 待测试 | 外部链接 |
| 14 | 海角社区 | ⬜ 待测试 | 外部链接 |
| 15 | INS社区 | ⬜ 待测试 | 外部链接 |
| 16 | 月光直播 | ⬜ 待测试 | 外部链接 |
| 17 | G點視 | ⬜ 待测试 | 外部链接 |
| 18 | 逼哩逼哩 | ⬜ 待测试 | 外部链接 |
| 19 | 杏吧Pro | ⬜ 待测试 | 外部链接 |
| 20 | 91Pron | ⬜ 待测试 | 外部链接 |
| 21 | 九游体育 | ⬜ 待测试 | 外部链接 (广告?) |
| 22 | 新水果派 | ⬜ 待测试 | 外部链接 |
| 23 | 全网吃瓜 | ⬜ 待测试 | 外部链接 |
| 24 | 51吃瓜 | ⬜ 待测试 | 外部链接 |
| 25 | 猫咪 | ⬜ 待测试 | 外部链接 |

**底部导航栏**:
- Home (当前选中)
- Chanel (频道)
- Search (搜索)

### 2026-08-28: Phase 3 API 分析完成

**Modev 记录统计** (截至 02:32):
- 总记录数: 4590
- 算法监听: 1837 条 (crypto)
- 数据库监听: 1806 条 (db)
- 设备环境: 466 条 (device_env)
- 行为监听: 324 条 (behavior)
- 偏好监听: 120 条 (prefs)
- WebView: 33 条 (webview)
- 网络监听: 4 条 (network)

**发现的 API 端点**:

#### 1. 视频列表 API
- **URL**: `https://spi.d5c4a712.com/video/list`
- **Method**: POST (加密)
- **加密算法**: AES/CBC/PKCS7Padding
- **加密 Key**: `VnEyMzR6QmVTZEdnWVh6VlRFZm5uampkbWFUa2s3QTQ=` (base64)
- **加密 IV**: `LXA5Qlt+UG5QczcyMzVhOA==` (base64)
- **请求签名**: MD5(`madou` + timestamp + key)
- **请求参数** (加密前):
  ```json
  {
    "encode_sign": "e37e0d20addd75ec26dd00de5580fd24",
    "list_row": "100",
    "page": "1",
    "timestamp": "1787855580386",
    "type": "2"
  }
  ```
- **响应结构** (解密后):
  ```json
  {
    "code": 0,
    "msg": "操作成功",
    "data": {
      "total": 2540,
      "per_page": "100",
      "current_page": "1",
      "last_page": 0,
      "data": [
        {
          "id": 483395,
          "title": "MFK-0088.想成为旁观者.",
          "status": 1,
          "thumb": "https://imgsmd.n123dx.xyz/doudou/ms/...",
          ...
        }
      ]
    }
  }
  ```

#### 2. 页面追踪 API
- **加密算法**: AES/CBC/PKCS7Padding
- **加密 Key**: `d494f748847f494d` (hex)
- **加密 IV**: `0a010b05040f070917030106080c0d5b` (hex)
- **用途**: 页面访问追踪
- **请求数据** (加密前):
  ```json
  [
    {
      "page_name": "com.android.s.ui.main.MainActivity",
      "duration": 107502,
      "page_start": 1787855472202,
      "type": 1
    }
  ]
  ```

#### 3. 流量统计 API
- **加密算法**: AES/CBC/PKCS7Padding
- **用途**: 上下行流量统计
- **请求数据** (加密前):
  ```json
  {
    "download_traffic": 1110586,
    "upload_traffic": 1905325
  }
  ```

**频道列表** (Chanel 页):
1. 麻豆传媒映画 - 2540 个片
2. 乐播传媒 - 40 个片
3. 91制作 - 556 个片
4. 91茄子 - 202 个片

**下一步**:
- 继续遍历剩余子工具 (外部链接)
- 启动 PinProxy 抓包
- 收集更多 API 接口数据
- 分析加密算法细节

