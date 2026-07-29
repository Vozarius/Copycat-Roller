# Known Limitations

1. Compatibility is intentionally limited to Minecraft 1.21.1, Create 6, and
   Copycats+ 3.0.4. The dependency ranges prevent the addon from silently
   applying to the next release, while `require=1` stops startup if a Create
   method signature changes.

2. A precise profile is available only for a train contraption when
   `createHeightProfileForTracks()` returns a `PaveTask`. A regular moving
   contraption has no track geometry, so the addon uses the Roller's standard
   target position and only the full state of the selected type.

3. The default is `surfaceOnly=true`: support blocks are not created beneath
   the upper partial cell. A Copycat block may therefore appear to float if
   there is no existing foundation below it. The previous downward-filling
   behavior is available through `surfaceOnly=false`; only that mode uses
   `fillDepthBlocks` and Create's `rollerFillDepth + 1` limit.

4. In `DOWN` mode, the addon may intentionally leave a gap of at most 1/8 of a
   block. This is the user-selected downward-rounding behavior.

5. Half Layer and Slope Layer support only horizontal X/Z directions. On a
   diagonal, the dominant component of the local tangent is selected, with X
   winning a tie. A single cell therefore cannot have a diagonal seam or
   diagonal `facing`. Create's X/Z coverage remains unchanged.

6. Slope Layer geometry is defined by the eight standard Copycats+ states.
   Their slopes are not parallel to one another. The addon places a state only
   when it lies entirely below the profile and stays within
   `slopeMaxVerticalError`; other cells are intentionally skipped. A lower
   value produces a more accurate but sparser surface, while a higher value
   produces a denser but more stepped surface.

7. Bezier rasterization repeats the internal LUT/segment traversal used by
   `TrackPaverV2` in Create 6. This localized geometry duplication is necessary
   because the public `PaveTask` already contains a rounded Y value.

8. `RollingMode` is package-private in Create 6. The centralized
   `STRAIGHT_FILL=1` ordinal is used; a required GameTest verifies the names and
   order of all three enum values.

9. Create's mounted storage runs synchronously on the server thread. There is
   no concurrent window between the separate simulation and the exact real
   `ItemHelper.extract` call. If a nonstandard `IItemHandler` violates the
   contract, or if the world rejects an already funded placement, the addon
   restores the block and returns the extracted items. Any remainder that can
   no longer be inserted is safely dropped into the world and logged.

10. Automatic mode uses the exact ID `create:zinc_ingot`; `zink_ingot` does
    not exist in the registry. Change is stored as real
    `copycats:copycat_half_layer` items. If extracting one ingot does not free
    a mounted-storage slot and an existing Half Layer stack cannot accept all
    of the change, the entire placement is cancelled. The addon intentionally
    uses neither a hidden balance nor world drops for normal change.

11. GameTests integrate the real registry, block entities, mixins,
    `TrackPaverV2`, and dedicated server. Consumption and placement operations
    invoke the service directly so that the tests do not depend on the timing
    of a fully assembled moving train. The straight, diagonal, and Bezier X/Z
    profiles are tested separately against Create's real `PaveTask`.
