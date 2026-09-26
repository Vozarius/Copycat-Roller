# Build and Test Report

Release verification environment:

- Windows 11 amd64
- Oracle JDK 21.0.9 LTS
- Gradle 8.14.3
- Minecraft 1.21.1
- NeoForge 21.1.219
- Create 6
- Copycats+ 3.0.9

## Commands and results

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat runGameTestServer --console=plain
```

The final version 2.0 build completed successfully. All 54 unit tests passed
with zero failures and zero errors. The dedicated NeoForge GameTest server
loaded Copycat Roller 2.0, Create 6.0.10, and Copycats+ 3.0.9, then completed
67 registered GameTests in 1.266 seconds with every required test passing.
The Randomize Filters integration was reported as one optional failure because
that optional mod was not installed; it is no longer counted as verified. The
server run also verifies that common code does not load client-only classes.

The final clean build took 12 seconds; the dedicated GameTest run took
34 seconds.

## Version 2.0 coverage

The new unit tests verify:

- one zinc ingot equals exactly eight Copycat Bytes;
- existing Byte items are consumed before zinc;
- exact change conservation and invalid payment inputs;
- Bytes follow the continuous normal of the local track tangent, including diagonals;
- one-sided seeds emit only outward and interior seeds emit no slope;
- the first side Byte matches the central height;
- each following outward half-block step lowers the surface by half a block;
- the reach formula matches Create's Wide Fill radius within the safety cap;
- equal-distance source selection is deterministic for every seed order;
- extreme Create fill-depth values are capped without integer overflow;
- adjacent longitudinal track samples merge into one lateral shell;
- non-emitting profiles from every Roller reserve the combined central mask
  without blocking the selected outer side;
- the nearest inward profile fixes a stable world-space outward normal;
- station correspondence wins over a spatially nearer rounded cell from a
  different longitudinal section;
- read-only halo seeds shape a complete contour but never own world output;
- moving writable windows across a quarter-turn produce exactly the same cells
  as one monolithic contour, with every half-block distance band connected by
  shared faces; isolated diagonal contacts receive one inward corner bridge,
  eliminating visual notches without widening already connected sections;
- writable height-changing bands retain every core cell even when a halo source
  is geometrically nearer, and remain connected across half-block transitions;
- adjacent track edges sharing one Create `PaveTask` retain separate section
  identities, so equal local station values cannot select the wrong side;
- only sources on the exterior boundary of the combined Roller footprint emit;
- reversing the track tangent cannot flip an explicitly resolved world side;
- longitudinal-only or overlapping profile samples cannot invent a side;
- straight profiles never extend beyond their two longitudinal ends;
- only unsupported open ends are clipped while interior curve contours remain;
- every planned Byte has a reachable predecessor in the prior height band;
- every half-block distance band exists at its own height and a quarter-turn
  surface remains connected through the half-cell diagonal rasterization;
- negative coordinates and a half-block track rise are rasterized correctly;
- ordinary material filling reproduces Create's exact `WIDE_FILL` Manhattan
  diamond at every depth, retains the fractional top sample, and floors
  negative world heights correctly.

The new GameTests verify:

- three Byte parts consume one zinc ingot and return five Byte items;
- eight Byte parts consume exactly one ingot with no change;
- an inventory that cannot hold the change leaves both inventory and world
  unchanged;
- finite zinc mixed with an unrelated Creative Crate cannot lose conversion
  change or report a false successful placement;
- a partially built and already filled Byte can be extended after resources
  are replenished without replacing its stored material;
- `WIDE_FILL` remains a separately guarded mode in the runtime enum order;
- Copycat Byte is internal to zinc Wide Fill and is not accepted as a direct
  Roller filter;
- only the two ends of a same-height, same-facing Roller row own outward Wide
  Fill sides; interior Rollers own none and a single Roller owns both;
- a real three-Roller service call reads both neighbouring `PaveTask` profiles,
  places Bytes from a Creative Crate, never enters their combined central X/Z
  footprint, and places nothing new after the carriage yaw is reversed by 180°;
- a short real `TrackPaverV2` window expands longitudinally for read-only
  context while retaining a smaller, explicitly writable core;
- two adjacent real `TrackEdge` calls can append to one `PaveTask` without
  losing precise samples, confusing their stations, or crashing the server;
- Create Creative Crates supply Copycat shapes, zinc conversion, and material
  filling without changing their infinite source stack;
- a descending slope Byte is found throughout Create's configured fill depth,
  filled exactly once, owns the paving column above it, and redirects Create's
  ordinary support target directly beneath it;
- ordinary block filters find slope Bytes at exact and one-block-lower Wide
  Fill targets and protect the cells above them;
- a real `RollerMovementBehaviour.triggerPaver` call in `WIDE_FILL` fills slope
  Bytes, including one above Create's normal paving point, keeps protected upper
  cells empty, fills a deeper Byte in the same Roller column, still lets Create
  pave the ordinary upper target, and creates no support ray below the Byte;
- one material Roller fills every Byte in its real vertical work column and
  charges each part, while every Byte redirects only its own paving level so a
  deeper Byte cannot create radial or vertical fill rays;
- every eligible non-Byte Copycat in the bounded work column is filled while
  the surface tolerance continues to exclude unrelated deep decoration.

The existing suite still covers precise straight, diagonal, and Bezier track
profiles, Layer/Half Layer/Slope Layer geometry, atomic inventory handling,
material assignment, normal Create paving fallback, third-party filter
transactions, unloaded chunks, portals, solid blocks, and dedicated server
classloading.

## Artifacts

- `build/libs/copycat_roller-2.0.jar` — 147,050 bytes
  - SHA-256: `4B4DB5900300B89A6D5579B693797931726A424E8865992C6A96CE8FC6AE99EF`
- `build/libs/copycat_roller-2.0-sources.jar` — 54,676 bytes
  - SHA-256: `87AFD0FEC1D0BA11F2A8E50CC7625CD4076EFED491DD4606D4E26408DFDAAF45`

The production JAR contains no GameTest classes or test structures. Its
NeoForge metadata identifies only `copycat_roller`; the unrelated root
Randomize Filters metadata has been removed from the repository.

Warnings printed by Copycats+, Flywheel, and Ponder concern their own mixin
compatibility metadata and development refmaps. They also occur without this
addon and did not prevent any required injection or test from succeeding.
