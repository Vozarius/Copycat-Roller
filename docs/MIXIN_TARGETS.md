# Mixin targets

Конфигурация: `copycat_roller.mixins.json`. Все инъекции имеют `require=1`,
а конфигурация также задаёт `defaultRequire=1`. Plugin применяет mixins
только когда в `LoadingModList` одновременно присутствуют `create` и
`copycats`.

## Targets

1. `com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity`

   - Mixin: `RollerBlockEntityMixin`
   - Method: `isValidMaterial`
   - Descriptor:
     `(Lnet/minecraft/world/item/ItemStack;)Z`
   - Injection: `HEAD`, cancellable; возвращает `true` только для точного
     предмета `CCBlocks.COPYCAT_LAYER`, `CCBlocks.COPYCAT_HALF_LAYER` или
     `CCBlocks.COPYCAT_SLOPE_LAYER`. Для остальных предметов callback не
     изменяется.

2. `com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour`

   - Mixin: `RollerMovementBehaviourMixin`
   - Method: `triggerPaver`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;)V`
   - Injection: `HEAD`, cancellable; отменяет только сочетание одного из
     трёх точных фильтров Copycat Layer и `STRAIGHT_FILL`.
   - Shadow:
     `createHeightProfileForTracks(MovementContext): PaveTask`.

3. `com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2`

   - Mixin: `TrackPaverV2Mixin`
   - Method: `pave`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/actors/roller/PaveTask;Lcom/simibubi/create/content/trains/graph/TrackGraph;Lcom/simibubi/create/content/trains/graph/TrackEdge;DD)V`
   - Injection: статический `HEAD`; сохраняет точный Y, локальную касательную
     и горизонтальный градиент до того, как Create сведёт Y к `BlockPos` или
     половине блока.

Больших `@Overwrite` нет. Сторонние методы `tryFill`,
`getStateToPaveWith` и `getMode` не заменяются.
