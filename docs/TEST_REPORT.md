# Build and Test Report

Release verification environment:

- Windows 11 amd64
- Oracle JDK 21.0.9 LTS
- Gradle 8.14.3
- Minecraft 1.21.1
- NeoForge 21.1.219
- Create 6.0.10-281
- Copycats+ 3.0.9

## Commands and results

```powershell
.\gradlew.bat test build runGameTestServer --offline --console=plain --no-daemon
```

The final version 2.0 build completed successfully. All 61 unit tests passed
with zero failures and zero errors. The dedicated NeoForge GameTest server
loaded Copycat Roller 2.0, Create 6.0.10, and Copycats+ 3.0.9, then completed
78 registered GameTests in 4.154 seconds: all 77 required tests passed.
The Randomize Filters integration was reported as one optional failure because
that optional mod was not installed; this automated run does not verify it. The
server run also verifies that common code does not load client-only classes.

The combined build, unit-test, and dedicated GameTest command took 44 seconds.
This run reused Gradle caches; it was not a clean build.

## Audit regression checks (2026-09-26)

The epsilon-boundary permutation test failed before the planner fix. Five new
GameTests also failed on the previous implementation: refused shape extraction,
partial shape extraction, refused material extraction, and final notification
failures for single-state and multistate Copycats. All now pass.

Additional checks cover extreme fill depths at positive and negative world
heights, bounded search arithmetic, combined prepaid/partial material refunds,
and an extraction exception after debit when storage rejects the returned item.
The latter must drop the finite remainder without placing a block. Expected
exceptions are deliberately injected, so rollback error logs in these tests do
not indicate a failed test. Existing Creative Crate and filled-Byte extension
tests still pass.

These checks do not establish compatibility with every declared dependency
version or replace a moving-train client test. Randomize Filters remains untested
in this automated environment because it is not installed. The user separately
reports successful Randomize Filters and multiplayer checks on the previous
build; those checks have not yet been repeated with the new gap fix.

## Intermittent slope gaps (2026-09-26)

A new GameTest uses real Create `TrackPaverV2` samples from both sides of a
12-by-12-block Bezier quarter-turn. Before the fix, the first half-block band
was disconnected: a staircase in the rasterized profile was mistaken for an
open longitudinal end. After correcting that classification, moving windows
still omitted two outer cells whose required support belonged to another core
window. A separate unit test covers a same-band bridge skipped by one-pass
coordinate ordering.

The planner now includes the necessary support ancestors from the already
bounded geometry. Halo samples cannot start independent output branches, and
straight windows retain their original longitudinal bounds. Runtime placement
uses a deterministic queue and propagates reach only after successful placement
or an already-existing matching part. Failed placement stops that branch.

The new regression verifies all 12 half-block bands are connected, and that
short moving windows produce exactly the full contour, with neither missing nor
extra cells, in forward and reversed window/sample order. Additional unit tests
cover blocked support, duplicate cells, required halo ancestors, and empty core
ownership. Existing world-placement, central-mask, payment, and idempotency tests
continue to pass. The new curved-profile test validates planning and traversal;
it does not drive a moving train or reproduce the user's saved world.

Ordinary Create filling to the bottom is unchanged. The Copycat Byte Wide Fill
safety cap remains unchanged. The user subsequently confirmed continuous
curved slopes; the separate straight-track fix and retest are recorded below.

## Straight-track gaps (2026-09-27)

The user confirmed the latest curved slopes are continuous and reported gaps
on axis-aligned straight track. A new regression reproduced loss of a core
column at a straight graph-edge endpoint: for an eight-block +X edge, a Create
window from 6.5 to 8 includes column X=8, while the clamped expanded window
covers only X=0 through X=7. Create rounds the window length independently of
its starting column. Previously the halo replaced the core sample list, so the
side planner never saw the final writable column.

`samplesWithHalo` now restores core samples missing from the expanded list.
Existing halo samples keep their stable geometry; core ownership is unchanged.
The preservation regression failed before this fix and now passes across +X,
-X, +Z and -Z edges, lengths 8, 8.5 and 31, and window starts spaced by 0.125.
A world-placement GameTest verifies every expected Byte on the endpoint strip
is placed on the first call using finite zinc, and that repeating the call
neither changes blocks nor consumes zinc or Byte change. Expected placement
height comes from Create's own profile, including negative world heights.

The curved-profile continuity regression still passes. The declared dependency
ranges and Byte depth cap are unchanged as requested. Compatibility beyond the
versions listed above is not established by this run. On 2026-09-27 the user
confirmed that gaps no longer occur after receiving this artifact. This closes
the outstanding gameplay confirmation for the reported straight-track issue.

## Release assessment (2026-09-27)

READY FOR RELEASE for the tested configuration: Minecraft 1.21.1, NeoForge
21.1.219, Create 6.0.10-281, Copycats+ 3.0.9, and Java 21. No known blocking
issue remains in that configuration after the successful automated checks and
the user's confirmation that the reported gaps are gone. The declared wider
dependency ranges remain unchanged at the user's request; this verdict does
not certify every version combination in those ranges.

This confirmation updates documentation only. The verified production JAR and
its SHA-256 below are unchanged; no new build was necessary.

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
- halo seeds shape the contour and supply only support ancestors required by
  the current core; they cannot start independent output branches;
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
- every planned Byte has a reachable predecessor at the same height or one
  half-block above;
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

- `build/libs/copycat_roller-2.0.jar` — 151,629 bytes
  - SHA-256: `023D3662519453393BF6C197DE85CC69BF59535E66F153EA743B08EBCF144113`
- `build/libs/copycat_roller-2.0-sources.jar` — 57,302 bytes
  - SHA-256: `FF89E5F331137EAC99240D9309EBB814284252ED1D5A05C3BFEB8F9CA8BE81A1`

The production JAR contains no GameTest classes or test structures. Its
NeoForge metadata identifies only `copycat_roller`; the unrelated root
Randomize Filters metadata has been removed from the repository.

Warnings printed by Copycats+, Flywheel, and Ponder concern their own mixin
compatibility metadata and development refmaps. They also occur without this
addon and did not prevent any required injection or test from succeeding.
