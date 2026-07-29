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

Final clean verification of version 1.4.0:

```powershell
.\gradlew.bat clean build runGameTestServer --console=plain
```

Result: `BUILD SUCCESSFUL in 31s`; all 23 unit tests passed, and the dedicated
GameTest server completed all `34/34` required tests in `586.2 ms`.

Additional checks were performed with both the lowest Copycats+-compatible
Create 6 build and the highest available Create 6 build. In both cases, the
project built successfully, the dedicated server started, and all `34/34`
GameTests passed. The addon declares the complete Create 6 branch as its
range; Copycats+ dependencies impose an additional effective lower bound on
the assembled modpack.

Artifacts:

- `build/libs/copycat_roller-1.4.0.jar` — 72,475 bytes;
- `build/libs/copycat_roller-1.4.0-sources.jar` — 34,557 bytes;
- SHA-256 of the main JAR:
  `E388EED61C3D7FA45DA4912F036FB1D4B92C2EC60F0A5DAABA8B4FF5D1E40AB1`.

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

The suite contains 34 required tests:

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
    changing the world or inventory.

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

The server log still contains warnings from the Copycats+, Flywheel, and
Ponder mixin configurations: compatibility level, repeated `@Unique`
annotations, and a missing Ponder development refmap. These warnings also
occur without the addon, do not involve any of Copycat Roller's three mixin
classes, and did not prevent the injections or tests from succeeding.
