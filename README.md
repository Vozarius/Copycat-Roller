# Copycat Roller

Create Mechanical Rollers, now with Copycats+ layers.

Copycat Roller lets train-mounted Rollers follow straight, diagonal, and
curved tracks with Copycats+ surfaces in 1/8-block steps.

## Features

- Precise Copycat paving along railway tracks
- Support for Layer, Half Layer, and Slope Layer
- Automatic Layer/Half Layer selection with a Zinc Ingot
- Ordinary blocks both fill existing Copycats and pave empty spaces
- Safe inventory handling with no hidden material balance

## Roller Filters

Put one of these items in a Mechanical Roller's filter:

- **Copycat Layer** — creates a surface with adjustable height
- **Copycat Half Layer** — gives the two sides independent heights
- **Copycat Slope Layer** — creates a slope when a suitable state exists
- **Zinc Ingot** — automatically chooses Layer or Half Layer
- **Any supported block** — applies its material to Copycats and continues
  normal Create paving everywhere else

Addon paving is active in **Fill** (`STRAIGHT_FILL`) mode. Tunnel and Wide
Fill keep their normal Create behavior.

## Item Use

- A Layer or Slope Layer with `layers=N` costs `N` matching items.
- A Half Layer costs `negative_layers + positive_layers`.
- One Zinc Ingot produces up to 8 Layers or 16 Half Layers. Any change is
  stored as real Half Layer items.
- Filling a Copycat costs one material block per empty Copycat part. Blocks
  placed by Create consume their normal additional items.

Existing player-assigned Copycat materials are never overwritten. An
operation is cancelled safely if its complete cost cannot be paid.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Create 6
- Copycats+ 3.0.4
- Java 21

## Installation

Install the requirements and place
`copycat_roller-1.0.jar` in the `mods` folder on both client and server.

The addon does not modify the Create or Copycats+ JAR files.

## Configuration

The common config is generated at
`config/copycat_roller-common.toml`.

- `roundingDirection` — round fractional heights down or up
- `surfaceOnly` — place only the visible upper Copycat surface
- `fillDepthBlocks` — downward Copycat depth when surface-only mode is disabled
- `slopeMaxVerticalError` — skip inaccurate Slope Layer states

Defaults favor a lightweight surface: downward rounding and only the nearest
upper Copycat cell.

## Building

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

The built JAR is written to `build/libs`.

## Developer Documentation

- [API research](docs/API_RESEARCH.md)
- [Mixin targets](docs/MIXIN_TARGETS.md)
- [Build and test report](docs/TEST_REPORT.md)
- [Known limitations](KNOWN_LIMITATIONS.md)
