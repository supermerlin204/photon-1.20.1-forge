# Photon Forge Port

## 中文说明

这是 Photon 的 **非官方 Minecraft Forge 1.20.1 移植版本**。

本仓库不是 Low-Drag-MC 官方发布物，也不代表原作者的官方支持或认可。代码、资源和行为可能与官方 Photon 版本不同；使用、分发或二次开发前，请先阅读仓库内的 [LICENSE](LICENSE)。

本移植版本面向 Forge 1.20.1，并使用经过移植的依赖：

- [LDLib2 Forge 1.20.1 移植版](https://github.com/supermerlin204/ldlib2-1.20.1-forge)
- [KilaGraph Forge 1.20.1 移植版](https://github.com/supermerlin204/kilagraph-1.20.1-forge)

### 上游与移植关系

- 上游 Photon：[Low-Drag-MC/Photon](https://github.com/Low-Drag-MC/Photon)
- 当前仓库：基于上游 Photon 的 Forge 1.20.1 适配、维护和实验性修改
- 当前仓库的修改不应被误认为官方 Photon 的功能、承诺或发行计划

### 移植版新增功能

下列内容由本仓库独立添加，**上游 Photon 没有这些功能**。相关代码会在提交、文档和代码注释中使用 `Port-specific` / `本移植新增` 标记：

- `WholeFXEffectExecutor`：绑定实体生命周期、但不跟随实体移动的整体 FX 旋转执行器
- `IWholeEffectTransformer`：为世界空间粒子提供整体位置与方向变换的专用接口
- 对模型、Billboard 和 Beam 渲染路径的整体旋转适配
- **参数修改即时重播预览（本移植新增）**：在 FX 对象参数面板修改旋转、大小等参数，或使用场景变换工具后，编辑器会重置整个预览 FX，并以原随机种子从零重播到修改前的时间刻，保留播放／暂停状态。参数撤销和重做也会刷新；同一帧的修改合并处理，重播不重复触发时间轴音频和信号。仅作用于编辑器，不改变游戏内发射器行为；预览进度越长，重播耗时越多。
- **GPU Model 透明排序（本移植新增，非上游功能）**：在 GPU 实例化且顶点排序不为 `NONE` 时，透明模型按三角形投影重叠关系进行远到近排序，修复刀光等模型在特定视角下出现的异常三角区域。配套优化包括工作区与共享顶点变换复用、精确排序缓存、按需索引上传，以及 OpenGL 4.2 快速绘制路径（保留 3.3 兼容路径）。不改变材质混合方式或整体旋转逻辑；大量密集重叠仍有排序开销，真正互穿或循环遮挡仍可能无法完全解决。

- **特效预热 API（本移植新增，非上游功能）**：`FXWarmup.warmup(id)` 默认模拟一 tick 并离屏绘制一次，用于在正式播放前准备实际触及的着色器、贴图和模型。不会将画面显示给玩家；临时实例会销毁，时间轴声音和信号不会触发，后处理请求相互隔离，共享资源缓存保留。此功能不是磁盘着色器缓存。

这些新增内容属于本移植版本的实现，不代表上游项目的 API，也不保证与上游未来版本兼容。

#### 特效预热调用示例

```java
import com.lowdragmc.photon.client.fx.FXWarmup;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.resources.ResourceLocation;

// 客户端资源加载完成后，在渲染线程的加载队列中调用（不在绘制过程中调用）。
FXWarmup.warmup(ResourceLocation.fromNamespaceAndPath("photon", "blade"));
// 延迟发射器可指定推进 tick 数，最后绘制一次：
FXWarmup.warmup(ResourceLocation.fromNamespaceAndPath("photon", "blade"), 20);
// 所有可加载 FX 的 ID，供加载队列逐个预热：
var ids = FXHelper.listAllFX();
```

建议每个加载帧处理少量 ID，不要在一个普通游戏帧中同步遍历全部。方法支持直接传入 `FX`，模拟 tick 数范围为 1–10000，返回模拟 tick 数、结束时普通粒子发射器的粒子数和调用耗时（纳秒）；粒子数为零时请检查发射延迟/速率。必须等待资源重载完成，不能在 `LoadingOverlay` 尚未结束时调用。

预热在空虚拟世界中同步执行，不是后台任务；仅准备模拟过程及最终绘制实际触及的路径，不能保证覆盖延迟／条件发射器、后续着色器变体或依赖世界／实体的行为，也不保证消除所有首播卡顿。资源重载后按需重新预热。

### 协议摘要

本项目及其直接修改版本遵循 [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-nc-sa/4.0/)（CC BY-NC-SA 4.0）。以下是便于阅读的摘要，完整法律条款以 [LICENSE](LICENSE) 为准：

1. **署名**：再分发、修改或移植时，必须注明 Photon、原作者 KilaBash、上游仓库链接、许可证链接，并说明所做修改。
2. **非商业**：不得将本项目用于付费下载、付费访问、商业整合、商业再分发或其他直接、间接变现行为。赞助、捐赠、众筹和商业服务也属于需要获得许可的范围。
3. **相同方式共享**：直接修改、分支和移植版本须继续使用 CC BY-NC-SA 4.0，并公开完整源代码。
4. **移植许可**：面向其他 Minecraft 版本公开发布 Photon 移植版前，需要取得原作者的书面许可；移植版本必须保持开源、署名和非商业限制。
5. **模组包与整合**：非商业模组包可以包含本项目；非商业情况下，也可以通过 jar-in-jar 集成，但不得将本项目本身单独收费或变现。
6. **创作内容**：使用 Photon 制作的特效、FX Pack、图表、配置、数据包、视频等内容不属于本项目本体，通常不受本许可证约束；请勿将这条理解为对 Photon 源码或资源的再分发授权。

商业授权、移植许可或其他许可问题，请联系原作者 KilaBash：`yefancy@foxmail.com`。

### 构建信息

- Minecraft：1.20.1
- Mod Loader：Minecraft Forge 47.4.x
- Java：17
- 依赖：移植版 LDLib2、移植版 KilaGraph（使用已移除 Kotlin for Forge 强依赖的修订版 JAR）

构建前请确认 `libs/` 中的本地依赖与 `build.gradle` 中的版本一致。该仓库主要用于移植维护、兼容性验证和下游开发，不等同于官方发行渠道。

## English

This repository is an **unofficial Minecraft Forge 1.20.1 port of Photon**.

It is not an official Low-Drag-MC release and does not imply endorsement, support, or compatibility guarantees from the original author. Code, assets, and behavior may differ from upstream Photon. Read the repository [LICENSE](LICENSE) before using, redistributing, or modifying this project.

This port targets Forge 1.20.1 and uses the following ported dependencies:

- [LDLib2 Forge 1.20.1 port](https://github.com/supermerlin204/ldlib2-1.20.1-forge)
- [KilaGraph Forge 1.20.1 port](https://github.com/supermerlin204/kilagraph-1.20.1-forge)

### Upstream and Port Status

- Upstream Photon: [Low-Drag-MC/Photon](https://github.com/Low-Drag-MC/Photon)
- This repository: Forge 1.20.1 adaptation, maintenance, and experimental changes based on upstream Photon
- Changes in this repository must not be presented as official Photon features, promises, or release plans

### Port-Specific Additions

The following features are maintained independently in this repository and **do not exist in upstream Photon**. Related commits, documentation, and code comments use the `Port-specific` / `本移植新增` marker:

- `WholeFXEffectExecutor`: an entity-bound whole-FX rotation executor that does not follow the entity after startup
- `IWholeEffectTransformer`: a dedicated interface for whole-effect position and orientation transforms of world-space particles
- Whole-rotation integration for model, billboard, and beam rendering paths
- **Live parameter replay (Port-specific)**: changing FX object inspector parameters (such as rotation or size), or using the scene transform gizmo, resets the entire preview FX and replays from tick zero to the pre-edit tick with the same seed and playback/pause state. Parameter undo/redo also refreshes the preview. Edits within a frame are coalesced; replay suppresses timeline audio and signals. This is editor-only and does not change in-world emitters. Longer preview times require more replay work.
- **GPU Model transparency sorting (Port-specific, not an upstream feature)**: with GPU instancing enabled and vertex sorting set to a mode other than `NONE`, translucent models use back-to-front triangle ordering based on projected overlap, fixing angle-dependent triangular artifacts in effects such as blade slashes. Optimizations include reusable workspaces and shared vertex transforms, exact-input caching, conditional index uploads, and an OpenGL 4.2 drawing fast path with a 3.3 fallback. Material blending and whole-FX rotation remain unchanged. Dense overlap still incurs sorting costs; genuine intersections or cyclic occlusion may remain unresolved.

- **FX warmup API (Port-specific, not an upstream feature)**: `FXWarmup.warmup(id)` simulates one tick and renders once to an offscreen target, preparing the shaders, textures, and models actually reached before normal playback. Temporary instances are destroyed without displaying the warmup; timeline audio/signals are suppressed and post-effect requests are isolated. Shared resource caches remain available. This is not a persistent shader cache.

These additions are specific to this port. They are not upstream Photon APIs and are not guaranteed to remain compatible with future upstream versions.

#### FX Warmup Usage

```java
import com.lowdragmc.photon.client.fx.FXWarmup;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.resources.ResourceLocation;

// Call after client resource loading, on the render thread, outside an active draw.
FXWarmup.warmup(ResourceLocation.fromNamespaceAndPath("photon", "blade"));
// Advance more ticks for delayed emitters, then draw once:
FXWarmup.warmup(ResourceLocation.fromNamespaceAndPath("photon", "blade"), 20);
// IDs to enqueue for warmup:
var ids = FXHelper.listAllFX();
```

An overload accepts an `FX` definition and/or a tick count (1–10000). The result reports ticks, the final particle-emitter particle count, and elapsed nanoseconds. If the particle count is zero, check emission delays/rates. Queue a small number of effects per loading frame **after resource reload completes**, on the render thread, outside world/UI drawing; do not call while `LoadingOverlay` is still active.

This is synchronous, not a background task. Only paths reached by the simulation and final draw are prepared: delayed/conditional emitters, later shader variants, and world/entity-dependent behavior are not guaranteed to be covered. Simulation uses an empty dummy world. It reduces some first-use work, not every possible first-play stall; rerun as needed after resource reload.

### License Summary

This project and its direct modifications are licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-nc-sa/4.0/) license (CC BY-NC-SA 4.0). The following is only a practical summary; the complete and authoritative terms are in [LICENSE](LICENSE):

1. **Attribution**: Redistributions, modifications, and ports must credit Photon and KilaBash, link the upstream repository and the license, and state what was changed.
2. **NonCommercial**: Paid downloads, paid access, commercial integration, commercial redistribution, and other direct or indirect monetization are prohibited without permission. Sponsorships, donations, crowdfunding, and commercial services are also within the restricted scope.
3. **ShareAlike**: Direct modifications, forks, and ports must remain under CC BY-NC-SA 4.0 and provide the complete source code.
4. **Port permission**: Public Photon ports for other Minecraft versions require prior written permission from the original author and must remain open source, attributed, and non-commercial.
5. **Modpacks and integration**: Non-commercial modpacks may include this project. Non-commercial jar-in-jar integration is allowed, but the mod itself must not be sold or monetized.
6. **Created content**: Effects, FX Packs, graphs, configurations, data packs, videos, and other content made with Photon are not part of this project and are generally not covered by this license. This does not grant permission to redistribute Photon source code or assets.

For commercial licensing, port permissions, or other licensing questions, contact the original author KilaBash at `yefancy@foxmail.com`.

### Build Information

- Minecraft: 1.20.1
- Mod loader: Minecraft Forge 47.4.x
- Java: 17
- Dependencies: ported LDLib2 and KilaGraph (use the revised JARs that remove the Kotlin for Forge requirement)

Make sure the local dependencies in `libs/` match the versions declared by `build.gradle`. This repository is intended for port maintenance, compatibility work, and downstream development; it is not an official Photon distribution channel.

## Links

- [Upstream Photon](https://github.com/Low-Drag-MC/Photon)
- [This repository](https://github.com/supermerlin204/Photon)
- [LDLib2 Forge 1.20.1 port](https://github.com/supermerlin204/ldlib2-1.20.1-forge)
- [KilaGraph Forge 1.20.1 port](https://github.com/supermerlin204/kilagraph-1.20.1-forge)
- [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)
- [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/)

## 致谢 / Thanks

- 感谢 KilaBash 与 [Low-Drag-MC](https://github.com/Low-Drag-MC) 开发并维护 Photon 上游项目。
- 感谢 [LDLib2 Forge 1.20.1 移植版](https://github.com/supermerlin204/ldlib2-1.20.1-forge) 和 [KilaGraph Forge 1.20.1 移植版](https://github.com/supermerlin204/kilagraph-1.20.1-forge) 为本移植版本提供基础依赖。
- Thanks to KilaBash and [Low-Drag-MC](https://github.com/Low-Drag-MC) for the original Photon project.
- Thanks to the maintainers of the [LDLib2 Forge port](https://github.com/supermerlin204/ldlib2-1.20.1-forge) and [KilaGraph Forge port](https://github.com/supermerlin204/kilagraph-1.20.1-forge) for the ported libraries used here.

For the full legal text, see [LICENSE](LICENSE).
