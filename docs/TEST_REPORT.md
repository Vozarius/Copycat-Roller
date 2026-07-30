# Build and Test Report

Test environment:

- Windows 11 amd64
- Oracle JDK 21.0.9 LTS
- Gradle 8.14.3
- Minecraft 1.21.1
- NeoForge 21.1.219
- Create 6
- Copycats+ 3.0.4

## Commands

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

Final combined run:

```powershell
.\gradlew.bat build runGameTestServer --console=plain
```

Final clean verification of version 1.0:

```powershell
.\gradlew.bat clean build --console=plain
.\gradlew.bat runGameTestServer --console=plain
```

Result: the clean build completed successfully in `12 s`; all 23 unit tests
passed, and the dedicated GameTest server completed all `49/49` required tests
in `704.8 ms`.

Additional checks were performed with both the lowest Copycats+-compatible
Create 6 build and the highest available Create 6 build. In both cases, the
project built successfully, the dedicated server started, and all `49/49`
GameTests passed. The addon declares the complete Create 6 branch as its
range; Copycats+ dependencies impose an additional effective lower bound on
the assembled modpack. The Create 6.0.8 verification used:

```powershell
.\gradlew.bat '-Pcreate_version=6.0.8-169' runGameTestServer --console=plain
```

It completed all `49/49` required tests in `1.243 s`.

Artifacts:

- `build/libs/copycat_roller-1.0.jar` — 96,993 bytes;
- `build/libs/copycat_roller-1.0-sources.jar` — 42,340 bytes;
- SHA-256 of the main JAR:
  `2BC63C709D8AD3149D258E9A1F42B4940C9127C624F9730B4DFFEC54EE63F7DB`.

## Unit Tests

`LayerMathTest` verifies:

- `DOWN` and `UP` modes for values from `0.0` through `0.999999`;
- noise around 1/8 boundaries;
- negative world Y coordinates;
- transitions across an integer coordinate;
- height quantization for the negative and positive halves of a Half Layer,
  including values that cross cell boundaries;
- `DOWN` as the default for pure mathematical operations;
- equivalence to the standard lower-slab behavior at `fraction=0.5`:
  `layers=8` below and `layers=4` above in `UP` mode.

`PavingLimitsTest` verifies the default depth of one nearest block, a custom
depth, and the standard `rollerFillDepth + 1` limit.

`SlopeLayerGeometryTest` verifies:

- the exact low- and high-edge heights of all eight Copycats+ states;
- selection of representable 1/4 and 1/2 slopes;
- rejection of an intermediate state that would create a sawtooth;
- rejection of any state that intersects either edge of the profile.

`ZincCreditMathTest` verifies:

- the cost of a standard Layer at two half-layer units per layer;
- the cost of both Half Layer sides at one unit each;
- exact change from one or more zinc ingots;
- priority of previously stored Half Layer items over a new ingot;
- conservation for all costs in `1..16` and stored-change amounts in `0..16`;
- rejection of invalid input values.

## NeoForge GameTests

The suite contains 49 required tests:

1. the exact Layer, Half Layer, Slope Layer, and `create:zinc_ingot` items are
   accepted by the Roller filter, and the exact zinc registry ID is confirmed;
2. another partial Copycat block remains rejected;
3. a horizontal profile does not create a partial upper layer;
4. a half-block slope creates `layers=4` above;
5. default configuration values are `DOWN`, `surfaceOnly=true`, one nearest
   block, and a Slope Layer tolerance of `0.25`;
6. standard Layer states are correct for 1/8 through 7/8;
7. Half Layer properties remain independent and their combined cost is exact;
8. both Half Layer halves use a conservative minimum height;
9. Half Layer does not protrude on a diagonal or transverse slope;
10. only the upper shell is placed, without a full base block or excess cost;
11. a full state is skipped on a level profile at an integer height;
12. Half Layer growth is atomic and incremental;
13. Slope Layer `facing` points uphill and `layers=4` represents 1/2;
14. Slope Layer `facing` reverses when the slope direction changes;
15. a Slope Layer state that would create a sawtooth is skipped, while a
    representable quarter-slope is retained;
16. a full Half Layer costs `16` and a full Slope Layer costs `8`;
17. a user-assigned material in a multistate Half Layer is protected;
18. a standard Layer with `layers=N` consumes `N` items;
19. a full standard Layer consumes eight items;
20. insufficient inventory causes an atomic rejection;
21. growing a standard Layer from 3 to 6 costs three items;
22. a user-assigned material in a standard Layer is protected;
23. repeating a pass is idempotent;
24. an unloaded chunk is protected;
25. a solid block and a portal are protected;
26. diagonal and Bezier X/Z coverage matches Create while preserving
    unrounded Y, the tangent, and the gradient direction;
27. the compatibility branch has a strict scope, and the runtime
    `RollingMode` order is verified;
28. common code loads on a dedicated GameTest server;
29. equal heights on both halves in zinc mode produce a standard Layer;
30. unequal heights produce a Half Layer with the correct axis and two layer
    values;
31. a partial standard Layer consumes one ingot and preserves exact change;
32. the next Half Layer operation consumes the preserved change without
    requiring a new ingot;
33. one ingot produces exactly either a full Layer with `layers=8` or a full
    Half Layer with `8 + 8`, with no change;
34. insufficient space for change atomically cancels the operation without
    changing the world or inventory;
35. an ordinary block filter assigns its material to an empty single-state
    Copycat and consumes exactly one matching block;
36. an existing player-assigned material is preserved without further
    consumption;
37. a Half Layer with two existing parts receives material on both sides and
    consumes two blocks;
38. an absent Half Layer side remains empty and consumes nothing;
39. insufficient material for every existing multistate part leaves both the
    Copycat and inventory unchanged;
40. a material rejected by Copycats+ is skipped without consumption;
41. a different inventory block cannot fund the selected filter material;
42. material filling also supports another Copycats+ shape without changing
    its block state;
43. a fractional track surface finds the Copycat in the adjacent vertical cell;
44. the material-assignment service leaves empty neighboring columns for
    Create instead of mutating them itself;
45. stacked Copycats are resolved to the surface closest to the track;
46. a sloped Half Layer is found and both existing parts are filled;
47. a decorative Copycat more than one block below the expected surface is
    ignored;
48. a full Copycat base is protected and its ordinary full-block target is
    redirected exactly one block downward;
49. a partial upper Copycat is protected while Create's base target remains
    unchanged.

## Diagnostic Iterations

- The initial dependency on a missing `slim` classifier for Create was
  replaced with the complete official artifact.
- An attempt to place a mixin in Create's Java package was rejected by JPMS as
  a split package. The mixins were moved into the addon's package, the mode
  ordinal was centralized, and a GameTest now guards it.
- The extended horizontal-profile test found Create's internal half-block
  offset. The sampler was corrected so that the offset used to select a
  `BlockPos` does not create a false four-layer surface on level track.
- The first extended GameTest run completed 23 of 24 tests: the auxiliary full
  Slope Layer position was outside the template's cleared area. The position
  was moved inside the test column; the repeated and final clean runs
  completed all 24 of 24 tests.
- The first clean 1.3.0 run found fixed world X/Z coordinates in the auxiliary
  upper-shell test. With randomized template placement, the position could
  fall into an unloaded chunk. The test was changed to
  `helper.absolutePos(...)`; the repeated clean run completed all 28 of 28
  tests.
- Zinc mode initially checked for change-storage capacity before extracting
  the ingot. That incorrectly rejected the valid case of one ingot in the
  only slot, because extracting the ingot itself frees the slot. The operation
  order was changed to synchronous extraction, change insertion, and full
  rollback on failure. Dedicated GameTests confirm both the successful
  single-slot case and the atomic failure when a stack of two ingots does not
  free the slot.
- The first material-filling branch intercepted each `tryFill(...)` target.
  Real track testing showed that this could miss a Copycat in an adjacent
  vertical cell and pass the wrong targets back to Create. Material selection
  was therefore moved to a precomputed `triggerPaver(...)` plan. The current
  implementation fills the selected Copycats first, protects only their exact
  cells, redirects a full Copycat base attempt to its support below, and lets
  all remaining calls run through Create's unchanged `tryFill`. Per-part
  multistate snapshots and exact extraction remain atomic.

The server log still contains warnings from the Copycats+, Flywheel, and
Ponder mixin configurations: compatibility level, repeated `@Unique`
annotations, and a missing Ponder development refmap. These warnings also
occur without the addon, do not involve any of Copycat Roller's three mixin
classes, and did not prevent the injections or tests from succeeding.
