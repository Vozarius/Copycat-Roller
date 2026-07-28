# Исследование API целевых версий

Исходники проверены до реализации, без переноса mapped-имён из другой
версии Minecraft.

## Зафиксированные исходники

- Create 6.0.11: commit
  `87b3c6a65fd00c023a07b37b0353144bc7e6a5bf`, Maven artifact
  `create-1.21.1:6.0.11-295`.
- Copycats+ 3.0.4 для Minecraft 1.21.1: tag `v3.0.4+mc1.21.1`,
  commit `9f808ac0b437817696c50f2e8b57f590b1196094`, CurseForge file
  `7251823`.

## Create 6.0.11

Проверенные сигнатуры:

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

`MovementContext` в этой версии предоставляет публичные `world`, `state`,
`localPos`, `blockEntityData`, `data`, `contraption` и `stall`.
Основной mounted inventory получается через
`context.contraption.getStorage().getAllItems()`.

Декомпиляция `tryFill` подтверждает, что Create 6.0.11 извлекает один
предмет, совпавший с фильтром, и устанавливает возвращённый
`getStateToPaveWith(...)` state. Отдельного runtime-банка либо механизма
возврата остатка для преобразования блока в несколько частичных блоков в
этой ветке нет. Поэтому цинковая совместимость использует собственную
атомарную конверсию непосредственно в mounted inventory.

`RollingMode` package-private и имеет порядок
`TUNNEL_PAVE`, `STRAIGHT_FILL`, `WIDE_FILL`. Создание mixin-класса в пакете
Create приводит к JPMS split-package, поэтому ordinal `1` изолирован в
`RollerModeGate`; GameTest сравнивает весь runtime-порядок enum и падает при
его изменении.

### Семантика профиля

`createHeightProfileForTracks()` создаёт `PaveTask`, передаёт в
`TrackPaverV2.pave()` фактические `TrackEdge` и затем добавляет
`context.localPos.getY()` к Y каждого столбца.

Прямая ветка сначала вычисляет позицию ребра как `double`, применяет
вертикальный offset (`1` для уклона, `0.5` для ровного ребра), затем
преобразует результат в `BlockPos`. Кривая проходит LUT-сегменты, выбирает
минимальный Y для повторно покрытой X/Z-ячейки, а перед записью в `PaveTask`
округляет Y до целого или половины блока.

Штатный Roller трактует значение профиля как Y базового полного блока и
при дробной части около `0.5` добавляет нижнюю плиту сверху. В legacy-режиме
`surfaceOnly=false` аддон сохраняет эту схему. В режиме по умолчанию
физическая верхняя поверхность считается как `profileY + 1`, после чего
выбирается только её самая высокая занятая ячейка; полный базовый блок не
ставится.

## Copycats+ 3.0.4

Проверенные элементы:

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
ItemStack ICopycatBlockEntity.getConsumedItem();

static ItemRequirement ICopycatBlock.getRequiredItemsForLayer(
    BlockState state,
    IntegerProperty property
);
```

`ICopycatBlockEntity.init()` устанавливает материал
`AllBlocks.COPYCAT_BASE.getDefaultState()`, пустой `consumedItem` и включает
connected textures. `hasCustomMaterial()` возвращает `false` именно для
стандартного `COPYCAT_BASE`.

`CopycatLayerBlock.getRequiredItems()` делегирует
`ICopycatBlock.getRequiredItemsForLayer(state, LAYERS)`. Реализация создаёт
ровно `LAYERS` требований типа `CONSUME`, что подтверждает стоимость
`layers=N -> N` предметов. В runtime аддон не использует
`SpecialBlockItemRequirement`; списание производится транзакционно из
mounted inventory.

`CopycatSlopeLayerBlock.getRequiredItems()` использует ту же функцию и
поэтому также стоит `LAYERS` предметов. `CopycatHalfLayerBlock` объединяет
два независимых требования:

```java
getRequiredItemsForLayer(state, POSITIVE_LAYERS)
    .union(getRequiredItemsForLayer(state, NEGATIVE_LAYERS));
```

Его полная стоимость равна `positive_layers + negative_layers`; полный блок
`8 + 8` требует 16 предметов `copycat_half_layer`. Свойства обеих половин
официально допускают `0..8`, хотя ненулевой новый блок всегда содержит хотя
бы одну половину.

Проверены JSON-рецепты внутри JAR Copycats+ 3.0.4:

```text
c:ingots/zinc -> 8 × copycats:copycat_layer
c:ingots/zinc -> 16 × copycats:copycat_half_layer
2 × copycats:copycat_half_layer -> 1 × copycats:copycat_layer
```

Регистр Create подтверждает точный предмет `create:zinc_ingot`. Поэтому
Half Layer является минимальной целой единицей для безостаточного банка:
один слиток равен 16 единицам, Half Layer стоит одну, обычный Layer — две.
Сдача сохраняется реальными Half Layer items, без собственного NBT или
скрытого счётчика аддона.

`CCShapes.SLOPE_LAYER` подтверждает точные высоты краёв нижней формы:

```text
layers:     1    2    3    4    5    6    7    8
low edge:   0    0    0    0   1/4  1/2  3/4   1
high edge: 1/4  1/2  3/4   1    1    1    1    1
```

Именно эти края используются quality gate аддона; предположение о восьми
параллельных склонах не делается.

Half Layer создаёт `MultiStateCopycatBlockEntity`, остальные два слоя —
`CCCopycatBlockEntity`. Для проверки пустого материала используется общий
`ICopycatBlockEntity.hasCustomMaterial()`, а у multistate BE дополнительно
проверяется пустой список `MaterialItemStorage.getAllConsumedItems()`.
