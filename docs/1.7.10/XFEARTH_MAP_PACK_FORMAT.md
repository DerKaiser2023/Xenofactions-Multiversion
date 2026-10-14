# `.xfmap` format and installation

An `.xfmap` is a ZIP64-compatible archive containing
`earth-map-pack.json`, `xenoearth-profile.json`, `template-level.dat`, `region/`,
and optionally `data/`, `DIM-1/`, and `DIM1/`. The manifest identifies format,
pack/version, Minecraft `1.7.10`, scale, chunk-aligned dimensions, population
mode, required content mod IDs, installed size, and every payload file's path,
size, and SHA-256. The profile is authoritative and must agree with it.

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
