# 固定频段退场与近场接收器实施规格

## 1. 目标

删除玩家可见的四个固定电台、三频校准任务、自动/手动轮播和全局调频交互。保留“调谐”作为附近异常信号时的短交互，并把“第四频段”改为污染工具、记录和波形的异常信号层，不再是第四个可选频点。

不改变：

- 四页终端、六个工具、单一当前任务和文件分栏。
- 四组原版建筑候选、服务端真实结构复核、世界共享碎片和私人短异常。
- 稳定文件 ID、结局 ID、异象 ID、旧日志 `band` wire ID 和存档 schema v5。

## 2. 新玩家流程

1. 首次打开终端直接进入主页，任务从“让终端记住一个经常回来的地方”开始。
2. 玩家记住住处或自然取得铁中的任一项后，终端完成绑定。不显示绑定进度条，不要求三次校准。
3. 取得铁仍触发现有的标志异象和校正场景。该场景启动后，`BAND_STAGE` 从 0 变为 1，对玩家表达为“终端开始收到无法归类的信号”。
4. 四组原版建筑候选在异常信号层出现后分配；候选列表统一出现在“工具 > 信号”，不再分散到四个频段。
5. 玩家到达真实候选建筑范围后，终端状态灯提示“附近有信号”，但不覆盖主页的生存任务。
6. 玩家打开“信号”工具后才显示近场接收器。拖动滑块时，静电声、波形稳定度和信号强度一起变化；误差≤2且保持 20 Tick 后锁定。
7. 服务端再次验证玩家仍在正确建筑范围内，然后播放私人短异常并共享碎片。

## 3. 接收器状态机

| 状态 | 条件 | 服务端行为 | 客户端行为 |
|---|---|---|---|
| `DORMANT` | `BAND_STAGE == 0` | 不分配新候选，拒绝调谐 | 信号工具只显示普通摘要 |
| `STANDBY` | 信号层已出现，附近无目标 | 不计锁定时间 | 右侧显示“接收器待机” |
| `NEARBY` | 真实候选建筑范围内 | 向工具快照发送局部峰值 | 全屏状态灯/信号工具图标提示，不显示滑块 |
| `TUNING` | `selectedTool == SIGNAL` 且仍在范围 | 接受 0..100 的 `TUNE` | 显示滑块、强度和反馈 |
| `LOCKING` | `abs(current-target) <= 2` | 累计 0..20 Tick；离开误差立即清零 | 显示锁定进度，波形趋于稳定 |
| `COMPLETE` | 连续 20 Tick 且服务端复核通过 | 写入共享发现，清空当地状态 | 播放锁定音和私人短异常 |

任一情况都会清空锁定进度：关闭终端、离开信号工具、离开建筑范围、目标已被其他玩家完成、当前终端失效。

## 4. 数值规则

- 滑块内部仍使用 0..100，但不再显示四个固定 MHz 电台。
- 每个候选建筑的峰值由 `fragment + group + dimension + position` 确定性派生，限制在 12..88，避免靠近滑块端点。
- 峰值不写入存档；相同候选在相同存档中永远得到相同结果。
- 锁定半径为 2，锁定时间为 20 Tick。
- 强度由误差计算，建议 `max(0, 100 - abs(current-target) * 4)`。强度只用于反馈，完成仍以服务端误差和时间为准。

## 5. 主线与存档

### 绑定

`StoryProgressService.update` 改为：

```text
earlySurvival = HOME || IRON
bind = earlySurvival && !bound
reveal = bandStage == 0 && bound && signatureSceneMask != 0
```

删除 `calibratedBandsMask == 0b111` 参与绑定。`objective()` 从 `set_home` 开始，不再返回 `calibrate`。

### 旧存档

- 已绑定存档：保持不变。
- 未绑定但已有 HOME 或 IRON：读档后下一次服务端更新自然绑定。
- 旧 `CALIBRATED_BANDS_MASK` 和 `AUTO_TUNING`：保留读取/默认值，不再参与任何分支，不作为迁移条件。
- 旧 `band` 日志：原样可读，不批量改写。
- 旧存档升级时若玩家正站在候选内，新局部峰值可与旧四个数值不同；锁定进度本来不持久化，最多损失 1 秒当次操作。
- 本轮不增加持久化字段，不升 schema v5。

## 6. 网络边界

### `TerminalSnapshotPayload`

继续使用 v5，不改字段顺序：

- `publicStationMask` 固定发送 0。
- `tuning` 仅代表当次近场接收器滑块数值。
- `bandStage` 保留为异常信号层阶段。
- `reminderBand` 固定发送 -1。
- objective 始终发送真实生存任务，不再借用 `nearby_signal`。

### `TerminalToolSnapshotPayload`

从 v1 升到 v2：

- 删除 `automaticTuning`。
- 保留 `nearbySignalCount`。
- 新增 `receiverAvailable`、`receiverTarget`、`receiverStrength`、`receiverLockTicks`。
- 客户端不显示 `receiverTarget`，它只用于本地波形和锁定反馈；服务端仍权威验证。

### `TerminalControlPayload`

- 保留 `TUNE = 1`，但服务端只在有效终端、已选择 SIGNAL 工具、附近有真实候选时接受。
- 保留数值 11 为废弃的 `SET_AUTO_TUNING`，服务端一律拒绝，不重排后续 action ID。
- 保留 `READ_TRUTH_FILE = 12`。
- 新增 `MARK_RECORDS_READ = 13`，只接受 `value == 0`，把当前终端的全部记录标为已读。

## 7. 服务端文件改动

### `StoryProgressService.java`

- 删除校准分支和 `recordBandLock` 生产调用。
- 绑定只使用 HOME/IRON。
- objective 从 `set_home` 开始。
- 揭示时仍写 `BAND_STAGE=1`，但提示改为无法归类的信号。

### `TerminalRuntimeService.java`

- 删除 `advanceAutomaticTuning`、固定电台锁定和 `lockedBand`/`automaticTuningIndex` 状态。
- 把 `acknowledgeLockedBand` 改为 `advanceNearbyReceiver`，仅在 SIGNAL 工具打开且附近目标未完成时工作。
- `TUNE` 分支增加工具和附近目标校验。
- 不再用附近信号替换主页 objective。
- 增加全部记录已读动作。

### `TerminalToolService.java`

- 删除自动调频设置和判定。
- 快照从当前 `Nearby` 和终端视图取得接收器字段。
- 信号工具继续常驻；信号层未出现时只显示普通摘要。

### `FragmentInvestigationService.java`

- 用候选的稳定属性派生局部峰值，删除 `{28,55,73,95}` 固定数组。
- 保留 `BAND_STAGE > 0` 的分配门槛和真实结构复核。
- 保留旧日志 band 分布作为不可见的存储兼容；客户端不再按它过滤。

### `TerminalSignalLog.java`

- 新增 `markAllRead`。
- 保留 `markBandRead` 仅供旧数据/迁移容错，生产 UI 不再调用。
- 第一轮继续每个内部 band 最多 32 条，避免无关的日志格式迁移。

### `TerminalSignalService.java` / `TerminalData.java`

- `maintenance_handoff` 改由 `BOUND` 解锁。
- 新建终端只写一条发放/自检记录，不再为三个固定电台写初始记录。
- 旧 `CALIBRATED_BANDS_MASK`/`AUTO_TUNING` 仍能读取和默认填充，但不再影响任何行为。

## 8. 客户端文件改动

### `TerminalScreen.java`

- 移除全局 `automaticTuning`、固定 band 判定、公开电台标题和频段提醒。
- 滑块只在 `page == TOOLS && selectedTool == SIGNAL && receiverAvailable` 时绘制和接受鼠标/键盘输入。
- 右侧 LCD 平时显示接收器待机、附近有信号或当前指路状态；接收器打开时显示强度与锁定状态。
- 信号工具按事件类型聚合全部 `fragment_*`/剧情信号，不再只读 UNKNOWN band。
- 波形基础态由异象视觉阶段决定；近场时再根据强度逐渐稳定。它不再因切换四个电台而改变。
- 进入 RECORDS 页时发送 `MARK_RECORDS_READ`。

### `TerminalSnapshot.java` / `TerminalToolSnapshot.java`

- 移除校准完成、公开电台和提醒 band 的客户端语义。
- 增加按事件类型的信号工具列表。
- “最近发生”从倒序列表取 `getFirst()`，不再取最旧条目。
- 工具快照提供接收器可用、强度和锁定进度访问器。

### `TerminalUiLayout.java`

- `TUNING_SLIDER` 改名为 `RECEIVER_SLIDER`。
- 删除 `AUTO_TUNING` 和 `MANUAL_TUNING` 互动区。
- 保留现有右侧硬件安全区，不扩张四页主显示区。

## 9. 调试与文案

- Debug 动作内部可继续使用 `band` 作为稳定 action ID，但页面改显示“异常信号状态”。
- 阶段 0..3 文案改为“未出现 / 已出现 / 已稳定 / 已停止”。
- 删除玩家可见的 `calibrate`、自动/手动调频、第一/第二/第三频段文案引用。
- 保留 MOD 名、结局和世界观中的“第四频段”，并统一表达为异常层，不是电台列表。

## 10. 实施批次

### 批次 A：主线与领域规则

- 修改 `StoryProgressService`、`TerminalControlPolicy`、`SignalBand`、`TerminalSignalLog`。
- 先更新纯单元测试，确保新绑定、局部峰值和全部已读规则独立通过。

### 批次 B：服务端接收器与网络

- 修改 `FragmentInvestigationService`、`TerminalRuntimeService`、`TerminalToolService`和两个快照/控制载荷。
- 验证非 SIGNAL 工具调谐被拒绝、离开目标清零、多人完成不重复发文件。

### 批次 C：客户端 UI 与反馈

- 修改 `TerminalScreen`、两个客户端快照包装和 `TerminalUiLayout`。
- 产出三张回归截图：普通主页无滑块、信号工具待机、附近信号正在锁定。

### 批次 D：兼容、全回归与文档

- 更新 `TerminalData`、Debug 文案、中英文资源、README 和 `docs/`。
- 运行编译、单元、完整服务端 GameTest 和 mainline 客户端链。

## 11. 验收条件

- 新世界首页没有 `0/3` 校准，右侧没有自动/手动轮播。
- 未靠近异常信号时，任何页面都不能拖动调谐滑块。
- 靠近信号不会覆盖主页生存任务。
- 只有打开信号工具才能调谐；错误值、离开工具或离开建筑都不得完成。
- 四组候选都能从同一信号工具查看、选择、导航和锁定。
- 记录页可一次清除全部未读，不再要求轮流调到各频段。
- 已绑定旧存档、未绑定但已建家/取铁的旧存档、旧日志和旧碎片均可继续使用。
- 专用服务器不加载客户端类，所有 `TUNE`/已读/工具选择仍由服务端校验。

## 12. 验证命令

```powershell
.\gradlew.bat compileJava compileClientJava compileGametestJava test --no-daemon
.\gradlew.bat runGameTest --no-daemon
.\gradlew.bat runClientGameTest -PtffClientTestSuite=mainline --no-daemon
```

