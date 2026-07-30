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
   - `HEAD` injection: cancellable; handles the three exact Copycat Layer
     filters and `create:zinc_ingot` in `STRAIGHT_FILL`. For an ordinary block
     filter, it builds Create's profile and selects the closest Copycat surface
     in each X/Z column, fills its material, and then lets the original method
     continue.
   - `RETURN` injection: preserves `WaitingTicks`, `LastPos`, and stalling when
     material assignment was the only successful change in the pass.
   - `@ModifyArg`, second `tryFill` invocation (`ordinal=1`, argument index 1):
     redirects Create's full-block base target one block down only when a full
     selected Copycat occupies that base cell.
   - Shadow:
     `createHeightProfileForTracks(MovementContext): PaveTask`.

3. `com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour`

   - Mixin: `RollerMovementBehaviourMixin`
   - Method: `tryFill`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/simibubi/create/content/contraptions/actors/roller/RollerMovementBehaviour$PaveResult;`
   - `@Redirect`: intercepts the method's single
     `Level.getBlockState(BlockPos)` call. For a Copycat cell selected by the
     active pass plan, it returns `toPlace` to the unchanged comparison, so
     Create returns `PASS` without extracting or replacing anything. Every
     other target reads the real world state.

4. `com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2`

   - Mixin: `TrackPaverV2Mixin`
   - Method: `pave`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/actors/roller/PaveTask;Lcom/simibubi/create/content/trains/graph/TrackGraph;Lcom/simibubi/create/content/trains/graph/TrackEdge;DD)V`
   - Injection: static `HEAD`; preserves the precise Y, local tangent, and
     horizontal gradient before Create reduces Y to a `BlockPos` or half-block
     step.

There are no large `@Overwrite` methods. The body of `tryFill` is not copied
or overwritten; only its single state read is redirected for positions in the
active Copycat plan. `getStateToPaveWith` and `getMode` are not modified.
