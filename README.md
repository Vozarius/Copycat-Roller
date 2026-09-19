# Copycat Roller

An addon that lets Create Mechanical Rollers build with Copycats+.

## Features

- Precise railway surfaces in 1/8-block steps
- Copycat Layer, Half Layer, and Slope Layer filters
- Automatic Layer/Half Layer selection with a Zinc Ingot
- Zinc-powered sloped fill made from Copycat Bytes in Wide Fill mode
- Ordinary blocks fill existing Copycats and keep Create's normal paving behavior
- Compatible with Roller filter mods that use Create's placement transaction
- Creative Crate support for Copycats, zinc, and copied materials
- Atomic item use: no hidden balance and no lost zinc

## Filters and modes

In **Fill** (`STRAIGHT_FILL`) mode, use a Layer, Half Layer, Slope Layer, or
Zinc Ingot to build the Copycat surface. A Zinc Ingot automatically chooses
between Layer and Half Layer.

In **Wide Fill** (`WIDE_FILL`) mode, a Zinc Ingot keeps normal central paving
under every Roller. Only the two outermost Rollers in each side-by-side row
build Byte slopes, and each one builds only outward. This prevents overlapping
internal slopes. On curves, each half-cell is assigned to one nearest track
sample and follows its continuous local normal. This keeps the surface joined,
prevents different height bands from overwriting one another, and prevents the
Byte slope from entering the central paving footprint. The first side Byte
matches the height under the edge Roller; every following half-block step drops
by half a block. Reach matches Create's normal Wide Fill radius, and obstacles
stop only the affected branch.

An ordinary block filter fills empty parts of existing Copycats with that
material and still paves non-Copycat positions through Create. Compatible
third-party filters are resolved from the block they actually choose for each
position.

## Item use

- Layer or Slope Layer with `layers=N`: `N` matching items
- Half Layer: `negative_layers + positive_layers` items
- Zinc: one ingot equals 8 Layers, 16 Half Layers, or 8 Copycat Bytes
- Copycat material: one block per empty Copycat part

All change is returned as real Copycats+ items. If the exact cost cannot be
paid or the change cannot be stored, the world and inventory remain unchanged.
Create Creative Crates work as infinite supplies for Copycats, zinc, and copied
block materials. Player-assigned Copycat materials are never overwritten.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.200+
- Create 6
- Copycats+ 3.0.x (tested with 3.0.9)
- Java 21

Install the dependencies and place `copycat_roller-1.1.jar` in the `mods`
folder on both client and server.

## Configuration

`config/copycat_roller-common.toml` controls height rounding, surface-only
paving, central downward depth, and Slope Layer accuracy. The Wide Fill Byte
reach follows Create's `rollerFillDepth`. Defaults are downward rounding and
one nearest central block of depth.

## Building

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

Developer notes are in [`docs`](docs), with known limitations in
[`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md).
