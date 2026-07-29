**Copycat Roller** is a lightweight NeoForge addon that adds compatibility
between Create's Mechanical Roller and thin building blocks from Copycats+.

The Roller follows the precise height of straight, diagonal, and curved
railway tracks, allowing it to create surfaces in 1/8-block increments.

![Slope situation](https://cdn.modrinth.com/data/cached_images/d17990c423aea4e41a16cb356ceadb2ef12e759c.jpeg)
![Boring default rollers](https://cdn.modrinth.com/data/cached_images/20d9179418af1a3ef1943781a5a1778570f842b4.jpeg)
![Usage of rollers with copycats](https://cdn.modrinth.com/data/cached_images/720c05ef36a0d1f18a57c92a120a5664ac77c030.jpeg)
![Filling results](https://cdn.modrinth.com/data/cached_images/0203b30c81c7a68d4f055036c49c2de3e5e6403a.jpeg)
## Features

The following items can be placed in the Mechanical Roller's filter slot:

- Copycat Layer
- Copycat Half Layer
- Copycat Slope Layer 
- Zinc Ingot

Compatibility is active only when the Roller is in **Fill**
(`STRAIGHT_FILL`) mode. `TUNNEL_PAVE`, `WIDE_FILL`, and all other filter
materials retain their original Create behavior.

## Automatic Zinc Mode

Place a **Zinc Ingot** in the Roller's filter to select
the block shape automatically:

- if both halves of the planned surface have the same height, the Roller
  places a standard copycat layer;
- if their heights differ, it places a copycat half layer with an
  independent layer count for each side.

This removes the unnecessary center seam on level sections while preserving
the more accurate Half Layer shape across height transitions. This also avoids texture issues with Copycats.

The zinc consumed by the Roller must be available in the contraption's
mounted storage. As with a normal Mechanical Roller, the item inside the
filter slot itself is not consumed.

### Zinc Consumption

Consumption follows the recipes provided by Copycats+:

- 1 Zinc Ingot = 8 standard Layers;
- 1 Zinc Ingot = 16 Half Layers;
- 1 standard Layer is worth 2 Half Layers.

Unused material is stored in the contraption inventory as real
`copycat_half_layer` items and is automatically spent on subsequent
placements.

For example, a standard Layer with `layers=3` uses 3 of the 8 Layers produced
by one ingot. The remaining value is returned as 10 Half Layer items.

If there is not enough inventory space for the remainder, that individual
placement is cancelled completely: no zinc is lost, no excess items are
dropped, and the world is left unchanged.

## Precise Track Surfaces

When the Roller is part of a train contraption, the addon preserves the
track's fractional height before Create rounds it:

- straight sections are interpolated from the real TrackEdge geometry;
- curves reuse Create's coverage while preserving the original fractional Y;
- track width, steering, and X/Z coverage remain unchanged;
- Half Layers account for the height above each half of the block;
- states that would protrude through the rails are not placed;
- unsuitable Slope Layer states may be skipped to prevent a sawtooth surface.

By default, only the upper partial surface is created. Full Copycat blocks are
not placed underneath it, and a level track aligned with an integer Y
boundary consumes no material.

Outside a train contraption, precise track geometry is unavailable. The addon
therefore uses the Mechanical Roller's normal target position and places a
full state of the selected block.

## Copycat Block Consumption

When one of the three Copycat blocks is selected directly, the Roller
consumes that exact item:

| State | Cost |
| --- | ---: |
| Standard Layer with `layers=N` | `N` |
| Full standard Layer | `8` |
| Slope Layer with `layers=N` | `N` |
| Half Layer | `negative_layers + positive_layers` |
| Full Half Layer | `16` |
| Growing an existing empty layer | Only the positive difference |
| Repeating a pass over the completed state | `0` |

Existing Copycat blocks with a material assigned by a player are never
overwritten.

Every placement is atomic. If there are not enough items for the complete
operation, neither the block nor the inventory is changed.

## Configuration

The following file is created after the first launch:

```text
config/copycat_roller-common.toml
```

Default settings:

```toml
[paving]
roundingDirection = "DOWN"
surfaceOnly = true
fillDepthBlocks = 1
slopeMaxVerticalError = 0.25
```

### `roundingDirection`

- `DOWN` — rounds to the previous 1/8 step:
  `(0, 1/8] → 0`, `(1/8, 2/8] → 1`, …, `(7/8, 1] → 7`;
- `UP` — rounds upward to avoid a vertical gap:
  `(0, 1/8] → 1`, …, `(7/8, 1) → 8`.

### `surfaceOnly`

- `true` — places only the upper partial surface cell;
- `false` — enables downward filling with full blocks.

### `fillDepthBlocks`

Controls the downward filling depth when `surfaceOnly=false`. Accepted values
are `1..512`. The result is also limited by Create's server-side
`rollerFillDepth` setting.

### `slopeMaxVerticalError`

Sets the maximum allowed vertical gap between a Slope Layer edge and the
track profile. A lower value creates a more precise but potentially sparser
surface.
