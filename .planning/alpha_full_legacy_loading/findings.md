# 发现与决策

## 需求
- Alpha 首次启用完成后，第二次及以后完整启动游戏时，不应再出现前 50% 原版加载界面、后 50% 旧版加载界面的切换。
- Alpha 启用后的加载表现应全面使用旧版界面。
- 首次进入世界的崩坏演出仍是一次性特殊流程，不能被普通旧版加载页覆盖。

## 研究发现
- `LoadingOverlay` 的 Mojang 标志由一个启动时直接从 Vanilla Pack 读取的 `LogoTexture` 提前加载；资源重载后同一纹理才按当前资源管理器更新，因此会产生前段原版、后段资源包版的可见切换。
- Golden Days Base 提供当前 `LoadingOverlay` 实际使用的 `assets/minecraft/textures/gui/title/mojangstudios.png`；Golden Days Alpha 的 `mojang.png` 是旧路径，不是现代遮罩的直接纹理入口。
- Golden Days Alpha 的旧版配色为背景 `#373363`、条底白色、轮廓黑色、进度 `#8E84FF`。
- `LoadingOverlay` 的完成和淡出由原版 `render`/`tick` 维护；替换整个 `render` 容易破坏遮罩移除，因此应只改固定纹理、背景取色和进度条绘制。
- `LevelLoadingScreen` 的普通旧版分支调用 `shouldRenderLegacyLoadingScreen()`，该方法当前要求 `active=true`；但连接 INIT/JOIN 事件可能晚于加载页首帧，从而先暴露原版页面。
- Alpha 完成标记在客户端初始化时已读取，且现有构造器 Mixin 能在第一次 ResourceManager 创建前准备三层资源包，因此可作为比连接状态更早的可靠表现判定。

## 技术决策
| 决策 | 理由 |
|------|------|
| 新增固定类路径 `ReloadableTexture` | 初始注册和后续 reload 都读取同一内置旧版图，不受资源应用阶段影响 |
| 让原版 `LoadingOverlay.render` 继续执行 | 保留原版平滑进度、淡入淡出、错误处理和自动关闭生命周期 |
| 持久旧版判定只依赖 `corruptionEverPlayed && !corruptionInProgress` | Alpha 标记已完成即可从第一帧生效；首次崩坏进行中仍走专用流程 |

## 视觉发现
- Golden Days Alpha 的 `mojang.png` 是紫色背景上的 “Mojang Specifications” 方形旧图。
- 当前后半段可见旧版标志来自 Golden Days Base 的 `mojangstudios.png`，该图按现代双半标志渲染入口制作；固定它可精确消除中途换图而不改变既有视觉风格。
- 本轮 `alpha-relaunch` 标题截图显示 Golden Days 泥土主页、`Minecraft 1.0.0` 左下版本和黄色中文标语均正常。
- 本轮进界截图显示从旧版泥土背景直接绘制“生成世界中 / 生成地形中”和绿灰进度条，没有红色崩坏或现代区块加载图。
