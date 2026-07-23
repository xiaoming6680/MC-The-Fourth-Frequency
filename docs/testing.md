# 测试与验收

本文记录 `0.2.1-beta` 当前测试入口、最近已完成证据和已知测试基础设施边界。只把实际运行完成且与当前 schema v9/个人追逐代码对应的结果列为当前证据。

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

# 聚合纯 JUnit/资源契约；当前会因 Minecraft 运行时类缺失出现 5 个加载错误
# XML 输出到 build/test-results/unit
.\gradlew.bat unitTest --no-daemon

# check/build 会依赖上述 unitTest；在修复测试 classpath 前不能作为全绿门禁
.\gradlew.bat check --no-daemon

# 干净构建；当前任务图会同时执行 unitTest 与服务端 GameTest，因此会继承该错误
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
| 聚合 JUnit/资源契约 | schema、载荷版本、资源键、数据表、迁移、策略公式与恢复规则；当前 classpath 非全绿 |
| 服务端 GameTest | 世界事件、目标推进、多人权威状态、方块/实体交互、镜像拓扑与持久化 |
| 客户端 GameTest | 终端 UI、告知/重启、异象呈现、模型、世界接口、诗篇与视距 |
| 人工验收 | 音画安全、多人反馈、窗口/桌面演出、LAN 房主体验与重玩流程 |

## 最近完成的自动化证据

| 验证 | 结果 | 边界 |
| --- | --- | --- |
| 主端/客户端/测试源码编译 | `BUILD SUCCESSFUL` | `compileJava compileClientJava compileGametestJava compileTestJava` |
| 追逐/异象/终端筛选纯测试 | 35/35；0 失败、0 错误 | 包含动态 5×5 窗口新增 3 项 |
| 服务端 GameTest | 56/56 必需测试通过 | 2026-07-24 当前运行时基线 |
| 六镜像维度资源 | 通过 | JAR 含六份 JSON；正常专用服务器已实例化六个维度目录 |
| remap JAR | `BUILD SUCCESSFUL` | 2026-07-24 当前发布物 |
| 聚合 `unitTest` | 160 个测试条目；0 断言失败、5 个类加载错误 | 4 个缺 `CompoundTag`、1 个缺 `ItemLike`；不是全绿证据 |
| 完整客户端 GameTest | 本轮未复跑 | 旧 schema v8 结果不作为当前追逐重构的发布证据 |

当前发布结论只能建立在编译、35 项筛选纯测试、56 项 GameTest、镜像维度实例化与 remap 打包之上。聚合 JUnit classpath 和完整客户端套件仍是发布前待修复/待复跑项。

## 个人追逐关键不变量

- 主线只提高 `allowedForm`；`actualForm` 每次成功最多前进一步，待追逐不形成队列。
- 五形态时长为 60/75/85/95/110 秒，成功间隔为 20–30 分钟，捕获/中断重试为 5 分钟。
- 每个形态必须先完成安全演示和终端警告，警告后至少等待 90 秒。
- 全服最多两场并发；两名玩家使用不同镜像槽位，第三人安全延后。
- 初始快照为 5×5、垂直 ±48 格、每会话每 Tick 8192 方块；水平随玩家区块流式扩展，不存在固定 30 格折返。
- 已复制区块不再覆盖；复制落后只暂停在最近安全位置并暂停追逐计时。
- 镜像破坏无掉落；临时放置退款幂等；断线/重启不会把玩家留在镜像或吞掉物品。
- 镜像维度不得污染主线、异象、导航、下界往返、末地进入或终局状态。

## 世界接口关键不变量

- 参战名单为 1—8 名在线非旁观者，提交前可撤回，提交失败必须返还终端。
- 生命为 `600 × 冻结人数`；三形态只前进不倒退。
- 崩塌为 12000 Tick；全员离线暂停；同 Tick 超时优先于致命伤。
- 10 个稳定锚按当前公式同时影响回复、承伤、移动、冷却和崩塌进度。
- 九类行动的预警、精确伤害、数量上限、排他控制与恢复账本保持稳定。
- 永久伤痕总预算为 2048 格，每 Tick 8 格，且不能破坏受保护结构和方块实体。
- 结算开放 3×3 出口，并沿原版 WinScreen/重生路径完成返程。
- 只有成功诗篇确认并真正回到主世界后，视距才永久解锁为 16。
- 失败 LAN 房主分支只改变房主客户端的呈现，不能污染服务器或客人。
- F8 只处理已存在的结局锁；存档隔离只使用无损标记。

## 本轮文档/资源静态检查

- 中英文 JSON 均可解析，键集合完全对称。
- 所有 Markdown 相对链接都能解析到仓库内目标。
- 对外文档不再把 schema v8、固定 30 格边界、旧测试数字或旧追逐设计写成当前状态。
- `git diff --check` 无尾随空格或补丁格式错误。
- `remapJar` 于 2026-07-24 `BUILD SUCCESSFUL`；发布 JAR 已核对流式快照类和六镜像维度资源。

候选包人工流程见 [Beta 0.2.1 人工验收](alpha-acceptance.md)。

## 发布物

| 文件 | 当前结果 |
| --- | --- |
| `build/libs/thefourthfrequency-0.2.1-beta.jar` | 15,063,243 字节；SHA-256 `7988E401310CB30DC6D9D7C0B3594E26AB4E404A8EFA6B6C9B5CE4D65FA69B92` |

## 发布前仍需完成

- 修复聚合 JUnit 的 Minecraft 运行时 classpath，并取得全绿结果。
- 复跑未筛选的完整客户端 GameTest。
- 使用两个真实客户端验证不同形态并发、断线重连、满背包退款和动态区块追赶。
- 在目标硬件上检查混音、强闪烁、多屏/DPI、LAN 房主分流和长时间 TPS。
