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

The final version 1.1 build completed successfully. All 47 unit tests passed
with zero failures and zero errors. The dedicated NeoForge GameTest server
loaded Copycat Roller 1.1, Create 6, and Copycats+ 3.0.9, then passed all
61/61 required GameTests in 1.138 seconds. The server run also verifies that
common code does not load client-only classes.

The final build took 13 seconds; the dedicated GameTest run took 31 seconds.

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
- non-emitting profiles from every Roller reserve the combined central mask
  without blocking the selected outer side;
- the nearest inward profile fixes a stable world-space outward normal;
- station correspondence wins over a spatially nearer rounded cell from a
  different longitudinal section;
- read-only halo seeds shape a complete contour but never own world output;
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
- a real three-Roller service call reads both neighbouring `PaveTask` profiles,
  places Bytes from a Creative Crate, never enters their combined central X/Z
  footprint, and places nothing new after the carriage yaw is reversed by 180°;
- a short real `TrackPaverV2` window expands longitudinally for read-only
  context while retaining a smaller, explicitly writable core;
- two adjacent real `TrackEdge` calls can append to one `PaveTask` without
  losing precise samples, confusing their stations, or crashing the server;
- Create Creative Crates supply Copycat shapes, zinc conversion, and material
  filling without changing their infinite source stack.

The existing suite still covers precise straight, diagonal, and Bezier track
profiles, Layer/Half Layer/Slope Layer geometry, atomic inventory handling,
material assignment, normal Create paving fallback, third-party filter
transactions, unloaded chunks, portals, solid blocks, and dedicated server
classloading.

## Artifacts

- `build/libs/copycat_roller-1.1.jar` — 161,005 bytes
  - SHA-256: `4DBECF46430353E1778B06D8B8AE7608234D8A86A010047108D0A67C72FE002C`
- `build/libs/copycat_roller-1.1-sources.jar` — 64,563 bytes
  - SHA-256: `19A445EF85865E6E04438E0C0F71CA1613AFB2E2B18DA6660D3EC598C3314537`

Warnings printed by Copycats+, Flywheel, and Ponder concern their own mixin
compatibility metadata and development refmaps. They also occur without this
addon and did not prevent any required injection or test from succeeding.
