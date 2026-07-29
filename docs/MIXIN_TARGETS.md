# Mixin Targets

Configuration: `copycat_roller.mixins.json`. Every injection uses `require=1`,
and the configuration also sets `defaultRequire=1`. The plugin applies the
mixins only when both `create` and `copycats` are present in `LoadingModList`.

## Targets

1. `com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity`

   - Mixin: `RollerBlockEntityMixin`
   - Method: `isValidMaterial`
   - Descriptor:
     `(Lnet/minecraft/world/item/ItemStack;)Z`
   - Injection: cancellable `HEAD`; returns `true` only for the exact
     `CCBlocks.COPYCAT_LAYER`, `CCBlocks.COPYCAT_HALF_LAYER`, or
     `CCBlocks.COPYCAT_SLOPE_LAYER` item, as well as the exact
     `AllItems.ZINC_INGOT` item (`create:zinc_ingot`). The callback remains
     unchanged for all other items.

2. `com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour`

   - Mixin: `RollerMovementBehaviourMixin`
   - Method: `triggerPaver`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;)V`
   - Injection: cancellable `HEAD`; cancels only the combination of one of the
     three exact Copycat Layer filters or `create:zinc_ingot` with
     `STRAIGHT_FILL`.
   - Shadow:
     `createHeightProfileForTracks(MovementContext): PaveTask`.

3. `com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2`

   - Mixin: `TrackPaverV2Mixin`
   - Method: `pave`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/actors/roller/PaveTask;Lcom/simibubi/create/content/trains/graph/TrackGraph;Lcom/simibubi/create/content/trains/graph/TrackEdge;DD)V`
   - Injection: static `HEAD`; preserves the precise Y, local tangent, and
     horizontal gradient before Create reduces Y to a `BlockPos` or half-block
     step.

There are no large `@Overwrite` methods. The third-party methods `tryFill`,
`getStateToPaveWith`, and `getMode` are not replaced.
