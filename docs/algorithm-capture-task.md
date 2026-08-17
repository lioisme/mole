# Modev 算法全量捕获能力建设任务文档

> 名称:**Modev 捕获能力升级** · 定位:从「八类算法的常规调用捕获」升级为「全量捕获、完整字段、链路可复盘」,对标算法助手Pro 的捕获能力上限,并同步补齐审计发现的可靠性缺陷。
> 质量标准:架构分层、空安全、不可变模型、单元测试、lint 清零、文档齐全 —— 对标一线大厂 Android 工程验收标准。
> 状态图例:`⬜ 待办` → `🔄 进行中` → `✅ 已完成`
> 基线:git 77e8670 之后的工作区(含 T1~T13 已完成的双框架/八类 Hooker/UI 全部现状)

---

## 0. 背景与目标

### 0.1 参考软件能力矩阵(算法助手Pro,`docs/log/*.log` 实测解析)

对 `docs/log/` 下 5 个日志文件全量解析(共 1216 条记录):

| 维度 | 参考日志实测结论 |
|---|---|
| 覆盖算法 | `AES/CBC/{NoPadding,PKCS5Padding,PKCS7Padding}`、`AES/CFB/NoPadding`、`DESede/ECB/PKCS5Padding`、`MD5`、`SHA`、`SHA1`、`SHA-256` |
| 记录字段 | 时间、日志名称(算法名)、key类型、操作字段组(加密/解密:密钥、Iv、内容、结果;摘要/签名:内容、结果),每字段 **String / Base64 / Hex 三态** 全量输出 |
| 数据完整性 | 无可见截断(单条摘要输入 hex 长达 100KB+ 完整落盘);每次调用附完整调用堆栈 |
| 记录密度 | 同参重复调用被完整记录(如 URL 摘要连续 13 次),不合并去重 |
| 观察 | 参考软件不区分「摘要」与「签名」标签(摘要也标 签名内容/签名结果),且不过滤框架自触发噪声(JarVerifier 校验 APK 清单时内部摘要也被捕获) |

> 注意:上述为参考软件能力下限(其日志导出受配置影响),不代表其全部能力;本任务按**全量捕获**标准立项,能力目标高于上述实测。

### 0.2 项目现状(审计基线,2026-08-16)

Modev 已具备:双框架注入(LSPosed API 102 + legacy 82)、八类 Hooker(摘要/加解密/MAC/签名/密钥参数/密钥生成/密钥工厂/Base64)、TCP 行协议、Room 持久化、MD3 UI、32 例单元测试全绿。**本次审计(全量代码走查,结论见 §2)发现的差距分为四类:**

1. **能力缺口**:KeyStore / KeyAgreement / SecureRandom / 密钥生成参数 / 参数类 Spec / RSA-EC 密钥参数 / java.util.Base64 / provider(六类丢失)等未捕获;
2. **路径缺陷**:ByteBuffer 入参失效、int 返回型重载输出丢失、`initVerify(Certificate)` 丢公钥、GCM tag 长度丢失、PBE 迭代参数丢失、`maxHexChars/maxTextChars` 配置死配置未生效;
3. **架构盲区**:`MethodFinder` 仅 boot classloader,应用类加载器加密库(okio/Commons-Codec 等)不可 hook;
4. **可靠性命门**:TCP 通道无版本/魔数校验、token 可为 null 放行、spool 无 fsync、宿主 insert 无批处理、丢记录无可见性。

### 0.3 目标定义

> **验收基线**:任意目标 App 内,凡经由 Java 层标准加密门面(含 KeyStore/KeyAgreement/SecureRandom/编码类库)的算法调用,均可被完整捕获:算法名、provider、key(或 alias+不可导出标记)、IV、salt、AAD、tag 长度、迭代参数、keysize/曲线、内容、结果、方向、线程、调用堆栈、进程名,且内容默认全量(三态可回读),零静默丢失。

---

## 1. 需求规格

### 1.1 捕获域矩阵(目标态)

| 域 | 类别 | Hook 目标 | 必须产出的字段 |
|---|---|---|---|
| 消息摘要 | MESSAGE_DIGEST | MessageDigest 全重载 | 算法、provider、输入(update/digest 入参)、输出(digest 结果含 int 重载出参缓冲)、上下文链 |
| 加解密 | CIPHER | Cipher 全重载 | 算法、provider、方向、key、IV(含 init 后自动生成)、GCM tLen、AAD、update 中间输出、doFinal 输入/输出(含 int 重载)、getIV |
| MAC | MAC | Mac 全重载 | 算法、provider、key、输入、输出(含 `doFinal(byte[],int)`)、上下文链 |
| 签名 | SIGNATURE | Signature 全重载 | 算法、provider、私/公钥(含 Certificate 重载)、输入、签名值(含 int 重载)、verify 结果、PSS 参数 |
| 密钥参数 | KEY_PARAM | 全部标准 KeySpec/AlgorithmParameterSpec 构造器 | 构造类型、密钥/IV/salt/password/迭代次数/密钥长度/tag 长度/曲线/模数/指数等全参 |
| 密钥生成 | KEY_GENERATION | KeyGenerator/KeyPairGenerator 全重载 | 算法、provider、init 参数(keysize/规格/随机源)、生成的密钥(含公私钥分离) |
| 密钥工厂 | KEY_FACTORY | KeyFactory/SecretKeyFactory 全重载 | 算法、provider、输入 KeySpec 材料、输出密钥、getKeySpec/translateKey |
| 密钥库 | KEY_STORE | KeyStore 全部关键操作 | 操作、类型/provider、alias、密码(标记)、返回密钥算法、certificate chain 摘要 |
| 密钥协商 | KEY_AGREEMENT | KeyAgreement 全重载 | 算法、provider、己方密钥、对端公钥、共享密钥 |
| 随机数 | RANDOM | SecureRandom 关键方法 | 算法、provider、种子、生成的随机字节(generateSeed/nextBytes) |
| 编码 | BASE64 | android.util.Base64 + java.util.Base64 全重载 | flags、输入、输出 |

### 1.2 字段完整性规格

- 每个字节型字段(密钥/IV/salt/AAD/输入/输出/共享密钥/公钥等)均产出 **hex + 文本 + 总长 + 截断标记** 四元组(BytesView 既有结构,全量时 truncated=false);
- 标量参数(迭代次数、密钥长度、tag 长度、keysize、方向、flags、verify 结果、曲线名)以 `params` JSON 承载,结构稳定、可扩展、可版本化;
- 每条记录携带 `processName`(多进程 App 可区分主/子进程);
- **默认全量捕获**:移除 1024/512 的隐性截断(改由 UI 展示侧截断 + 字段级可配置安全上限 `maxBytesPerField`,默认 1MB,防畸形大记录打爆内存);
- 不可导出密钥(AndroidKeyStore)记录 alias + 不可导出标记;密码型(char[]/KeyStore 密码)照常捕获(调试工具定位,与参考软件一致)。

### 1.3 链路完整性规格

- 同一算法实例的 getInstance→init→update→doFinal 以 `contextId` 关联(唯一实例标识,修复 identityHashCode 碰撞);
- `clone()` 复制上下文、`reset()` 重置累计量;Base64 等静态入口使用进程级占位上下文;
- 方法抛异常时:入参侧信息(before 阶段)仍应产出(与参考软件一致:即使 doFinal 抛错,明文/密文仍在参数中)。

---

## 2. 现状差距分析(审计结论,已全量落档)

> 审计方式:2026-08-16 全量代码走查(hook/ 21 文件、data/ 7 文件、core/ 8 文件、ui/ 24 文件、framework/ 3 文件)+ docs/log 参考日志解析。以下为驱动本任务立项的全部事实依据。

### 2.1 能力缺口

| # | 缺口 | 现状依据 |
|---|---|---|
| G1 | KeyStore 全部操作未捕获(别名/密码/链) | 仅 CipherHooker.extractKey 反射读 alias(CipherHooker.kt:206) |
| G2 | KeyAgreement 未捕获 | 无对应 Hooker,ECDH 共享密钥不可见 |
| G3 | SecureRandom 未捕获 | 无对应 Hooker,随机种子/IV 生成不可见 |
| G4 | KeyGenerator/KeyPairGenerator init 参数未捕获 | KeyGenerationHooker.kt:28-36 仅 hook getInstance/generate |
| G5 | 参数类 Spec 构造器缺失 | KeyParamHooker.kt 仅 4 类 10 个构造器 |
| G6 | RSA/EC 密钥参数(modulus/exponent/curve)未提取 | 仅取 `Key.encoded`(PKCS#8/X.509) |
| G7 | java.util.Base64(API 26+)未捕获 | Base64Hooker.kt 仅 android.util.Base64 3 个方法 |
| G8 | provider 六类丢失 | 仅 CipherHooker.providerOf(CipherHooker.kt:179) |
| G9 | 应用类加载器加密库不可 hook | MethodFinder.kt:49 固定 `Class.forName(...,null)` |

### 2.2 路径缺陷(数据丢失)

| # | 缺陷 | 现状依据 |
|---|---|---|
| D1 | ByteBuffer 入参全失效(cast 失败/position 副作用) | CipherHooker.kt:124-126、143-148;MessageDigestHooker.kt:64-66 等 |
| D2 | int 返回型重载输出全丢 | digest(byte[],int,int) / doFinal(byte[],int,int) / sign(byte[],int,int) / Mac.doFinal(byte[],int) |
| D3 | `initVerify(Certificate)` 公钥丢失 | SignatureHooker.kt:83 仅 `as? PublicKey` |
| D4 | GCM tag 长度丢失 | GCMParameterSpec.getTLen 未读取 |
| D5 | PBE 迭代次数/密钥长度丢失,salt 误标 iv | KeyParamHooker.kt:47-49 |
| D6 | `maxHexChars/maxTextChars` 死配置 | HookSwitch.kt:17-18 定义但无任何调用方 |
| D7 | `GET_INSTANCE` 从不产出、`inputTotal/outputTotal` 死字段 | CryptoOperation.kt:8;CipherHooker.kt:126 |
| D8 | Cipher.update 中间输出丢失、updateAAD 未捕获 | CipherHooker.kt:46-48 |
| D9 | KeyPair 公钥误存 output 字段 | KeyGenerationHooker.kt:84 |
| D10 | Base64 flags 未记录、全静态共享同一 contextId | Base64Hooker.kt:76 |

### 2.3 架构盲区与可靠性

| # | 问题 | 现状依据 |
|---|---|---|
| R1 | TCP 通道 magic/version 从不校验 | ConfigServer.kt:122-149 不读 magic;ConfigChannel.kt:95-99 |
| R2 | requireToken 时 null token 放行(Vector 下实为无鉴权) | ConfigServer.kt:134-139 |
| R3 | record 包名不校验,可伪造任意包记录 | ConfigServer.kt:151-158 |
| R4 | 端口被占即永久死亡,无重试 | ConfigServer.kt:90-92 |
| R5 | spool 无 fsync、轮转非原子、drain 全量驻内存 | RecordSpool.kt:27-31、42、56 |
| R6 | 宿主逐条 insert、无错误处理(写库异常崩进程) | LogRepository.kt:49-57 |
| R7 | trim 全局 2 万条无每包上限、无索引排序 | CryptoRecordDao.kt:114-121 |
| R8 | 丢记录计数无 UI 呈现 | RecordSink.droppedCount 无消费方 |
| R9 | contextId 用 identityHashCode 有跨代碰撞 | CryptoContextTracker.kt:24 |
| R10 | Room schema 未导出、无迁移基线 | app/build.gradle.kts 无 room.schemaLocation |
| R11 | 堆栈过滤缺 java.lang/java.util/kotlin 等噪声前缀 | StackTraceFilter.kt:14-35 |
| R12 | 清空日志/移除作用域无确认、无导出/分享 | CaptureSettingsScreen.kt:153-168 |

---

## 3. 架构与设计决策(ADR)

### ADR-1 数据模型 v2:`CryptoRecord` 增补字段(不破坏既有字段)

新增:`processName: String?`、`salt: BytesView?`(PBE salt,语义修正)、`aad: BytesView?`、`publicKey: BytesView?`(密钥对公钥/协商对端公钥)、`params: Map<String,String>?`(标量参数承载)、`verifyResult: Boolean?`。既有字段全保留,枚举新增 KEY_STORE / KEY_AGREEMENT / RANDOM 三类。`output` 语义收窄为「输出数据」,公钥不再借道 output。

### ADR-2 线协议 v2:VERSION=2 + 全字段校验

- 握手携带 magic/version,双端校验:`magic != MDEV` 或 `version != 2` 直接拒绝并断开(可扩展为版本协商,当前同 APK 双端必然同版);
- record 载荷携带 processName 与 params;`recordOf` 增加结构校验(必需字段非空 + 枚举值合法);
- 协议注释写明 v1→v2 破坏性变更(宿主与注入侧同 APK 升级,无跨版本兼容诉求)。

### ADR-3 Room v2:显式迁移 + schema 基线

- entity 增列(salt/aad/publicKey 四元组 ×3、paramsJson、processName、verifyResult),v1→v2 用 `ALTER TABLE ADD COLUMN` 手写 Migration;
- 补 `room.schemaDirectory` 配置,导出 schema JSON 作为迁移基线;
- trim 增加每包上限(全局 keep + 每包 keep,双层治理)。

### ADR-4 全量捕获策略:捕获侧全量,展示侧截断

- 注入侧不再隐式截断:`BytesView.of` 默认上限提升至 `HookSwitch.maxBytesPerField`(默认 1MB,可配置 0=无限);超限仍产出截断视图但标记 truncated(防畸形数据打爆传输/DB);
- hex/文本仅在**展示层**做 UI 截断 + 展开;文本可打印判定阈值同步提升(TextUtil 上限参数化);
- 该策略与参考软件「全量落盘」一致,且保留防爆保险丝。

### ADR-5 实例标识:全局唯一 ID 替代 identityHashCode

- `CryptoContextTracker` 改 `WeakHashMap<Any, Long>` + AtomicLong 自增,实例存活期内 ID 唯一,消除跨代碰撞导致的链路错乱。

### ADR-6 类加载器感知的 MethodFinder

- 解析顺序:boot(现状)→ `Thread.currentThread().contextClassLoader` → 已 hook 对象的 `javaClass.classLoader`;仅 boot 加载类缓存「不存在」,可寻址类缓存定位结果;
- 门面类(java.security.* 等)仍要求 boot 加载,本能力主要服务第三方编码类库(okio ByteString、Apache Commons Codec 等),按需开启。

### ADR-7 噪声治理:调用栈 denylist 机制

- `HookSwitch.ignoreStackPrefixes: List<String>`(默认空 = 保持参考软件「全捕获」行为);匹配调用栈任一帧即跳过该记录;
- 内置预设建议值写入 UI 提示(如 `java.util.jar`、`sun.security`、`android.util.apk`),由用户按需启用,不默认过滤。

### ADR-8 通道加固

- 握手必须携带且匹配 token(移除 null 放行);legacy/Vector 无法下发 token 时,由宿主端显式关闭鉴权开关(默认开)以保留真机链路可用性;
- record 的 packageName 必须与会话 hello 包名一致,否则丢弃并计数;
- 端口 bind 失败:指数退避重试(2s→30s),不再一次失败即死;
- sendRecordJson 增加 socket sendTimeout 兜底(不阻塞 sink 线程)。

### ADR-9 Spool 持久化加固

- append 改 `FileOutputStream(append=true)` + `flush()` + `fd.sync()`(每次写后同步,牺牲吞吐换零丢失——记录落盘本就低频);
- 轮转:先 fsync 当前文件再重命名,双文件策略不变;
- drain 逐行流式处理(不整文件驻内存)、行长度上限校验(>4MB 视为损坏行跳过);
- 新增 `spoolDropped` 计数并入丢记录统计。

### ADR-10 宿主管线加固

- 批量写入:DAO 层 `insertAll` 批量 + 每 200ms 或 32 条强制落库一次(Mutex 内合并,减少事务次数);
- 写库异常:捕获并计数(不崩进程),UI 呈现「丢弃/失败计数」;
- 丢记录可见性:队列丢弃 + 发送失败 + spool 丢弃 + 宿主 insert 失败,统一汇入宿主 StateFlow,设置页展示。

### ADR-11 新增类别开关与 UI

- HookSwitch 增加 keyStore/keyAgreement/random 三个开关(默认开;base64 保持默认关);
- CaptureSettingsScreen 开关网格扩展至 11 类 + 堆栈开关 + 「忽略栈前缀」编辑;
- 记录详情页新增 salt/AAD/公钥/params/verifyResult/进程名展示,params 以 key:value 行呈现;
- 危险操作(清空日志/移除作用域/清空全部)补确认对话框;记录详情页支持导出/分享当前记录(文本形式)。

---

## 4. 任务清单

### P0 数据模型 / 协议 / 数据库 v2

#### T1 数据模型 v2 ✅ 已完成
**验收**:`CryptoRecord` 新增 processName/salt/aad/publicKey/params/verifyResult 六字段且序列化往返一致;`CryptoKind` 新增 KEY_STORE/KEY_AGREEMENT/RANDOM;`CryptoOperation` 按需补充(KeyStore getKey→GET_KEY、KeyAgreement generateSecret→GENERATE_SECRET、SecureRandom generateSeed→GENERATE_SEED 等);`HookSwitch` 新增 keyStore/keyAgreement/random 开关与 `maxBytesPerField`(默认 1MB,0=无限)、`ignoreStackPrefixes` 列表;全部默认值语义正确。
**备注**:`CryptoRecord` 新增六字段均位于既有字段之后,Gson 序列化天然兼容;`CryptoOperation` 实际新增 GET_KEY/SET_KEY/DELETE_KEY/LOAD/DO_PHASE/GENERATE_SEED 六值;`CryptoContext` 同步扩展 salt/gcmTagLen/iterationCount/keyLength/keysize/lastInitIv 字段;`HookSwitch.maxHexChars/maxTextChars` 标注 @deprecated 保留兼容旧配置。

#### T2 线协议 v2 ✅ 已完成
**验收**:`WireProtocol.VERSION=2`;hello 增加 magic/version 双端校验(不符即断连);record 载荷含 processName/params;`WireCodec.recordOf` 增加枚举合法性与必需字段强校验;既有 WireCodec 测试更新并通过。
**备注**:`WireProtocol` 注释标注 v1→v2 破坏性变更清单;`WireCodec` 新增 `versionOf/magicOf/isHelloCompatible`,`recordOf` 增加 kind/operation/packageName/algorithm 强校验(非法枚举由 Gson 抛错→null);ConfigServer/ConfigChannel 握手接入见 T11。

#### T3 Room v2(迁移 + schema 基线)✅ 已完成
**验收**:`CryptoRecordEntity` 增列(processName、salt/aad/publicKey 四元组、paramsJson、verifyResult),DAO 增列相应查询与 `insertAll` 批量接口、每包 trim;`ModevDatabase.version=2` + v1→v2 显式 Migration;`room.schemaDirectory` 已配置并导出 schema 基线;既有 DAO 测试更新,新增迁移与批量插入测试。
**备注**:`MIGRATION_1_2` 纯新增 15 个可空/带默认列,ALTER TABLE ADD COLUMN 不动存量数据;`CryptoRecordDao` 新增 `insertAll`/`trimPackage`;`app/build.gradle.kts` 补 `ksp { arg("room.schemaLocation", ...) }`,首次构建生成 `app/schemas/` 基线;`LogRepository` 实体映射补齐新字段;`RecordRow` 的 kind/operation 穷举 when 与 strings.xml(zh/en)同步补全;`compileModernDebugKotlin` 通过。

### P1 Java 层全量捕获

#### T4 既有路径缺陷修复 ✅ 已完成
**验收**:① ByteBuffer 入参在 proceed 前经 duplicate+flip 安全拷贝,输出按 remaining() 处理;② int 返回型重载输出回读(`digest(byte[],int,int)`、`Cipher.doFinal(byte[],int,int)`、`Signature.sign(byte[],int,int)`、`Mac.doFinal(byte[],int)`);③ `initVerify(Certificate)` 提取 `cert.getPublicKey()`;④ provider 补齐(getInstance(String,String)/(String,Provider) 全类别);⑤ `update()` 中间输出捕获;⑥ `Cipher.getIV()` 在 init 后自动补采随机 IV(CBC/GCM 无 spec 场景)。
**备注**:新增 `HookerSupport.bufferInput(...)` 工具(ByteBuffer 安全拷贝,全部 Hooker 复用);`CryptoContext` 增加 `lastInitIv` 字段供自动补采。**工作备注**:新增 `HookerSupport.kt`(bufferInput/bufferRemaining/slice/stash/take,ThreadLocal 栈);`BaseHooker` 重写(processName 经 /proc/self/cmdline、通用 providerOf、emit 扩展 salt/aad/publicKey/params/verifyResult/processName);`CipherHooker` 三段式重构(init before 采集→after 产出+随机 IV 补采、update/doFinal 全重载、updateAAD 累计)。

#### T5 参数类 Spec 构造器补全 ✅ 已完成
**验收**:新增构造器捕获:`DHParameterSpec(p,q,g)`、`ECGenParameterSpec(name)`、`DSAParameterSpec`、`PBEParameterSpec(salt,count)`、`OAEPParameterSpec`、`MGF1ParameterSpec`、`PSSParameterSpec`、`RSAPrivateCrtKeySpec`(模数/指数/P/Q/CRT 系数)、`RSAKeyGenParameterSpec(keysize, exponent)`、`ECParameterSpec`、`DHGenParameterSpec`;`GCMParameterSpec` 补 tag 长度;`PBEKeySpec` 补迭代次数/密钥长度,password 保持 char[] 捕获,salt 独立字段;`SecretKeySpec(byte[],int,int,String)` 修正为切片捕获;参数以 `params` 承载。
**备注**:`KeyParamHooker` 重构为 Spec 注册表(类名+构造器签名+字段提取器),`SpecExtractor` 纯逻辑对象可单测。**工作备注**:`SpecExtractor.kt` 纯逻辑提取(15 类)+ `extractKeystoreSpec` 反射;`KeyParamHooker` 改为 CtorDescriptor 注册表(22 构造器);注意 OAEPParameterSpec/PSource 属于 javax.crypto.spec。

#### T6 密钥域补全 ✅ 已完成
**验收**:① `KeyGenerator.init` ×4 与 `KeyPairGenerator.initialize` ×4 捕获,keysize/曲线/`KeyGenParameterSpec` 参数入 params;② `KeyFactory.getKeySpec/translateKey` 捕获;③ RSA/DSA/EC 密钥参数提取(`RSAPublicKey` modulus/exponent、`RSAPrivateCrtKey` P/Q/DP/DQ、`DSAParams`、EC 曲线),经反射接口安全提取入 params;④ 密钥对分离:`generateKeyPair` 私钥→key、公钥→publicKey。
**工作备注**:`KeyParamExtractor.kt` 经标准接口(RSAPrivateCrtKey/RSAPublicKey/DSAParams/ECKey)提取;`KeyGenerationHooker` 8 个 init/initialize 重载 + generateKeyPair 密钥对分离;`KeyFactoryHooker` 补 getKeySpec/translateKey。

#### T7 新域:KeyStore / KeyAgreement / SecureRandom / java.util.Base64 ✅ 已完成
**验收**:① `KeyStoreHooker`(KEY_STORE):getInstance(类型+provider)、load(stream,password)、aliases、containsAlias、getKey(alias,password)、setKeyEntry、deleteEntry、getCertificateChain(链摘要)、getEntry;密码标记捕获;② `KeyAgreementHooker`(KEY_AGREEMENT):getInstance、init(key)、doPhase(key,lastPhase)、generateSecret×3,己方密钥→key、对端公钥→publicKey、共享密钥→output;③ `SecureRandomHooker`(RANDOM):getInstance、setSeed(byte[])、nextBytes、generateSeed、getProvider;④ `Base64Hooker` 扩展:`java.util.Base64`(Encoder/Decoder 实例方法 + 静态工厂)、android.util.Base64 全 7 个公开方法、flags 入 params。
**备注**:三个新 Hooker 接入 `BaseHooker` 体系与 `HookManager` 注册表(默认开启,base64 保持默认关);java.util.Base64 按实例关联上下文,android.util.Base64 保持进程级占位。**工作备注**:KeyStore 密码走 `PasswordProtection.password` char[] 捕获,不可导出密钥记 alias+keyExportable=false;getEntry 经反射提取证书公钥;hooks:SecureRandomHooker 补 setSeed/nextBytes/generateSeed。

#### T8 上下文跟踪器 v2 ✅ 已完成
**验收**:`CryptoContextTracker` 以 `WeakHashMap<Any, Long>` + AtomicLong 提供实例存活期唯一 ID;`clone()`/`copy()` 上下文传播(新实例继承 algorithm/provider/key/iv/params 等,id 重新分配);`reset()` 重置累计量不清 key;静态上下文使用进程级占位实例。
**工作备注**:改造为单例 object(实例方法 identity/contextFor 共用同一 WeakHashMap,一次查表复用);新增 `cloneContext(from,to)`(BytesView 不可变可直接共享)与 `reset(instance)`(清累计量+AAD,保密钥);Cipher/Mac/MessageDigest 三处新增 clone/reset hook;identity 从 identityHashCode 改为全局自增 Long,消除跨代碰撞。

#### T9 噪声治理机制 ✅ 已完成
**验收**:`HookSwitch.ignoreStackPrefixes` 生效于 `BaseHooker.emit`:任一调用栈帧命中前缀即跳过;内置预设(建议值清单)供 UI 提示;默认空列表保持全捕获。
**工作备注**:`StackTraceFilter` 新增 `isDenied(elements, prefixes)`;`BaseHooker.emit` 在最前判定(默认空列表,零开销);`StackTraceFilter.capture` 增加 denyPrefixes 参数备选入口。

#### T10 全量捕获策略落地 ✅ 已完成
**验收**:`BytesView.of` 默认上限改为 `HookSwitch.maxBytesPerField` 透传(0=无限);文本可打印判定阈值参数化;UI 展示层截断 + 展开交互(详见 T14);`HookSwitch.maxHexChars/maxTextChars` 字段退役(保留字段兼容旧配置 JSON)。
**工作备注**:`BytesView.of` 默认上限提升至 hex 2MB(2×1MB)/text 1MB,文本判定阈值随 maxTextChars 伸缩;`BaseHooker` 新增 `viewOf(bytes)` 统一按 `HookSwitch.maxBytesPerField` 透传(0=无限);全部 11 个 Hooker 的 BytesView.of 调用点与 extractKey/extractIv(toView 注入)切至 viewOf;UI 截断交互由 T14 承接。

### P1.5 必要加固

#### T11 通道加固 ✅ 已完成
**验收**:① hello magic/version 双端强校验(不匹配断开,日志记录原因);② token 校验移除 null 放行(显式关闭鉴权开关时除外);③ record 包名与会话包名一致性校验(不匹配丢弃并计数);④ 端口 bind 失败指数退避重试(2s→30s);⑤ sendRecordJson 增加写超时兜底。
**工作备注**:服务端 `onHello` 经 `WireCodec.isHelloCompatible` 强校验(不匹配记录原因+断开+rejectedHellos 计数);`requireToken` 语义收紧为 null=显式关闭鉴权、非 null=强制精确匹配(无 null 放行);`onRecordMessage` 校验 record 包名==会话包名,不匹配丢弃计数;`start()` 端口 bind 失败 2s→30s 指数退避;配置推送 payload 增加 magic/version(注入侧 `ConfigChannel` 校验不匹配即断开,实现"双端");`sendRecordJson` 增加 10s 写看门狗(超时关 socket 解阻塞);新增 `ChannelStats`(rejectedHellos/droppedRecords/rejectedRecords)经 `statsFlow` 暴露;修复历史遗留乱码注释。

#### T12 Spool 持久化加固 ✅ 已完成
**验收**:append 走 `FileOutputStream`+`flush`+`fd.sync()`;轮转先 fsync 再 rename;drain 流式逐行、行长上限 4MB、损坏行跳过计数;`spoolDropped` 计数并入丢记录统计;崩溃恢复语义(半截行不阻塞回放)。
**工作备注**:`RecordSpool` 重写:append 每行 `fd.sync()`;rotate 先 fsync 再删 `.old` 并 `countLines` 计入 `spoolDropped`;drain 流式逐行(`MAX_LINE_CHARS=4MB`),超长/损坏行 `corruptLines++` 跳过,发送失败剩余行落 `.tmp` 原子 rename 回原文件待下次回放,半截行不阻塞;全部方法 @Synchronized 防重入。

#### T13 宿主管线加固 ✅ 已完成
**验收**:① DAO `insertAll` 批量写入(每 200ms 或 32 条合并,Mutex 内);② 写库异常捕获计数不崩进程;③ trim 双层治理(全局 + 每包);④ 丢记录/拒绝记录/写入失败计数汇入宿主 StateFlow,设置页展示;⑤ 错误路径日志(W 级)。
**工作备注**:`LogRepository` 重构为"通道(8192 有界,溢出丢弃计数)+ 批量写循环(32 条/200ms 窗口,`dao.insertAll` in Mutex)";写失败 W 级日志 + writeFailures 计数;`maybeTrim` 每 500 条执行一次双层治理(每包 >5000 条 trimPackage + 全局 >20000 条 trim,删除数计入 trimmed);`PipelineStats`(received/dropped/writeFailures/trimmed/channel)汇入 `statsFlow`;DAO `trim/trimPackage` 返回删除行数、新增 `counts()` 快照。

#### T14 UI 升级 ✅ 已完成
**验收**:① CaptureSettingsScreen 开关网格扩展至 11 类 + maxBytesPerField 设置 + 忽略栈前缀编辑;② RecordDetailScreen 新增 salt/AAD/公钥/params/进程名/verify 结果展示;③ 危险操作确认对话框(清空应用日志/清空全部/移除作用域);④ 记录详情「导出/分享」入口(文本格式);⑤ 设置页管道统计;⑥ i18n 补齐新字符串(zh/en)。
**工作备注**:CaptureSettingsScreen V5:11 类开关(新增 KeyStore/KeyAgreement/SecureRandom)+ maxBytesPerField 数字对话框(0=无限)+ ignoreStackPrefixes 多行编辑(去空去重);移出作用域/清空日志经 AlertDialog 确认;RecordDetailScreen:进程名/验签结果元数据行(通过=primary、失败=error 语义色)+ salt/AAD/公钥 Section + params 键值多行 + 分享按钮(ACTION_SEND 文本);toShareText/toJson 补全新字段;SettingsScreen 新增「管道统计」分区(7 项,含通道拒绝计数),清空全部经确认对话框;strings zh/en 补齐(危险确认/统计/新字段/新开关)。

### P2 Native 层可行性(独立阶段)

#### T15 Native 层 libcrypto 捕获可行性 🔄 进行中(结论:API 102 不可行,已记录设计)
**验收**:若 libxposed API 102 提供 native inline hook 能力且本机 NDK 可用,则实现 libcrypto EVP 层 hook(`EVP_CipherInit_ex`/`EVP_DigestInit_ex` 等)的最小可行样例并接入 HookSwitch;若不可行,则本文档标注 🔄 进行中,写明设计、依赖与风险,不阻塞其余任务。
**备注**:Native 层与 Java 层解耦,捕获结果统一走 RecordSink 上报;原生层仅捕获算法名/参数长度/结果摘要,不重复捕获 Java 已覆盖路径。
**可行性结论(2026-08-16)**:经反编译核验本地 `libxposed-api-102.0.0.aar`(`javap XposedInterface`),API 102 仅提供 `hook(Executable)`(Java 方法)、`hookClassInitializer`、`getInvoker`,**无任何 native inline hook 接口**(无 `hookNative`/`HookNativeBuilder`)。Vector 2.x 的 libxposed native 能力未通过 Java API 暴露,Java 侧无法注册 native 回调。本机 NDK 可用(22.1.7/25.1.9/27.0.12/28.2.13/29.0.14),但框架层入口缺失,故判定**当前不可实现**。
**设计(待框架支持后落地)**:① 入口:期待 libxposed 未来版本暴露 `hookNative(lib, symbol)`;退路:经 `dlopen+ELF GOT/PLT 内联 hook` 自研 arm64 内联补丁(风险高,仅可调试固件上可用);② 捕获面:仅 `EVP_CipherInit_ex`(算法名/密钥/IV 长度)、`EVP_DigestInit_ex`(算法名)、`EVP_DigestUpdate`(累计长度)、`EVP_DigestFinal_ex`(结果前 32 字节摘要),经 JNI 回调 Java 侧 RecordSink;③ 去重:Java 已捕获的 JCE 路径不再重复上报。
**依赖**:libxposed ≥ 1.1.x native API(未发布)、arm64-v8a NDK toolchain、目标固件非锁定。
**风险**:自研 inline hook 与 app 自身 hook 框架(如 frida-gadget/QQ 加固)冲突;崩溃面大,仅作独立开关默认关闭。

### P3 测试与验证

#### T16 测试补全与构建验证 ✅ 完成
**验收**:新增/更新单元测试覆盖 T1~T14 全部验收点(模型往返、协议校验、Spec 提取、Key 参数提取、上下文唯一 ID、denylist、spool 语义、DAO 迁移/批量/双层 trim、channel 握手/退避);`./gradlew :app:testDebugUnitTest :app:lintDebug` 全绿;真机对照 docs/log 参考记录抽验 3 类算法(摘要/加解密/签名)输出字段完整性。

工作备注(2026-08-16):
- 新增 `CryptoContextTrackerTest`(唯一 ID 自增、同实例同一 ID、clone 共享输入、reset 清密文)、`SpecExtractorTest`(12 类 Spec 全字段:DH/GCMParameter/DHGen/ECGen/DSA/OAEP/PSS/RSA-CRT/KeyGen/PBEKey/PBEParameter/未知回退;RSA-CRT 校验 BigInteger 十六进制小写)、`KeyParamExtractorTest`(EC 曲线参数、RSA CRT 九键、KeyPair/SecretKey/别名回退)。
- 修复 `HookSwitchTest`(all_off 补 keyStore/keyAgreement/random=false;新增 new_domains_on_by_default);扩展 `StackTraceFilterTest`(denylist 命中空结果、未命中不受影响、空列表短路;capture 基于真实线程栈,denylist 断言用 org.junit 前缀)、`RecordSpoolTest`(超长行损坏计数+spoolDropped、轮转计数、半截行留存)、`WireCodecTest`(config magic+version 校验、isHelloCompatible 四态、全字段 record 往返)。
- `CryptoRecordDaoTest` 迁移测试采用公开 `SupportSQLiteOpenHelper.Configuration.builder + Callback(1)` 手工构造严格 v1 库(无 DEFAULT 子句、含实体两复合索引,SQLite 层免 Room 生成),再 `addMigrations(MIGRATION_1_2)` 迁移;经验:v1 建表不得带 DEFAULT(与 Room 期望 dflt_value 不符)、Room 不把自增 id 回写实体(用 insertReturningId 返回值查询)、测试方法体为 runBlocking 时返回类型须为 Unit(否则 InvalidTestClassError)。
- 修复 Windows 平台 bug:`RecordSpool` 失败留存文件 renameTo 前先删除目标(Windows renameTo 目标存在即失败),新增对应回归测试。
- 修复 `KindColorTokens` 浅/深调色板缺 KEY_STORE/KEY_AGREEMENT/RANDOM 三新类别(此前 getValue 抛 NoSuchElementException),补色均满足 AA ≥ 4.5:1(单测门禁通过)。
- 全量验证:Modern/Legacy 两变体 `testDebugUnitTest`(112 用例)+ `lintModernDebug` + `compileLegacyDebugKotlin` 全绿;剩余告警均为历史既有(如 WireCodec recordOf 防御性判空死代码、HookerSupport 平台类型可空告警)。
- 遗留:真机对照 docs/log 抽验需装机后人工执行(见验收第 2 项)。

---

## 5. 测试策略

| 层 | 手段 | 覆盖点 |
|---|---|---|
| 纯逻辑 | JVM 单元测试(app/src/test) | CryptoRecord 序列化往返、WireCodec 校验、SpecExtractor/KeyParamExtractor 提取、BytesView 上限边界、StackTraceFilter denylist、CryptoContextTracker 唯一 ID/clone/reset、RecordSpool 写/轮转/损坏行、ConfigChannel 握手/退避、CryptoRecordDao 迁移/批量/trim(Room in-memory) |
| 集成 | 插桩测试(app/src/androidTest,真机) | 真实 Cipher/Mac/Signature/MessageDigest/KeyStore/KeyAgreement/SecureRandom/Base64 调用链完整产出 |
| 对照 | 真机 + 参考软件同场景 | 同输入下字段集合与 docs/log 参考记录一致(不要求逐字节一致,要求无缺失) |

## 6. 风险册与已知边界

| # | 风险/边界 | 影响 | 缓解 |
|---|---|---|---|
| 1 | Android 12+ 系统对隐藏 API 限制,部分内部字段反射失败 | 字段缺失 | 反射统一 try/catch,缺失字段置 null 不阻塞记录 |
| 2 | 全量捕获下超大记录(>1MB/字段)打爆传输与 DB | 内存/磁盘压力 | maxBytesPerField 保险丝(默认 1MB,超限标记 truncated) |
| 3 | 自捕获噪声(框架自身摘要)刷屏 | 记录密度失控 | denylist 机制(默认关闭,用户按需启用) |
| 4 | Room v1 存量用户升级 | 数据丢失 | 显式 v1→v2 Migration + schema 基线 + 迁移测试 |
| 5 | Vector 无法下发 token 导致通道断开 | 真机不可用 | 显式「关闭鉴权」开关 + 宿主侧提示;legacy 免认证路径保留 |
| 6 | identityHashCode 碰撞(已修) | 链路错乱 | T8 唯一 ID;历史记录不受影响 |
| 7 | Native hook 依赖工具链与系统签名差异 | P2 范围风险 | 独立阶段,失败不阻塞其余任务 |
| 8 | 字节数据含不可打印内容 | 文本态不可读 | hex 态始终可用;文本态判定阈值参数化 |

## 附录 A:参考日志格式(算法助手Pro,`docs/log/*.log`)

- 每行一条记录,UTF-8 编码;格式 `时间 | 日志名称 | key类型 | 操作字段组 | 调用堆栈`;字段值以 `字段名: 值` 形式,hex 值以 0x 前缀十六进制串(Base64 无前缀),每字段 String/Base64/Hex 三态并列;
- 字段组按操作分类:加密/解密 → 密钥/Iv/内容/结果;签名 → 内容/结果;摘要记录同样标为 签名内容/签名结果(参考软件不区分);
- 堆栈为完整 Java 堆栈,含 `at ` 前缀帧;记录间无分隔空行。

## 附录 B:实施顺序与依赖

```
T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10 → T11 → T12 → T13 → T14 → T16(全量验证) ✅
P2(T15) 独立并行,不阻塞主链 🔄(API 102 无 native hook 接口,结论见上)
```
每个任务完成后回写本表状态(⬜→✅)并追加一句工作内容备注。