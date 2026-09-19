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

The final version 1.1 build completed successfully. All 35 unit tests passed
with zero failures and zero errors. The dedicated NeoForge GameTest server
loaded Copycat Roller 1.1, Create 6, and Copycats+ 3.0.9, then passed all
58/58 required GameTests in 1.150 seconds. The server run also verifies that
common code does not load client-only classes.

The final build took 14 seconds; the dedicated GameTest run took 20 seconds.

## Version 1.1 coverage

The new unit tests verify:

- one zinc ingot equals exactly eight Copycat Bytes;
- existing Byte items are consumed before zinc;
- exact change conservation and invalid payment inputs;
- Bytes are emitted only along the local lateral axis, never longitudinally;
- one-sided seeds emit only outward and interior seeds emit no slope;
- the first side Byte matches the central height;
- each following outward half-block step lowers the surface by half a block;
- the reach formula matches Create's Wide Fill radius;
- adjacent longitudinal track samples merge into one lateral shell;
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

- `build/libs/copycat_roller-1.1.jar` — 137,222 bytes
  - SHA-256: `8F56C5B0207E6446C289417F8EA4925CD783D14F374C055E901C2D0DCB00CF61`
- `build/libs/copycat_roller-1.1-sources.jar` — 56,216 bytes
  - SHA-256: `07460359EA82FFD6499027DAAEB53A7CE93FBB6FD1CB4AE1F0489180BA7B1F4E`

Warnings printed by Copycats+, Flywheel, and Ponder concern their own mixin
compatibility metadata and development refmaps. They also occur without this
addon and did not prevent any required injection or test from succeeding.
