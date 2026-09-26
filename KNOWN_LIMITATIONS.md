# Known Limitations

1. Compatibility is intentionally limited to Minecraft 1.21.1, Create 6, and
   Copycats+ 3.0.x. The dependency ranges prevent the addon from silently
   applying to the next major release, while `require=1` stops startup if a Create
   method signature changes.

2. A precise profile is available only for a train contraption when
   `createHeightProfileForTracks()` returns a `PaveTask`. A regular moving
   contraption has no track geometry, so the addon uses the Roller's standard
   target position and only the full state of the selected type.

3. The default is `surfaceOnly=true`: support blocks are not created beneath
   the upper partial cell. A Copycat block may therefore appear to float if
   there is no existing foundation below it. The previous downward-filling
   behavior is available through `surfaceOnly=false`; only that legacy central
   fill uses `fillDepthBlocks`, capped by Create's Roller depth.

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
   `STRAIGHT_FILL=1` and `WIDE_FILL=2` ordinals are used; a required
   GameTest verifies the names and order of all three enum values.

9. Create's mounted storage runs synchronously on the server thread. There is
   no concurrent window between the separate simulation and the exact real
   `ItemHelper.extract` call. If a nonstandard `IItemHandler` violates the
   contract, or if the world rejects an already funded placement, the addon
   restores the block and returns the extracted items. Any remainder that can
   no longer be inserted is safely dropped into the world and logged.

10. Automatic mode uses the exact ID `create:zinc_ingot`; `zink_ingot` does
    not exist in the registry. Straight Fill change is stored as real
    `copycats:copycat_half_layer` items; Wide Fill change is stored as real
    `copycats:copycat_byte` items. If extracting one ingot does not free
    a mounted-storage slot and an existing Half Layer stack cannot accept all
    of the change, the entire placement is cancelled. The addon intentionally
    uses neither a hidden balance nor world drops for normal change.

11. GameTests integrate the real registry, block entities, mixins,
    `TrackPaverV2`, and dedicated server. Most consumption and placement
    operations invoke the service directly so that the suite does not depend
    on the timing of a fully assembled moving train. A separate optional
    integration test runs the actual Create `tryFill` transaction with Create:
    Randomize Filters 1.0.7 when that mod is installed. If it is absent, the
    test is reported as an optional failure rather than a successful
    compatibility check. The straight, diagonal, and Bezier X/Z profiles are
    tested separately against Create's real `PaveTask`.

12. Automatic material filling is active in `STRAIGHT_FILL` and `WIDE_FILL`.
    Each material Roller scans its real X/Z work column from one cell above
    Create's active paving position down through the configured
    safely bounded `rollerFillDepth`, selecting every `copycat_byte` rather
    than only the nearest profile match. The exact track profile finds every
    other Copycat surface inside the same bounded work column, while the
    one-block surface tolerance still excludes unrelated deep decoration.
    Selected cells are passed through Create's real Roller
    placement transaction to discover the block chosen for that position, then
    their paving columns are protected from the later ordinary pass. Every
    target without a selected Copycat continues through Create's original
    paving code. A material rejected by Copycats+ is skipped without net
    consumption.

13. Material orientation is resolved through
    `ICopycatBlock.getAcceptedBlockState(...)` as if the top face had been
    targeted. A multistate Copycat consumes one material block for each
    existing empty part; absent parts and parts with a player-assigned
    material are not changed.

14. A selected Copycat at or below Create's base target owns the vertical
    paving cells above it, preventing an ordinary block or slab from being
    placed on top. Create's base attempt is redirected directly below that
    Copycat. Further occupied-block handling, leaves, portals, inventory
    extraction, and `rollerFillDepth` remain controlled by Create. Consequently,
    as with Create's normal Roller, a pass can stop at the first depth where any
    block is successfully placed.

15. Third-party filter compatibility is protocol-based rather than tied to a
    list of mods. A compatible filter must resolve its material synchronously
    during Create's normal `tryFill` call and reach `Level.setBlockAndUpdate`
    for the target position. The selected `BlockItem` is normally identified
    from the mounted-inventory deduction. For bottomless inventories such as a
    Creative Crate, the unchanged matching stack is resolved from Create's
    planned block state. A mod that bypasses this complete transaction, writes
    blocks later or asynchronously, or represents material without a
    `BlockItem` cannot be inferred safely and needs a dedicated integration.

16. Zinc Wide Fill creates two outer lateral surface strips, not a solid
    embankment. Every enabled Wide Fill Roller with the same local Y, facing,
    and longitudinal row contributes its exact `PaveTask` profile to one
    protected central mask. This includes the cells directly under both edge
    Rollers. Only the two edge Rollers emit Bytes, and each emits away from the
    row; interior Rollers emit none. A single Roller owns both sides. Profiles
    are gathered synchronously from `Contraption.getActors()` and no context,
    level, entity, or contraption reference is retained after the call.

    The edge and inward profiles are matched by the unquantized station along
    the same `TrackEdge`, rather than by the nearest rounded X/Z cell. Each
    Create work window is extended by the configured Byte reach plus two blocks
    at both longitudinal ends. These halo samples shape the distance field and
    the exterior mask but never own world output. Only source half-cells on the
    actual exterior boundary may emit. This makes overlapping actor calls agree
    on one contour and prevents moving end caps, internal fans, gaps, and a
    duplicate radius when the carriage turns. An ambiguous sample with no
    lateral separation is still skipped instead of guessing.

    Curves use the normalized local tangent captured before Create quantizes
    the profile. Each output half-column is owned by its nearest track sample
    and receives one distance band. The field is quantized into nested,
    diagonal-connected contours. At a physical open end of the `TrackEdge`,
    unsupported cells are rejected, while minimal support chains keep visible
    outer bands reachable. The first side Byte matches the central surface
    height; every following half-block step lowers by one half block. Maximum
    reach follows `(rollerFillDepth + 1) / 2` blocks up to the add-on safety
    cap of 32 depth levels (16 lateral blocks). This cap prevents overflow,
    unbounded allocations, and multi-second server ticks from pathological
    Create configuration values. A solid obstacle terminates only the affected branch, which
    prevents placement through walls but can leave an intentional opening.
17. Copycat Byte is reserved for automatic zinc Wide Fill and is not accepted
    as a direct Roller filter. Ordinary block filters in Wide Fill additionally
    fill existing Copycats directly under them. They do not reserve empty future
    Byte cells, because doing so would alter Create's normal depth-stop
    semantics. Tunnel Pave and unrelated filters retain Create's standard
    behavior.
