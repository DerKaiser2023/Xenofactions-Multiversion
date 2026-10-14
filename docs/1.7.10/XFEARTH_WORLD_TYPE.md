# Earth Map world type

## Singleplayer

Choose **Singleplayer → Create New World → More World Options → World Type:
Earth Map → Customize**, select a pack, and create the world. The clean pack is
the default. The create button is intercepted only for `earthmap`; installation
runs on a worker thread and the integrated server starts only after the temporary
save is verified and finalized.

**Clean** is a lightweight 1:16000, 2688×1344 export with primarily solid stone
underground and no Minecraft population. **Populated** is heavier and
modpack-dependent: it retains ores, pools, deposits, and compatible mod plants.
Both are pregenerated and runtime population is always disabled.

> **Do not distribute the populated map until its required-mod list and Forge
> registry preservation have been tested in a clean installation of the intended
> modpack.**

The installed `level.dat` is based on the template, not a new vanilla tag tree.
Consequently top-level Forge/FML registry tags survive. The installer removes
`Data.Player`, patches `LevelName`, `RandomSeed`, `GameType`, `hardcore`,
`allowCommands`, `MapFeatures`, `generatorName`, `generatorVersion`,
`generatorOptions`, and `bonusChest`, and normalizes time/weather metadata.

Inside profile bounds an absent chunk raises a detailed corruption exception.
Outside bounds the fallback contains bedrock at Y=0 and air above. It never
populates, decorates, creates structures, or delegates to vanilla generation.
`PROFILE` border mode applies the authoritative dimension-0 profile bounds;
`CONFIG` leaves configured border values intact, and `OFF` disables Earth-specific
border adoption. Nether and End are untouched.

## Dedicated servers

Install/extract and verify a pack while the world is offline, then set
`level-type=earthmap`. Use `/xc earth status`, `/xc earth packs`, or
`/xc earth verify <pack-id>` for administration; verification never downloads or
extracts a live world. Clients receive ordinary chunks and never need or receive
the archive.

## Map item (Forge 1.7.10)

The Xenofactions creative tab contains **Map**, registry ID `hfr:earth_map`.
Right-click it to open the entire active Earth map. A red marker labelled
**YOU ARE HERE** reads the current player X/Z every frame. Mouse wheel and the
`+`/`-` buttons zoom; left-drag pans; **Center on you** centers the current player
position, including near an edge; **Full map** restores the initial view.
The screen does not pause singleplayer. The existing Claim Map and vanilla maps
are unchanged, and the new item has no recipe or JourneyMap requirement.

The server supplies the validated active `xenoearth-profile.json` description
through read-only packets on its tick thread. Only an adopted `earthmap`
overworld (dimension 0) is available; Nether, End and other worlds display an
unavailable message. Client GUI/rendering stays behind the sided proxy. Replies
are checked against the request and current client world, and dimension changes
discard the old description. Clients and servers must use matching builds.

Map coordinates are `(X - minimumX) / width` and `(Z - minimumZ) / height`.
Bounds identify inclusive blocks; their continuous image extent ends at
`maximumX + 1` and `maximumZ + 1`. The raster origin is the northwestern block,
columns increase eastward (+X), and rows increase southward (+Z). No image flip
or rotation is applied. Images and markers use the same viewport transform for
zooming and dragging. Offscreen positions have an explicitly labelled edge
indicator. Outside-profile positions show an outside-bounds message.

For `equirectangular` profiles with a positive effective scale, longitude is
`(X - minimumX - width/2) * effectiveScale / 120000`; latitude is
`-(Z - minimumZ - height/2) * effectiveScale / 120000`. This matches the original
source's coordinate calculator and preprocessing reference rectangles for the
Alps, Andes, Himalaya, Rockies and Mariana. The source README documents symmetric
cropping from 43200×21600 to 43008×21504: the standard profiles cover
358.4°×179.2°, so stretching their bounds to ±180°/±90° would misalign positions.
Unknown projections retain X/Z and omit geographic coordinates.

The marker follows the actual synchronized player position after server border
wrapping. It does not apply a second modulo: the border's inclusive `max-min`
wrap span, safety margin, CONFIG/OFF modes and exemption regions can differ from
the raster extent. This preserves the existing border behavior and avoids
displaying an exempt outside player on the opposite continent.

### Offline imagery and reusable map data

Prepared land-cover imagery is bundled under
`assets/hfr/earthmaps/images/raster-{10,20,40}k/`. It comes from the existing
MinecraftEarthMapXF terrain inputs `globecover10k.png`, `globecover20k.png`, and
`globecover40k.png`, not Minecraft chunk scans or online maps. The original
MinecraftEarthMapXF README attributes GlobCover to ESA
(`http://due.esrin.esa.int/page_globcover.php`). These images show the terrain
source geography and land-cover colors, rather than current player buildings,
claims or a pixel-perfect rendered block surface. Terrain preprocessing may
clean surface colors without changing horizontal geography.

`tools/1.7.10/build_xfearth_source.py` now also prepares a display pyramid offline
using Pillow (already used by the original Earth preparation tooling):

```sh
python tools/1.7.10/build_xfearth_source.py --map-tiles-only --map-raster /path/to/MinecraftEarthMapXF/images/globecover10k.png --map-id raster-10k --output /empty/output/raster-10k
```

Repeat for 20k/40k to reproduce those assets. Each manifest records the source
filename, SHA-256, source dimensions, orientation, 256-pixel tile size, and three
display levels with longest sides 512, 1024 and 2048 pixels. Categorical colors
use nearest-neighbor sampling. The stock image source dimensions multiplied by
the active profile's `resize/100` must match its width/height; profile names and
an `earth4000` size are never used as bounds. The bundled smoke packs and
earth8000/4000 use the 10k source; earth2000 uses 20k; production uses 40k.

For another raster or a rectangular/custom profile, prepare the raster on that
profile's final grid with the same command and a distinct safe lowercase
`--map-id`. Add optional `"mapImageId": "that-id"` to its existing profile and
distribute `map-image.json` and its tile directories in a client resource pack at
`assets/hfr/earthmaps/images/that-id/`. Custom images must match the final profile
dimensions, aspect ratio and X-east/Z-south orientation. Missing or mismatched
imagery displays a clear unavailable message instead of substituting unrelated
geography. Geographic conversion assumes the profile's centered source origin;
another geographic projection needs its own supported converter.

The common `XFEarthProfile` owns bounds/projection conversion, and
`XFEarthMapData` delegates to it. The client
`EarthMapImages` and `EarthMapTileCache` provide reusable image metadata and tiles;
the JourneyMap preload uses the same metadata and projection, independently of
the item GUI. The item does not depend on JourneyMap.
Cache keys include server map identity (profile plus save/install identity) and
source image SHA-256. One bounded worker decodes only requested PNG tiles;
uploads and GL changes run on the client thread. The cache caps RGBA texture
storage at 8 MiB and 32 fixed registry slots. Eviction deletes GL storage and
replaces the registered texture with the shared fallback, releasing the old
Java pixel array as well. World unload, disconnect and resource reload cancel
pending reads, reject stale uploads and release cached textures. Resource reload
also reloads the open screen's image metadata. No world generation, gameplay
download, new game dependency, or 1.16.5 implementation is involved.

Source checks and tile validation establish the projection/extent relationship;
they do not establish in-game accuracy. Runtime checks remain necessary for
dedicated-server opening, movement and wrap teleports, all supported scales,
edge/offscreen indicators, zoom/drag/centering, dimensions, resource reload and
disconnect/reconnect texture cleanup.

### Optional JourneyMap Earth preload

Entering an Earth overworld automatically requests the same server-authoritative
profile used by the Map item, even without opening the item. This preload targets
the inspected Deadhour `journeymap-forge-1.7.10-6.0.7.jar` (reported version
`1.7.10-6.0.7`, build `2026-09-19-18:38:56`). Other versions retain their existing
claim overlays but do not receive an Earth preload. JourneyMap is not required
on the server; absent clients do not register an importer or emit absence logs.

The inspected API 2.0.0 can display image overlays and read map tiles, but has no
terrain tile import operation. An overlay would cover subsequently explored
terrain. Instead, the reflection-only adapter uses the inspected native
`FileHandler`, `RegionCoord`, `RegionImageSet` and `ImageHolder` storage path.
Day and night surfaces are 512-by-512 RGBA PNGs named `regionX,regionZ.png` under
JourneyMap's own resolved world directory, `DIM<dimension>/day` and `/night`.
Region coordinates are `floor(X/512), floor(Z/512)`, including negative positions;
image columns increase along +X and rows along +Z. The importer never guesses
world folder names. Biome, topographic, cave maps, waypoints and chunk caches
are untouched. The entire Earth background appears on day/night surface maps;
JourneyMap's existing zoom, map selection, permissions and player marker remain
in effect. No JourneyMap configuration is changed.

The highest prepared image level is sampled using the item's exact normalized
X/Z conversion. Pixels outside the profile's inclusive block bounds remain
transparent. Only entirely transparent 16-by-16 areas or areas still exactly
matching this importer's ownership record can be filled/refreshed. All other
terrain pixels are preserved, including partial regions. Background pixels use
alpha 254; the inspected native `BaseRenderer.paintBlock` writes alpha 255.
Consequently normal terrain updates replace the background and cease matching
ownership even if their RGB colors happen to match. No chunk is generated,
loaded, visited, or marked rendered/explored by the import.

Completion and per-region restart journals live in
`addon-data/hfr/earth-preload` beneath the resolved JourneyMap world directory.
Keys include actual multiplayer endpoint (or singleplayer save), JourneyMap
world ID/directory, dimension, authoritative Earth identity/profile, metadata,
and SHA-256 of the actual prepared tile bytes. A resource reload or changed
profile/imagery invalidates the affected import; still-owned backgrounds are
updated, including removal outside reduced bounds. Login verifies imagery and
native file presence but skips completed region conversions/writes. Journals
allow interrupted imports to resume without replacing detailed terrain. Losing
an ownership record makes the importer preserve those existing pixels.

One cancellable worker handles PNG decoding, resampling, journals and native
PNG persistence. It hands off at most one resource/publication operation per
client tick and waits for it before proceeding. Cartography and disk writers
are excluded using the inspected holder write lock; texture publication and
listener notification run on the client thread, while JourneyMap performs its
own GL binding. LOD invalidation/persistence run on the worker. Unbound clean
imported image buffers are released on the client thread to avoid retaining
planet-sized RGBA data in JourneyMap's cache. Disconnect, world/dimension/ID
changes and resource reload cancel queued work; an already-running bounded
file operation can finish, but no cancelled job records global completion.
Successful imports log one completion message. Failure stops that job with a
useful error and leaves the Map item available.

**World identity limit:** without a server-supplied JourneyMap world UUID, this
build derives its native multiplayer folder from the saved server name, not the
address. Import records include the actual address, and a folder bound to a
different address is rejected rather than mixing backgrounds. Such servers
need distinct saved server names or distinct JourneyMap world IDs. The importer
does not rename their directories, change settings, or move exploration data.

The existing offline Forge 1.7.10 `compileJava` task passed. Source/bytecode
inspection establishes storage, coordinate orientation, overwrite rules and
thread ownership; in-game visibility, LOD refresh, player alignment, terrain
replacement, reconnect/reload cancellation, changed profiles/images, and
multiplayer identity collisions still require runtime checks. Support for other
JourneyMap builds has not been established.

## Height implementation findings (Stage 1)

**Active support remains vanilla Anvil, Y=0..255.** The shared profile and all
later geographic features work at that height. Changing a configuration value
or `WorldProvider.getActualHeight()` cannot add real blocks above it. No save
migration, height coremod installation, or new chunk format is implemented here.
The [shared profile contract](XFEARTH_MAP_PACK_FORMAT.md#shared-earth-profile-stage-1-forge-1710)
defines the fields and interfaces that subsequent stages must reuse.

The inspected engine is the project's existing RFG-patched Minecraft source at
`versions/1.7.10/build/rfg/minecraft-src/java`, using Forge **10.13.4.1614**,
MCP stable **12**, and Java **8**. Its relevant limitations are:

| Path | Confirmed limitation / required integration |
| --- | --- |
| `World`, `WorldProvider`, `Chunk`, `ChunkCache` | World reads/writes and lighting reject/clamp Y outside 0..255; Chunk allocates 16 sections indexed by Y >> 4 and 16 entity slices. |
| `AnvilChunkLoader` | Reads into 16 section slots; section `Y` is a signed byte. HeightMap is already an int array of 256 X/Z columns, but algorithms and skylight traversal still assume the old vertical domain. |
| `S21PacketChunkData`, `S26PacketMapChunkBulk` | Sixteen-section masks and fixed section iteration; increasing the section array alone cannot synchronize tall chunks. |
| `S23PacketBlockChange`, `S22PacketMultiBlockChange`, `C07PacketPlayerDigging`, `C08PacketPlayerBlockPlacement` | Byte Y or packed eight-bit Y truncates positions above 255; both packet readers/writers and interaction handlers must agree. |
| `PlayerManager`, `NetHandlerPlayServer`, `NetHandlerPlayClient`, `RenderGlobal` | Chunk subscriptions, build-limit checks, client fill/render invalidation and a 16-section vertical renderer require a coordinated replacement. |
| XF Earth generator/provider/install | Native terrain buffers, bedrock fallback and `.mca` pregeneration remain Anvil; Earth adoption requires `region/`. A cube generator/storage adapter and pack/install/verification changes are needed for a separate new-world format. |

XF's `usesMixins=false` build and inspected sources contain no existing height
transformer or integration. The existing `devmods/` launch path can load external
normal/coremods; it does not establish their compatibility. Server-border
wrapping stays dimension-0-only, with PROFILE/CONFIG/OFF adoption, safety margin,
inclusive endpoint span and exemption behavior preserved. Map coordinates follow
actual player positions. Nether, End and claim persistence remain unchanged.

The selected **evaluation candidate** is
[GTNH CubicChunks1710 v0.1.23-alpha-pre](https://github.com/GTNewHorizons/CubicChunks1710/releases/tag/v0.1.23-alpha-pre),
pinned source `e362225ec67d32d7251331f6deb7096fd8e089a8`.
Its build targets the same Forge 1614/MCP stable 12 and Java 8. It is an
existing engine replacement, not merely a height setting: `CubeIO` and
`RegionCubeStorage` store columns/cubes under `region2d/` and `region3d/`;
`PacketEncoderWorldHeight` synchronizes signed int bounds; cube/block/heightmap
packets, world/interaction mixins, lighting and `MixinRenderGlobal` cover the
major engine paths above. `ICubicWorldType` exposes the custom generator and
generation range boundary needed by Earth.

The initial target for **future integration testing** is 512 blocks,
Y=0..511 (32 cubes vertically). Its exclusive upper bound is 512, consistent
with the candidate's multiple-of-16 limits and signed supported range. This is
source-supported by the candidate, **not enabled or verified support in XF**.
The active profile implementation stays `vanilla-anvil` until the gate is met.

For an isolated evaluation, both server and every client need the matching
`cubicchunks-v0.1.23-alpha-pre.jar`, **GTNHLib 0.11.51** and
**RegionLib v0.1.0-GTNH**, as declared in the
[pinned dependencies](https://github.com/GTNewHorizons/CubicChunks1710/blob/v0.1.23-alpha-pre/dependencies.gradle).
The early coremod implements GTNH's `IEarlyMixinLoader`; a compatible
UniMixins/GTNH mixin bootstrap supplying at least **0.8.5-GTNH** and
**MixinExtras 0.5.0** is also required by the pinned mixin configuration. Its
exact distribution and transitive runtime libraries still need verification in
a real Java 8 installation. Do not substitute a 1.12 CubicChunks jar, the dev
jar, or assume the repository's development launcher supplies these components.
The older [Tall Worlds releases](https://www.cuchazinteractive.com/minecraft/tall-worlds/versions.html)
target Minecraft 1.8.3/M3L and are not an alternative for Forge 1.7.10.

**Remaining height blockers before enabling anything:** upstream explicitly
labels current builds buggy, reports compatibility testing incomplete, and lists
world conversions as TODO. XF's Anvil-only Earth generation/installation has no
cube adapter. Storage/reopen, matching client/server packets, renderers,
lighting, tile entities, placement/digging, entity ticking, spawn/border safe-Y
searches, claims at tall Y, optional map renderers and dedicated-server lifecycle
must be validated together on new disposable worlds. The candidate excludes
Angelica from its vanilla RenderGlobal mixin, so an alternate renderer cannot
be assumed covered. Its optional JourneyMap mixins target native classes;
compatibility with the inspected XF JourneyMap 6.0.7 importer or the wider
Deadhour modpack has not been established. No proven compatible height stack is
selected for production; these blockers do not prevent Stage 2 geography.

Stage 1 source inspection checked raster shifts against the original
MinecraftEarthMapXF generator and coordinate calculator, the bundled profiles,
packet/thread ownership, claims and border adoption. The existing offline
`compileJava` task succeeded. Actual map alignment, server restart/profile
default loading, pregenerator cancellation/pause/resume and world unload still
require in-game checks. No height-mod runtime launch, save conversion,
reobfuscation or packaging was performed.

## Manual runtime plan

With real exports available, run `runClient`, create each variant, verify the
progress screen remains responsive, terrain identities are correct, no edge
terrain or second population appears, and reopen each save. For populated, use
the intended full modpack and repeat after removing one required mod; creation
must be blocked with no partial save. These tests cannot be completed without the
owner's binary exports.
