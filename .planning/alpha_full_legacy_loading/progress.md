# 进度日志

## 会话：2026-07-21

### 阶段 1：需求与根因审计
- **状态：** complete
- 已定位 `LoadingOverlaySuppressionMixin`、`LevelLoadingScreenCorruptionMixin`、`AlphaLoadSessionController`、Alpha 状态文件和现有回归测试。
- 已读取 Minecraft 1.21.11 `LoadingOverlay` 字节码，确认启动 Logo 先从 Vanilla Pack 预加载、重载期间再切换资源纹理。
- 已目视核对 Golden Days Base/Alpha 的 Mojang 纹理，并记录 Alpha 旧版加载配色。
- 已确认进界旧版加载判定等待连接 `active`，存在首帧暴露原版页面的独立时序窗口。

### 阶段 2：实现
- **状态：** complete
- 新增纯策略 `AlphaLoadingPresentationPolicy`，明确只有“崩坏曾播放且当前未在崩坏中”才进入持久旧版表现。
- 新增 `PersistentAlphaLoadingStyle`，从 JAR 内 Golden Days Base 固定读取旧版 `mojangstudios.png`，并实现紫色背景、黑框白底紫色进度条。
- 扩展 `LoadingOverlaySuppressionMixin`：启动时预注册固定纹理，持久 Alpha 遮罩全程改用旧版背景、标志和进度条，同时保留原版生命周期。
- `shouldRenderLegacyLoadingScreen()` 已移除对连接 `active` 的依赖，二次启动进界页从首帧即可进入旧版分支。
- 已增加纯策略单元测试，覆盖未启用、首次崩坏进行中、持久 Alpha 三种状态。
- 创建/修改文件：`AlphaLoadingPresentationPolicy.java`、`PersistentAlphaLoadingStyle.java`、`AlphaLoadSessionController.java`、`LoadingOverlaySuppressionMixin.java`、`AlphaLoadTimelineTest.java`。

### 阶段 3：验证与交付
- **状态：** complete
- 已扩展 `ResourceContractTest`，锁定固定类路径旧版 Logo、旧版配色、遮罩注入点和不依赖连接状态的策略调用。
- 四源集编译 `compileJava compileClientJava compileGametestJava compileTestJava --no-daemon` 在 18 秒内 `BUILD SUCCESSFUL`；仅有项目既存过时 API 警告。
- 随后的 Gradle 定向测试触发重新编译时，工作区中的无关终端重构出现调用签名中间态（`TerminalToolService` 与 `TerminalGuidancePolicy` 不一致）；首次四源集编译已证明本轮加载修改可编译。本轮不接触该并发工作，改为直接复用刚生成的 class 文件运行定向 JUnit。
- 直接 JUnit Console 回归已完成：`AlphaLoadTimelineTest` 全类加 Alpha 资源契约方法共 7/7 通过，0 skipped/failed。
- 已同步 README、架构、人工验收和测试矩阵：二次启动遮罩与进界加载页都明确要求从第一帧保持旧版，不得在约 50% 处换皮。
- 无关终端重构完成其签名同步后，Gradle 定向回归已重新执行成功：全链重新核对源码输出并运行 7/7 测试，`BUILD SUCCESSFUL`（14 秒）。
- 为客户端二次启动回归新增只读计数探针：测试现在要求持久 Alpha 启动遮罩在到达标题页前至少创建一次并绘制首帧，避免仅检查最终标题而漏掉加载前半段原版闪现。
- 首次独立启动 `alpha-relaunch` 在 44 秒后于 `waitForScreen(TitleScreen.class)` 超时；日志显示初始 ResourceManager 只有 vanilla/Fabric/MOD 默认包，没有 Programmer Art/Base/Alpha，证明自动 `deleteGameTestRunDir` 清除了套件所需的上一轮本地状态。Mixin 已完成加载且未报注入/纹理错误，此失败发生在新增断言之前，属于二次启动夹具缺失。
- 明确写入“安全告示已确认 + Alpha 降级完成”的二次启动夹具并跳过再次清理后，`alpha-relaunch` 在 38 秒内 `BUILD SUCCESSFUL`。新断言确认启动遮罩在标题页前已创建且绘制过持久旧版首帧；首次 ResourceManager 日志直接含 Programmer Art/Base/Alpha，进界旧版页与零崩坏计数也通过。
- 已目视复核本轮标题与进界截图：Golden Days 主页/1.0.0 身份/黄色标语正常，进界为泥土背景、中文双行与绿灰旧版进度条。
- 最终 `assemble --no-daemon` 23 秒成功；remap JAR 为 10,402,012 字节，SHA-256 `76EDE4BADE52F82F9EE97F5D2334E50E7A8BF2829C5BC554DC417491D5D57EB3`。
- JAR 审计确认策略类、固定加载样式、固定纹理内部类、LoadingOverlay Mixin 和内置 `mojangstudios.png` 全部存在；运行日志无 Mixin 注入、固定纹理或 Alpha 加载错误。
- 文件规划技能的完成检查脚本因安装文件乱码无法解析；未修改技能目录，改用计划文件的未勾选项/阶段状态只读审计完成收口。

## 测试结果
| 测试 | 预期结果 | 实际结果 | 状态 |
|------|---------|---------|------|
| 四源集编译 | 新增 common/client/test 代码与 Mixin 均可编译 | `BUILD SUCCESSFUL`（18 秒） | pass |
| Gradle 定向测试启动 | 运行两个指定方法 | 被无关终端重构的 3 个签名错误阻断，未进入测试执行 | blocked-by-unrelated-work |
| 定向 JUnit Console | Alpha 时间线/持久表现策略全类 + Alpha 资源契约 | 7/7 通过，0 skipped/failed（270ms） | pass |
| Gradle 定向回归 | 同上，并重新经过 Gradle 编译依赖链 | 7/7 通过，`BUILD SUCCESSFUL`（14 秒） | pass |
| Alpha 二次启动客户端回归（首次独立运行） | 保留前轮告示与 Alpha 状态后进入标题页 | 运行目录被任务清理，状态未保留；等待标题页超时 | fixture-missing |
| Alpha 二次启动客户端回归（显式持久夹具） | 启动遮罩首帧旧版、三层包首载、主页身份、进界旧版、0 次崩坏 | `BUILD SUCCESSFUL`（38 秒），新增首帧探针通过 | pass |

## 错误日志
| 错误 | 尝试次数 | 解决方案 |
|------|---------:|---------|
| 首轮 `rg` 输出被大型资源包截断 | 1 | 限定相关源码集与类名 |
| `javap` 命令未加入 PATH | 1 | 改用 `C:\Program Files\Java\jdk-21.0.10\bin\javap.exe` |
| Gradle 定向测试重新编译时命中并发终端代码中间态 | 1 | 保留用户改动，使用首次成功编译的输出直接运行 JUnit Console |
| `alpha-relaunch` 独立运行缺少上一轮客户端状态 | 1 | 准备显式持久状态夹具后以跳过再次清理的方式重跑 |
| `check-complete.ps1` 自身乱码导致解析失败 | 1 | 手工只读核对 scoped plan 的复选框和 complete 状态 |

## 五问重启检查
| 问题 | 答案 |
|------|------|
| 我在哪里？ | 阶段 2：实现 |
| 我要去哪里？ | 完成实现后进入定向测试、四源集编译与产物审计 |
| 目标是什么？ | Alpha 启用后所有加载页从第一帧采用旧版视觉 |
| 我学到了什么？ | 见 findings.md |
| 我做了什么？ | 见上方记录 |
