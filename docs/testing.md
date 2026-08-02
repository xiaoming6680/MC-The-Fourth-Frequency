# 测试与验收

本文记录 `0.2.1-beta` 当前测试入口、最近已完成证据和已知测试基础设施边界。只把实际运行完成且与当前 schema v10/个人追逐代码对应的结果列为当前证据。

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

| 验证 | 结果 | 边界 |
| --- | --- | --- |
| 主端/客户端/测试源码编译 | `BUILD SUCCESSFUL` | `compileJava compileClientJava compileGametestJava compileTestJava` |
| 追逐演出/资源定向测试 | 37/37；0 失败、0 错误 | 覆盖无前置滤镜、混色临时预警、返程记录、矿洞降速接线、单一常驻提示与加载遮罩 |
| 服务端 GameTest | 60/60 必需测试通过 | 包含校正者反搭高与快速破障回归 |
| 六镜像维度资源 | 通过 | JAR 含六份 JSON；正常专用服务器已实例化六个维度目录 |
| `clean build`（含 `check`、`runGameTest`、`remapJar`） | `BUILD SUCCESSFUL` | 2026-08-02 当前发布物 |
| 聚合 `unitTest` | 216 个测试条目；216 通过、0 失败 | 见下方"聚合测试门禁修复"说明；现为全绿证据 |
| `notice-entry` 客户端 GameTest | `BUILD SUCCESSFUL` | 客户端混入、资源、音频、HUD 与首次终端入口冒烟 |
| 完整（`all`）客户端 GameTest | 本轮未复跑 | 定向冒烟不能替代未筛选的完整客户端套件 |

当前发布结论建立在编译、37 项追逐演出/资源定向测试、60 项 GameTest、216 项聚合 `unitTest`、镜像维度实例化与 remap 打包之上。完整（`all`）客户端套件仍是发布前待复跑项。

### 聚合测试门禁修复（2026-08-02）

`unitTest` 此前用 `--scan-class-path` 发现测试类；JUnit 在扫描期间对每个候选类调用 `Class.getDeclaredMethods()`，只要某个方法**签名**（不要求方法体执行到）引用了缺失的类型就抛 `NoClassDefFoundError`，而扫描器只在 DEBUG 级别记录后静默丢弃整个类——不计入 `tests`，也不计入 `errors`。运行时 classpath 又只包含 JUnit Console Launcher 和 Gson，不含 Minecraft，因此任何在方法签名（而不仅是方法体）中出现 Minecraft 类型的测试类都会被这样吞掉。

受影响的 6 个测试类此前完全不出现在任何报告里：`TerminalSnapshotPayloadTest`（唯一断言终端主快照协议号的测试）、`WorldInterfaceProtocolPersistenceTest`、`WorldInterfaceStatePersistenceTest`、`WorldInterfaceStateSchemaTest`、`WorldInterfaceSequenceContractTest`、`TerminalFileStateTest`。另有 `TerminalTaskServiceTest` 的 4 个方法会被正常发现但在**执行期**因缺少 `CompoundTag`/`ItemLike` 而报错——这就是历史上"4 个类加载错误"的真实来源：实际是同一个类的 4 个方法，不是 4 个不同的类。

修复分两步：`unitTest` 的 `classpath` 改为复用 `sourceSets.test.runtimeClasspath`（Loom 已为其配置好 remap 后的 Minecraft/Fabric 依赖）；测试发现方式改为在 `doFirst` 里遍历 `sourceSets.test.output.classesDirs` 下的编译产物，对每个顶层类显式传入 `--select-class`，不再使用 `--scan-class-path`。`TerminalTaskServiceTest` 额外需要在 `@BeforeAll` 中调用 `SharedConstants.tryDetectVersion()` 与 `Bootstrap.bootStrap()`，因为 `Items.BREAD` 等字段的静态初始化会触发 `BuiltInRegistries`，而后者会断言已完成引导。修复后聚合 `unitTest` 报告的测试条目从 198 个（4 个失败、6 个类完全不可见）变为 216 个全部通过——多出的条目正是此前被静默丢弃的那 6 个类贡献的测试方法。

## 个人追逐关键不变量

- 主线只提高 `allowedForm`；`actualForm` 每次成功最多前进一步，待追逐不形成队列。
- 五形态时长为 60/75/85/95/110 秒，成功间隔为 20–30 分钟，捕获/中断重试为 5 分钟。
- 每个形态必须先完成安全演示；安全条件通过后固定执行 200 Tick 前置：80 Tick 终端阅读、80 Tick 只做渐进掉帧、40 Tick 输入锁定/卡死音效；前置不绘制滤镜或干扰遮罩。
- 黑屏只由服务端时序切换；从镜像复制到返程来源世界与加载界面消失前，加载画面都必须被遮蔽。
- 全服最多两场并发；两名玩家使用不同镜像槽位，第三人安全延后。
- 初始快照为 5×5、垂直 ±48 格、每会话每 Tick 8192 方块；水平随玩家区块流式扩展，不存在固定 30 格折返。
- 已复制区块不再覆盖；复制落后只暂停在最近安全位置并暂停追逐计时。
- 初始生成探针全部位于玩家后半视野；42 格/5 秒、18 格外断视线/8 秒和玩家亲自击杀均可权威结算成功。
- 正式追逐没有 bossbar；只常驻红色“尝试逃离”，其他提示锁至完整返程后；客户端使用黑白低色阶马赛克滤镜及距离心跳。
- 追逐会话期间暂停菜单的保存退出/断开连接按钮必须禁用并替换文案，会话完全清除后恢复。
- 捕获冻结/故障音和绿色成功结算都固定为 60 Tick；捕获扣除 2 点最大生命，成功增加 2 点，技术性中断不惩罚。
- 成功撑过、逃离、反杀或完成 Debug 追逐后删除临时预警；返程完成时写入“用户周围的磁场很不稳定...”记录。
- 追逐校正者使用快速破障、支撑拆除和垂直跃迁；玩家仍可正常反杀。玩家满足矿洞判定时降至 0.27 基础移速与 1.10 寻路倍率，离开后恢复 0.32 与 1.42。
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
- 对外文档不再把旧协议号、固定 30 格边界、旧测试数字或旧追逐设计写成当前状态。
- `git diff --check` 无尾随空格或补丁格式错误。
- `clean build`（含 `remapJar`）于 2026-08-02 `BUILD SUCCESSFUL`；发布 JAR 已核对流式快照类和六镜像维度资源。

候选包人工流程见 [Beta 0.2.1 人工验收](alpha-acceptance.md)。

## 发布物

| 文件 | 当前结果 |
| --- | --- |
| `build/libs/thefourthfrequency-0.2.1-beta.jar` | 15,130,453 字节；SHA-256 `02CE9D2C7E3B08723227FB93CFE5660E4B618B6CD9890743B1E67F917BA70FE4` |

## 发布前仍需完成

- 复跑未筛选的完整（`all`）客户端 GameTest。
- 使用两个真实客户端验证不同形态并发、断线重连、满背包退款和动态区块追赶。
- 在目标硬件上检查混音、强闪烁、多屏/DPI、LAN 房主分流和长时间 TPS。
