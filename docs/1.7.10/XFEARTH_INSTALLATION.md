# XenoFactions Earth installation

Install UniMixins 0.3.1 or newer alongside Xenofactions on both client and server,
including ordinary 256-height Earth installations. GTNHMixins supplies the early
save-preflight Mixin and selects the pinned CubicChunks adapters only when its
coremod is installed. CubicChunks remains optional and extended-height gameplay
remains disabled; installing it still causes ordinary Earth startup to reject
its global provider replacement. See the [injection architecture](XFEARTH_WORLD_TYPE.md#extended-networking-and-rendering-bridge-stage-3).

## Offline major roads for the bundled 1:16000 Earth

For approximate major roads, use **GRIP4 vectors**, not a full OSM planet or GRIP
road-density rasters. This workflow is independent of the optional OSM importer
below. The [GLOBIO source page](https://www.globio.info/download-grip-dataset) and
[PBL distribution](https://dataportaal.pbl.nl/GRIP4) identify type 1 highways,
type 2 primary roads, type 3 secondary roads, and the CC0 release. The global
[vector FGDB ZIP](https://dataportaal.pbl.nl/data/GRIP4/GRIP4_global_vector_fgdb.zip)
is listed as **2.2 GB**. GRIP4 is a historical, harmonized inventory from multiple
sources, unsuitable for navigation. Coverage, topology and positional accuracy
are limited; compressed-scale routes and widths are generalized.

Use an existing local ZIP or extracted `.gdb`; acquisition never runs in Minecraft.
The converter discovers actual layers containing `GP_RTP`, prints their names,
fields and CRS, and transforms the declared CRS to WGS84. It fails on missing
CRS/fields rather than assigning guessed values. `--inspect` prints the actual
input schema without preparing a mask; `--layer NAME` explicitly selects layers.
The publisher archive was not available during implementation: its exact internal
layer names/CRS and a real conversion remain unverified. No names are hardcoded.

**Short Windows workflow.** Run PowerShell from this repository. Use Miniforge
already installed at the usual user location; a separate `xf-grip` environment
keeps the existing Osmium environment unchanged. The Python/GDAL libraries are
offline tooling only; Minecraft still runs Java 8.

```powershell
& "$env:USERPROFILE\miniforge3\shell\condabin\conda-hook.ps1"
conda create -n xf-grip -c conda-forge --strict-channel-priority python=3.11 gdal=3.11 shapely=2 pillow=11 numpy=2 scipy=1
conda activate xf-grip
$repo = (Get-Location).Path
$work = "$env:USERPROFILE\EarthRoads"
$earth = "C:\Earth\MinecraftEarthMapXF" # existing external generator checkout
$profile = "$repo\versions\1.7.10\src\main\resources\assets\hfr\earthmaps\bundled\earth-16k-clean.xfmap"
```

1. **Download once.** Put the manually downloaded complete archive at
   `$work\GRIP4_global_vector_fgdb.zip`. Alternatively, opt into this downloader:

   ```powershell
   python tools/1.7.10/download_grip.py --output "$work\GRIP4_global_vector_fgdb.zip"
   ```

   It reuses a complete local archive, uses 20-second connection/socket timeouts,
   rejects transfers below 1 KiB/s over a 30-second window, and tries at most three
   times. `.part` and `.part.json` preserve resume position, length and available
   HTTP validators. Refused/changed ranges fail without appending incompatible
   content. HTTP errors, wrong lengths and ZIP CRC failures are errors. Move
   incompatible partials aside deliberately or download manually. `--sha256 HASH`
   verifies an actual published SHA-256 if available; the referenced publisher
   pages do not supply one, and no expected hash is invented. Local input needs
   no network request. A bounded metadata request timed out at TLS during
   implementation; no large acquisition was run to validate this tool.

2. **Prepare the mask.** Use the real installed profile JSON, generated profile
   JSON, or `.xfmap` containing it. This example reads the bundled profile directly:

   ```powershell
   python tools/1.7.10/prepare_grip_roads.py --source "$work\GRIP4_global_vector_fgdb.zip" --profile $profile --output "$work\smoke-roads"
   ```

   An extracted `.gdb` can replace the ZIP path. Highways and primary roads are
   selected by default; `--secondary` includes category 3. Defaults are
   `--width 1 --tolerance 0.5`: one symbolic block wide and half an output pixel
   simplification tolerance. Explicit odd widths 1..31 are supported. Two streaming
   passes and a temporary disk-backed shared-vertex index preserve supplied
   endpoints/junctions; they do not infer intersections or missing connections.
   Antimeridian segments split before projection; simplification follows projection
   into the profile's cropped grid. One pixel represents one X/Z column, with
   `(0,0)` at `(minimumX,minimumZ)`, east right and south down. No resize/flip is
   applied to the final mask. Priority is highway > primary > secondary; PNG sample
   values are 3, 2, 1 respectively, with 0 absent/unknown. Output is bounded to
   64 Mi pixels; larger profiles need a separate tiled preparation implementation.

   `roads.png` is the compressed full-grid categorical mask. `roads.json` records
   source identity/checksum, actual layers/CRS/metadata, categories, profile identity
   and full contract, dimensions/orientation, width/simplification policy, processing
   version, attribution and limitations. Small 256-pixel RGBA tiles are maximum
   pooled from that same mask for overview display, retaining thin routes and
   category priority. Their apparent widths are generalized too. Existing output
   directories are refused. Keep this directory: exports and resource packs can
   reuse it without reopening or downloading GRIP.

3. **WorldPainter preparation and new export.** The authoritative generator is in
   the separate [MinecraftEarthMapXF repository](https://github.com/RagexPrince683/MinecraftEarthMapXF):
   `tools/preprocess_xenoearth.py` prepares terrain, and `world_xenofactions_core.js`
   creates the project. Acquire that checkout and its referenced terrain assets
   through its README if absent. Prepare its matching terrain before running:

   ```powershell
   python "$earth\tools\preprocess_xenoearth.py" --profile=smoke
   wpscript "$repo\tools\1.7.10\world_xenofactions_grip.js" --sourceDirectory="$earth" --roadDirectory="$work\smoke-roads" --profile=smoke --material=cobblestone --exportDirectory="$work\smoke-grip-export"
   ```

   The inspected preprocessor resolves its root from its own file location.
   `wpscript` must be WorldPainter **2.27.0**, the external source's
   API target. The launcher loads the actual external core and inserts mask
   application at its single checked `wp.saveWorld(world)` boundary, after terrain
   and vegetation preparation. It evaluates the hook in memory: the external
   original is **not modified**, preserving its source fingerprints. Changed or
   absent save boundaries fail closed. It saves a new `_grip.world` project and
   refuses an existing file. The reusable `worldpainter_grip_roads.js` also exposes
   `XFRoads.apply(world, fullProfile, roadDirectory, material, vegetationLayers, waterLayers)`
   for other generation scripts after terrain preparation and before save/export.

   `--material=cobblestone` or `--material=gravel` is mandatory. XF registers asphalt
   as `hfr:tile.asphalt`, but this source has no verified legacy numeric WorldPainter
   export mapping; the old `street.layer` is not treated as one. Explicit vanilla
   materials avoid inventing an ID. The helper verifies PNG identity/dimensions,
   exact profile fields, legacy Anvil format and 0..255 height. It replaces surface
   terrain only, leaves elevations/water levels unchanged, skips water/ice/void and
   the generator's deferred Rivers layer (whose water is created during export), and
   clears the supplied generator vegetation layers only in painted road cells.
   Population settings and other cells remain intact. It does not engineer grades,
   bridges, tunnels or approaches. GRIP's road category alone cannot identify dry
   bridges/underground segments without structure flags. Where an input supplies
   verified structure attributes, use `--exclude-where` with its actual OGR SQL
   predicate; exclusions are retained in the manifest and no routes are invented.

   `--exportDirectory` creates a **new staging directory** and runs WorldPainter's
   export after project save; any existing directory is refused. Omit the option
   to save only the project for manual inspection/export. Format remains
   **Minecraft 1.2–1.12 / Anvil**, height 256, sea level unchanged. Never merge or
   overwrite an existing player world. Keep the generator's existing clean
   export/population settings and copy its matching generated profile into the
   exported save as `xenoearth-profile.json`. Export uses the existing WorldPainter
   operation; see its [scripting API](https://www.worldpainter.net/trac/wiki/Scripting/API).

4. **Map installation and map packs.** An overlay needs no WorldPainter export:

   ```powershell
   python tools/1.7.10/build_road_resource_pack.py --roads "$work\smoke-roads" --profile $profile --output "$work\GRIP-smoke.zip"
   ```

   Copy that ZIP to the client's `resourcepacks/` and enable it. It places the mask,
   manifest and display tiles at `assets/hfr/earthmaps/roads/smoke/`. The existing
   **Roads** toggle controls this independent GRIP overlay as well as available OSM
   vectors. GRIP credit/status remains separate from OSM-unavailable notices. Full
   profile/dimension/orientation checks reject incompatible packs. Terrain and
   `YOU ARE HERE` use the same transform; the player marker draws last. Textures
   share the existing 8 MiB/32-slot cache, decode on its bounded worker with at
   most 1 MiB per tile, upload/release on the client thread, and clear on world
   changes/resource reload. No geographic dataset selection or block placement is
   triggered by opening the Map.

   For a new terrain distribution, the inspected generator exports the save as
   `XenoEarth` inside the staging parent. Package it with the existing tool:

   ```powershell
   $save = "$work\smoke-grip-export\XenoEarth"
   Copy-Item "$earth\generated\xenoearth-profile-smoke.json" "$save\xenoearth-profile.json"
   $scale = (Get-Content "$save\xenoearth-profile.json" -Raw | ConvertFrom-Json).effectiveScale
   python tools/1.7.10/build_xfmap.py --input $save --output "$work\smoke-grip.xfmap" --id smoke-grip --display-name "Earth with GRIP roads" --population-mode clean-pregenerated --effective-scale $scale --roads "$work\smoke-roads"
   ```

   Place this `.xfmap` in `.minecraft/xenofactions/earthmaps/` and select it for a
   **new** Earth Map world through the existing installer. The normal verified
   payload/installer retains roads under `data/xfearth-roads/`; distribute the companion resource-pack ZIP to
   clients as well. It can be rebuilt from that installed directory without GRIP.
   Server saves are not client resources and the mod does not transfer these
   images over gameplay packets. The overlay displays source routes even when a
   historical world has no matching blocks. **Existing worlds require a separate
   supported application path**: this workflow only paints new WorldPainter
   projects; it adds no GRIP live-world block importer and does not bypass the
   existing native baseline, protection, preview or manual-backup safeguards.

Implementation validation: pinned offline Forge 1.7.10 `compileJava` succeeded.
Python/JavaScript parsing and direct projection/antimeridian arithmetic were
checked. No real GRIP conversion, WorldPainter execution/export, packaging of a
real road dataset, runtime launch or in-game rendering is claimed.

## Bundled default 1:16000 map and missing OSM data

The bundled Clean and Populated packs install pregenerated terrain with the
`smoke` profile (2688 x 1344, scale 1:16000). Neither distributed archive contains
`source.json`, `features.geojsonseq`, `dataset.json`, `features.dat` or geographic
region indices. The bundled background imagery is terrain land cover, not OSM.
Selecting 1:16000 installs terrain; it does not acquire, import or select OSM.
The displayed scale and attribution do not establish that an import exists.
Prepared GRIP roads above provide an independent major-road alternative; they
do not populate the OSM dataset or enable the other OSM feature layers.

`Geographic data unavailable: no completed dataset selected` means a missing
dataset selection, not an empty geographic query or a layer/zoom setting. Source
preparation remains external. For the optional OSM vector layers, the supported setup is:

1. Acquire a regional OSM extract and export the local sequence and provenance
   described below under `earthGeographicSourceDirectory/<dataset-id>/`.
2. In the adopted Earth world, run `/xc earth geo import <dataset-id>` as a
   level-4 operator. `/xc earth geo status` reports the job and setup locations;
   wait for successful completed publication.
3. Run `/xc earth geo select <dataset-id>`. This validates the completed dataset
   against this world's full profile and saves the explicit choice. Importing
   alone does not change an administrator's selection, even with one dataset.
4. The Map source layers use that selection independently of block generation.
   Missing/incomplete/incompatible data produces a reason and setup command;
   an empty successful query still means unverified source coverage.

The selection record is `<save>/data/xfearth-geographic-selection.json`; it
retains the canonical dataset path and immutable identity across save/restart.
Keep the selected external files available. A nonblank `earthRoadDatasetDirectory`
in `XENOFACTIONS_18_EARTH_WORLD` remains the explicit administrator override.
To use per-world selection, clear that override in `hfr.cfg` and restart first;
`geo select` refuses to silently replace it. No dataset discovery guesses among
multiple datasets. A rejected selection leaves the previous choice intact.
Accepted choices cancel stale block previews, invalidate server map work/cache
and notify connected overworld clients to release vector frames/display lists.
An open Map requests a fresh view; optional adapters discard the old frame.

**Blocks are separate:** opening the Map, loading a world and selecting a
dataset place no blocks. Inspection of every chunk in both distributed packs
found no `XFEarthRoads` trusted native baseline tag. The existing-world commands
below support authorized application only to eligible native baseline chunks;
the bundled historical chunks are excluded, with no force option or invented
baseline. OSM overlays remain usable on these packs. The only current native
new-terrain path is external-raster **earth2000** pregeneration, requiring its
own exactly matching dataset; it cannot regenerate the 1:16000 smoke world.
WorldPainter regeneration is not a prerequisite for source overlays and is not
a repair for this dataset-selection problem.

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
/xc earth geo select regional-snapshot
```

With `earthGeographicDataDirectory` blank, output is
`<save>/data/xfearth-geography/<dataset-id>/`. A configured external root uses
`<root>/<SHA-256 of canonical save path>/<dataset-id>/` to isolate saves; moving
a save changes that namespace. Output anywhere inside a Git workspace is refused,
including ignored development saves. Configure an external root for development.
Import never overwrites an existing destination and never automatically selects
its output. Successful completion reports `geo select <dataset-id>`. Incomplete
directories retain their `.importing-<UUID>` suffix and failure details; there is no resume or automatic
cleanup. Cancellation also occurs on owning-world unload or server stop.

Keep `dataset.json` and its source/license/attribution metadata with any distributed
derived files. `geo info` exposes that attribution and snapshot identity. Later
consumers must display source credit and interpret empty regions as **unknown
coverage**, not confirmed absence. Imports place no blocks and create no claims.
They operate on ordinary 256-block Earth worlds; the extended-height gate remains
closed. Supported geometry, limits and reader APIs are documented in
[the Stage 5 architecture](XFEARTH_WORLD_TYPE.md#offline-geographic-data-stage-5).

## Road generation and bounded imports (Stage 6)

Use `/xc earth geo select <dataset-id>` after import, or set
`earthRoadDatasetDirectory` in `XENOFACTIONS_18_EARTH_WORLD` to the complete
Stage 5 dataset directory containing `dataset.json`, `features.dat` and `regions/`.
Blank uses the saved per-world choice; without either, source data is unavailable.
The full profile identity must match the target Earth world. A mismatch names
the differing profile field; import under a new ID in the intended world or
correct the override. Native earth2000 pregeneration uses the explicit config
override, requires an earth2000 dataset, and records that selection in the output
save for subsequent Map and preview use.
Keep the dataset and its full source attribution with distributions of derived
road terrain. The existing Map item uses the same selected source and filtering
policy for its road layer, with OSM/ODbL credit and unverified-coverage notice.

For **new** terrain, set the explicit `earthRoadDatasetDirectory` override to a
matching earth2000 dataset before `/xc earth pregen start earth2000`.
Road planning follows raster surface/water preparation, and budgeted road edits
precede chunk construction, lighting and Anvil serialization. This project has
no live Earth terrain generator: its in-game provider only guards pregenerated
chunks. Roads are therefore automatic in new raster pregeneration, not retroactive
population on chunk load or `.xfmap` installation. A partial pregeneration rejects
a changed road dataset/policy. No existing world is regenerated for this feature.

Source/dataset verification runs on the pregenerator worker before terrain
preparation; `pregen status` reports preparation failures. It does not parse the
geographic manifest or hash the raster package on the server tick thread.

At the default 1:2000 scale, only motorway, trunk, primary and secondary routes
(including links) are used. Below 1:1000, tertiary routes are also included; below
1:250, supported local streets/tracks/paths are included. At 1:4000 or coarser only
motorway/trunk remain. `earthRoadCoarseScale` changes the 1:1000 threshold and its
four-times threshold. Major routes have a minimum visual width of three blocks,
other routes one; `width` in metres supplies a nominal scale-based estimate,
capped by `earthRoadMaxWidth` (default 7, rounded down to odd). These widths are
exaggerated at coarse scales, not exact street-scale replicas. Asphalt uses XF's
existing speed block; unpaved/track/path surfaces use vanilla gravel. Roads with
area geometry alone have no inferred centerline.

For the bundled **1:16000** profile with default `earthRoadCoarseScale=1000`,
only motorway/trunk and their links survive source-road filtering. Their actual
source centerlines are retained; the existing block road policy uses symbolic
minimum widths, not surveyed road footprints. Primary/secondary/local roads
are filtered at this scale; importing data does not change that policy.

Surface roads skip water/ice, obstructed columns and excessive slopes. Bridge,
tunnel and unresolved nonzero-layer source segments are retained on the Map layer
but **not built**: this source has no trusted deck/tunnel elevation or approaches.
No missing connection or crossing is fabricated. Defaults limit cuts/fills to one
block and neighbouring grade changes to one; configure `earthRoadMaxCut`,
`earthRoadMaxFill` (0..4) and `earthRoadMaxSlope` (0..2). Terrain/protection gaps and
coarse projection overlaps remain possible. See the
[Stage 6 architecture and limits](XFEARTH_WORLD_TYPE.md#road-routes-stage-6).

Existing-world application is disabled by default (`earthRoadExistingImports=false`).
An in-world level-4 operator can preview without enabling application or acknowledging
a backup. Preview uses chunk coordinates, at most `earthRoadMaxImportChunks`
(default 16, maximum 64), and only already loaded chunks:

```text
/xc earth roads preview <minChunkX> <minChunkZ> <maxChunkX> <maxChunkZ>
/xc earth roads status
/xc earth roads inspect <job-token>
/xc earth roads inspect <job-token> <changed-block-offset>
```

`inspect` lists chunk exclusions/counts and pages through the actual planned block
changes, twelve at a time. The token expires five minutes after preview completion.
Historical chunks without XF's native pristine baseline, edited chunks, completed
or interrupted road chunks and chunks containing tile entities are skipped. There
is no force/override option. All claimed/protected territory uses the existing
dimension-aware claim lookup; expected blocks and baseline identity are rechecked
before placement. This conservative policy may produce an entirely skipped preview
on older exports, including current bundled packs.

Use the existing **manual world/config backup process** before applying. Prefer a
stopped-server backup, then start the server and create the preview. No backup
command or dependency is added, and XF cannot verify that the backup exists or is
current. After enabling `earthRoadExistingImports`, inspecting the preview and
confirming that the manual backup is current, the same operator uses:

```text
/xc earth roads backup <job-token>
/xc earth roads apply <job-token>
/xc earth roads cancel
```

`backup` is an explicit operator statement, logged and bound to this operator,
world and preview job. Application requires that acknowledgement, matching dataset
and policy, the current token and continuing operator/world/session permission.
Restart, world unload or cancellation invalidates it. Cancellation **does not undo
blocks already placed**; restore the manual backup if rollback is required.
Incomplete chunks retain pending markers and cannot silently replay.

New-terrain road edits and existing-world reads/placement share
`earthRoadStepsPerTick` (default 1,024) and `earthRoadTickMillis` (default 2 ms).
Time is checked between bounded steps; a Minecraft block/light callback can exceed
that time. No import force-loads its rectangle. Map requests stream a bounded source
viewport (at most 16 regions / 4,096 segments); zoom in to view roads on larger maps.
The Map source layer does not certify placement success, complete source coverage
or functioning bridge/tunnel crossings.

## Fields, buildings and businesses (Stage 7)

Stage 7 uses the **same** `earthRoadDatasetDirectory`, profile, tick budget and
pristine-chunk protection as roads. `earthGenerateGeographicFeatures=true` enables
the new-terrain pass when a dataset is selected; no dataset still means no
geographic generation. Set it false to continue an older partial pregeneration
without adding features. Resume rejects changed feature identity/policy.

Supported farmland becomes a generic dirt/grass row pattern, meadow grass and
farmyard gravel. At or below `earthFeatureBuildingMaxScale` (default 100), an
explicit `crop=wheat`, carrot(s), potato(es) or rice tag selects existing crops.
Missing/unsupported crops keep the generic field appearance; irrigation and farm
buildings are not inferred from farmland. Dry farmland/crop ticking remains normal
Minecraft behavior. Rice reuses XF's existing crop, with no optional mod needed.

Representable source building polygons become generic shells. Explicit barn,
farm/farm_auxiliary, stable, cowshed and shed tags select timber; greenhouse glass;
other buildings stone brick. Supported shops/food/lodging select brick and
offices/crafts timber. There are no generated machines, inventories or businesses
with new gameplay logic. Appearance, windows and a basic opening are procedural.
Building levels only bound a generic height; they do not reconstruct architecture.
At coarse scales, representable polygons use low generic shells and small buildings
remain information only. Fine-scale supported business points can produce a
procedural 3-by-3 kiosk around their actual source cell, not a surveyed footprint.

Structures require at least nine whole footprint cells and an interior cell,
dry near-level terrain, profile height clearance and a projected span no greater
than `earthFeatureMaxSpan` (default/max 32). `earthFeatureMaxHeight` defaults to 9
(3..16); the detail threshold is configurable 1..250. Water, polygon holes and
selected imported road corridors remain clear. No driveway is invented: source
road contact can leave an opening, while missing mapped entrances/access ways
remain unresolved. Source entrance-only nodes were not imported by Stage 5.

Fields can span chunks. New native terrain also supports structure fragments
across chunks; existing-world previews conservatively skip structures that span
chunks, avoiding a protected/unloaded half-building. Source-building footprints
take priority over business kiosks, then farmyards, farmland and meadows; source
keys break ties. Reserved higher-priority footprints can leave gaps in lower-priority
features. At earth2000 most ordinary buildings are smaller than one block: they
are not enlarged or relocated to make a replica.

At **1:16000**, farmland/meadow/farmyard need at least one complete projected
block cell inside the source polygon, excluding holes and road corridors.
Orchards/vineyards remain source outlines. Building and supported business
polygons still need width/depth 3..32 blocks (default span limit), at least nine
whole cells and an interior cell; ordinary small buildings remain information
only. There are no point kiosks or detailed crops at this scale under the default
`earthFeatureBuildingMaxScale=100`; representable shells are at most three
blocks high. No source polygon is enlarged or moved to become representable.

Existing-world feature application is separately off by default
(`earthFeatureExistingImports=false`). The **same administrator job system** handles:

```text
/xc earth features preview <minChunkX> <minChunkZ> <maxChunkX> <maxChunkZ>
/xc earth features status
/xc earth features inspect <job-token> [changed-block-offset]
/xc earth features backup <job-token>
/xc earth features apply <job-token>
/xc earth features cancel
```

Use the current manual world backup process, then inspect and acknowledge it for
this operator/world/job before application. This acknowledgement is not automated
backup verification. The Stage 6 limits, five-minute tokens, expected-block checks,
claim/tile/build protections and no-force-load rule apply unchanged. Untouched
Stage 6 native chunks can receive a feature pass despite completed roads; feature
completion is separate. Historical chunks without trusted baselines remain blocked.
Cancellation does not undo placed blocks.

The existing Map source layer includes agricultural/building outlines, holes and
business-point symbols. Brighter styles reflect recorded generated fragments in
loaded chunks under the matching policy, not verified whole-feature completion or
current block condition. Stage 10 supersedes the original primitive split with
typed feature/vertex bounds and independent layer controls described below.
Unknown land use, undersized/oversized or
elevated buildings, unsupported business categories and coarse business points
remain source information. No satellite reconstruction or exact architecture is
claimed. See [Stage 7 implementation](XFEARTH_WORLD_TYPE.md#geographic-fields-and-structures-stage-7).

## Claim nodes and terrain eligibility (Stages 8–9)

Stage 8 claim node metadata is added automatically to the existing faction save
on normal server binding/saving; no claim conversion command or geographic
dataset is required. Back up the save before updating. Use matching updated XF
client/server versions for the Map node styles. The Ownership and Eligibility
controls can show up to 64 owned/tracked nodes with no selected dataset; cyan is
pending/unavailable, green eligible and red insufficient surface. The open Map
refreshes its viewport every 40 client ticks. New claims require actual terrain classification;
existing ownership is not reset. No extended-height mode is enabled.

In `XENOFACTIONS_05_CLAIMS_CITIES`, Stage 9 defaults are `claimTerrainMinArea=64`,
`claimTerrainCoveragePercent=25`, `claimTerrainMaxStep=1`,
`claimTerrainMaxAgeTicks=100` and `claimTerrainRefreshNodes=2`. Capture shares the
existing `earthRoadStepsPerTick` / `earthRoadTickMillis` budget. Required chunks
must already be loaded; first/stale placement attempts defer without consuming
the City Center item. Wait nearby while sampling, then retry placement. Retries
share pending work; missing chunks and capture failures give specific reasons.
Unrelated chunk unloads do not discard a completed sample. Protected/rejected
placements retain the item and do not charge founding prestige.
`/c city terrain` shows the reason/counts; an officer/leader
can use `/c city terrain retry` inside their city after reclamation or to restart
deferred expansion following a restart. Ocean neighbors must qualify separately.
Review [exact rules and bulk-edit limits](XFEARTH_WORLD_TYPE.md#terrain-claim-eligibility-stage-9)
before deployment; real platforms, save/reopen and modded collision behavior still
need runtime validation.

## Geographic Map overview (Stage 10)

Open the existing Map item in an adopted Earth world. Toggle Roads, Fields,
Buildings, Businesses, Admin, Ownership and Eligibility independently; zoom in
when the query exceeds 16 regions. Drag and mouse-wheel zoom retain the player
marker/coordinate alignment. Click to select a feature and repeat a click to cycle
overlaps. **Info** opens pageable source attribution, notices, legend and selection
details. No internet access or JourneyMap installation is needed.

Use matching updated XF client/server builds: geographic packet protocol is now
version 2. Missing geographic data is marked unavailable, while public node layers
remain functional. Missing base imagery leaves a neutral background with marker
and available vectors. The overview shows up to 128 features / 4,096 vertices,
including at most 64 tracked/owned nodes; dense data is simplified, omitted or
reported truncated. Toggle fewer layers or zoom in for detail. Admin boundaries
are informational. Ownership fills use public faction/zone colors; eligibility
outlines are cyan pending, green eligible and red insufficient surface. Gray means
wilderness. Brighter geographic features indicate loaded-chunk generation records,
not a survey of current blocks or proof of whole-feature completion. Source
buildings/businesses do not imply exact architecture or complete coverage.

Optional JourneyMap adapters reuse the last accepted Xenofactions Map viewport and
layer mask, not an independent whole-world dataset. The native API
1.7.10-2.0.0 polygon path and legacy 5.2 reflection path are source-inspected and
compile, but added overlay rendering has no runtime validation. Dynmap stays
optional. The Map remains fully usable without either integration.

Nothing in this display enables extended height, grants terrain eligibility from
geography, changes claim permissions or modifies existing-world import safeguards.
The existing manual-backup acknowledgement and reviewed preview requirements
remain mandatory for enabled administrator block imports; opening the map performs
no block placement. See [Stage 10 interfaces, bounds and remaining validation](XFEARTH_WORLD_TYPE.md#geographic-overview-stage-10).

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
