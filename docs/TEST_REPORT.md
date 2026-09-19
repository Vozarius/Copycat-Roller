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

The final version 1.1 build completed successfully. All 37 unit tests passed
with zero failures and zero errors. The dedicated NeoForge GameTest server
loaded Copycat Roller 1.1, Create 6, and Copycats+ 3.0.9, then passed all
58/58 required GameTests in 1.084 seconds. The server run also verifies that
common code does not load client-only classes.

The final build took 13 seconds; the dedicated GameTest run took 32 seconds.

## Version 1.1 coverage

The new unit tests verify:

- one zinc ingot equals exactly eight Copycat Bytes;
- existing Byte items are consumed before zinc;
- exact change conservation and invalid payment inputs;
- Bytes follow the continuous normal of the local track tangent, including diagonals;
- one-sided seeds emit only outward and interior seeds emit no slope;
- the first side Byte matches the central height;
- each following outward half-block step lowers the surface by half a block;
- the reach formula matches Create's Wide Fill radius;
- adjacent longitudinal track samples merge into one lateral shell;
- straight profiles never extend beyond their two longitudinal ends;
- only unsupported open ends are clipped while interior curve contours remain;
- every planned Byte has a reachable predecessor in the prior height band;
- every half-block distance band exists at its own height and a quarter-turn
  surface remains connected through the half-cell diagonal rasterization;
- negative coordinates and a half-block track rise are rasterized correctly.

The new GameTests verify:

- three Byte parts consume one zinc ingot and return five Byte items;
- eight Byte parts consume exactly one ingot with no change;
- an inventory that cannot hold the change leaves both inventory and world
  unchanged;
- `WIDE_FILL` remains a separately guarded mode in the runtime enum order;
- Copycat Byte is internal to zinc Wide Fill and is not accepted as a direct
  Roller filter;
- only the two ends of a same-height, same-facing Roller row own outward Wide
  Fill sides; interior Rollers own none and a single Roller owns both;
- Create Creative Crates supply Copycat shapes, zinc conversion, and material
  filling without changing their infinite source stack.

The existing suite still covers precise straight, diagonal, and Bezier track
profiles, Layer/Half Layer/Slope Layer geometry, atomic inventory handling,
material assignment, normal Create paving fallback, third-party filter
transactions, unloaded chunks, portals, solid blocks, and dedicated server
classloading.

## Artifacts

- `build/libs/copycat_roller-1.1.jar` — 140,854 bytes
  - SHA-256: `8CDDB91E106CCE36EF2483BC10459F9E2B8C078CEA75DD0805FBDE388808793B`
- `build/libs/copycat_roller-1.1-sources.jar` — 57,603 bytes
  - SHA-256: `7A42F8A77C0958A544043656CDEBA131F631FE4EF7279BD3F31393E073D79910`

Warnings printed by Copycats+, Flywheel, and Ponder concern their own mixin
compatibility metadata and development refmaps. They also occur without this
addon and did not prevent any required injection or test from succeeding.
