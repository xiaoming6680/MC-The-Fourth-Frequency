# 测试与验收

本文记录 `0.2.0-beta` 当前测试入口、最近已完成证据与本轮文档同步验证。历史 Alpha 数字不再作为当前质量结论。

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

# 纯 JUnit/资源契约；XML 输出到 build/test-results/unit
.\gradlew.bat unitTest --no-daemon

# check 会依赖 unitTest；Gradle 的 test 任务被禁用并委托给 unitTest
.\gradlew.bat check --no-daemon

# 完整装配（不等同于服务端 GameTest）
.\gradlew.bat clean build --no-daemon

# 服务端 GameTest
.\gradlew.bat runGameTest --no-daemon

# 客户端 GameTest，默认 all
.\gradlew.bat runClientGameTest --no-daemon

# 世界接口定向套件
.\gradlew.bat runClientGameTest -PtffClientTestSuite=end-boss --no-daemon
```

允许的客户端套件 ID：`all`、`default`、`mainline`、`tools-ui`、`notice-entry`、`alpha-relaunch`、`anomalies`、`anomaly-meta-smoke`、`rework-forms`、`watcher-model`、`end-boss`。仅 `anomalies` 套件允许额外指定 `-PtffAnomaly=<id>`。

## 验证分层

| 层 | 覆盖重点 |
| --- | --- |
| JUnit/资源契约 | schema、载荷版本、资源键、数据表、迁移、策略公式与恢复规则 |
| 服务端 GameTest | 世界事件、目标推进、多人权威状态、方块/实体交互与持久化 |
| 客户端 GameTest | 终端 UI、告知/重启、异象呈现、模型、世界接口、诗篇与视距 |
| 人工验收 | 音画安全、多人反馈、窗口/桌面演出、LAN 房主体验与重玩流程 |

## 最近完成的自动化证据

| 验证 | 结果 | 边界 |
| --- | --- | --- |
| JUnit/资源契约 | 138/138 通过；0 失败、0 错误、0 跳过 | 最近完整构建基线 |
| 服务端 GameTest | 53/53 通过 | 最近完整服务器定向运行 |
| 完整客户端 GameTest | `BUILD SUCCESSFUL` | 覆盖破损文件渐进解锁、主线、异象、加载画面、终局、返程与视距 |
| remap JAR | 成功 | 最近完整构建基线 |

这些结果是当前代码基线的已完成证据。本轮已执行未筛选的完整客户端套件、服务端 GameTest、JUnit/资源契约与完整 `build`。

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
- 对外文档不再把旧 Alpha JAR、旧协议或旧终局数字写成当前状态。
- `git diff --check` 无尾随空格或补丁格式错误。
- `assemble` 于 2026-07-23 `BUILD SUCCESSFUL`；发布 JAR 内已核对中英文新终局提示。

候选包人工流程见 [Beta 0.2 人工验收](alpha-acceptance.md)。

## 发布物

| 文件 | 当前结果 |
| --- | --- |
| `build/libs/thefourthfrequency-0.2.0-beta.jar` | 15,466,198 字节；SHA-256 `3A552861478E6847EC856C372D7E1BCEB9FC3A138D20905A12DC1C071B1D2CDF` |
| `build/libs/thefourthfrequency-0.2.0-beta-sources.jar` | 14,743,222 字节；SHA-256 `52263907E8F2E54C01755854807CBAE09C93AF03119D522446DC9544DA0D08D6` |
