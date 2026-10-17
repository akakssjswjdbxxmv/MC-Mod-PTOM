# Litematica / MaLiLib API 参考文档

> **适用版本**：Litematica `0.19.61`、MaLiLib `0.21.10`、Minecraft `1.21`
> **来源**：使用 JDK 24 `javap -public` 反编译本地 Modrinth 缓存 jar 中的真实字节码（非人工凭记忆编写）
> **生成日期**：2026-10-17
>
> **关于 `net.minecraft.class_XXXX`**：Litematica / MaLiLib 发布 jar 使用 Fabric 的 **intermediary（中介名）** 编译，因此反编译输出中 Minecraft 类与方法均显示为 `class_XXXX` / `method_XXXX` 形式（如 `class_310` = `Minecraft`、`class_2338` = `BlockPos`、`class_2487` = `CompoundTag`）。本项目基于 Loom 依赖 **Mojang 官方映射**，Gradle 会在构建时**自动把依赖重映射为 Mojang 名**，所以你写代码时直接使用官方名即可，无需手动翻译，本文档保留 intermediary 原样以忠实反映 jar 内容。

## 使用约束

- **必须放在客户端源集**：litematica 与 malilib 均为**纯客户端模组**。本项目启用了 `splitEnvironmentSourceSets`，所有调用它们的代码必须位于 `src/client` 源集（客户端入口/客户端初始化逻辑），否则专用服务器启动时会抛出 `NoClassDefFoundError`。
- **主线程调用**：运行时对管理器/放置/选区的读写必须在客户端主线程（事件回调、GUI、tick 中）；不要在渲染线程或自建线程中直接调用。
- **进世界后可用**：`DataManager` 下的各管理器（选区、放置、项目）、`WorldSchematic` 等需在**进入存档后**才初始化完毕，进世界前调用会得到空/无效状态。
- **依赖声明**：本项目已通过 `modImplementation` 引入两个模组依赖，并在 `fabric.mod.json` 的 `depends` 中声明，构建期可直接 import。

---

# 第一章 Litematica（fi.dy.masa.litematica）

## 1.1 数据中枢

### data.DataManager

Litematica 的全局单例中枢，持有选区管理器、放置管理器、项目、工具模式、渲染层范围等核心状态，并负责配置读写与目录管理。

```java
// 单例获取
public static fi.dy.masa.litematica.data.DataManager getInstance();

// 子管理器访问
public static fi.dy.masa.litematica.selection.SelectionManager getSelectionManager();
public static fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager getSchematicPlacementManager();
public static fi.dy.masa.litematica.schematic.projects.SchematicProjectsManager getSchematicProjectsManager();
public static fi.dy.masa.litematica.schematic.transmit.SchematicBufferManager getSchematicBufferManager();
public static fi.dy.masa.litematica.materials.MaterialListBase getMaterialList();
public static void setMaterialList(fi.dy.masa.litematica.materials.MaterialListBase);

// 工具模式与渲染层
public static fi.dy.masa.litematica.tool.ToolMode getToolMode();
public static void setToolMode(fi.dy.masa.litematica.tool.ToolMode);
public static fi.dy.masa.malilib.util.LayerRange getRenderLayerRange();
public static fi.dy.masa.litematica.selection.AreaSelectionSimple getSimpleArea();

// 投影工具物品（Litematica 权杖）
public static net.minecraft.class_1799 getToolItem();
public static void setToolItem(java.lang.String);

// 服务器环境探测
public static boolean isCarpetServer();
public static boolean hasServuxServer();

// 配置与状态持久化
public static void load();
public static void save();
public static void save(boolean);
public static void clear();
public static boolean getCreatePlacementOnLoad();
public static void setCreatePlacementOnLoad(boolean);

// 目录管理
public static java.io.File getDefaultBaseSchematicDirectory();
public static java.io.File getCurrentConfigDirectory();
public static java.io.File getSchematicsBaseDirectory();
public static java.io.File getAreaSelectionsBaseDirectory();
public java.io.File getCurrentDirectoryForContext(java.lang.String);
public void setCurrentDirectoryForContext(java.lang.String, java.io.File);

// 聊天消息监听
public static boolean onChatMessage(net.minecraft.class_2561);
public static void addChatListener(fi.dy.masa.litematica.util.ToBooleanFunction<net.minecraft.class_2561>);
public static void removeChatListener(fi.dy.masa.litematica.util.ToBooleanFunction<net.minecraft.class_2561>);

// 其他（节选）
public static long getClientTickStartTime();
public boolean hasIntegratedServer();
public void onWorldPre(net.minecraft.class_5455);   // class_5455 = RegistryAccess
```

### schematic.projects.SchematicProjectsManager

投影**版本项目管理器**（版本控制式存档工作流）。

> ⚠️ 候选清单原列于 `data` 包，**实际位于 `schematic.projects` 包**，以 jar 内真实路径为准。通过 `DataManager.getSchematicProjectsManager()` 获取实例。

```java
// （节选）
public fi.dy.masa.litematica.schematic.projects.SchematicProject getCurrentProject();
public boolean hasProjectOpen();
public void createNewProject(java.io.File, java.lang.String);
public boolean openProject(java.io.File);
public void closeCurrentProject();
public void saveCurrentProject();
public boolean cycleVersion(int);            // 切换版本
public boolean commitNewVersion(java.lang.String);
public boolean pasteCurrentVersionToWorld(); // 把当前版本粘贴到世界
public void openSchematicProjectsGui();
public void clear();
```

## 1.2 投影加载与持有

### data.SchematicHolder

全局投影缓存/持有者，负责按文件加载 `LitematicaSchematic` 并维持已加载实例集合。

```java
public fi.dy.masa.litematica.data.SchematicHolder();  // public 构造器
public static fi.dy.masa.litematica.data.SchematicHolder getInstance();
public fi.dy.masa.litematica.schematic.LitematicaSchematic getOrLoad(java.io.File);
public void addSchematic(fi.dy.masa.litematica.schematic.LitematicaSchematic, boolean);
public boolean removeSchematic(fi.dy.masa.litematica.schematic.LitematicaSchematic);
public java.util.Collection<fi.dy.masa.litematica.schematic.LitematicaSchematic> getAllSchematics();
public void clearLoadedSchematics();
```

### schematic.LitematicaSchematic

Litematica 自有格式（.litematic）的投影对象，也支持读取 Sponge / Vanilla 结构格式。

```java
// （节选）
public static final java.lang.String FILE_EXTENSION;
public static final int SCHEMATIC_VERSION;

// 创建
public static fi.dy.masa.litematica.schematic.LitematicaSchematic createFromWorld(net.minecraft.class_1937, fi.dy.masa.litematica.selection.AreaSelection, fi.dy.masa.litematica.schematic.LitematicaSchematic$SchematicSaveInfo, java.lang.String, fi.dy.masa.malilib.interfaces.IStringConsumer);
public static fi.dy.masa.litematica.schematic.LitematicaSchematic createEmptySchematic(fi.dy.masa.litematica.selection.AreaSelection, java.lang.String);
public static fi.dy.masa.litematica.schematic.LitematicaSchematic createFromFile(java.io.File, java.lang.String);
public static fi.dy.masa.litematica.schematic.LitematicaSchematic createFromFile(java.io.File, java.lang.String, fi.dy.masa.litematica.util.FileType);
public static fi.dy.masa.litematica.schematic.LitematicaSchematic createFromFile(java.nio.file.Path, java.lang.String, fi.dy.masa.litematica.util.FileType);
public fi.dy.masa.litematica.schematic.LitematicaSchematic(java.nio.file.Path, net.minecraft.class_2487, fi.dy.masa.litematica.util.FileType); // class_2487 = CompoundTag

// 基本信息与子区域
public fi.dy.masa.litematica.schematic.SchematicMetadata getMetadata();
public int getSubRegionCount();
public net.minecraft.class_2338 getSubRegionPosition(java.lang.String);
public java.util.Map<java.lang.String, net.minecraft.class_2338> getAreaPositions();
public java.util.Map<java.lang.String, net.minecraft.class_2338> getAreaSizes();
public java.util.Map<java.lang.String, fi.dy.masa.litematica.selection.Box> getAreas();
public net.minecraft.class_2382 getTotalSize();   // class_2382 = Vec3i
public static boolean isSizeValid(net.minecraft.class_2382);

// 读写与放置
public boolean writeToFile(java.io.File, java.lang.String, boolean);
public boolean writeToFile(java.io.File, java.lang.String, boolean, boolean);
public boolean readFromFile();
public boolean placeToWorld(net.minecraft.class_1937, fi.dy.masa.litematica.schematic.placement.SchematicPlacement, boolean);
public net.minecraft.class_2487 writeToNBT();
public static net.minecraft.class_2487 readNbtFromFile(java.io.File);
public static fi.dy.masa.litematica.schematic.SchematicMetadata readMetadataFromFile(java.io.File, java.lang.String);
```

### schematic.SchematicMetadata

投影的元数据（名称、作者、体积、时间戳、版本等）。

```java
// （节选）
public java.lang.String getName();
public java.lang.String getAuthor();
public java.lang.String getDescription();
public int getRegionCount();
public int getTotalVolume();
public int getTotalBlocks();
public int getEntityCount();
public net.minecraft.class_2382 getEnclosingSize();
public net.minecraft.class_2338 getEnclosingSizeAsBlockPos();
public long getTimeCreated();
public long getTimeModified();
public int getSchematicVersion();
public int getMinecraftDataVersion();
public java.lang.String getMinecraftVersion();
public boolean wasModifiedSinceSaved();
public void setName(java.lang.String);
public void setAuthor(java.lang.String);
public void setDescription(java.lang.String);
public void setTimeModifiedToNow();
```

## 1.3 投影放置

### schematic.placement.SchematicPlacement

一次投影在世界中的放置实例（原点、旋转、镜像、子区域状态），由 `SchematicPlacementManager` 管理。

```java
// （节选）
// 静态工厂
public static fi.dy.masa.litematica.schematic.placement.SchematicPlacement createFor(fi.dy.masa.litematica.schematic.LitematicaSchematic, net.minecraft.class_2338, java.lang.String, boolean, boolean);
public static fi.dy.masa.litematica.schematic.placement.SchematicPlacement createTemporary(fi.dy.masa.litematica.schematic.LitematicaSchematic, net.minecraft.class_2338);

// 状态
public boolean isEnabled();
public boolean isRenderingEnabled();
public boolean isLocked();
public void setEnabled(boolean);
public void toggleEnabled();
public void toggleLocked();
public void setRenderSchematic(boolean);
public boolean ignoreEntities();

// 几何属性
public net.minecraft.class_2338 getOrigin();          // class_2338 = BlockPos
public fi.dy.masa.litematica.schematic.placement.SchematicPlacement setOrigin(net.minecraft.class_2338, fi.dy.masa.malilib.interfaces.IStringConsumer);
public net.minecraft.class_2470 getRotation();        // class_2470 = BlockRotation
public fi.dy.masa.litematica.schematic.placement.SchematicPlacement setRotation(net.minecraft.class_2470, fi.dy.masa.malilib.gui.interfaces.IMessageConsumer);
public net.minecraft.class_2415 getMirror();          // class_2415 = BlockMirror
public fi.dy.masa.litematica.schematic.placement.SchematicPlacement setMirror(net.minecraft.class_2415, fi.dy.masa.malilib.gui.interfaces.IMessageConsumer);
public java.lang.String getName();
public fi.dy.masa.litematica.schematic.LitematicaSchematic getSchematic();
public java.util.UUID getHashId();
public int getSubRegionCount();

// 子区域操作
public fi.dy.masa.litematica.schematic.placement.SubRegionPlacement getRelativeSubRegionPlacement(java.lang.String);
public java.util.Collection<fi.dy.masa.litematica.schematic.placement.SubRegionPlacement> getAllSubRegionsPlacements();
public com.google.common.collect.ImmutableMap<java.lang.String, fi.dy.masa.litematica.schematic.placement.SubRegionPlacement> getEnabledRelativeSubRegionPlacements();
public com.google.common.collect.ImmutableMap<java.lang.String, fi.dy.masa.litematica.selection.Box> getSubRegionBoxes(fi.dy.masa.litematica.schematic.placement.SubRegionPlacement$RequiredEnabled);
public void moveSubRegionTo(java.lang.String, net.minecraft.class_2338, fi.dy.masa.malilib.interfaces.IStringConsumer);
public void setSubRegionRotation(java.lang.String, net.minecraft.class_2470, fi.dy.masa.malilib.gui.interfaces.IMessageConsumer);
public void setSubRegionMirror(java.lang.String, net.minecraft.class_2415, fi.dy.masa.malilib.gui.interfaces.IMessageConsumer);
public void toggleSubRegionEnabled(java.lang.String, fi.dy.masa.malilib.gui.interfaces.IMessageConsumer);
public void resetAllSubRegionsToSchematicValues(fi.dy.masa.malilib.interfaces.IStringConsumer);

// 区块与材料
public java.util.Set<net.minecraft.class_1923> getTouchedChunks();   // class_1923 = ChunkPos
public com.google.common.collect.ImmutableMap<java.lang.String, fi.dy.masa.malilib.util.IntBoundingBox> getBoxesWithinChunk(int, int);
public fi.dy.masa.litematica.materials.MaterialListBase getMaterialList();
public fi.dy.masa.litematica.schematic.verifier.SchematicVerifier getSchematicVerifier();

// 序列化与移除
public void onRemoved();
public com.google.gson.JsonObject toJson();
public static fi.dy.masa.litematica.schematic.placement.SchematicPlacement fromJson(com.google.gson.JsonObject);
```

> 注：源码中存在公开方法 `getEclosingBox()`（拼写即如此，返回包围盒），如需调用请按原拼写使用。

### schematic.placement.SchematicPlacementManager

全部放置实例的管理器（增删、粘贴到世界、区块重建调度、选区切换）。通过 `DataManager.getSchematicPlacementManager()` 获取。

```java
// （节选）
public java.util.List<fi.dy.masa.litematica.schematic.placement.SchematicPlacement> getAllSchematicsPlacements();
public void addSchematicPlacement(fi.dy.masa.litematica.schematic.placement.SchematicPlacement, boolean);
public boolean removeSchematicPlacement(fi.dy.masa.litematica.schematic.placement.SchematicPlacement);
public boolean removeSchematicPlacement(fi.dy.masa.litematica.schematic.placement.SchematicPlacement, boolean);
public java.util.List<fi.dy.masa.litematica.schematic.placement.SchematicPlacement> getAllPlacementsOfSchematic(fi.dy.masa.litematica.schematic.LitematicaSchematic);
public void removeAllPlacementsOfSchematic(fi.dy.masa.litematica.schematic.LitematicaSchematic);
public fi.dy.masa.litematica.schematic.placement.SchematicPlacement getSelectedSchematicPlacement();
public void setSelectedSchematicPlacement(fi.dy.masa.litematica.schematic.placement.SchematicPlacement);

// 粘贴到世界
public void pasteCurrentPlacementToWorld(net.minecraft.class_310);   // class_310 = Minecraft
public void pastePlacementToWorld(fi.dy.masa.litematica.schematic.placement.SchematicPlacement, net.minecraft.class_310);

// 区块重建
public void markChunksForRebuild(fi.dy.masa.litematica.schematic.placement.SchematicPlacement);
public void markChunkForRebuild(net.minecraft.class_1923);
public boolean hasPendingRebuilds();
public boolean hasQueuedChunks();
public void processQueuedChunks();

// 其他
public boolean changeSelection(net.minecraft.class_1937, net.minecraft.class_1297, int);  // class_1297 = Entity
public void clear();
```

### schematic.placement.SubRegionPlacement

放置中单个子区域的相对位置/旋转/镜像状态。

```java
// （节选）
public fi.dy.masa.litematica.schematic.placement.SubRegionPlacement(net.minecraft.class_2338, java.lang.String);
public java.lang.String getName();
public net.minecraft.class_2338 getPos();
public net.minecraft.class_2338 getDefaultPos();
public net.minecraft.class_2470 getRotation();
public net.minecraft.class_2415 getMirror();
public boolean isEnabled();
public boolean isRenderingEnabled();
public void setRenderingEnabled(boolean);
public void toggleRenderingEnabled();
public boolean ignoreEntities();
public boolean isRegionPlacementModifiedFromDefault();
public boolean matchesRequirement(fi.dy.masa.litematica.schematic.placement.SubRegionPlacement$RequiredEnabled);
```

## 1.4 选区

### selection.SelectionManager

选区系统总管理器（普通/简单模式、选区文件加载保存、元素抓取移动）。通过 `DataManager.getSelectionManager()` 获取。

```java
// （节选）
public fi.dy.masa.litematica.selection.SelectionMode getSelectionMode();
public void switchSelectionMode();
public fi.dy.masa.litematica.selection.AreaSelection getCurrentSelection();
public fi.dy.masa.litematica.selection.AreaSelection getSelection(java.lang.String);
public fi.dy.masa.litematica.selection.AreaSelection getOrLoadSelection(java.lang.String);
public java.lang.String getCurrentSelectionId();
public boolean hasNormalSelection();
public void setCurrentSelection(java.lang.String);
public java.lang.String createNewSelection(java.io.File, java.lang.String);
public boolean createNewSubRegion(net.minecraft.class_310, boolean);
public boolean removeSelection(java.lang.String);
public boolean renameSelection(java.lang.String, java.lang.String, fi.dy.masa.malilib.gui.interfaces.IMessageConsumer);
public boolean hasSelectedElement();
public void moveSelectedElement(net.minecraft.class_2350, int);   // class_2350 = Direction
public boolean hasGrabbedElement();
public boolean grabElement(net.minecraft.class_310, int);
public void releaseGrabbedElement();
public static fi.dy.masa.litematica.selection.AreaSelection tryLoadSelectionFromFile(java.io.File);
public void clear();
```

### selection.AreaSelection

一个命名选区（多个子区域盒 + 可选显式原点），即创建投影时的数据来源。

```java
// （节选）
public fi.dy.masa.litematica.selection.AreaSelection();
public static fi.dy.masa.litematica.selection.AreaSelection fromPlacement(fi.dy.masa.litematica.schematic.placement.SchematicPlacement);
public java.lang.String getName();
public void setName(java.lang.String);
public net.minecraft.class_2338 getEffectiveOrigin();
public net.minecraft.class_2338 getExplicitOrigin();
public void setExplicitOrigin(net.minecraft.class_2338);
public fi.dy.masa.litematica.selection.Box getSelectedSubRegionBox();
public fi.dy.masa.litematica.selection.Box getSubRegionBox(java.lang.String);
public java.util.Collection<java.lang.String> getAllSubRegionNames();
public java.util.List<fi.dy.masa.litematica.selection.Box> getAllSubRegionBoxes();
public com.google.common.collect.ImmutableMap<java.lang.String, fi.dy.masa.litematica.selection.Box> getAllSubRegions();
public java.lang.String createNewSubRegionBox(net.minecraft.class_2338, java.lang.String);
public boolean addSubRegionBox(fi.dy.masa.litematica.selection.Box, boolean);
public boolean removeSubRegionBox(java.lang.String);
public boolean removeSelectedSubRegionBox();
public void removeAllSubRegionBoxes();
public boolean renameSubRegionBox(java.lang.String, java.lang.String);
public void moveEntireSelectionTo(net.minecraft.class_2338, boolean);
public void moveSelectedElement(net.minecraft.class_2350, int);
public void setSubRegionCornerPos(fi.dy.masa.litematica.selection.Box, fi.dy.masa.litematica.util.PositionUtils$Corner, net.minecraft.class_2338);
public net.minecraft.class_2338 getSubRegionCornerPos(fi.dy.masa.litematica.selection.Box, fi.dy.masa.litematica.util.PositionUtils$Corner);
public fi.dy.masa.litematica.selection.AreaSelection copy();
```

### selection.AreaSelectionSimple

简化版选区（单一子区域盒，原点固定为 pos1），`DataManager.getSimpleArea()` 返回此类型，继承自 `AreaSelection`。

```java
public fi.dy.masa.litematica.selection.AreaSelectionSimple(boolean);
public fi.dy.masa.litematica.selection.AreaSelectionSimple copy();
public java.lang.String createNewSubRegionBox(net.minecraft.class_2338, java.lang.String);
// 其余（addSubRegionBox/removeSubRegionBox/removeSelectedSubRegionBox/setSelectedSubRegionBox/removeAllSubRegionBoxes）为对父类的覆写，签名见 AreaSelection
```

### selection.Box

一个子区域盒（pos1/pos2 两角 + 名称）。

```java
public fi.dy.masa.litematica.selection.Box();
public fi.dy.masa.litematica.selection.Box(net.minecraft.class_2338, net.minecraft.class_2338, java.lang.String);
public net.minecraft.class_2338 getPos1();
public net.minecraft.class_2338 getPos2();
public net.minecraft.class_2338 getSize();
public java.lang.String getName();
public void setName(java.lang.String);
public void setPos1(net.minecraft.class_2338);
public void setPos2(net.minecraft.class_2338);
public fi.dy.masa.litematica.util.PositionUtils$Corner getSelectedCorner();
public void setSelectedCorner(fi.dy.masa.litematica.util.PositionUtils$Corner);
public net.minecraft.class_2338 getPosition(fi.dy.masa.litematica.util.PositionUtils$Corner);
public int getCoordinate(fi.dy.masa.litematica.util.PositionUtils$Corner, fi.dy.masa.malilib.util.PositionUtils$CoordinateType);
public void setCoordinate(int, fi.dy.masa.litematica.util.PositionUtils$Corner, fi.dy.masa.malilib.util.PositionUtils$CoordinateType);
public fi.dy.masa.litematica.selection.Box copy();
```

## 1.5 工具模式与材料清单

### tool.ToolMode

Litematica 权杖/工具模式枚举。

```java
public static final fi.dy.masa.litematica.tool.ToolMode AREA_SELECTION;
public static final fi.dy.masa.litematica.tool.ToolMode SCHEMATIC_PLACEMENT;
public static final fi.dy.masa.litematica.tool.ToolMode FILL;
public static final fi.dy.masa.litematica.tool.ToolMode REPLACE_BLOCK;
public static final fi.dy.masa.litematica.tool.ToolMode PASTE_SCHEMATIC;
public static final fi.dy.masa.litematica.tool.ToolMode GRID_PASTE;
public static final fi.dy.masa.litematica.tool.ToolMode MOVE;
public static final fi.dy.masa.litematica.tool.ToolMode DELETE;
public static final fi.dy.masa.litematica.tool.ToolMode REBUILD;

public static fi.dy.masa.litematica.tool.ToolMode[] values();
public static fi.dy.masa.litematica.tool.ToolMode valueOf(java.lang.String);
public java.lang.String getName();
public fi.dy.masa.litematica.tool.ToolMode cycle(net.minecraft.class_1657, boolean);   // class_1657 = PlayerEntity
public boolean getUsesSchematic();
public boolean getUsesAreaSelection();
public boolean getUsesBlockPrimary();
public boolean getUsesBlockSecondary();
public net.minecraft.class_2680 getPrimaryBlock();   // class_2680 = BlockState
public void setPrimaryBlock(net.minecraft.class_2680);
public net.minecraft.class_2680 getSecondaryBlock();
public void setSecondaryBlock(net.minecraft.class_2680);
```

### materials.MaterialListBase

材料清单抽象基类（统计所需/已有/缺失方块）。`MaterialListPlacement`、`MaterialListSchematic` 等为其子类；可经 `SchematicPlacement.getMaterialList()` 或 `DataManager.getMaterialList()` 获取。

```java
// （节选）public abstract class MaterialListBase implements IMaterialList
public abstract java.lang.String getName();
public abstract java.lang.String getTitle();
public com.google.common.collect.ImmutableList<fi.dy.masa.litematica.materials.MaterialListEntry> getMaterialsAll();
public java.util.List<fi.dy.masa.litematica.materials.MaterialListEntry> getMaterialsFiltered(boolean);
public java.util.List<fi.dy.masa.litematica.materials.MaterialListEntry> getMaterialsMissingOnly(boolean);
public abstract void reCreateMaterialList();
public void recreateFilteredList();
public void updateCounts();
public long getCountTotal();
public long getCountMissing();
public long getCountMismatched();
public int getMultiplier();
public void setMultiplier(int);
public boolean getHideAvailable();
public void setHideAvailable(boolean);
public void ignoreEntry(fi.dy.masa.litematica.materials.MaterialListEntry);
public void clearIgnored();
public fi.dy.masa.litematica.materials.MaterialListHudRenderer getHudRenderer();
```

## 1.6 世界与工具类

### world.WorldSchematic

Litematica 的**投影虚拟世界**（独立于原版客户端世界的方块快照世界）。

> ⚠️ 候选清单中的 `world.SchematicWorld` 在本版本**不存在**，真实类名为 `world.WorldSchematic`；入口为 `fi.dy.masa.litematica.world.SchematicWorldHandler`（未在候选清单内，未展开）。

```java
// （节选）该类继承 net.minecraft.class_1937（Level），大量覆写方法在 jar 中即为 intermediary 名 method_XXXX
public fi.dy.masa.litematica.world.WorldSchematic(net.minecraft.class_5269, net.minecraft.class_5455, net.minecraft.class_6880<net.minecraft.class_2874>, java.util.function.Supplier<net.minecraft.class_3695>, fi.dy.masa.litematica.render.schematic.WorldRendererSchematic);
public fi.dy.masa.litematica.world.ChunkManagerSchematic getChunkManager();
public fi.dy.masa.litematica.world.ChunkManagerSchematic getChunkProvider();
public fi.dy.masa.litematica.world.ChunkSchematic getChunk(int, int);
public java.util.List<fi.dy.masa.litematica.world.ChunkSchematic> getChunksWithinBox(net.minecraft.class_238);   // class_238 = AABB
public int getRegularEntityCount();
public void scheduleChunkRenders(int, int);
```

### util.PositionUtils

位置/盒子/旋转镜像相关的静态工具集。

```java
// （节选）
public static final fi.dy.masa.litematica.util.PositionUtils$BlockPosComparator BLOCK_POS_COMPARATOR;

public static net.minecraft.class_2338 getMinCorner(net.minecraft.class_2338, net.minecraft.class_2338);
public static net.minecraft.class_2338 getMaxCorner(net.minecraft.class_2338, net.minecraft.class_2338);
public static boolean isPositionInsideArea(net.minecraft.class_2338, net.minecraft.class_2338, net.minecraft.class_2338);
public static net.minecraft.class_2338 getAreaSizeFromRelativeEndPosition(net.minecraft.class_2338);
public static net.minecraft.class_2338 getRelativeEndPositionFromAreaSize(net.minecraft.class_2382);
public static boolean isBoxValid(fi.dy.masa.litematica.selection.Box);
public static java.util.List<fi.dy.masa.litematica.selection.Box> getValidBoxes(fi.dy.masa.litematica.selection.AreaSelection);
public static net.minecraft.class_2338 getEnclosingAreaSize(fi.dy.masa.litematica.selection.AreaSelection);
public static net.minecraft.class_2338 getEnclosingAreaSize(java.util.Collection<fi.dy.masa.litematica.selection.Box>);
public static int getTotalVolume(java.util.Collection<fi.dy.masa.litematica.selection.Box>);
public static net.minecraft.class_2338 getTransformedBlockPos(net.minecraft.class_2338, net.minecraft.class_2415, net.minecraft.class_2470);
public static net.minecraft.class_2338 getReverseTransformedBlockPos(net.minecraft.class_2338, net.minecraft.class_2415, net.minecraft.class_2470);
public static net.minecraft.class_2338 getModifiedPosition(net.minecraft.class_2338, int, fi.dy.masa.malilib.util.PositionUtils$CoordinateType);
public static int getCoordinate(net.minecraft.class_2338, fi.dy.masa.malilib.util.PositionUtils$CoordinateType);
public static net.minecraft.class_238 createEnclosingAABB(net.minecraft.class_2338, net.minecraft.class_2338);
public static net.minecraft.class_2470 cycleRotation(net.minecraft.class_2470, boolean);
public static net.minecraft.class_2415 cycleMirror(net.minecraft.class_2415, boolean);
public static java.lang.String getRotationNameShort(net.minecraft.class_2470);
public static java.lang.String getMirrorName(net.minecraft.class_2415);
public static fi.dy.masa.litematica.selection.Box growOrShrinkBox(fi.dy.masa.litematica.selection.Box, int);
```

### util.SchematicUtils

投影编辑/保存相关的高层操作（保存选区为投影、投影内方块编辑、选区克隆等）。

```java
// （节选）
public static boolean saveSchematic(boolean);
public static boolean saveAreaSelectionToSchematic(fi.dy.masa.litematica.selection.AreaSelection, net.minecraft.class_1937);
public static void unloadCurrentlySelectedSchematic();
public static void cloneSelectionArea(net.minecraft.class_310);
public static boolean breakSchematicBlock(net.minecraft.class_310);
public static boolean breakSchematicBlocks(net.minecraft.class_310);
public static boolean placeSchematicBlock(net.minecraft.class_310);
public static boolean placeSchematicBlocksInDirection(net.minecraft.class_310);
public static boolean replaceSchematicBlocksInDirection(net.minecraft.class_310);
public static boolean fillAirWithBlocks(net.minecraft.class_310);
public static void moveCurrentlySelectedWorldRegionTo(net.minecraft.class_2338, net.minecraft.class_310);
public static net.minecraft.class_2680 getUntransformedBlockState(net.minecraft.class_2680, fi.dy.masa.litematica.schematic.placement.SchematicPlacement, java.lang.String);
```

### util.RayTraceUtils

射线检测工具：对原版世界与投影虚拟世界联合取最近交点等。

```java
// （节选）
public static net.minecraft.class_2338 getSchematicWorldTraceIfClosest(net.minecraft.class_1937, net.minecraft.class_1297, double);
public static fi.dy.masa.litematica.util.RayTraceUtils$RayTraceWrapper getSchematicWorldTraceWrapperIfClosest(net.minecraft.class_1937, net.minecraft.class_1297, double);
public static net.minecraft.class_2338 getFurthestSchematicWorldBlockBeforeVanilla(net.minecraft.class_1937, net.minecraft.class_1297, double, boolean);
public static fi.dy.masa.litematica.util.RayTraceUtils$RayTraceWrapper getGenericTrace(net.minecraft.class_1937, net.minecraft.class_1297, double);
public static fi.dy.masa.litematica.util.RayTraceUtils$RayTraceWrapper getWrappedRayTraceFromEntity(net.minecraft.class_1937, net.minecraft.class_1297, double);
public static net.minecraft.class_2338 getTargetedPosition(net.minecraft.class_1937, net.minecraft.class_1297, double, boolean);
public static net.minecraft.class_3965 traceToSchematicWorld(net.minecraft.class_1297, double, boolean, boolean);   // class_3965 = BlockHitResult
public static net.minecraft.class_239 getRayTraceFromEntity(net.minecraft.class_1937, net.minecraft.class_1297, boolean);
public static double squareDistanceTo(net.minecraft.class_243, net.minecraft.class_243);
```

---

# 第二章 MaLiLib（fi.dy.masa.malilib）

## 2.1 配置系统

### config.options.ConfigBoolean

布尔配置项，构造参数依次为：名称、默认值、注释翻译键、（可选）简单显示名/ prettyName。

```java
public fi.dy.masa.malilib.config.options.ConfigBoolean(java.lang.String, boolean);
public fi.dy.masa.malilib.config.options.ConfigBoolean(java.lang.String, boolean, java.lang.String);
public fi.dy.masa.malilib.config.options.ConfigBoolean(java.lang.String, boolean, java.lang.String, java.lang.String);
public fi.dy.masa.malilib.config.options.ConfigBoolean(java.lang.String, boolean, java.lang.String, java.lang.String, java.lang.String);
public boolean getBooleanValue();
public boolean getDefaultBooleanValue();
public void setBooleanValue(boolean);
public boolean isModified();
public void resetToDefault();
public java.lang.String getStringValue();
public void setValueFromString(java.lang.String);
public void setValueFromJsonElement(com.google.gson.JsonElement);
public com.google.gson.JsonElement getAsJsonElement();
```

### config.options.ConfigInteger

整数配置项，可指定 min/max 并启用滑块。

```java
// （节选）
public fi.dy.masa.malilib.config.options.ConfigInteger(java.lang.String, int);
public fi.dy.masa.malilib.config.options.ConfigInteger(java.lang.String, int, java.lang.String);
public fi.dy.masa.malilib.config.options.ConfigInteger(java.lang.String, int, int, int);          // name, defaultValue, min, max
public fi.dy.masa.malilib.config.options.ConfigInteger(java.lang.String, int, int, int, java.lang.String);
public fi.dy.masa.malilib.config.options.ConfigInteger(java.lang.String, int, int, int, boolean); // 最后的 boolean = useSlider
public int getIntegerValue();
public int getDefaultIntegerValue();
public void setIntegerValue(int);
public int getMinIntegerValue();
public int getMaxIntegerValue();
public boolean shouldUseSlider();
public void toggleUseSlider();
public boolean isModified();
public void resetToDefault();
public java.lang.String getStringValue();
public void setValueFromString(java.lang.String);
```

### config.options.ConfigDouble

双精度浮点配置项，API 形态与 `ConfigInteger` 一致。

```java
// （节选）
public fi.dy.masa.malilib.config.options.ConfigDouble(java.lang.String, double);
public fi.dy.masa.malilib.config.options.ConfigDouble(java.lang.String, double, java.lang.String);
public fi.dy.masa.malilib.config.options.ConfigDouble(java.lang.String, double, double, double);          // name, defaultValue, min, max
public fi.dy.masa.malilib.config.options.ConfigDouble(java.lang.String, double, double, double, boolean); // 最后的 boolean = useSlider
public double getDoubleValue();
public double getDefaultDoubleValue();
public void setDoubleValue(double);
public double getMinDoubleValue();
public double getMaxDoubleValue();
public boolean shouldUseSlider();
public void toggleUseSlider();
public boolean isModified();
public void resetToDefault();
public java.lang.String getStringValue();
public void setValueFromString(java.lang.String);
```

### config.options.ConfigString

字符串配置项。

```java
public fi.dy.masa.malilib.config.options.ConfigString(java.lang.String, java.lang.String);
public fi.dy.masa.malilib.config.options.ConfigString(java.lang.String, java.lang.String, java.lang.String);
public fi.dy.masa.malilib.config.options.ConfigString(java.lang.String, java.lang.String, java.lang.String, java.lang.String);
public fi.dy.masa.malilib.config.options.ConfigString(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String);
public java.lang.String getStringValue();
public java.lang.String getDefaultStringValue();
public java.lang.String getOldStringValue();
public void setValueFromString(java.lang.String);
public void resetToDefault();
public boolean isModified();
```

### config.options.ConfigOptionList

下拉选项列表配置项，值类型为实现 `IConfigOptionListEntry` 的枚举。

```java
public fi.dy.masa.malilib.config.options.ConfigOptionList(java.lang.String, fi.dy.masa.malilib.config.IConfigOptionListEntry);
public fi.dy.masa.malilib.config.options.ConfigOptionList(java.lang.String, fi.dy.masa.malilib.config.IConfigOptionListEntry, java.lang.String);
public fi.dy.masa.malilib.config.IConfigOptionListEntry getOptionListValue();
public fi.dy.masa.malilib.config.IConfigOptionListEntry getDefaultOptionListValue();
public void setOptionListValue(fi.dy.masa.malilib.config.IConfigOptionListEntry);
public boolean isModified();
public void resetToDefault();
public java.lang.String getStringValue();
public void setValueFromString(java.lang.String);
```

### config.options.ConfigHotkey

热键配置项（配置值 + 按键合体），推荐作为自定义热键的标准载体。

```java
public fi.dy.masa.malilib.config.options.ConfigHotkey(java.lang.String, java.lang.String);                       // name, defaultStorageString
public fi.dy.masa.malilib.config.options.ConfigHotkey(java.lang.String, java.lang.String, java.lang.String);     // + commentTranslationKey
public fi.dy.masa.malilib.config.options.ConfigHotkey(java.lang.String, java.lang.String, fi.dy.masa.malilib.hotkeys.KeybindSettings);
public fi.dy.masa.malilib.hotkeys.IKeybind getKeybind();
public java.lang.String getStringValue();
public void setValueFromString(java.lang.String);
public boolean isModified();
public void resetToDefault();
```

### config.ConfigUtils

配置 JSON 读写批量工具。

```java
public static void readConfigBase(com.google.gson.JsonObject, java.lang.String, java.util.List<? extends fi.dy.masa.malilib.config.IConfigBase>);
public static void writeConfigBase(com.google.gson.JsonObject, java.lang.String, java.util.List<? extends fi.dy.masa.malilib.config.IConfigBase>);
public static void readHotkeys(com.google.gson.JsonObject, java.lang.String, java.util.List<? extends fi.dy.masa.malilib.hotkeys.IHotkey>);
public static void writeHotkeys(com.google.gson.JsonObject, java.lang.String, java.util.List<? extends fi.dy.masa.malilib.hotkeys.IHotkey>);
public static void readHotkeyToggleOptions(com.google.gson.JsonObject, java.lang.String, java.lang.String, java.util.List<? extends fi.dy.masa.malilib.config.IHotkeyTogglable>);
public static void writeHotkeyToggleOptions(com.google.gson.JsonObject, java.lang.String, java.lang.String, java.util.List<? extends fi.dy.masa.malilib.config.IHotkeyTogglable>);
```

## 2.2 按键输入

> ⚠️ 候选清单原列于 `input` 包，本版本 `input` 包**不存在**，按键类实际位于 `hotkeys` 包（老版本迁移后保留）。

### hotkeys.KeybindMulti

多键组合按键绑定实现，实现 `IKeybind`。**无公开构造器**，须通过静态工厂或 `ConfigHotkey` 创建。

```java
// （节选）
public static fi.dy.masa.malilib.hotkeys.KeybindMulti fromStorageString(java.lang.String, fi.dy.masa.malilib.hotkeys.KeybindSettings);
public void setCallback(fi.dy.masa.malilib.hotkeys.IHotkeyCallback);
public fi.dy.masa.malilib.hotkeys.IHotkeyCallback getCallback();
public boolean isPressed();
public boolean isKeybindHeld();
public boolean updateIsPressed();
public boolean matches(int);
public boolean isValid();
public void addKey(int);
public void removeKey(int);
public void clearKeys();
public java.util.List<java.lang.Integer> getKeys();
public java.lang.String getKeysDisplayString();
public boolean overlaps(fi.dy.masa.malilib.hotkeys.IKeybind);
public boolean isModified();
public void resetToDefault();
public java.lang.String getStringValue();
public void setValueFromString(java.lang.String);
public fi.dy.masa.malilib.hotkeys.KeybindSettings getSettings();
public void setSettings(fi.dy.masa.malilib.hotkeys.KeybindSettings);

// 静态工具
public static int getKeyCode(net.minecraft.class_304);   // class_304 = KeyMapping
public static boolean isKeyDown(int);
public static java.lang.String getStorageStringForKeyCode(int);
public static java.lang.String getActiveKeysString();
public static void onKeyInputPre(int, int, int, int);
```

### hotkeys.KeybindSettings

按键触发行为设置（上下文、按下/松开触发、按键顺序敏感等），提供大量预设常量。

```java
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings DEFAULT;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings EXCLUSIVE;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings RELEASE;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings RELEASE_EXCLUSIVE;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings PRESS_ALLOWEXTRA;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings PRESS_NON_ORDER_SENSITIVE;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings INGAME_BOTH;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings MODIFIER_INGAME;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings MODIFIER_GUI;
public static final fi.dy.masa.malilib.hotkeys.KeybindSettings GUI;

public static fi.dy.masa.malilib.hotkeys.KeybindSettings create(fi.dy.masa.malilib.hotkeys.KeybindSettings$Context, fi.dy.masa.malilib.hotkeys.KeyAction, boolean, boolean, boolean, boolean);
public static fi.dy.masa.malilib.hotkeys.KeybindSettings create(fi.dy.masa.malilib.hotkeys.KeybindSettings$Context, fi.dy.masa.malilib.hotkeys.KeyAction, boolean, boolean, boolean, boolean, boolean);
public fi.dy.masa.malilib.hotkeys.KeybindSettings$Context getContext();
public fi.dy.masa.malilib.hotkeys.KeyAction getActivateOn();
public boolean getAllowEmpty();
public boolean getAllowExtraKeys();
public boolean isOrderSensitive();
public boolean isExclusive();
public boolean shouldCancel();
```

### hotkeys.KeyAction

按键动作枚举。

```java
public static final fi.dy.masa.malilib.hotkeys.KeyAction PRESS;
public static final fi.dy.masa.malilib.hotkeys.KeyAction RELEASE;
public static final fi.dy.masa.malilib.hotkeys.KeyAction BOTH;
public static fi.dy.masa.malilib.hotkeys.KeyAction[] values();
public static fi.dy.masa.malilib.hotkeys.KeyAction valueOf(java.lang.String);
public java.lang.String getStringValue();
public java.lang.String getDisplayName();
```

### hotkeys.IHotkeyCallback（回调接口）

热键触发回调，`KeybindMulti.setCallback` / `IHotkey` 使用。

```java
public abstract boolean onKeyAction(fi.dy.masa.malilib.hotkeys.KeyAction, fi.dy.masa.malilib.hotkeys.IKeybind);
```

## 2.3 事件

> `event` 包下**没有独立的事件对象类**，全部为"单例管理器 + 注册方法"模式；实际回调逻辑实现在 `fi.dy.masa.malilib.interfaces` 包下的监听器接口中（如 `IClientTickHandler`、`IRenderer`），一并列出如下。

### event.TickHandler

```java
public static fi.dy.masa.malilib.event.TickHandler getInstance();
public void registerClientTickHandler(fi.dy.masa.malilib.interfaces.IClientTickHandler);
public void onClientTick(net.minecraft.class_310);
```

对应监听器接口：

```java
public interface fi.dy.masa.malilib.interfaces.IClientTickHandler {
    public abstract void onClientTick(net.minecraft.class_310);   // class_310 = Minecraft
}
```

### event.WorldLoadHandler（实现 event.IWorldLoadManager）

```java
public static fi.dy.masa.malilib.event.IWorldLoadManager getInstance();
public void registerWorldLoadPreHandler(fi.dy.masa.malilib.interfaces.IWorldLoadListener);
public void unregisterWorldLoadPreHandler(fi.dy.masa.malilib.interfaces.IWorldLoadListener);
public void registerWorldLoadPostHandler(fi.dy.masa.malilib.interfaces.IWorldLoadListener);
public void unregisterWorldLoadPostHandler(fi.dy.masa.malilib.interfaces.IWorldLoadListener);
```

对应监听器接口（方法均为 default，按需覆写）：

```java
public interface fi.dy.masa.malilib.interfaces.IWorldLoadListener {
    public default void onWorldLoadImmutable(net.minecraft.class_5455$class_6890);
    public default void onWorldLoadPre(net.minecraft.class_638, net.minecraft.class_638, net.minecraft.class_310);   // class_638 = ClientLevel
    public default void onWorldLoadPost(net.minecraft.class_638, net.minecraft.class_638, net.minecraft.class_310);
}
```

### event.InputEventHandler（实现 hotkeys.IKeybindManager 与 hotkeys.IInputManager）

```java
public static fi.dy.masa.malilib.hotkeys.IKeybindManager getKeybindManager();
public static fi.dy.masa.malilib.hotkeys.IInputManager getInputManager();
public void registerKeybindProvider(fi.dy.masa.malilib.hotkeys.IKeybindProvider);
public void unregisterKeybindProvider(fi.dy.masa.malilib.hotkeys.IKeybindProvider);
public void registerKeyboardInputHandler(fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler);
public void registerMouseInputHandler(fi.dy.masa.malilib.hotkeys.IMouseInputHandler);
public void addHotkeysForCategory(java.lang.String, java.lang.String, java.util.List<? extends fi.dy.masa.malilib.hotkeys.IHotkey>);
```

对应接口 `IKeybindManager`：

```java
public abstract void registerKeybindProvider(fi.dy.masa.malilib.hotkeys.IKeybindProvider);
public abstract void unregisterKeybindProvider(fi.dy.masa.malilib.hotkeys.IKeybindProvider);
public abstract void addKeybindToMap(fi.dy.masa.malilib.hotkeys.IKeybind);
public abstract void addHotkeysForCategory(java.lang.String, java.lang.String, java.util.List<? extends fi.dy.masa.malilib.hotkeys.IHotkey>);
public abstract void updateUsedKeys();
public abstract java.util.List<fi.dy.masa.malilib.hotkeys.KeybindCategory> getKeybindCategories();
```

对应接口 `IKeybindProvider`（注册热键时实现）：

```java
public abstract void addKeysToMap(fi.dy.masa.malilib.hotkeys.IKeybindManager);
public abstract void addHotkeys(fi.dy.masa.malilib.hotkeys.IKeybindManager);
```

### event.RenderEventHandler（实现 malilib.interfaces.IRenderDispatcher）

```java
public static fi.dy.masa.malilib.interfaces.IRenderDispatcher getInstance();
public void registerGameOverlayRenderer(fi.dy.masa.malilib.interfaces.IRenderer);
public void registerTooltipLastRenderer(fi.dy.masa.malilib.interfaces.IRenderer);
public void registerWorldLastRenderer(fi.dy.masa.malilib.interfaces.IRenderer);
```

对应监听器接口 `IRenderer`（方法均为 default，按需覆写）：

```java
public default void onRenderWorldLast(org.joml.Matrix4f, org.joml.Matrix4f);
public default void onRenderGameOverlayLastDrawer(net.minecraft.class_332, float, net.minecraft.class_3695, net.minecraft.class_310);   // class_332 = GuiGraphics
public default void onRenderGameOverlayPost(net.minecraft.class_332);
public default void onRenderTooltipLast(net.minecraft.class_332, net.minecraft.class_1799, int, int);   // class_1799 = ItemStack
public default java.util.function.Supplier<java.lang.String> getProfilerSectionSupplier();
```

### event.ServerHandler（实现 event.IServerManager）

```java
public static fi.dy.masa.malilib.event.IServerManager getInstance();
public void registerServerHandler(fi.dy.masa.malilib.interfaces.IServerListener);
public void unregisterServerHandler(fi.dy.masa.malilib.interfaces.IServerListener);
public void onServerStarting(net.minecraft.server.MinecraftServer);
public void onServerStopping(net.minecraft.server.MinecraftServer);
```

### event.InitializationHandler

```java
public static fi.dy.masa.malilib.interfaces.IInitializationDispatcher getInstance();
public void registerInitializationHandler(fi.dy.masa.malilib.interfaces.IInitializationHandler);
public void onGameInitDone();
```

## 2.4 GUI

### gui.GuiBase

MaLiLib 全部 GUI 的基类（继承原版 Screen），提供按钮/文本框/控件装配与消息提示等能力。

```java
// （节选）
public static final java.lang.String TXT_WHITE;
public static final java.lang.String TXT_GRAY;
public static final java.lang.String TXT_RED;
public static final java.lang.String TXT_GREEN;
public static final java.lang.String TXT_YELLOW;
public static final java.lang.String TXT_BOLD;
public static final java.lang.String TXT_RST;
public final net.minecraft.class_310 mc;
public final net.minecraft.class_327 textRenderer;   // class_327 = Font

public fi.dy.masa.malilib.gui.GuiBase setParent(net.minecraft.class_437);   // class_437 = Screen
public net.minecraft.class_437 getParent();
public void initGui();
public <T extends fi.dy.masa.malilib.gui.button.ButtonBase> T addButton(T, fi.dy.masa.malilib.gui.button.IButtonActionListener);
public <T extends fi.dy.masa.malilib.gui.GuiTextFieldGeneric> fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper<T> addTextField(T, fi.dy.masa.malilib.gui.interfaces.ITextFieldListener<T>);
public <T extends fi.dy.masa.malilib.gui.widgets.WidgetBase> T addWidget(T);
public fi.dy.masa.malilib.gui.widgets.WidgetLabel addLabel(int, int, int, int, int, java.lang.String...);
public void addMessage(fi.dy.masa.malilib.gui.Message$MessageType, java.lang.String, java.lang.Object...);
public boolean onMouseClicked(int, int, int);
public boolean onMouseScrolled(int, int, double, double);
public boolean onKeyTyped(int, int, int);
public boolean onCharTyped(char, int);
public int getStringWidth(java.lang.String);
public void drawString(net.minecraft.class_332, java.lang.String, int, int, int);
public void drawStringWithShadow(net.minecraft.class_332, java.lang.String, int, int, int);

// 静态工具
public static void openGui(net.minecraft.class_437);
public static boolean isShiftDown();
public static boolean isCtrlDown();
public static boolean isAltDown();
public static boolean isMouseOver(int, int, int, int, int, int);
```

### gui.GuiConfigsBase

配置界面抽象基类（继承 `GuiListBase`），自定义模组配置界面继承它并实现回调方法即可获得 MaLiLib 风格的配置列表 GUI。

```java
// （节选）
public fi.dy.masa.malilib.gui.GuiConfigsBase(int, int, java.lang.String, net.minecraft.class_437, java.lang.String, java.lang.Object...);
public void initGui();
public fi.dy.masa.malilib.gui.GuiConfigsBase setConfigWidth(int);
public fi.dy.masa.malilib.gui.GuiConfigsBase setHoverInfoProvider(fi.dy.masa.malilib.gui.interfaces.IConfigInfoProvider);
public java.lang.String getModId();
public void clearOptions();
public void addKeybindChangeListener(java.lang.Runnable);
public void setActiveKeybindButton(fi.dy.masa.malilib.gui.button.ConfigButtonKeybind);
public fi.dy.masa.malilib.config.gui.ButtonPressDirtyListenerSimple getButtonPressListener();
```

## 2.5 工具类

### util.PositionUtils

坐标修改/视线方向/命中部位等静态工具（注意与 litematica 的同名类相互独立）。

```java
// （节选）
public static final net.minecraft.class_2350[] ALL_DIRECTIONS;
public static final net.minecraft.class_2350[] HORIZONTAL_DIRECTIONS;
public static final net.minecraft.class_2350[] VERTICAL_DIRECTIONS;

public static net.minecraft.class_2338 modifyValue(fi.dy.masa.malilib.util.PositionUtils$CoordinateType, net.minecraft.class_2338, int);
public static net.minecraft.class_243 modifyValue(fi.dy.masa.malilib.util.PositionUtils$CoordinateType, net.minecraft.class_243, double);
public static net.minecraft.class_2338 setValue(fi.dy.masa.malilib.util.PositionUtils$CoordinateType, net.minecraft.class_2338, int);
public static net.minecraft.class_243 setValue(fi.dy.masa.malilib.util.PositionUtils$CoordinateType, net.minecraft.class_243, double);
public static net.minecraft.class_2338 getEntityBlockPos(net.minecraft.class_1297);
public static net.minecraft.class_2350 getClosestLookingDirection(net.minecraft.class_1297);
public static net.minecraft.class_2350 getClosestLookingDirection(net.minecraft.class_1297, float);
public static net.minecraft.class_2338 getPositionInfrontOfEntity(net.minecraft.class_1297);
public static fi.dy.masa.malilib.util.PositionUtils$HitPart getHitPart(net.minecraft.class_2350, net.minecraft.class_2350, net.minecraft.class_2338, net.minecraft.class_243);
public static net.minecraft.class_2350 getTargetedDirection(net.minecraft.class_2350, net.minecraft.class_2350, net.minecraft.class_2338, net.minecraft.class_243);
```

### util.BlockUtils

方块状态属性解析工具（继承 `util.nbt.NbtBlockUtils`）。

```java
// （节选）
public static net.minecraft.class_2350 getFirstPropertyFacingValue(net.minecraft.class_2680);
public static net.minecraft.class_2350 getPropertyFacingValue(net.minecraft.class_2680);
public static net.minecraft.class_2350 getPropertyHorizontalFacingValue(net.minecraft.class_2680);
public static net.minecraft.class_2350 getPropertyHopperFacingValue(net.minecraft.class_2680);
public static java.util.List<java.lang.String> getFormattedBlockStateProperties(net.minecraft.class_2680);
public static java.util.List<java.lang.String> getFormattedBlockStateProperties(net.minecraft.class_2680, java.lang.String);
public static net.minecraft.class_6880<net.minecraft.class_2248> getBlockEntry(net.minecraft.class_2960, net.minecraft.class_5455);   // class_2248 = Block
```

> ⚠️ 候选清单中的 malilib `util.ItemUtils` 在本版本**不存在**（litematica 有自己的 `litematica.util.ItemUtils`，未列入候选清单故未展开），已跳过。

### util.StringUtils

字符串/翻译/文本渲染宽度等静态工具。

```java
// （节选）
public static java.lang.String translate(java.lang.String, java.lang.Object...);
public static net.minecraft.class_5250 translateable(java.lang.String);
public static net.minecraft.class_5250 translateable(java.lang.String, java.lang.Object...);
public static net.minecraft.class_2561 translateAsText(java.lang.String, java.lang.Object...);
public static boolean hasTranslation(java.lang.String);
public static java.lang.String getTranslatedOrFallback(java.lang.String, java.lang.String);
public static java.util.List<java.lang.String> translateAndLineSplit(java.lang.String, java.lang.Object...);
public static net.minecraft.class_2960 identifier(java.lang.String);
public static net.minecraft.class_2960 identifier(java.lang.String, java.lang.String);
public static java.lang.String getModVersionString(java.lang.String);
public static java.lang.String splitCamelCase(java.lang.String);
public static java.lang.String prettifyRawTranslationPath(java.lang.String);
public static int getStringWidth(java.lang.String);
public static int getFontHeight();
public static java.lang.String getClampedDisplayStringRenderlen(java.util.List<java.lang.String>, int, java.lang.String, java.lang.String);
public static java.lang.String getWorldOrServerName();
public static java.lang.String getPrettyFileSizeText(long, int);
```

### util.LayerRange

渲染层范围（单层/上下N层/区间，沿某坐标轴），Litematica 的层渲染模式即基于它。

```java
// （节选）
public fi.dy.masa.malilib.util.LayerRange(fi.dy.masa.malilib.interfaces.IRangeChangeListener);
public fi.dy.masa.malilib.util.LayerMode getLayerMode();
public net.minecraft.class_2350$class_2351 getAxis();   // Direction.Axis
public int getLayerSingle();
public int getLayerAbove();
public int getLayerBelow();
public int getLayerRangeMin();
public int getLayerRangeMax();
public int getLayerMin();
public int getLayerMax();
public int getCurrentLayerValue(boolean);
public void setLayerMode(fi.dy.masa.malilib.util.LayerMode);
public void setAxis(net.minecraft.class_2350$class_2351);
public void setLayerSingle(int);
public boolean setLayerRangeMin(int);
public boolean setLayerRangeMax(int);
public boolean moveLayer(int);
public boolean isPositionWithinRange(net.minecraft.class_2338);
public boolean isPositionWithinRange(int, int, int);
public int getClampedValue(int, net.minecraft.class_2350$class_2351);
public fi.dy.masa.malilib.util.IntBoundingBox getClampedRenderBoundingBox(fi.dy.masa.malilib.util.IntBoundingBox);
public fi.dy.masa.malilib.util.IntBoundingBox getClampedArea(net.minecraft.class_2338, net.minecraft.class_2338);
public java.lang.String getCurrentLayerString();
```

### render.RenderUtils

渲染静态工具（HUD 文本、方框描边、物品预览等）。

```java
// （节选）
public static void setupBlend();
public static void setupBlendSimple();
public static void bindTexture(net.minecraft.class_2960);
public static void color(float, float, float, float);
public static void drawRect(int, int, int, int, int);
public static void drawOutline(int, int, int, int, int);
public static void drawTexturedRect(int, int, int, int, int, int);
public static void drawCenteredString(int, int, int, java.lang.String, net.minecraft.class_332);
public static int renderText(int, int, double, int, int, fi.dy.masa.malilib.config.HudAlignment, boolean, boolean, java.util.List<java.lang.String>, net.minecraft.class_332);
public static int getHudPosY(int, int, int, double, fi.dy.masa.malilib.config.HudAlignment);
public static int getHudOffsetForPotions(fi.dy.masa.malilib.config.HudAlignment, double, net.minecraft.class_1657);
public static float[] getColorComponents(int);
public static void drawTextPlate(java.util.List<java.lang.String>, double, double, double, float);
public static void renderBlockOutline(net.minecraft.class_2338, float, float, fi.dy.masa.malilib.util.Color4f, net.minecraft.class_310);
public static void renderAreaOutline(net.minecraft.class_2338, net.minecraft.class_2338, float, fi.dy.masa.malilib.util.Color4f, fi.dy.masa.malilib.util.Color4f, fi.dy.masa.malilib.util.Color4f, net.minecraft.class_310);
public static void renderBlockTargetingOverlaySimple(net.minecraft.class_1297, net.minecraft.class_2338, net.minecraft.class_2350, fi.dy.masa.malilib.util.Color4f, org.joml.Matrix4f, net.minecraft.class_310);
public static void renderMapPreview(net.minecraft.class_1799, int, int, int);
public static void renderShulkerBoxPreview(net.minecraft.class_1799, int, int, boolean, net.minecraft.class_332);
```

---

# 第三章 快速上手

以下示例**仅使用本文档收录的真实签名**。示例代码处于 Loom 重映射环境，Minecraft 类直接写 Mojang 名（如 `Minecraft` = `class_310`、`BlockPos` = `class_2338`）。所有调用代码请放在 `src/client` 源集。

## 示例 1：注册客户端 Tick 事件

```java
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.tool.ToolMode;

public class MyClientTickHandler implements IClientTickHandler {
    @Override
    public void onClientTick(Minecraft mc) {   // 参数在 jar 中为 net.minecraft.class_310
        // 只有进入世界后各管理器才可用
        if (DataManager.getToolMode() == ToolMode.AREA_SELECTION) {
            // 每客户端 tick 的自定义逻辑
        }
    }
}

// 客户端初始化时注册：
TickHandler.getInstance().registerClientTickHandler(new MyClientTickHandler());
```

## 示例 2：定义并注册自定义热键（ConfigHotkey + 回调）

```java
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;

public class MyHotkeys implements IKeybindProvider {
    // 构造器签名：(String name, String defaultStorageString, String commentTranslationKey)
    public static final ConfigHotkey OPEN_GUI =
            new ConfigHotkey("myMod.openGui", "X", "myMod.hotkeys.openGui.comment");

    public MyHotkeys() {
        // 回调签名：boolean onKeyAction(KeyAction, IKeybind)
        OPEN_GUI.getKeybind().setCallback((IHotkeyCallback) (action, keybind) -> {
            if (action == KeyAction.PRESS) {
                GuiBase.openGui(new MyConfigGui());   // GuiBase.openGui(Screen)
                return true;
            }
            return false;
        });
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        manager.addKeybindToMap(OPEN_GUI.getKeybind());
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory("myMod", "myMod.hotkeys.category", List.of(OPEN_GUI));
    }
}

// 客户端初始化时注册：
InputEventHandler.getKeybindManager().registerKeybindProvider(new MyHotkeys());
```

## 示例 3：世界加载后读取选区并保存为投影

```java
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.interfaces.IWorldLoadListener;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.util.SchematicUtils;

public class MyWorldListener implements IWorldLoadListener {
    @Override
    public void onWorldLoadPost(ClientLevel world, ClientLevel oldWorld, Minecraft mc) {
        // onWorldLoadPost 在进入世界后触发，此时管理器已可用
        AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();
        if (selection != null) {
            // 签名：saveAreaSelectionToSchematic(AreaSelection, Level) 返回 boolean
            boolean saved = SchematicUtils.saveAreaSelectionToSchematic(selection, mc.level);
            if (saved) {
                SchematicUtils.unloadCurrentlySelectedSchematic(); // 需要时卸载
            }
        }
    }
}

// 客户端初始化时注册：
WorldLoadHandler.getInstance().registerWorldLoadPostHandler(new MyWorldListener());
```

## 附：候选类验证结果

| 候选类（任务清单） | 实际情况 |
|---|---|
| litematica `data.SchematicProjectsManager` | ✅ 存在，但实际包为 `schematic.projects` |
| litematica `world.SchematicWorld` | ❌ 不存在，实际为 `world.WorldSchematic`（已收录） |
| malilib `input.KeybindMulti` / `input.KeybindSettings` | ❌ `input` 包不存在，实际位于 `hotkeys` 包（已收录） |
| malilib `util.ItemUtils` | ❌ 不存在（malilib 无此类），跳过；另验证 `util.InventoryUtils` 存在但不在候选清单 |
| malilib `event` 包事件类 | 无具体事件对象，全部为 Handler 单例 + `malilib.interfaces` 监听器接口（已如实收录） |
| 其余全部候选类 | ✅ 存在且已收录 |
