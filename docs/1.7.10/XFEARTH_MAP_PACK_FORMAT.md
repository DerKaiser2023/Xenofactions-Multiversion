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
| `heightImplementationVersion` | `"1.7.10"` | Exact storage implementation version |
| `verticalElevationScale` | `1.0` | Multiplier on decoded nominal raster elevation relative to `seaLevel` |

Gameplay and `.xfmap` installation still accept only `vanilla-anvil`, version
`1.7.10`, Y=0..255. Stage 2 metadata validation additionally accepts
`cubicchunks1710`, exact version `v0.1.23-alpha-pre`, Y=0..511 for detached storage
preparation. `requireGameplayHeight()` rejects that profile before normal world
loading and at the provider/Anvil chunk boundary. Unsupported implementations,
versions and ranges are rejected. There is no height enable switch or save
migration. Existing save profiles and bundled archives are not rewritten.
Explicit null implementation/version and non-positive/non-finite elevation
scales are invalid; omit new fields to retain ordinary defaults.

The overworld identity is saved as `data/xenoearth_storage.dat`, using ordinary
WorldSavedData NBT. Its `data` tag contains formatVersion 1, dimension 0,
implementation/version and inclusive Y bounds. Legacy ordinary worlds without
this metadata remain Anvil and acquire the identity on their next world save.
New native pregeneration records it before finalization. Metadata/profile
mismatches, root mixed Anvil/cubic directories and incorrect native height
metadata are rejected. Non-overworld dimensions are not migrated or tagged.
Gameplay packs also reject root `region2d/`, `region3d/`, native cubic height data
and cubic templates before extraction; an optional identity must match their
ordinary profile.

Horizontal `effectiveScale` controls geographic coordinates independently of
`verticalElevationScale`. The native raster decoder first rounds unsigned 16-bit
samples from 0..65535 to nominal Y=0..255, then computes
`seaLevel + (nominalY - seaLevel) * verticalElevationScale`, clamps to the
existing inclusive surface range, and rounds to a block. Scale 1 preserves every
previous sample result for ordinary profiles. These nominal levels are **not metres**: the supplied
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
  `earth2000Extended()` reuses the same geography for detached Y=0..511 storage;
  `XFEarthColumnData` is the sparse section preparation shared with the ordinary
  generator, and `XFEarthCubicStorage` is its native storage boundary. Do not pass
  extended section data to vanilla `Chunk` constructors or Anvil loaders.
- Stage 3's `XFEarthHeightSync.bind/unload/clear` lifecycle and client-thread
  `XFEarthHeightClient.getActiveProfile(world)` for accepted extended sessions.
  The format handshake reuses this complete profile and storage identity, with
  native CubicChunks codecs and renderer; it introduces no second geographic
  grid. The gameplay/installation gate remains closed through Stage 4. See
  [Stage 3 requirements and blockers](XFEARTH_WORLD_TYPE.md#extended-networking-and-rendering-bridge-stage-3).
- `XFEarthProfileLoader.forWorld(world)` on the owning thread for active Earth
  gameplay bounds, `containsBlockY` for inclusive block checks, and server-thread
  `XFEarthTeleportSafety.find` for Earth collision/terrain searches. World types
  and dimensions outside adopted Earth retain their existing bounds. Stage 4
  remains [blocked from activation](XFEARTH_WORLD_TYPE.md#gameplay-bounds-and-activation-blockers-stage-4)
  by the selected engine's global initialization and gameplay source gaps.
- `XFEarthMapData`, `EarthMapImages` and `EarthMapTileCache` for the existing
  offline imagery and optional JourneyMap import. MapData delegates coordinate
  calculations to the profile; neither integration chooses another Earth grid.
- Stage 5's pure `XFEarthGeoImporter.importDataset` reuses this profile for
  projection and clipping. `XFEarthGeoDataset.open` validates
  the full profile-bound identity; `readRegion` streams conservative candidates,
  and `XFEarthGeoGeometry.area` preserves the derived even-odd hole/multipart
  representation. Format/projection/classification versions are 1. Always retain
  source/license attribution and treat source-only coverage as unverified.
  Derived datasets are separate external world data, not default `.xfmap` payloads.
  See [Stage 5](XFEARTH_WORLD_TYPE.md#offline-geographic-data-stage-5) for limits
  and [import setup](XFEARTH_INSTALLATION.md#local-geographic-imports-stage-5).
- Stage 6's `XFEarthRoadSource` / `XFEarthRoadPlanner` share selected geometry,
  scale/width policy and grade rules across native raster preparation, previews
  and the Map source layer. `XFEarthWorkQueue` owns budgeted server-tick placement.
  Version-1 `XFEarthRoads` chunk stamps preserve pristine baselines and
  pending/completion identity; older exports without them remain protected.
  See [road routes](XFEARTH_WORLD_TYPE.md#road-routes-stage-6) and
  [manual backup/import commands](XFEARTH_INSTALLATION.md#road-generation-and-bounded-imports-stage-6).
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
