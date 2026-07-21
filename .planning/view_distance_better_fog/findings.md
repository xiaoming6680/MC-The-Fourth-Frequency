# 发现与决策

## 需求
- 将本 MOD 的视距强制固定为 3 区块，玩家不可调整。
- 研究并尽可能无损整合 `D:\!XM的项目\他人项目\Better-Fog-main` 的更好雾效。
- 保留本 MOD 既有玩法、视觉演出、存档和构建能力。

## 研究发现
- 本 MOD 使用 Minecraft 1.21.11、Fabric Loader 0.19.3、Fabric API 0.141.4、官方 Mojang 映射与 Java 21。
- 本 MOD 已有客户端 `OptionsAnomalyMixin`，在特定异象期间覆写 `Options.getEffectiveRenderDistance()` 为 2；固定 3 区块应整合该入口，避免两个视距策略互相覆盖。
- 本 MOD 已有 `FogRendererAnomalyMixin`，会修改 `FogRenderer.setupFog` 的雾颜色和起止距离；Better Fog 的适配必须与现有红雾/地平线异常组合，而不能平行覆盖同一 uniform。
- 项目资源包还包含 Sodium 雾着色器兼容文件，因此需同时审计原版渲染路径、现有 Mixin 与资源包着色器三层。
- 当前 Git 仓库没有提交，项目文件整体未跟踪；本轮不清理、不回退、不提交。
- Better Fog 源码目标为 Minecraft 1.20.1、Forge 47.3.5、官方 Mojang 映射；本 MOD 是 1.21.11 Fabric，因此入口、事件 API、渲染缓冲和雾 uniform 均不能直接编译复用。
- Better Fog 的 `gradle.properties` 明确写为 `mod_license=All Rights Reserved`，仓库顶层未发现 LICENSE/COPYING/NOTICE/README；在未获得作者授权前，不应把它的源码或衍生移植直接装入可公开发布的本 MOD。
- Better Fog 共 13 个 Java 类。入口只在 Forge 客户端初始化生成 JSON 配置；主体通过 `ViewportEvent.RenderFog`、`ViewportEvent.ComputeFogColor`、`TickEvent.RenderTickEvent` 和自定义 `DimensionSpecialEffects` 接管雾、天空、云、雨雪等渲染。
- Better Fog 并非单纯“调雾距离”：`BetterFogProcedure` 还包含自绘云层、天空盒、太阳/月亮、星星、天气粒子与声音。整包移植会大幅侵入本 MOD 已有天空盒、红雾、资源包与异常演出，不能称为无损。
- 可独立重写的核心思路是：按生物群系/高度/天气得到目标颜色、透明度、起止距离，再用有限步长平滑过渡；不需要复制其 Forge 事件层或整套天空/天气替换。
- Better Fog 默认数据把日间雾末端缩为原值 0.9，雨天缩为 0.6、雷暴缩为 0.5，并对洞穴/高空云层/虚空分别调色和限距；这套“环境状态叠加”是最值得保留的体验目标。
- Better Fog 实现存在不适合照搬的脆弱点：用反射替换 `ClientLevel.effects`、以引用不等号比较生物群系字符串、每帧重复读取全局状态，以及雾透明度在 0..1 与 0..255/0..100 之间混用。独立重写也能避免把这些风险带入本 MOD。
- Minecraft 1.21.11 的有效视距仍由 `Options.renderDistance()`（`OptionInstance<Integer>`）和 `getEffectiveRenderDistance()`控制；选项控件统一由 `OptionInstance.createButton(...)` 创建，可在控件生成后将视距按钮禁用。
- 1.21.11 `FogRenderer.setupFog` 仍集中写入颜色、环境雾起止与渲染距离雾起止六项数据；现有 Mixin 的 index 2/5/6 路径正好允许在不接管天空/天气的情况下组合增强雾。

## 技术决策
| 决策 | 理由 |
|------|------|
| 以 `getEffectiveRenderDistance()` 作为有效视距的底层强制入口之一 | 已有异象实现验证该入口在当前版本可注入，也能覆盖配置文件加载后的值 |
| 雾效优先合并进现有 `FogRendererAnomalyMixin` 或共享策略类 | 避免两个 Mixin 同时改 `setupFog` 参数造成顺序依赖 |
| 不直接复制 Better Fog 源码 | 版本/加载器跨度很大，且本地源码声明 All Rights Reserved |
| 第一版只实现“环境雾增强”，不接管天空、云和天气渲染 | 这是与现有视觉系统冲突最小、可验证且接近用户目标的范围 |
| 固定选项值、底层有效值和视频设置控件三层同时约束 | 配置文件、其他界面和服务器限制都不能让 UI 显示与实际效果互相矛盾 |
| 环境雾只缩短现有距离，不延长水下、熔岩、失明或服务器更小的限制 | 保留原版安全/玩法语义并避免视野穿透 |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| 根目录和旧作用域已有大量规划记录 | 为本任务建立独立作用域，不覆盖旧记录 |
| 首次用 PATH 调用 `jar`/`javap` 失败，随后误用未映射的缓存 JAR | 已通过 Gradle `javaToolchains` 定位 JDK 21，并改用 Loom 的 named clientonly JAR 完成 API 审计 |

## 资源
- 本 MOD：`D:\!XM的项目\MC-The-Fourth-Frequency`
- Better Fog 源码：`D:\!XM的项目\他人项目\Better-Fog-main`

## 视觉/浏览器发现
- 首轮查看 `0002_render-distance-locked-three-chunks.png`：视频设置界面已正常打开，但视距选项位于当前首屏下方，截图本身尚不能直观看到已禁用的锁定控件；需要滚动到该行后重新截图，并在运行时直接断言控件状态与文案。
- 首轮查看 `0004_m1-relay-station-zero.png`：场景主体位于封闭中继站内部，只能从小面积开口看到天空，不适合作为 3 区块边界与环境雾的视觉验收图；需要改看开阔场景或补专用截图。
- 第二轮重新查看 `0002_render-distance-locked-three-chunks.png`：自动居中后，画面清楚显示“渲染距离：3 个区块（已锁定）”；同一轮客户端测试还直接验证该控件 `active=false`，因此既有可视证据，也有运行时不可点击证据。
- 第二轮查看 `0043_m5-trend-swarm-rework-body.png`：开阔平面上近景实体与地面纹理清晰，中远景平滑融入蓝灰色雾幕，远端区块边缘没有突兀的垂直切断；日落暖色仍保留在雾层下缘，说明基础天空/生物群系色没有被整套替换。
- 专项查看 `0002_anomaly-13-red_horizon-peak.png`：红地平线峰值仍是完整深红天空，近地平线保持由浅红雾到暗红天幕的渐变；新环境雾没有覆盖既有异常色，红色异常继续作为最终叠加层。
- 最终归档复核 `build/visual-qa/render-distance-locked-three-chunks.png` 与 `build/visual-qa/atmospheric-fog-open-world.png`：归档图与通过测试时的画面一致；锁定文案完整可读，普通开阔场景的近景清晰、远景融合和日落色保留均稳定。

---
*每执行 2 次查看/搜索操作后更新此文件。*
