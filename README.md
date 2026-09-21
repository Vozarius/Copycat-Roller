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
under every Roller. Before creating the side slope, the addon combines the
exact track profiles of every enabled Wide Fill Roller in the same row into one
protected central mask. Copycat Bytes can never occupy that mask. Only the two
outermost Rollers emit Bytes, one outward side each, and only from the real
exterior boundary of that mask. Curves use a continuous station along the track
instead of pairing already rounded X/Z cells. Each short Create work window is
sampled with a read-only longitudinal halo, so neighbouring calls agree on the
same connected contours without creating moving end caps or gaps. The outward
side is derived from the edge Roller and its nearest inward neighbour in world
space, so carriage rotation on a curve cannot build the same slope again at
another radius. Profiles and halos are used synchronously and are never cached
with the world or contraption. The first side Byte matches the height under the
edge Roller; every following half-block step drops by half a block.
Reach matches Create's normal Wide Fill radius, and obstacles stop only the
affected branch.

An ordinary block filter fills empty parts of existing Copycats with that
material in both Fill and Wide Fill, and still paves non-Copycat positions
through Create. Every pass scans the Roller's real vertical work column, from
the cell immediately below its body through the full `rollerFillDepth`, and
fills every Copycat Byte it finds. In Wide Fill, the future Byte cells of all
zinc Roller rows—and the cells directly above them—are reserved before normal
Create paving, so actor order cannot turn a slope gap into a full block.
Compatible third-party filters are resolved from the block they actually choose
for each position.

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

Install the dependencies and place `copycat_roller-2.0.jar` in the `mods`
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
