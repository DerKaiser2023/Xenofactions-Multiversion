# `.xfmap` format and installation

An `.xfmap` is a ZIP64-compatible archive containing
`earth-map-pack.json`, `xenoearth-profile.json`, `template-level.dat`, `region/`,
and optionally `data/`, `DIM-1/`, and `DIM1/`. The manifest identifies format,
pack/version, Minecraft `1.7.10`, scale, chunk-aligned dimensions, population
mode, required content mod IDs, installed size, and every payload file's path,
size, and SHA-256. The profile is authoritative and must agree with it.

## Shared Earth profile (Stage 1, Forge 1.7.10)

`XFEarthProfile` remains the format-1 source of truth. Existing `sourceScale`,
`resize`, `effectiveScale`, `width`, `height`, X/Z bounds, surface Y range,
`seaLevel`, projection, population flags and optional `mapImageId` retain their
meaning. In particular, `height` is the raster's north/south size, not build
height. These optional additions have defaults for all existing profiles:

| Field | Default | Meaning |
| --- | --- | --- |
| `minimumY` | `0` | Inclusive lowest block Y |
| `maximumY` | `255` | Inclusive highest block Y |
| `heightImplementation` | `"vanilla-anvil"` | Active storage/runtime implementation |
| `verticalElevationScale` | `1.0` | Multiplier on decoded nominal raster elevation relative to `seaLevel` |

Only `vanilla-anvil` and Y=0..255 are accepted in this stage. A different
implementation or taller/negative range is rejected during pack verification,
save-profile loading and client description decoding. The Earth world load check
also rejects an advertised world height different from 256. There is no height
enable switch or save migration. Existing save profiles and bundled archives are
not rewritten. An explicit null implementation and non-positive/non-finite
elevation scales are invalid; omit new fields to retain defaults.

Horizontal `effectiveScale` controls geographic coordinates independently of
`verticalElevationScale`. The native raster decoder first rounds unsigned 16-bit
samples from 0..65535 to nominal Y=0..255, then computes
`seaLevel + (nominalY - seaLevel) * verticalElevationScale`, clamps to the
existing inclusive surface range, and rounds to a block. Scale 1 preserves every
previous sample result. These nominal levels are **not metres**: the supplied
heightmap has no physical-elevation calibration. Changing profile metadata does
not rescale saved terrain. Future physical elevation data needs its own explicit
calibration into this shared profile, not an assumption that horizontal and
vertical scales are equal.

Later geographic stages must reuse:

- `XFEarthProfileLoader.load(saveRoot)` on the server thread, and the existing
  Earth request/snapshot packets and `XFEarthMapData.decode` on the client thread.
  The whole validated profile, including defaulted additions, is synchronized;
  profile caches are cleared on overworld unload/server stop.
- `XFEarthProfile.getBounds()` / `XFEarthBounds` for inclusive block/chunk bounds
  and negative-coordinate floor division. `mapX`, `mapZ` and
  `containsMapPosition` describe the continuous raster extent through max+1.
- `hasGeographicConversion`, `longitude`, `latitude`, `blockX` and `blockZ` on
  that profile. Inverse conversion returns continuous block coordinates; callers
  choose block rounding and bounds policy. Unknown projections expose X/Z only
  and reject geographic conversion. Conversion never wraps or clamps locations.
- `getMinimumY`, `getMaximumY`, `getBlockHeight`, `getSeaLevel`, surface range
  getters, `getVerticalElevationScale` and `surfaceY` for vertical metadata and
  raster decoding. `earth2000()` is the canonical native generator profile,
  reused by source validation, terrain preparation, region iteration and output
  metadata. The external source manifest still validates that exact dataset.
- `XFEarthMapData`, `EarthMapImages` and `EarthMapTileCache` for the existing
  offline imagery and optional JourneyMap import. MapData delegates coordinate
  calculations to the profile; neither integration chooses another Earth grid.
- The existing dimension-aware `ClowderTerritory` / `CoordPair` /
  `TerritoryMeta` claim grid and city-origin metadata for claim nodes. Preserve
  its existing coordinate lookup and claim NBT semantics; do not introduce a
  parallel geographic claim registry or replace its legacy lookup with a new
  floor-division rule. World/claim reads and mutations belong to the server.

Geographic features can proceed on ordinary 256-block worlds. See
[height implementation findings](XFEARTH_WORLD_TYPE.md#height-implementation-findings-stage-1)
for the separate increased-height integration gate.

## Map imagery and archive installation

The Map item consumes that active profile separately from terrain loading.
Standard sourceScale/resize profiles use bundled display tiles; a custom raster
may specify optional `mapImageId` and distribute its final-grid tiles as a client
resource pack. Tiles are not world-region payloads and are not fetched during
gameplay. See [Map item and offline imagery](XFEARTH_WORLD_TYPE.md#map-item-forge-1710)
for projection, image preparation and lifecycle details.

The verifier rejects traversal, absolute/drive/UNC paths, NULs, duplicate
normalized paths, executable extensions, disallowed save/player/log data,
overlong paths, excessive entries, files over 8 GiB, and extraction over 128 GiB.
It counts bytes actually read and checks canonical destinations rather than
trusting ZIP sizes. No external archive executable is used.

Build archives with `python tools/1.7.10/build_xfmap.py --help`. The clean and populated
examples in `earthmaps-input/README.md` use the original exports; repeat
`--required-mod MODID` for **every content mod** needed by populated chunks. The
tool requires `level.dat`, `region/`, and a profile (or `--profile`), removes stale
player/lock/stat data, stores `level.dat` as `template-level.dat`, hashes payloads,
uses deterministic ordering/timestamps, stores already-compressed region data,
refuses overwrite without `--force`, and verifies its output.

Local/future 1:1000 packs belong in `.minecraft/xenofactions/earthmaps/`. They use
the same verifier/installer and generator options contain only a pack ID, never a
machine path. Bundled resources are copied into the hash-named `cache/`, verified,
and reused; every save still gets a private extracted copy.

Installation refuses an existing final save. It uses
`<name>.xfearth-installing`, removes a stale directory only when its private
partial marker proves ownership, extracts and verifies, atomically patches
`level.dat`, writes `xfearth-install.json`, and renames last. Cancellation closes
streams and removes only the partial directory.
