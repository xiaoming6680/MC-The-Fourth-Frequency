# 《第四频段》文档导航

本目录描述 `0.2.0-beta` 的当前实现。玩法数字以源码中的策略类、协议 DTO、资源目录和已完成验证为准；根目录规划日志与 `.planning/` 仅保留开发历史，不作为当前玩法说明。

| 文档 | 用途 | 主要读者 |
|---|---|---|
| [人工验收与游玩说明](alpha-acceptance.md) | 发布前按步验收新档、终端、多人、世界接口、结局和 Meta | QA、测试玩家 |
| [架构与安全边界](architecture.md) | 权威数据流、持久化、协议、性能与本地 Meta 边界 | 开发者、审核者 |
| [末地祭坛与世界接口规则](end-altar-and-terminal-guidance.md) | 当前终局的完整数值、仪式、攻击、结局和 F8 契约 | 设计、QA、服务器管理员 |
| [测试策略与当前证据](testing.md) | Gradle 入口、分层覆盖、当前结果与人工门禁 | 开发者、发布维护者 |
| [当前完成矩阵](goal-progress.md) | 按子系统汇总交付状态和未完成的发布前实机项 | 项目管理、发布维护者 |
| [Beta 0.2 交付报告](final-report.md) | 当前交付物、验证摘要、兼容界限与发布建议 | 项目所有者、发布维护者 |
| [世界观圣经](world-bible.md) | 不可改写的叙事事实、语言与 Meta 原则 | 剧情、文案、美术 |
| [返工体美术流程](art/rework_body/README.md) | 五形态概念图、材质参考与 UV 契约 | 美术、渲染开发 |
| [观察者美术流程](art/watcher/README.md) | 观察者参考图、生成提示、运行时纹理与冻结资产 | 美术、渲染开发 |

## 维护规则

- 版本、依赖和产物名称从 `gradle.properties`、`fabric.mod.json` 与 `build/libs/` 提取。
- schema/协议号从对应 `CURRENT_VERSION` / `CURRENT_PROTOCOL_VERSION` / `FORMAT_VERSION` 常量提取。
- 终局数值从 `WorldInterfacePolicy`、`WorldInterfaceActionScheduler`、`EndBossArenaService` 与 `WorldInterfaceAttackService` 提取，不复用旧 `EndBoss*` 兼容类中的数字。
- 自动化数据只记录已完成的实际运行；不把编译成功写成运行时验收。
- 调整任一玩家可见契约时，同步检查 `zh_cn.json`、`en_us.json`、README、人工验收、架构、测试与终局规则文档。
