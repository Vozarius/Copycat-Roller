# Copycat Roller

Create Mechanical Rollers, now with Copycats+.

Build smooth railway foundations along straight, diagonal, and curved tracks
in 1/8-block steps.

![Slope situation](https://cdn.modrinth.com/data/cached_images/d17990c423aea4e41a16cb356ceadb2ef12e759c.jpeg)
![Boring default rollers](https://cdn.modrinth.com/data/cached_images/20d9179418af1a3ef1943781a5a1778570f842b4.jpeg)
![Usage of rollers with copycats](https://cdn.modrinth.com/data/cached_images/720c05ef36a0d1f18a57c92a120a5664ac77c030.jpeg)
![Filling results](https://cdn.modrinth.com/data/cached_images/0203b30c81c7a68d4f055036c49c2de3e5e6403a.jpeg)

## Features

- Layer, Half Layer, and Slope Layer paving
- Automatic Layer/Half Layer selection with Zinc
- Zinc-powered half-block slopes made from Copycat Bytes in Wide Fill mode
- Ordinary blocks fill Copycats and continue normal Create paving
- Support for compatible Roller filter mods
- Creative Crate support
- Safe, exact item consumption

In normal **Fill** mode, put a supported Copycat or Zinc Ingot in the Roller
filter. In **Wide Fill**, every Roller keeps normal central paving. Only the two
outermost Rollers build Byte slopes, one outward side each, so adjacent Rollers
do not create overlapping internal slopes. The first Byte matches the central
height, then every half-block step outward drops by half a block. Reach matches
Create's normal Wide Fill.

One Zinc Ingot equals 8 Layers, 16 Half Layers, or 8 Copycat Bytes. Change is
returned as real items; an operation is cancelled if its full cost or change
cannot be handled. Create Creative Crates can supply Copycats, zinc, and copied
block materials. Existing player-assigned materials are never overwritten.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.200+
- Create 6
- Copycats+ 3.0.x (tested with 3.0.9)
- Java 21

Install Copycat Roller on both client and server. The addon does not modify the
Create or Copycats+ JAR files.
