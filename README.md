# Copycat Roller

A NeoForge addon that integrates Create's Mechanical Roller with adjustable
layers from Copycats+.

It preserves fractional track heights and can pave straight, diagonal, and
curved railway sections in 1/8-block increments.

## Supported Filters

The compatibility branch accepts:

- `copycats:copycat_layer`
- `copycats:copycat_half_layer`
- `copycats:copycat_slope_layer`
- `create:zinc_ingot` for automatic Layer/Half Layer selection

These paving filters are handled only in `STRAIGHT_FILL` mode. Other Roller
modes retain their original Create behavior. Ordinary block filters have the
combined material-filling and ordinary paving behavior described below.

## Filling Existing Copycats

With an ordinary block in the filter, a Roller in `STRAIGHT_FILL` mode applies
that block as the material of existing Copycats+ blocks beneath the precise
track surface and simultaneously retains Create's ordinary paving behavior.
The Copycat block state and shape remain unchanged.

The Roller searches a narrow vertical band in every X/Z profile column and
selects the closest Copycat surface below the track. A selected Copycat cell
is protected from replacement, while columns without Copycats are passed to
Create unchanged. A full Copycat occupying Create's base paving cell redirects
that full-block attempt to the cell directly below it; a partial upper Copycat
leaves the ordinary base cell available to Create.

The material must be accepted by Copycats+ and must be present in the
contraption's mounted inventory. The filter item itself is not consumed.

- A single-state Copycat consumes one material block.
- A multistate Copycat consumes one material block for each existing empty
  part. A Half Layer with both halves present therefore costs two blocks.
- Missing multistate parts consume nothing.
- Existing player-assigned materials are never overwritten.
- Insufficient inventory leaves both the Copycat and inventory unchanged.

## Requirements

- Minecraft `1.21.1`
- NeoForge `21.1.x`
- Create `6`
- Copycats+ `3.0.4`
- Java `21`

The dependency ranges in `neoforge.mods.toml` intentionally restrict the
addon to the tested Create and Copycats+ releases.

## Installation

1. Install NeoForge, Create, Copycats+, and their required dependencies.
2. Place `copycat_roller-1.7.0.jar` in the `mods` directory on both the
   client and server.
3. Run Minecraft with Java 21.

The addon does not modify the original Create or Copycats+ JAR files.

## Configuration

The common configuration is generated at:

```text
config/copycat_roller-common.toml
```

Defaults:

```toml
[paving]
roundingDirection = "DOWN"
surfaceOnly = true
fillDepthBlocks = 1
slopeMaxVerticalError = 0.25
```

- `roundingDirection`: selects downward or upward 1/8-step rounding.
- `surfaceOnly`: places only the upper surface cell when enabled.
- `fillDepthBlocks`: controls downward filling when `surfaceOnly=false`.
- `slopeMaxVerticalError`: controls when an inaccurate Slope Layer is
  skipped.

Ordinary block filters always combine Copycat material assignment with
Create's normal paving; there is no separate material-fill mode.

## Zinc Mode

With `create:zinc_ingot` in the filter, equal half-cell heights produce a
standard Layer and unequal heights produce a Half Layer.

One zinc ingot is worth eight standard Layers or sixteen Half Layers.
Remainders are stored as real `copycat_half_layer` items in the contraption
inventory. If the remainder cannot be stored, the placement is cancelled
without changing the world or losing zinc.

## Building

From the project directory:

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTestServer
.\gradlew.bat build
```

The built JAR is written to `build/libs`.

The verified 1.7.0 build passes 23 unit tests and 49 required NeoForge
GameTests, including dedicated-server classloading.

## Technical Documentation

- [API research](docs/API_RESEARCH.md)
- [Mixin targets and descriptors](docs/MIXIN_TARGETS.md)
- [Build and test report](docs/TEST_REPORT.md)
- [Known limitations](KNOWN_LIMITATIONS.md)

All Mixin injections use `require=1`, and the Mixin plugin applies them only
when both Create and Copycats+ are present.
