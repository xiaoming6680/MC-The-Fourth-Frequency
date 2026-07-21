# 进度日志

## 会话：2026-07-21

### 阶段 1：兼容性与许可审计
- **状态：** complete
- **开始时间：** 2026-07-21
- 执行的操作：
  - 读取并启用 `planning-with-files-zh` 技能。
  - 确认本 MOD 与 Better Fog 源码目录均存在。
  - 确认旧活动计划为 `.planning/terminal_survival_rework`，本任务使用独立作用域。
  - 核对本 MOD 构建配置：Minecraft 1.21.11、Fabric、官方映射、Java 21。
  - 定位现有视距 Mixin、雾渲染 Mixin 与 Sodium 雾着色器兼容资源。
  - 确认工作区无提交且项目文件整体未跟踪；不清理用户现有内容。
  - 核对 Better Fog：Minecraft 1.20.1、Forge 47.3.5、13 个 Java 类，许可证元数据为 All Rights Reserved，顶层无独立许可证文件。
  - 识别其主体不仅修改雾，还替换天空、云、日月、星星、天气与声音；整包直接移植与“无损”目标冲突。
  - 读取 Better Fog 的默认配置和核心状态合成逻辑，提取日间、雨、雷暴、洞穴、高空和虚空的体验目标。
  - 使用项目 JDK 21 与 Loom named JAR 核对 1.21.11 `Options`、`OptionInstance`、视频设置和 `FogRenderer` API。
- 创建/修改的文件：
  - `.planning/view_distance_better_fog/task_plan.md`
  - `.planning/view_distance_better_fog/findings.md`
  - `.planning/view_distance_better_fog/progress.md`

### 阶段 2：方案冻结与基线
- **状态：** complete
- 执行的操作：
  - 冻结三层视距约束：配置值设为 3、底层有效值不超过 3、视频设置控件禁用。
  - 冻结轻量环境雾：只组合雾颜色与渲染距离边界，不接管天空、云、天气或声音。
  - 冻结兼容顺序：原版环境限制优先，普通环境雾其次，现有红雾最后叠加。
  - 改动前 `compileJava compileClientJava compileGametestJava unitTest` 通过，JUnit 91/91。

### 阶段 3：实现
- **状态：** complete
- 执行的操作：
  - 新增纯 Java `AtmosphericFogProfile`：固定 3 区块边界，并按天光、雨、雷暴、夜间、高度、洞穴与云层组合雾距离和颜色。
  - 将 `OptionsAnomalyMixin` 改为构造、加载、保存和有效值四层锁定 3 区块；移除红地平线临时 2 区块逻辑。
  - 新增 `OptionInstanceRenderDistanceMixin`，禁用视频设置中的视距控件并显示中英文锁定说明。
  - 将环境雾合并进现有 `FogRendererAnomalyMixin`，原版近雾优先、环境雾其次、红雾最后叠加。
  - 新增纯策略单元测试并扩展资源/Mixin 静态契约。
  - 修正文案中“红地平线恢复渲染距离”的旧描述；全局视距现在始终为 3，异象只恢复雾和天空颜色。
  - 在 mainline 客户端 GameTest 中加入配置值、有效值、保存纠偏和视频设置实际构建验证，并新增锁定界面截图。
- 创建/修改的文件：
  - `src/main/java/com/xm/thefourthfrequency/client_ui/AtmosphericFogProfile.java`
  - `src/client/java/com/xm/thefourthfrequency/mixin/OptionsAnomalyMixin.java`
  - `src/client/java/com/xm/thefourthfrequency/mixin/OptionInstanceRenderDistanceMixin.java`
  - `src/client/java/com/xm/thefourthfrequency/mixin/FogRendererAnomalyMixin.java`
  - `src/client/java/com/xm/thefourthfrequency/client_ui/AnomalyPresentationController.java`
  - `src/main/resources/thefourthfrequency.mixins.json`
  - `src/main/resources/assets/thefourthfrequency/lang/en_us.json`
  - `src/main/resources/assets/thefourthfrequency/lang/zh_cn.json`
  - `src/test/java/com/xm/thefourthfrequency/client_ui/AtmosphericFogProfileTest.java`
  - `src/test/java/com/xm/thefourthfrequency/ResourceContractTest.java`

### 阶段 4：验证
- **状态：** complete
- 已完成：
  - 改动后 `compileJava compileClientJava compileGametestJava unitTest` 全部通过，JUnit 95/95。
  - mainline 客户端 GameTest 完整通过；真实客户端日志记录从 10 切换到固定视距 3，Mixin 注入、保存纠偏和有效值断言均成功。
  - 查看首轮两张截图，确认设置界面能打开，但锁定行尚未滚入截图；中继站画面太封闭，不适合雾效验收。下一步补运行时控件断言、滚动截图和开阔场景视觉检查。
  - 将视频设置测试加强为直接定位视距控件、断言 `active=false` 和精确中文标签，并自动把该行滚到截图中央。
  - 加强后的 mainline 再次完整通过；新截图清楚展示锁定行，开阔 M5 场景显示远景自然融入环境雾且未出现区块硬切边。
  - `red_horizon` 异象专项客户端回归通过；峰值截图确认旧有红雾、红天幕和渐变仍完整保留。
  - `assemble` 成功生成正式 JAR；归档红地平线截图后，再次执行最终 mainline 回归并通过，将锁定界面和开阔雾效截图一并归档到 `build/visual-qa`。

### 阶段 5：交付
- **状态：** complete
- 已完成：
  - 最终 `options.txt` 为 `renderDistance:3`，客户端日志再次显示从 10 切换为 3。
  - 最终日志未发现 `MixinApplyError`、`InjectionError`、`InvalidInjectionException` 或相关注入问题。
  - 正式 JAR 为 `thefourthfrequency-0.1.0-alpha.2.jar`，10,353,982 bytes，SHA-256 `01B56E2D35C1651BFDB43EE8A08B017FD1E7E7DBF23BA8AC1510B5E19C8DA3BB`。
  - JAR 内容包含独立环境雾策略、三个相关 Mixin、Mixin 配置与双语资源，未包含 Better Fog 类名、包名或 Forge 事件引用。

## 测试结果
| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
| 改动前编译与单元测试基线 | `compileJava compileClientJava compileGametestJava unitTest` | 全部通过 | `BUILD SUCCESSFUL`，JUnit 91/91 | pass |
| 改动后编译与单元测试 | `compileJava compileClientJava compileGametestJava unitTest` | 全部通过 | `BUILD SUCCESSFUL`，JUnit 95/95 | pass |
| mainline 客户端回归 | `runClientGameTest -PtffClientTestSuite=mainline` | Mixin、锁定值、保存与完整流程通过 | `BUILD SUCCESSFUL`；日志显示视距从 10 改为 3 | pass |
| 加强后 mainline 客户端回归 | `runClientGameTest -PtffClientTestSuite=mainline` | 控件禁用、精确文案、滚动截图与完整流程通过 | `BUILD SUCCESSFUL in 1m 53s`；锁定行与开阔雾效截图可用 | pass |
| 红地平线兼容回归 | `runClientGameTest -PtffClientTestSuite=anomalies -PtffAnomaly=red_horizon` | 环境雾不覆盖既有红雾/红天空 | `BUILD SUCCESSFUL in 52s`；峰值视觉正常 | pass |
| 正式打包 | `assemble` | 生成可发布 Fabric JAR | `BUILD SUCCESSFUL in 21s`；JAR 10,353,982 bytes | pass |
| 最终 mainline 复跑 | `runClientGameTest -PtffClientTestSuite=mainline` | 交付前完整回归不退化 | `BUILD SUCCESSFUL in 1m 52s`；两张最终视觉证据已归档 | pass |

## 错误日志
| 时间戳 | 错误 | 尝试次数 | 解决方案 |
|--------|------|---------:|---------|
| 2026-07-21 | 系统 `python` 是 Microsoft Store 占位符，恢复脚本无法启动 | 1 | 改用工作区依赖 Python 或人工恢复核对 |
| 2026-07-21 | PATH 中没有 `jar`/`javap`，随后首次选到未映射 JAR | 2 | 用 Gradle 工具链定位 JDK 21，并改读 Loom named clientonly JAR，已解决 |
| 2026-07-21 | 一次 PowerShell `rg` 的翻译键正则引号转义错误 | 1 | 改用 UTF-8 `Get-Content -Tail` 读取实际 JSON 末尾，不重复该正则 |
| 2026-07-21 | 首轮客户端编译发现 `OptionsAnomalyMixin` 漏导入 `CallbackInfo` | 1 | 补入正确 Mixin callback 类型后重跑同一编译与测试矩阵 |
| 2026-07-21 | 一次只读源码盘点的末尾 `rg` 无匹配导致命令返回 1 | 1 | 已取得所需测试入口内容；后续使用精确方法名定位，不重复无匹配表达式 |
| 2026-07-21 | 首轮 mainline 客户端回归在精确视距断言处失败 | 1 | `options.txt` 已确认写为 3，Mixin 均成功加载；增加 stored/effective 诊断值，区分客户端值与测试框架服务端上限后再修正 |
| 2026-07-21 | 一次诊断命令读取不存在的可选 GameTest properties 文件而返回 1 | 1 | 所需 `options.txt` 与字节码信息已取得；不再读取该可选文件 |
| 2026-07-21 | 第二轮 mainline 诊断得到 `stored=5, effective=3` | 2 | 底层限制正确；GameTest 后置流程直接写入内存选项。新增 `OptionInstance.set` 参数钳制，使显示值和有效值都固定为 3 |
| 2026-07-21 | 首次定位 M0 GameTest 时把包目录误写为 `gametest`，随后一次使用了当前 PowerShell 不支持的 `||` | 1 | 通过 `rg --files` 定位真实 `test` 包；后续只使用兼容的 PowerShell 语法和精确路径 |

## 五问重启检查
| 问题 | 答案 |
|------|------|
| 我在哪里？ | 已完成 |
| 我要去哪里？ | 向用户交付结果、兼容性结论和正式 JAR |
| 目标是什么？ | 固定 3 区块视距并安全整合 Better Fog |
| 我学到了什么？ | 见 findings.md |
| 我做了什么？ | 见上方记录 |
