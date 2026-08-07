# 测试与验收

本文记录 `0.4.0-beta` 当前测试入口、最近已完成证据和已知测试基础设施边界。只把实际运行完成且与当前 schema v10/个人追逐代码对应的结果列为当前证据。

## 固定环境

| 项目 | 当前值 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.141.4+1.21.11 |
| Loom | 1.17.14 |
| Gradle Wrapper | 9.5.1 |
| Java Toolchain | 21 |

## 常用命令

```powershell
# 编译与资源处理
.\gradlew.bat compileJava compileClientJava processResources --no-daemon

# 聚合纯 JUnit/资源契约；classpath 复用 Loom 为 test source set 配置的 remap 后 Minecraft
# 运行时，并用 --select-class 枚举编译产物中的每个测试类（而非 --scan-class-path）,
# 因此方法签名引用 Minecraft 类型的测试类不会再被静默跳过。XML 输出到 build/test-results/unit
.\gradlew.bat unitTest --no-daemon

# check/build 都依赖上述 unitTest，现在可以作为全绿门禁
.\gradlew.bat check --no-daemon

# 干净构建；任务图会依次执行 unitTest 与服务端 GameTest
.\gradlew.bat clean build --no-daemon

# 服务端 GameTest
.\gradlew.bat runGameTest --no-daemon

# 当前可发布 remap JAR
.\gradlew.bat remapJar --no-daemon

# 客户端 GameTest，默认 all
.\gradlew.bat runClientGameTest --no-daemon

# 世界接口定向套件
.\gradlew.bat runClientGameTest -PtffClientTestSuite=world-interface --no-daemon
```

允许的客户端套件 ID：`all`、`default`、`mainline`、`tools-ui`、`notice-entry`、`alpha-relaunch`、`anomalies`、`anomaly-meta-smoke`、`rework-forms`、`watcher-model`、`world-interface`。`all` 覆盖主线、工具 UI、异象、校正者、观察者模型和 World Interface；告知/重启类套件仍独立运行。仅 `anomalies` 套件允许额外指定 `-PtffAnomaly=<id>`。

`alpha-relaunch` 会在测试运行目录清空后自动写入“第一次启动已经完成”的最小持久化夹具，再启动客户端验证第二次启动；它不会读取或修改玩家的正常 `run/client` 配置。

## 验证分层

| 层 | 覆盖重点 |
| --- | --- |
| 筛选纯逻辑测试 | 异象池/节奏、五形态策略、终端外观、动态区块窗口和不跳形态规则 |
| 聚合 JUnit/资源契约 | schema、载荷版本、资源键、数据表、迁移、策略公式与恢复规则；当前全绿 |
| 服务端 GameTest | 世界事件、目标推进、多人权威状态、方块/实体交互、镜像拓扑与持久化 |
| 客户端 GameTest | 终端 UI、告知/重启、异象呈现、模型、世界接口、诗篇与视距 |
| 人工验收 | 音画安全、多人反馈、窗口/桌面演出、LAN 房主体验与重玩流程 |

## 最近完成的自动化证据

以下全部于 2026-08-04 在 Beta 0.4.0 工作区实际运行完成。

| 验证 | 结果 | 边界 |
| --- | --- | --- |
| 主端/客户端/GameTest/测试源码编译 | `BUILD SUCCESSFUL` | `compileJava compileClientJava compileGametestJava compileTestJava` |
| 聚合 `unitTest` | 66 个容器、288 项测试；288 通过、0 失败、0 跳过 | 见下方“聚合测试门禁修复”说明。删除四个死策略类后，其自带的三个测试类一并移除，总数不变 |
| 服务端 GameTest | `All 61 required tests passed`（批 0：50 项，批 1：11 项） | 含 HIM 调度修复、终端 NBT 瘦身与语言键清理后的复跑 |
| `clean build`（含 `check`、`unitTest`、`runGameTest`、`remapJar`） | `BUILD SUCCESSFUL` | 本轮发布物 |
| 中英文语言 JSON | 解析通过；730 键完全对称 | 删除 38 组旧系统遗留键后复核 |
| 文档相对链接 | 全部解析到仓库内目标 | README 与 `docs/**` |
| `git diff --check` | 无空白或补丁格式错误 | 仅有既有 LF/CRLF 提示 |
| 完整（`all`）客户端 GameTest | 本轮未复跑 | 定向冒烟不能替代未筛选的完整客户端套件 |

零号站重建（2026-08-04）改动了 `M1GameTests.worldOwnsExactlyOneBoundedStationPlan` 的计划规模上下界，并把 `M0ClientGameTest` 的持久化取样点从硬编码坐标改为 `ZeroStationLayout.solidWallSample`。服务端 GameTest 已随本轮 61/61 复跑覆盖；对应客户端套件仍未复跑。

## 个人追逐关键不变量

- 主线只提高 `allowedForm`；`actualForm` 每次成功最多前进一步，待追逐不形成队列。
- 五形态时长为 60/75/85/95/110 秒，成功间隔为 20–30 分钟，捕获/中断重试为 5 分钟。
- 每个形态必须先完成安全演示；安全条件通过后固定执行 200 Tick 前置：80 Tick 终端阅读、80 Tick 只做渐进掉帧、40 Tick 输入锁定/卡死音效；前置不绘制滤镜或干扰遮罩。
- 黑屏只由服务端时序切换；从镜像复制到返程来源世界与加载界面消失前，加载画面都必须被遮蔽。
- 全服最多两场并发；两名玩家使用不同镜像槽位，第三人安全延后。
- 初始快照为 5×5、垂直 ±48 格、每会话每 Tick 8192 方块；水平随玩家区块流式扩展，不存在固定 30 格折返。
- 已复制区块不再覆盖；复制落后只暂停在最近安全位置并暂停追逐计时。
- 初始生成探针覆盖玩家周围完整圆环（25–42 格），正前方也可能生成；42 格/5 秒、18 格外断视线/8 秒和玩家亲自击杀均可权威结算成功。
- 正式追逐没有 bossbar；只常驻红色“尝试逃离”，其他提示锁至完整返程后；客户端使用黑白低色阶马赛克滤镜，心跳在校正者坐标定位播放（有方位声像与距离衰减），并全程为玩家施加无图标夜视。
- 追逐会话期间暂停菜单的保存退出/断开连接按钮必须禁用并替换文案，会话完全清除后恢复。
- 捕获冻结/故障音和绿色成功结算都固定为 60 Tick；捕获扣除 2 点最大生命，成功增加 2 点，技术性中断不惩罚。
- 成功撑过、逃离、反杀或完成 Debug 追逐后删除临时预警；返程完成时写入“用户周围的磁场很不稳定...”记录。
- 追逐校正者使用快速破障、支撑拆除和垂直跃迁；玩家仍可正常反杀。玩家满足矿洞判定时降至 0.25 基础移速与 1.04 寻路倍率，离开后恢复 0.31 与 1.32。
- 镜像破坏无掉落；临时放置退款幂等；断线/重启不会把玩家留在镜像或吞掉物品。
- 镜像维度不得污染主线、异象、导航、下界往返、末地进入或终局状态。

## 世界接口关键不变量

- 参战名单为 1—8 名在线非旁观者，提交前可撤回，提交失败必须返还终端。
- 生命为 `600 × 冻结人数`；三形态只前进不倒退。
- 崩塌为 7200 Tick；全员离线暂停；同 Tick 超时优先于致命伤。
- 10 个稳定锚按当前公式同时影响回复、承伤、移动与冷却，但不影响崩塌进度；每次拆除在 HUD 血条上播放一次金光拂过。
- 八类行动的预警、精确伤害、数量上限、排他控制与恢复账本保持稳定。
- 永久伤痕总预算为 8192 格，每 Tick 32 格，且不能破坏受保护结构和方块实体。
- 结算开放 3×3 出口，并沿原版 WinScreen/重生路径完成返程。
- 只有成功诗篇确认并真正回到主世界后，视距才永久解锁为 16。
- 失败 LAN 房主分支只改变房主客户端的呈现，不能污染服务器或客人。
- F8 只处理已存在的结局锁；存档隔离只使用无损标记。

## 本轮文档/资源静态检查

- 中英文 JSON 均可解析，730 个键集合完全对称。
- 所有 Markdown 相对链接都能解析到仓库内目标。
- 对外文档不再把旧协议号、固定 30 格边界、旧测试数字或旧追逐设计写成当前状态。
- `git diff --check` 无尾随空格或补丁格式错误。
- `clean build`（含 `remapJar`）于 2026-08-04 `BUILD SUCCESSFUL`；288 项单元测试与 61 项 GameTest 全部通过。

候选包人工流程见 [Beta 0.4.0 人工验收](alpha-acceptance.md)。

## 发布物

| 文件 | 当前结果 |
| --- | --- |
| `build/libs/thefourthfrequency-0.4.0-beta.jar` | 44,617,562 字节；SHA-256 `A13BF9D348D10B0B9F259BB130DD74D8BBB7D92A29E769A8C1B1EF12FB6E493C` |
| `build/libs/thefourthfrequency-0.4.0-beta-sources.jar` | 43,941,045 字节；SHA-256 `2C5E12A0829B45CCBF6C7E2BEE4D7F6C74DFB7EA5A67EEF2E20382F4D9ABC01E` |
| `build/libs/thefourthfrequency-0.3.0-beta.jar` | 上一版实测：44,564,361 字节；SHA-256 `91586F4E53248CF51A2D9F0D72AD7485E7A4BA292D8C5F34415027D5666E2297` |

## 发布前仍需完成

- 复跑未筛选的完整（`all`）客户端 GameTest。
- 使用两个真实客户端验证不同形态并发、断线重连、满背包退款和动态区块追赶。
- 在目标硬件上检查混音、强闪烁、多屏/DPI、LAN 房主分流和长时间 TPS。
