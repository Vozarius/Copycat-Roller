# Target-Version API Research

The source code was inspected before implementation without transferring
mapped names from another Minecraft version.

## Pinned Sources

- Create 6 artifacts for Minecraft 1.21.1 from the official Create Maven.
- Copycats+ 3.0.4 for Minecraft 1.21.1: tag `v3.0.4+mc1.21.1`, commit
  `9f808ac0b437817696c50f2e8b57f590b1196094`, CurseForge file `7251823`.

## Create 6

Verified signatures:

```java
// RollerBlockEntity
protected boolean isValidMaterial(ItemStack newFilter);

// RollerMovementBehaviour
protected void triggerPaver(MovementContext context, BlockPos pos);
@Nullable
protected PaveTask createHeightProfileForTracks(MovementContext context);
protected PaveResult tryFill(
    MovementContext context,
    BlockPos targetPos,
    BlockState toPlace
);
public static BlockState getStateToPaveWith(ItemStack itemStack);
protected BlockState getStateToPaveWith(MovementContext context);
protected RollingMode getMode(MovementContext context);

// TrackPaverV2
public static void pave(
    PaveTask task,
    TrackGraph graph,
    TrackEdge edge,
    double from,
    double to
);
public static void paveStraight(
    PaveTask task,
    BlockPos startPos,
    Vec3 direction,
    int extent
);
public static void paveCurve(
    PaveTask task,
    BezierConnection connection,
    double from,
    double to
);

// PaveTask
public PaveTask(double h1, double h2);
public Couple<Double> getHorizontalInterval();
public void put(int x, int z, float y);
public float get(Couple<Integer> coords);
public Set<Couple<Integer>> keys();

// ItemHelper
public static ItemStack extract(
    IItemHandler inventory,
    Predicate<ItemStack> test,
    int exactAmount,
    boolean simulate
);

// MountedStorageManager
public CombinedInvWrapper getAllItems();
```

In this version, `MovementContext` exposes the public fields `world`, `state`,
`localPos`, `blockEntityData`, `data`, `contraption`, and `stall`. The main
mounted inventory is obtained through
`context.contraption.getStorage().getAllItems()`.

Decompilation of `tryFill` confirms that Create 6 extracts one item matching
the filter and places the state returned by `getStateToPaveWith(...)`. This
branch has no separate runtime bank or remainder-return mechanism for
converting one block into multiple partial blocks. Zinc compatibility
therefore performs its own atomic conversion directly in the mounted
inventory.

The first material-filling implementation injected into this same `tryFill`
method, but a non-Copycat target then fell back to ordinary Create paving and
could place unwanted full blocks. It also missed a Copycat in the adjacent
vertical cell selected by the fractional surface planner.

The current implementation deliberately does not resolve an ordinary or
third-party filter from its visible `ItemStack`. At the head of
`triggerPaver`, it uses Create's `PaveTask` for exact X/Z coverage and searches
a narrow Y band for the closest Copycat surface. It then invokes Create's real
`tryFill` transaction for each selected Copycat cell.

The protected Copycat is exposed as replaceable only for that transaction.
Create and third-party Mixins can therefore choose a position-specific block
and extract it from mounted storage exactly as they normally would. Immediately
before `Level.setBlockAndUpdate`, the addon compares the mounted inventory with
a component-sensitive snapshot, identifies the actual extracted `BlockItem`,
cancels the world replacement, and assigns that material to the Copycat.

For a multistate Copycat, the first extracted item is treated as prepaid.
The addon simulates and extracts the remaining exact per-part cost before
changing its block entity. If the full cost cannot be paid, all observed
extractions are returned and the Copycat remains unchanged. The original
`triggerPaver` then continues for ordinary paving.

Three narrowly scoped intercepts keep both operations compatible:

- the later ordinary `tryFill` attempt treats an already processed Copycat
  cell as `PASS`, without resolving or extracting the filter twice;
- if a full Copycat occupies Create's base target, that one base argument is
  changed to `copycatPos.below()`, allowing the original `tryFill` transaction
  to create a support block;
- `Level.setBlockAndUpdate` is intercepted only while the thread-local Roller
  probe targets the same level and position. All other world writes are
  untouched.

All other `tryFill` calls retain Create's original loaded-chunk, leaves,
replaceable-block, portal, mounted-inventory, state-placement, fill-depth, and
result handling.

Create: Randomize Filters 1.0.7 was inspected as a real interoperability case.
Its Roller Mixin returns a provisional state from `getStateToPaveWith`, chooses
the actual position-specific block while redirecting `ItemHelper.extract`
inside `tryFill`, and substitutes that selected state at
`Level.setBlockAndUpdate`. Capturing after extraction and before that final
write is therefore necessary; inspecting the custom filter or provisional
state alone would select the wrong material. Copycat Roller has no compile-time
or runtime dependency on Randomize Filters.

`RollingMode` is package-private and ordered as `TUNNEL_PAVE`,
`STRAIGHT_FILL`, `WIDE_FILL`. Placing a mixin class in Create's package causes
a JPMS split-package error, so ordinal `1` is isolated in `RollerModeGate`. A
GameTest compares the complete runtime enum order and fails if it changes.

### Profile Semantics

`createHeightProfileForTracks()` creates a `PaveTask`, passes the actual
`TrackEdge` instances to `TrackPaverV2.pave()`, and then adds
`context.localPos.getY()` to the Y value of every column.

The straight branch first calculates the edge position as a `double`, applies
the vertical offset (`1` for a slope and `0.5` for a level edge), and then
converts the result to a `BlockPos`. The curve branch traverses LUT segments,
selects the minimum Y for an X/Z cell covered more than once, and rounds Y to
a whole or half block before writing it to `PaveTask`.

The standard Roller interprets the profile value as the Y of the full base
block and adds a lower slab above it when the fractional part is approximately
`0.5`. In legacy mode, `surfaceOnly=false`, the addon preserves this layout.
In the default mode, the physical top surface is calculated as
`profileY + 1`; only its highest occupied cell is then selected, and the full
base block is not placed.

## Copycats+ 3.0.4

Verified elements:

```java
public static final BlockEntry<CopycatLayerBlock> COPYCAT_LAYER;
public static final BlockEntry<CopycatHalfLayerBlock> COPYCAT_HALF_LAYER;
public static final BlockEntry<CopycatSlopeLayerBlock> COPYCAT_SLOPE_LAYER;

public class CopycatLayerBlock
    extends CCWaterloggedCopycatBlock
    implements SpecialBlockItemRequirement, IStateType {
    public static final DirectionProperty FACING =
        BlockStateProperties.FACING;
    public static final IntegerProperty LAYERS =
        BlockStateProperties.LAYERS;

    public ItemRequirement getRequiredItems(
        BlockState state,
        BlockEntity blockEntity
    );
}

public class CopycatHalfLayerBlock
    extends WaterloggedMultiStateCopycatBlock
    implements SpecialBlockItemRequirement {
    public static final EnumProperty<Direction.Axis> AXIS =
        BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<Half> HALF =
        BlockStateProperties.HALF;
    public static final IntegerProperty POSITIVE_LAYERS =
        IntegerProperty.create("positive_layers", 0, 8);
    public static final IntegerProperty NEGATIVE_LAYERS =
        IntegerProperty.create("negative_layers", 0, 8);
}

public class CopycatSlopeLayerBlock
    extends CCWaterloggedCopycatBlock
    implements SpecialBlockItemRequirement, IStateType {
    public static final DirectionProperty FACING =
        BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF =
        BlockStateProperties.HALF;
    public static final IntegerProperty LAYERS =
        BlockStateProperties.LAYERS;
}

public class CCCopycatBlockEntity
    extends SmartBlockEntity
    implements ICopycatBlockEntity;

public class MultiStateCopycatBlockEntity
    extends SmartBlockEntity
    implements IMultiStateCopycatBlockEntity;

default void ICopycatBlockEntity.init();
default boolean ICopycatBlockEntity.hasCustomMaterial();
default void ICopycatBlockEntity.setMaterial(BlockState material);
default void ICopycatBlockEntity.setConsumedItem(ItemStack stack);
ItemStack ICopycatBlockEntity.getConsumedItem();

default BlockState ICopycatBlock.getAcceptedBlockState(
    Level level,
    BlockPos pos,
    ItemStack stack,
    Direction face
);

String IMultiStateCopycatBlock.defaultProperty();
Set<String> IMultiStateCopycatBlock.storageProperties();
boolean IMultiStateCopycatBlock.partExists(
    BlockState state,
    String property
);
BlockState IMultiStateCopycatBlock.getAcceptedBlockState(
    String property,
    Level level,
    BlockPos pos,
    ItemStack stack,
    Direction face
);

void IMultiStateCopycatBlockEntity.setMaterial(
    String property,
    BlockState material
);
void IMultiStateCopycatBlockEntity.setConsumedItem(
    String property,
    ItemStack stack
);

static ItemRequirement ICopycatBlock.getRequiredItemsForLayer(
    BlockState state,
    IntegerProperty property
);
```

`ICopycatBlockEntity.init()` sets the material to
`AllBlocks.COPYCAT_BASE.getDefaultState()`, clears `consumedItem`, and enables
connected textures. `hasCustomMaterial()` returns `false` specifically for
the standard `COPYCAT_BASE`.

`CopycatLayerBlock.getRequiredItems()` delegates to
`ICopycatBlock.getRequiredItemsForLayer(state, LAYERS)`. The implementation
creates exactly `LAYERS` requirements of type `CONSUME`, confirming a cost of
`layers=N -> N` items. The addon does not use
`SpecialBlockItemRequirement` at runtime; items are consumed transactionally
from the mounted inventory.

`CopycatSlopeLayerBlock.getRequiredItems()` uses the same function and
therefore also costs `LAYERS` items. `CopycatHalfLayerBlock` combines two
independent requirements:

```java
getRequiredItemsForLayer(state, POSITIVE_LAYERS)
    .union(getRequiredItemsForLayer(state, NEGATIVE_LAYERS));
```

Its total cost is `positive_layers + negative_layers`; a full `8 + 8` block
requires 16 `copycat_half_layer` items. Both half properties officially allow
`0..8`, although a new nonempty block always contains at least one half.

The JSON recipes inside the Copycats+ 3.0.4 JAR were verified:

```text
c:ingots/zinc -> 8 × copycats:copycat_layer
c:ingots/zinc -> 16 × copycats:copycat_half_layer
2 × copycats:copycat_half_layer -> 1 × copycats:copycat_layer
```

Create's registry confirms the exact item `create:zinc_ingot`. Half Layer is
therefore the smallest integral unit for a lossless material bank: one ingot
equals 16 units, a Half Layer costs one, and a standard Layer costs two.
Change is preserved as real Half Layer items without custom NBT or a hidden
addon counter.

`CCShapes.SLOPE_LAYER` confirms the exact edge heights of the lower shape:

```text
layers:     1    2    3    4    5    6    7    8
low edge:   0    0    0    0   1/4  1/2  3/4   1
high edge: 1/4  1/2  3/4   1    1    1    1    1
```

These exact edges are used by the addon's quality gate; it does not assume
eight parallel slopes.

Half Layer creates a `MultiStateCopycatBlockEntity`; the other two layer types
create a `CCCopycatBlockEntity`. Empty material is verified with the common
`ICopycatBlockEntity.hasCustomMaterial()` method. For a multistate block
entity, the addon additionally checks that
`MaterialItemStorage.getAllConsumedItems()` is empty.

For Roller material filling, `ICopycatBlock.getAcceptedBlockState(...)`
provides the same material validation used by normal player interaction. The
addon passes `Direction.UP`, matching a Roller approaching the Copycat from
above. A single-state Copycat stores one size-one consumed item. For a
multistate Copycat, only properties for which `partExists(...)` is true and
which still have the default material are changed; each such property stores
and consumes one item. Simulation and exact extraction complete before any
block entity mutation, and the original material storage is restored if
post-assignment verification fails.
