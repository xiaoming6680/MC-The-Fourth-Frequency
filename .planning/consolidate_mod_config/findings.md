# 发现与决策

## 需求
- 将 MOD 所有配置收敛到一个配置文件中。
- 现有配置过于凌乱，新结构应有明确分组与单一入口。
- MOD 尚未发布，不需要兼容旧世界或迁移旧配置。

## 研究发现
- 仓库没有 AGENTS.md；.agents 与 .codex 目录当前未列出额外项目指引。
- Git 仓库尚无提交，main 分支上的项目文件整体未跟踪；必须只改本任务文件，不清理或回退现有内容。
- 已明确的主配置入口是 config/thefourthfrequency.json，由 ModConfig 与 ConfigManager 管理，并在 TheFourthFrequency 启动时加载到 RuntimeServices。
- README 还明确列出两个独立客户端状态文件：config/thefourthfrequency-client-state.json 与 config/thefourthfrequency-alpha-state.json；它们也是本次“所有配置文件”合并范围，需继续定位实现。
- 资源包内 golden_days 的 polytone/config_entries 属于资源包自身声明，不是本 MOD 在游戏 config 目录中生成的运行时配置，不能因名称相同而误合并。
- 主配置已有 ModConfigTest，覆盖默认值、校验和 JSON 键名；合并后需扩展其契约。
- 三个运行时文件的写入点已全部定位：ConfigManager 写主配置，FirstRunNoticeController 写告示确认，AlphaDowngradeState 写 Alpha 降级完成标记；项目内没有第四个 Fabric config 目录写入点。
- 两个客户端状态写入器都各自重复了 Gson、目录创建、临时文件和原子移动逻辑，这是当前结构冗乱的核心。
- 客户端初始化先调用 AlphaLoadSessionController.initialize，再调用 FirstRunNoticeController.initialize；两者都只在初始化或状态变化时读写标记，不需要持续热重载。
- common 初始化先由 ConfigManager 加载配置并放入 RuntimeServices；客户端状态可以通过同一 ConfigManager 原子更新同一个文件，同时保留 common 启动时加载的运行参数。
- 直接读取主配置的生产/测试调用点约二十处，集中在开发加速、节奏、音量、Meta 开关与校正预算；若改为分组结构，需要同步机械迁移这些访问器。
- 当前 run/client 的主配置仍是九个平铺字段；run/datagen 与 run/server 还保留更旧的七字段版本。由于用户明确无需兼容，最终结构可直接替换，不解析旧平铺键。
- ResourceContractTest 对两个旧状态文件名、旧类内原子写入实现有静态断言；M0ClientGameTest 也直接依赖旧 AlphaDowngradeState 测试入口，合并时必须同步改写契约。
- 实现已将 ModConfig 改为 meta、pacing、limits、clientState 四个嵌套记录；旧的 SerializedName 平铺键别名已移除。
- ConfigManager 现在提供同步的客户端状态读取/更新，并在一次原子写入中保留其余分组；首次告示和 Alpha 控制器不再直接接触 Gson、Fabric config 路径或临时文件。
- AlphaDowngradeState 已删除；主源码与 GameTest 的运行参数读取均已迁移到新分组访问器。
- README、架构说明和 Alpha 验收步骤已改为唯一配置文件与 clientState 字段。
- 四源码集 compileJava、compileClientJava、compileGametestJava、compileTestJava 已全部成功。
- 完整 unitTest 共 101 项，100 项通过；唯一失败是 ResourceContractTest 的既有中文术语一致性检查，命中未在本任务中修改的 zh_cn.json 多处“异常”文案。
- 单独运行 ModConfigTest 4/4 通过，证明默认值、分组 JSON、边界校验与两个客户端标记的保留式转换符合新契约。
- run/client、run/datagen、run/server 中三个独立运行目录的本 MOD 主配置均已原地整理为同一四分组格式；每个运行实例仍只有一个 thefourthfrequency.json。
- 配置与两个受影响静态契约的定向测试合计 6/6 通过。
- remapJar 构建成功；发布 JAR 仅含 ConfigManager、ModConfig 及四个嵌套分组类，不含 AlphaDowngradeState。
- 三个开发运行实例各有且仅有一个 thefourthfrequency.json，旧 client-state/alpha-state 文件计数为 0；三个 JSON 均可解析且根分组完全一致。
- 发布 JAR 为 build/libs/thefourthfrequency-0.1.0-alpha.2.jar，SHA-256 为 1EB9DBD730D9AE3D97E13A81BC3CD0F7E4CEE8CDC5C904EE935C27FFF6DE0C2C。

## 技术决策
| 决策 | 理由 |
|---|---|
| 不加入旧键名别名、文件迁移或世界存档兼容逻辑 | 未发布阶段可以直接确立最终契约 |
| 保留唯一文件名 config/thefourthfrequency.json | 已是 MOD 主配置的稳定、直观名称；无需再引入目录或第二个名字 |
| 将配置按职责分组，并把两个客户端持久标记纳入 clientState | 一个文件内仍需保持可读性，避免把十一项继续平铺 |
| 由 ConfigManager 独占 JSON 与原子写入逻辑 | 消除三个类重复管理文件、临时文件与错误恢复的实现 |
| 最终根分组固定为 meta、pacing、limits、clientState | 分别承载表现安全开关、节奏参数、工作预算和两个客户端持久标记，结构小而明确 |
| 删除 AlphaDowngradeState，FirstRunNoticeController 只保留界面状态职责 | 两者不再各自拥有配置文件或序列化实现；Alpha 控制器和首次告示直接调用统一管理器 |

## 遇到的问题
| 问题 | 解决方案 |
|---|---|
| 系统 python 不可用 | 改用 Codex 工作区内置 Python |
| 补丁字符串两次被 JavaScript 转义语法拒绝 | 改用不含嵌套反引号的原始字符串 |
| 首次并行盘点中 AGENTS.md 搜索无结果并返回退出码 1，导致整组输出提前失败 | 后续搜索显式将 ripgrep 的“无匹配”退出码视为正常结果，并拆分获取 Git 与配置结果 |
| 一次合并测试与文档的大补丁因 Markdown 反引号再次终止 JavaScript 模板字符串 | 将代码补丁与文档补丁拆开，文档用占位符替换为反引号后成功应用 |
| 完整单元测试有一项无关的中文术语契约失败 | 定位到未改动语言资源；保留现状，单独运行配置专项并继续做产物审计 |
| 系统 PATH 中没有 jar 命令 | 使用 PowerShell ZipFile API 直接审计 JAR 条目 |
| 技能的 PowerShell 完成检查器不可执行 | 使用 Git Bash 运行同技能 shell 检查器并传入活动计划，得到 5/5 完成 |

## 资源
- 工作区：D:\!XM的项目\MC-The-Fourth-Frequency

## 视觉/浏览器发现
- 本任务尚未需要视觉或浏览器检查。
