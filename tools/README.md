# tools · 资产管线 / Asset pipeline

这里的每个 Python 脚本都**确定性地**生成一份运行时资产：贴图、模型、UV 布局或音频。同一份输入重跑一次，输出逐字节相同；没有手绘产物，参考图也从不被缩放粘贴进游戏。

Every Python script here generates a runtime asset **deterministically** — textures, models, UV layouts or audio. Re-running with the same input produces byte-identical output. Nothing is hand-painted, and reference art is never scaled and pasted into the game.

```powershell
# 从仓库根目录运行，需要装了 Pillow 的 Python
# Run from the repository root with a Python that has Pillow
python tools/<script>.py
```

`tools/assets/` 存放母板素材（高分辨率材质板等），它们是脚本的输入，不是运行时资源。

`tools/assets/` holds the masters (high-resolution material boards and the like). They are inputs to the scripts, not runtime resources.

## 完整说明 / Full documentation

每个脚本的产出、UV 契约、自发光契约与冻结资产清单见：

Per-script outputs, UV contracts, emissive contracts and the frozen-asset list live in:

- [美术与资产管线](../docs/zh/art-pipeline.md)（中文）
- [Art and asset pipeline](../docs/en/art-pipeline.md) (English)

> `docs/art/**` 下的清单文件被单元测试直接按路径读取，**改名或移动会让 `unitTest` 变红**。
>
> Manifest files under `docs/art/**` are read by unit tests at fixed paths. **Renaming or moving them turns `unitTest` red.**
