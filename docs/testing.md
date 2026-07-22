# 《第四频段》测试策略与当前证据

## 固定环境

| 组件 | 版本 |
|---|---|
| Minecraft | 1.21.11 |
| Java | 21 |
| Gradle Wrapper | 9.5.1 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.141.4+1.21.11 |
| Fabric Loom | 1.17.14 |

## 常用命令

最终 BOSS“世界接口”重制必须同时通过四源码集编译、纯单元测试、服务端 GameTest 和专属客户端视觉套件。Windows Meta 的真实桌面接管、3/8 人专服和性能基准仍需在隔离验收环境中单独执行。

```powershell
# 四源码集编译
.\gradlew.bat compileJava compileClientJava compileTestJava compileGametestJava assemble remapJar --no-daemon --no-parallel

# 完整无缓存构建：单元测试、服务端 GameTest、remap JAR
.\gradlew.bat clean build --no-daemon

# 纯 Java 单元测试
.\gradlew.bat unitTest --no-daemon

# Minecraft 服务端 GameTest
.\gradlew.bat runGameTest --no-daemon

# 默认客户端链：主线回归后串行全部异象
.\gradlew.bat runClientGameTest --no-daemon

# 工具网格、锁定点击、矿物扫描与主页实时卡定向视觉回归
.\gradlew.bat runClientGameTest -PtffClientTestSuite=tools-ui --no-daemon

# 仅运行 16 项异象专项
.\gradlew.bat runClientGameTest -PtffClientTestSuite=anomalies --no-daemon

# 按稳定 ID 单项重跑
.\gradlew.bat runClientGameTest -PtffClientTestSuite=anomalies -PtffAnomaly=phantom_echo --no-daemon

# 显式 Windows 真实窗口/记事本烟雾
.\gradlew.bat runClientGameTest -PtffClientTestSuite=anomaly-meta-smoke --no-daemon

# 校正体五阶段正面、背面与暗光视觉验收（15 张截图）
.\gradlew.bat runClientGameTest -PtffClientTestSuite=rework-forms --no-daemon

# 观察者正面、四分之三、背面与昏暗近景视觉验收（4 张截图）
.\gradlew.bat runClientGameTest -PtffClientTestSuite=watcher-model --no-daemon

# 最终 BOSS 三形态多角度、两次变形、九攻击、三色光柱、祭坛、双诗篇、真实出口回程与视距生命周期（30 张终局截图）
.\gradlew.bat runClientGameTest -PtffClientTestSuite=end-boss --no-daemon

# 暗处观察者真实生成、可见回调、环境音与清理定向回归
.\gradlew.bat runClientGameTest -PtffClientTestSuite=anomalies -PtffAnomaly=dark_watcher --no-daemon

# 普通专用服务器烟雾测试
.\gradlew.bat runServer --args="--nogui"
```

普通专服必须在出现 `Done` 后使用服务器原生 `stop` 正常退出，并确认主世界、下界和末地都已保存。不要用强杀进程替代保存验证。

## 自动测试分层

| 层级 | 覆盖范围 |
|---|---|
| `src/test` | 配置边界、首次告示、Alpha 降级、3/6/12/16 视距与旧配置解锁字段、schema、终端/导航/异象策略、校正体与观察者资源契约，以及“世界接口”600×人数生命、三阶段单向推进、锚公式、12000 Tick 裁决、动作互斥、终端事务、协议固定 ID、旧终局隔离和 2048/8 破坏预算 |
| `src/gametest` 服务端 | 既有世界/终端/结构/异象/校正体覆盖；新增祭坛传送与复活、20 门/10 锚、冻结名单与献祭回滚、九攻击精确伤害、托管/抓取/掉落恢复、无人暂停、保存恢复、友好真龙及成功/失败互斥结局 |
| 客户端主线回归 | 中文端到端流程：标题页首次安全告示/确认返回标题/确认落盘/进界不重复、建世界、协议 v5 四页终端、工具快照 v2、情境接收器音频/锁定、候选导航、折叠卡片、四碎片/完整文件、四模块测试工作台中的 16 项可滚动异象、成功结局与保存重开 |
| Alpha 二次启动 | 保留前轮客户端状态，验证启动遮罩从第一帧固定为旧版 Logo/背景/进度条、第一次 ResourceManager 已包含三层包、无第二次受控重载、主页 1.0.0 身份、资源来源、进界从第一帧仅旧版页且崩坏计数为 0 |
| 客户端异象专项 | 每项独立临时世界、固定种子、正式效果、加速时间线、只读峰值快照、完成后单次日志、逐项清理与聚合报告 |
| 校正体五阶段专项 | 单一摄影棚按世界累计拆除数生成五套真实模型，每阶段自动截取正面、背面与暗光；人工检查 UV、背臂连接、剔除、贴图和阶段 4–5 发光范围 |
| 观察者模型专项 | 固定影棚生成真实观察者，自动截取正面、四分之三、背面与昏暗近景；人工检查 2.9 格瘦长轮廓、长手、眼位、背部结构、UV/剔除和稀疏低亮眼部发光 |
| 最终 BOSS 专项 | 真实末地夹具依次截取三形态正/侧/背、两次变形、九种攻击、三色光柱、崩坏跳增、祭坛与双诗篇；另实际验证主世界 3→下界 6→主世界 3→末地 12，进入主岛 3×3 出口后断言原版 `WinScreen`、诗 ACK、`PERFORM_RESPAWN`、主世界返回、复活点恢复和 `COMPLETE`，再验证视距变为 16、持久解锁且跨维度不复锁；同时检查 854×480/1920×1080 裁切 |
| 普通专服 | common/client 隔离、配置与数据加载、专服启动和正常保存退出 |
| Mock / Windows Meta | 固定事件顺序、关闭与恢复、失败降级、路径/句柄所有权及真实 Windows 白名单组合 |

`test`、`check` 和 `build` 强制依赖 `unitTest`。纯测试通过独立 JUnit Console JVM 执行，XML 报告写入 `build/test-results/unit/`；Minecraft 行为不由纯 JUnit 冒充，统一由 GameTest 覆盖。

## 当前完整回归结果

| 验证 | 结果 |
|---|---|
| JUnit | 历史完整套件 91/91 通过；本轮加载时间线/持久表现策略与 Alpha 资源契约定向 7/7 通过 |
| 服务端 GameTest | 38/38 通过；包含无校准绑定、旧自动调频拒绝、近场调谐授权、全部记录已读、旧存档和多人共享 |
| 客户端 mainline GameTest | 1 分 53 秒成功；四页终端、情境接收器循环音/锁定音、四碎片、完整文件、成功结局与保存重开均通过 |
| Alpha 二次启动 | 本轮 38 秒成功；启动遮罩在标题页前命中持久旧版首帧，首次资源加载直接含 Programmer Art/Base/Alpha，主页与进界回归通过 |
| 异象专项 | 16/16 PASS，场景总耗时 105.793 秒；16 张峰值截图，`summary.json` 无失败项 |
| 校正体五阶段专项 | 最终 70.5 秒成功；五阶段正面/背面/暗光共 15 张 854×480 截图，最终接触表目视通过 |
| 观察者模型专项 | 夜间亮度复验 1 分 5 秒成功；正面/四分之三/背面/昏暗近景共 4 张 854×480 截图，体型、眼位、背部、裁切及更清晰但无光晕的眼部发光均目视通过 |
| `dark_watcher` 定向场景 | 1/1 PASS；真实观察者生成、短暂对眼、可见回调、环境音和清理通过，报告场景耗时约 26.3 秒 |
| 受影响视觉 | 假裂纹/双手乱码、观察者原生人形与巨眼、物品 GUI、红色浓雾与触发视角已复核截图 |
| Windows 受控烟测 | `BUILD SUCCESSFUL`；验证窗口进出缩放、标题/图标/尺寸恢复、Notepad 前台 PID 归属、逐字 Unicode 输入、进程树与临时目录清理 |
| Assemble | 本轮四源码集编译与 `assemble/remapJar` 共 36 秒，`BUILD SUCCESSFUL`；未触发测试任务 |
| 普通专服 | 到达 `Done`，RCON 原生 `stop` 后三维度保存并正常退出 |
| 发布物 | `build/libs/thefourthfrequency-0.1.0-alpha.2.jar`，10,471,355 字节，SHA-256 `FAB8DF6C79C4B75E912CA9725F96EB241494FB2A3235C93C3EA9D1D3E9EFDDB9` |
| 最终 BOSS“世界接口”重制 | 四源码集编译通过；JUnit 139/139、服务端 GameTest 54/54、简体中文 `end-boss` 客户端套件 `BUILD SUCCESSFUL`（30 张终局截图，含 854×480/1920×1080）通过；截图只有一套自定义 Boss HUD，战场为原生末地主岛。真实出口回程已验证 `showEndCredits` 打开原版诗屏，跳过后由原版协议回到主世界并完成账本；同一套件验证终局前 3/6/12 强锁、成功返回后 16、配置落盘、控件/setter 解锁及跨维度不复锁。1662 个 JSON、58 个 OGG 与 11 张终局纹理静态校验通过。真实 3/8 人专服、Windows 桌面事务与 p95/p99 性能基准待隔离环境人工验收 |

默认客户端任务先运行保留布局与功能的 `M0ClientGameTest`，再运行独立的 `AnomalyClientGameTest`。异象报告写入 `build/reports/client-anomalies/summary.json`，峰值截图写入 `build/reports/client-anomalies/screenshots/`；全量运行严格要求 16 行结果和 16 张截图，不允许静默跳过。截图用于人工视觉证据，自动成败来自客户端只读快照、服务端权威世界状态、日志和清理断言。

观察者模型专项的最终四图复制到 `build/reports/watcher-model/screenshots/`，避免后续客户端套件清理运行目录时覆盖证据。概念三视图、材质基准、完整生成提示词与精确 UV 指南位于 `docs/art/watcher/`；生成图只作参考，不直接作为运行时 UV。

## 关键回归不变量

- 每世界只有一个零号站；完成后不修复玩家主动改动。
- 每位玩家只有一台有效终端；旧代次、重复副本和错误世界投影无效。
- 满背包恢复等待安全空槽，不覆盖、删除或吞失物品。
- 资源扫描只读已加载区块；获得目标资源、跨维度或完成绑定后导航按规则停止。
- 每位玩家同时只有一个活动异象；现实生物、门和真实位移按服务端范围可见，幻觉、声音、摄像机、菜单、窗口和物品图标只对目标玩家可见。
- 每碎片至多三个原版结构候选；客户端只能选择服务端给出的候选，且只有打开“工具 → 信号”并处于真实候选范围时才能调谐；完成时服务端必须再次核对玩家仍在结构范围内。
- 碎片文件按世界共享，发现者姓名和来源坐标持久化；个人导航、行动记录与短异常不得共享。
- “世界接口”名单在第一台终端提交时冻结，最大生命恒为 `600 × 冻结人数`；阶段只前进，十个初始稳定锚是唯一权威。锚不会被 BOSS 伤害，毁锚按固定公式增加减伤档位与 600 Tick 崩坏惩罚。
- 合法末地伤痕无视 `mobGriefing`、无掉落、受整场 2048 格与每 Tick 8 格预算及保护策略约束，并在两种结局中永久保留；预算外效果只在客户端表现。
- 武器托管、抓取、热栏控制与驱逐互斥且可幂等恢复；死亡、掉线、重启、切阶段和结算都必须返还托管物。成功与失败在达到 100% 的 Tick 先裁决失败，之后的伤害不得翻转结果。
- 成功终局返回前，主世界/下界/末地视距必须分别锁定 3/6/12 且不可调；只有成功诗篇后的真实主世界返回才能设为 16 并持久解除所有视距锁，失败结局不得解锁。
- F8 或 `meta.enabled=false` 关闭 Windows 窗口/记事本操作并幂等恢复，之后的五级异象降级为无提示文字的游戏内模拟；不影响权威剧情和结局。

## 发布前人工检查

自动化不能证明主观体验。发布候选仍应在目标硬件上检查：

1. 854×480 与常规窗口下的文字裁切、折叠卡片、碎片列表、候选导航、键盘操作和 `Esc` 退出。
2. 普通/青/红三阶段与未读红灯是否易辨识且不只依赖颜色。
3. 近场接收器、继电器、异象、返工体、最终肉身与终止声音的混音舒适度；字幕阅读速度。
4. Windows 不同 DPI、多屏与通知设置下的窗口恢复、内置文本打开和受控记事本进程/临时文件边界。
5. 两台真实客户端在网络延迟下的共享文件提示、私人行动记录、热栏错位轮转/租约隔离与多人结局体验。
6. Linux/macOS 客户端的纯游戏内 Meta 降级路径。
7. 3 人与 8 人真实专服下的同时提交、死亡/跨维度、驱逐后立即重连和逐玩家诗篇 ACK。
8. 8 人 1080p 中配战斗的服务端 p95/p99 Tick、50ms 尖峰、额外客户端内存、箭雨并发和 96 格 LOD。

完整人工主线、当前两个末地结局和旧结局兼容步骤见 [Alpha 人工验收与游玩说明](alpha-acceptance.md)。
