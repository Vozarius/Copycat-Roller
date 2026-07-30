# Mixin Targets

Configuration: `copycat_roller.mixins.json`. Every injection uses `require=1`,
and the configuration also sets `defaultRequire=1`. The plugin applies all
four mixin classes only when both `create` and `copycats` are present in
`LoadingModList`.

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
     unchanged for all other items, so Create and other filter mods retain
     control over their own validation.

2. `com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour`

   - Mixin: `RollerMovementBehaviourMixin`
   - Method: `triggerPaver`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;)V`
   - `HEAD` injection: cancellable for the three exact Copycat Layer filters
     and `create:zinc_ingot` in `STRAIGHT_FILL`. For every other nonempty
     filter, it builds only the geometric Copycat plan, obtains Create's
     provisional paving state, and runs the real `tryFill` transaction for
     every planned Copycat position. It does not interpret third-party filter
     data.
   - `RETURN` injection: preserves `WaitingTicks`, `LastPos`, and stalling when
     material assignment was the only successful change in the pass.
   - `@ModifyArg`, second `tryFill` invocation (`ordinal=1`, argument index 1):
     redirects Create's full-block base target one block down only when a full
     selected Copycat occupies that base cell.
   - Shadows:
     `createHeightProfileForTracks(MovementContext): PaveTask` and
     `getStateToPaveWith(MovementContext): BlockState`.

3. `com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour`

   - Mixin: `RollerMovementBehaviourMixin`
   - Method: `tryFill`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lcom/simibubi/create/content/contraptions/actors/roller/RollerMovementBehaviour$PaveResult;`
   - Cancellable `HEAD`: returns `PaveResult.PASS` for a Copycat already
     processed by the current material plan. The callback is not changed
     during the dedicated material probe.
   - `@Redirect`: intercepts the method's single
     `Level.getBlockState(BlockPos)` call. Only during the probe, the exact
     selected Copycat position is exposed as air so Create and third-party
     Mixins reach their normal extraction and placement hooks.
   - Cancellable `RETURN`: replaces Create's nominal result with `PASS` or
     `FAIL` when the intercepted Copycat assignment was skipped or could not
     be completely funded.

4. `net.minecraft.world.level.Level`

   - Mixin: `LevelMixin`
   - Method: `setBlockAndUpdate`
   - Descriptor:
     `(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z`
   - Cancellable `HEAD`: active only inside the thread-local Roller material
     probe and only for its exact level and position. It observes the final
     block chosen by Create or another Roller Mixin, determines the actual
     extracted `BlockItem`, prevents replacement of the Copycat, and delegates
     atomic per-part material assignment to the service. Without an active
     probe, it has no effect on world writes.

5. `com.simibubi.create.content.contraptions.actors.roller.TrackPaverV2`

   - Mixin: `TrackPaverV2Mixin`
   - Method: `pave`
   - Descriptor:
     `(Lcom/simibubi/create/content/contraptions/actors/roller/PaveTask;Lcom/simibubi/create/content/trains/graph/TrackGraph;Lcom/simibubi/create/content/trains/graph/TrackEdge;DD)V`
   - Static `HEAD`: preserves the precise Y, local tangent, and horizontal
     gradient before Create reduces Y to a `BlockPos` or half-block step.

There are no large `@Overwrite` methods. The body of `tryFill` is not copied.
Its protected invocation uses a lazily initialized `MethodHandle` with the
same exact descriptor shown above, avoiding broad reflective enumeration and
client-only class loading on a dedicated server. `getStateToPaveWith` and
`getMode` are not modified.
