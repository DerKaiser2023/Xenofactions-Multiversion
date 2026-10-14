# XenoFactions Earth installation

## Native external-raster earth2000 generation

XenoFactions can now generate the clean 1:2000 overworld without WorldPainter. The five source PNGs remain external: they are neither copied into `src/main/resources` nor included in the normal JAR. Put an exported source package at `xenofactions/earthmaps/sources/earth2000/` under the server root, or set `earthSourceDirectory` to another source-root directory. The package must contain `HeightMap20k.png`, `BiomeMap20k.png`, `WaterMap20k.png`, `Ice20k.png`, `globecover20k.png`, and `xenoearth-source.json`.

Create the package from a MinecraftEarthMapXF checkout with:

```sh
python tools/1.7.10/build_xfearth_source.py --profile earth2000 --images images --output generated/earth2000-source
```

The exporter validates each PNG's 21,504 x 10,752 dimensions and records its SHA-256. On the server, `/xc earth source status` shows the configured location and `/xc earth source verify earth2000` checks the complete manifest, exact profile geometry, dimensions, and every hash before generation is allowed.

Run the pregenerator from a temporary, non-Earth bootstrap server as a level-4 operator:

```text
/xc earth pregen start earth2000
/xc earth pregen status
/xc earth pregen pause
/xc earth pregen resume
/xc earth pregen cancel
```

Raster decoding and preparation run on a single worker with at most one prepared chunk awaiting the server. Minecraft chunk construction, Forge block/lighting callbacks and world-dependent Anvil serialization run through the existing server tick queue. Only detached NBT disk flushing, region-file verification and metadata file processing run on the worker. World settings are captured on the server before the job starts; server stop or bootstrap-overworld unload cancels queued work. This is partial background processing, not asynchronous Minecraft world generation. It visits regions deterministically from `r.-21.-11.mca` through `r.20.10.mca`, clips the edge regions, and writes all 903,168 chunks (1,344 by 672). Server-thread work can affect tick time and generation throughput; no performance measurement is available. Raster access uses a configurable bounded LRU of 512 x 512 source tiles. Clean generation includes height/bathymetry, water, biome IDs, GlobCover surfaces, rivers, ice, bedrock, filler and stone; population, vegetation, caves, ores, lava and structures remain disabled.

The canonical `XFEarthProfile.earth2000()` supplies bounds, horizontal conversion, sea level and vertical decoding to this path and its output profile. Existing source encoding and generator version remain unchanged. Preparation now uses nullable native block sections rather than a fixed-height flat buffer; the command continues to produce ordinary Anvil worlds. Output explicitly records `minimumY=0`, `maximumY=255`, `heightImplementation=vanilla-anvil`, `heightImplementationVersion=1.7.10` and `verticalElevationScale=1.0`, plus a matching `data/xenoearth_storage.dat` identity. Existing packs default to these profile values without rewriting saves. Taller worlds remain disabled through Stage 4; the [Stage 2 storage API](XFEARTH_WORLD_TYPE.md#extended-storage-preparation-stage-2) is preparation-only. The [Stage 3 protocol/renderer bridge](XFEARTH_WORLD_TYPE.md#extended-networking-and-rendering-bridge-stage-3) is gated and has not been runtime-validated; its exact peer requirements and Angelica rejection do not enable extended gameplay. An active CubicChunks coremod is rejected by this ordinary pregenerator and by ordinary Earth startup because the pinned build replaces providers globally. RegionLib remains optional and is needed only for detached cubic storage preparation on the ordinary path.

Output first goes to `earth2000.xfearth-generating`. Its ownership marker and `xfearth-pregen.json` record the manifest hash, generator version, current/completed regions, chunk totals and timestamps. Pause/cancel/interruption never marks it complete. Resume rejects changed sources or generator versions. XF never deletes this partial directory and never overwrites an existing `earth2000` world. After generation, XF verifies every in-bounds chunk, writes `xenoearth-profile.json`, a 1.7.10 `level.dat`, and installation metadata, removes the ownership marker, and atomically renames the directory to `earth2000`. Set `level-name=earth2000` and `level-type=earthmap`, then restart the server.

This staged workflow deliberately avoids trying to create 903,168 chunks during Forge's overworld construction. Previously Singleplayer provisioned a selected `.xfmap` before starting its integrated server, while a dedicated server had no equivalent pre-start UI and entered the strict missing-profile/missing-chunk path. The bootstrap-server workflow supplies that missing server-side provisioning step safely. Existing `.xfmap` installation remains supported and unchanged.

For existing map packs, `earthmap` remains a guard around an **already pregenerated** Minecraft 1.7.10 Anvil world. A WorldPainter `.world` project is not a Minecraft save. Existing exported chunks continue to be loaded by Minecraft's Anvil loader; the provider only handles absent chunks and prevents vanilla terrain appearing around or inside the map.

## Local geographic imports (Stage 5)

The Forge 1.7.10 importer accepts a **local Osmium GeoJSON sequence**, not a PBF,
OSM XML file or GeoJSON FeatureCollection. Prepare a regional extract outside
Minecraft; no source acquisition occurs in the mod. OSM's editing API policy
directs bulk consumers toward extracts rather than repeated editing-API queries.
Retain OpenStreetMap attribution and the ODbL notice when distributing derived
data. See [OSM copyright](https://www.openstreetmap.org/copyright) and the
[API policy](https://operations.osmfoundation.org/policies/api/).

For an already acquired regional PBF, an external
[osmium-tool](https://docs.osmcode.org/osmium/latest/osmium-export.html) export is:

```sh
osmium export regional.osm.pbf -f geojsonseq -i sparse_file_array -a type,id,version,timestamp,changeset,way_nodes -u type_id -e -o features.geojsonseq 2> export-errors.log
```

This command is preparation guidance, not a tool bundled or executed by XF.
The file-backed node index reduces the exporter's node-cache RAM requirement;
its relation processing still has independent memory requirements. Inspect the
export log: Osmium can omit invalid geometry and relations it cannot export.
Record those limitations in the source metadata. Do not substitute a full-history
extract. Reserved `@` tag names can collide with exporter attributes; XF cannot
recover tags or features omitted upstream. RFC 8142 record separators or one
complete feature per line are accepted, using WGS84 longitude/latitude pairs.

Place these two files in
`xenofactions/earthmaps/geographic-sources/<dataset-id>/` under the game/server
directory, or under the configured `earthGeographicSourceDirectory`:

- `features.geojsonseq`, including `@type`, `@id` and `@timestamp` properties
  from the original OSM object. Feature export IDs alone are insufficient.
- `source.json`, with the following schema. Replace every angle-bracket value
  with the actual provenance; the example does not describe an existing dataset.

```json
{
  "formatVersion": 1,
  "datasetId": "regional-snapshot",
  "source": "OpenStreetMap",
  "sourceFormat": "osmium-geojsonseq",
  "snapshotTimestamp": "<actual ISO-8601 UTC snapshot timestamp>",
  "sourceUrl": "<actual HTTPS regional extract provenance URL>",
  "attribution": "© OpenStreetMap contributors",
  "license": "ODbL-1.0",
  "licenseUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
  "exporter": "osmium-tool <actual installed version>"
}
```

`datasetId` must match its folder and contain 1..64 lowercase letters, digits,
underscores or hyphens, starting with a letter or digit. Optional `extractSha256`
records the actual 64-hex-digit PBF checksum; XF verifies the input sequence's own
SHA-256 separately. Additional
metadata, such as extract coverage and upstream export errors, is retained.
Provenance URLs are recorded without fetching them. Preserve the full attribution
text required by the extract provider, including the OSM contributor notice.

Run as a level-4 operator in an adopted Earth overworld:

```text
/xc earth geo import regional-snapshot
/xc earth geo status
/xc earth geo cancel
/xc earth geo info regional-snapshot
```

With `earthGeographicDataDirectory` blank, output is
`<save>/data/xfearth-geography/<dataset-id>/`. A configured external root uses
`<root>/<SHA-256 of canonical save path>/<dataset-id>/` to isolate saves; moving
a save changes that namespace. Output anywhere inside a Git workspace is refused,
including ignored development saves. Configure an external root for development.
Import never overwrites an existing destination. Incomplete directories retain
their `.importing-<UUID>` suffix and failure details; there is no resume or automatic
cleanup. Cancellation also occurs on owning-world unload or server stop.

Keep `dataset.json` and its source/license/attribution metadata with any distributed
derived files. `geo info` exposes that attribution and snapshot identity. Later
consumers must display source credit and interpret empty regions as **unknown
coverage**, not confirmed absence. Imports place no blocks and create no claims.
They operate on ordinary 256-block Earth worlds; the extended-height gate remains
closed. Supported geometry, limits and reader APIs are documented in
[the Stage 5 architecture](XFEARTH_WORLD_TYPE.md#offline-geographic-data-stage-5).

## Smoke-map adoption

1. Run the WorldPainter smoke profile and export it as a Minecraft 1.7.10 Anvil save.
2. Copy `xenoearth-profile-smoke.json` into the exported save root as `xenoearth-profile.json`.
3. With an NBT editor, set these values in the exported `level.dat`:
   ```text
   Data.generatorName=xf_earth
   Data.generatorVersion=0
   Data.generatorOptions=
   ```
4. On a dedicated server, set `level-type=xf_earth` in `server.properties`.
5. Copy the save into the directory selected by the server's `level-name`.
6. Back up the world before starting it.
7. Start the server and run `/xc earth status` as an operator.
8. Test the smoke map before preview or production.
9. Never start a production Earth world with an empty or incomplete `region` directory.

The default `FAIL` policy deliberately stops access when an expected in-bounds chunk is absent. `VOID` is an explicit diagnostic escape hatch, not a repair. `/xc earth check <chunkX> <chunkZ>` requires integer chunk coordinates. Region-file existence reported by the command does not prove that a particular chunk is present in that region.

Production save data, `.world` projects, and `.mca` files are deployed separately and must not be committed to Xenofactions or bundled in its jar.

## New map-pack workflow

Stage 4 has **not** enabled extended-height creation. The selected native engine
still lacks Earth-only isolation and has confirmed entity/placement source gaps;
installing its dependencies or changing a profile to maximumY=511 does not make
that mode available. The existing pregenerator/installer still produce ordinary
Anvil worlds. See [Stage 4 bounds changes and activation blockers](XFEARTH_WORLD_TYPE.md#gameplay-bounds-and-activation-blockers-stage-4).

The supported `earthmap` Singleplayer installer, `.xfmap` security model, local-pack
workflow, and bundled release procedure are documented in
[XFEARTH_WORLD_TYPE.md](XFEARTH_WORLD_TYPE.md),
[XFEARTH_MAP_PACK_FORMAT.md](XFEARTH_MAP_PACK_FORMAT.md), and
[XFEARTH_BUNDLED_RELEASE.md](XFEARTH_BUNDLED_RELEASE.md). The older manual
WorldPainter adoption notes above remain useful only for pre-pack saves.
