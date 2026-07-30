# Copycat Roller

Create Mechanical Rollers, now with Copycats+ layers.

Build smooth railway foundations along straight, diagonal, and curved tracks
in 1/8-block steps.

![Slope situation](https://cdn.modrinth.com/data/cached_images/d17990c423aea4e41a16cb356ceadb2ef12e759c.jpeg)
![Boring default rollers](https://cdn.modrinth.com/data/cached_images/20d9179418af1a3ef1943781a5a1778570f842b4.jpeg)
![Usage of rollers with copycats](https://cdn.modrinth.com/data/cached_images/720c05ef36a0d1f18a57c92a120a5664ac77c030.jpeg)
![Filling results](https://cdn.modrinth.com/data/cached_images/0203b30c81c7a68d4f055036c49c2de3e5e6403a.jpeg)

## Features

- Precise Copycat paving along railway tracks
- Layer, Half Layer, and Slope Layer support
- Automatic Layer/Half Layer selection with a Zinc Ingot
- Ordinary blocks fill existing Copycats and pave empty spaces at the same time
- Safe item use with no hidden material balance

## Filters

Put one of these items in a Mechanical Roller's filter:

- **Copycat Layer**
  - An adjustable surface in 1/8-block steps
- **Copycat Half Layer**
  - Independent height on both sides
- **Copycat Slope Layer**
  - A smooth slope where a suitable state exists
- **Zinc Ingot**
  - Automatically chooses Layer or Half Layer
- **Any supported block**
  - Applies its material to Copycats and continues normal Create paving

Addon features are active in **Fill** (`STRAIGHT_FILL`) mode. Tunnel and Wide
Fill keep their normal Create behavior.

## Item Use

- `layers=N` costs `N` Layer or Slope Layer items.
- A Half Layer costs the sum of its two sides.
- One Zinc Ingot makes up to 8 Layers or 16 Half Layers. Change is kept as
  real Half Layer items.
- Filling a Copycat costs one material block per empty part. Normal paving
  consumes its usual additional blocks.

Existing player-assigned materials are never overwritten. If the complete
cost is unavailable, the operation is cancelled safely.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Create 6
- Copycats+ 3.0.4
- Java 21

Install the requirements and place Copycat Roller in the `mods` folder on both
client and server.

## Configuration

The config can change height rounding, surface-only paving, Copycat fill
depth, and Slope Layer accuracy. By default, the Roller rounds down and places
only the nearest upper Copycat surface.
