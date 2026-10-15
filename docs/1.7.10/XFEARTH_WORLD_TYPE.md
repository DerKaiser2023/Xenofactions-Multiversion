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

Both distributed packs use profile `smoke` and contain no offline OSM sequence,
imported geographic dataset or native `XFEarthRoads` pristine baseline stamps.
Creation only installs terrain; scale selection performs no OSM download/import.
Use the [external preparation, import and explicit selection workflow](XFEARTH_INSTALLATION.md#bundled-default-116000-map-and-missing-osm-data)
for source layers. Historical bundled chunks remain excluded from block imports.

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

These are the Stage 1 inspection findings. The Stage 2 storage implementation
below advances preparation while keeping the gameplay gate closed.

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

## Extended storage preparation (Stage 2)

The selected format is still the pinned CubicChunks1710 **v0.1.23-alpha-pre**
native column/cube format at **Y=0..511**. `XFEarthCubicStorage` uses the public
`SaveCubeColumns.create(Path)` driver from optional **RegionLib v0.1.0-GTNH**.
This is the non-shadow-paging branch already supported by the candidate's
`RegionCubeStorage`, not a second custom region format. It writes GZIP native NBT
to `region2d/*.2dr` and `region3d/*.3dr`. The driver supplies region indexing,
file access, flush and close; XF does not implement a region-file container.
The existing Gradle/toolchain versions, Java 8 target and normal runtime
dependencies are preserved. The existing `coreModClass` convention registers a
narrow XF client launch guard; no mixin setup or build-tool upgrade is added.
No driver or height mod has been bundled or installed.

Only RegionLib is required to use this detached preparation API. Loading the
CubicChunks coremod just to prepare storage would change other worlds: the
inspected release initializes cube providers unconditionally for WorldServer
and WorldClient. The ordinary Earth preflight and Anvil pregenerator therefore
reject an active CubicChunks mod until runtime integration is complete.
Unrelated world types retain their existing behavior; XF adds no dimension
provider or height-engine transformer in Stage 2.

The preparation boundary consists of:

- `XFEarthProfile.earth2000Extended()` / validated profile JSON for the exact
  implementation/version/range, reusing all existing horizontal geography.
- `XFEarthTerrainConverter.prepare(...).getColumnData()` / `XFEarthColumnData`
  for detached sections and biomes. The ordinary raster generator now uses this
  same representation and explicitly constructs only an ordinary Anvil Chunk
  on the owning server thread. There is no dense 16x16x256 generation buffer.
- `XFEarthCubicStorage.createNew(destination, profile, levelSnapshot)` for a new,
  absent directory and a complete detached template NBT snapshot. It creates
  the profile, WorldSavedData identity, native `cubicChunksData.dat` with minHeight
  0/maxHeight 512 (exclusive), and native storage. It refuses existing targets.
- `writeNewColumn(data)`, `readColumn(x,z)`, `close()` and
  `openExisting(destination)`. Reopen is for inspection only. Writes reject any
  existing column or partial cube at that position; reads use the driver's
  `createRegion=false`, require every cube record, and never regenerate missing
  storage. Closure is synchronized with operations. There is no command or
  gameplay switch exposing an extended world yet.

`XFEarthColumnData` holds 32 nullable section slots. A block at Y=511 addresses
slot 31, local Y=15; block cells use the vanilla section index
`localY << 8 | localZ << 4 | localX` (0..4095). Data, BlockLight, SkyLight and Add
are 2048-byte nibble arrays; Blocks is 4096 bytes. A native cube's `Level.y` is an
**int cube coordinate**, not Anvil's byte section Y. Empty cubes have records so
column completeness is explicit, but allocate no block arrays. Ordinary Anvil
conversion rejects an extended profile rather than slicing away upper sections.

The native base codec is pinned to cube/column NBT version 1. Metadata and light
arrays are copied intact. Tile entities retain their complete detached NBT and
integer absolute X/Y/Z; cube membership is checked on read and write, without
constructing tile entities or touching a World on the worker. Existing cube
entities, ticks, extra NBT, lighting-engine identity and lighting bookkeeping are
retained. Unregistered block IDs, malformed arrays, misplaced tile entities,
wrong coordinates/version and unknown lighting identities fail explicitly.
ChunkAPI/EndlessIDs preparation is rejected because their alternative section
codec has not been integrated; ordinary integrations remain optional/unaffected.

HeightMap3D uses the pinned native stream: 256 Z-major X/Z cells, each containing
a VarInt interval count followed by sorted inclusive integer Y pairs. Generated
sections encode nonzero opacity with this layout, retaining heights above 255.
Existing streams are retained unless blocks are changed. Loading checks interval
count, ordering, range, truncation and trailing bytes. Generated vanilla terrain
uses static opacity; snapshots involving world-dependent custom block opacity
need the owning world's native opacity index in later integration.

`FMLServerAboutToStartEvent` preflight resolves the save root directly from the
existing SaveFormatOld/AnvilSaveConverter before server-side conversion,
SaveHandler construction and world construction. Singleplayer's client creates
a SaveHandler even earlier, inside `Minecraft.launchIntegratedServer`; the XF
early client Mixin injects the same preflight at that method's head before
session.lock or level writes. Its exact selector requires one injection and
uses Mixin's generated Minecraft refmap. Preflight failures propagate unchanged.
Dedicated servers do not select this client Mixin.
Preflight validates the profile, identity and native height metadata and rejects
extended Earth gameplay. On ordinary Earth load the dimension-0 identity is
registered for normal WorldSavedData persistence. Nether/End and existing chunk
payloads are not rewritten. This is a load safety gate, not a migration.

**Verification and remaining risks:** source inspection traces upper sections
from lookup through native NBT, RegionLib storage and reload, and checks the
candidate's native reader schema (including tile entity creation and light
identity handling). Offline `compileJava` succeeded. Native file round-trip,
close/reopen, interrupted writes, process restart, real tile entities and both
server preflight paths on real Earth saves still require runtime validation.
Mixin startup/remapping validation is recorded in Stage 3 below. The driver promises
flush but not multi-entry atomicity; interrupted preparation can leave a partial
column, which is retained and rejected rather than repaired or overwritten.
The non-shadow-paging driver does not claim crash recovery equivalent to the
candidate's shadow-paging option. Storage preparation itself has not been
validated through an actual native save round-trip.
Stages 3 and 4 must complete network/render/gameplay integration and address the
candidate's global initialization before gameplay can be enabled. Deadhour,
Angelica and JourneyMap compatibility remain unverified.

## Extended networking and rendering bridge (Stage 3)

Stage 3 connects the Stage 2 identity/profile to the **same** pinned native
codecs and vanilla terrain renderer. Extended Earth gameplay remains disabled:
`requireGameplayHeight()` still runs before opening the save, installing a pack,
attaching storage or constructing gameplay chunks. No height switch, migration,
new chunk format, bundled dependency or replacement renderer is introduced.
These integration paths are therefore compiled but have not run against an
extended gameplay world. Stage 4 must complete gameplay/provider integration
and address the candidate's global world initialization before enabling them.

`XFEarthHeightSync.bind(world, profile)` is wired after the existing world-load
gate. It accepts only server dimension 0, the validated Stage 2 native storage
identity/layout, the exact selected components, the native CubeProviderServer,
and matching native min/max bounds (0/512, with maximum exclusive). Ordinary
profiles do not bind a height session. Three optional compatibility Mixins target
only the pinned CubicChunks classes; they supply no world engine patches.
`CubicPlayerManager.addPlayer` injects at cancellable HEAD, `removePlayer` at
both RETURN sites, and `NetworkChannel.sendToPlayer` at cancellable HEAD.
Two separate redirects intercept the single description send in `flush()` and
the single send in `lambda$sendCubeData$3(ChunkPosition, TileEntity)`, the actual
compiled tile callback of `sendCubeData(Cube)` in v0.1.23-alpha-pre. This release
does not have `flush(EntityPlayerMP)` or `sendCubeData(EntityPlayerMP, CubeWatcher)`.
All adapters delegate directly to `XFEarthHeightSync`; session/protocol ownership
and native handling outside managed Earth sessions are unchanged.

GTNHGradle's `usesMixins=true` supplies UniMixins and the annotation processor.
`XFMixinLoader` implements GTNHMixins' `IEarlyMixinLoader` and registers
`mixins.hfr.early.json`; it registers no ASM transformers. FML adds the pinned
CubicChunks coremod JAR to LaunchClassLoader before early selection, and its
bootstrap does not load these hook targets. The loader selects compatibility
Mixins only when `com.cardinalstar.cubicchunks.asm.coremod.CubicChunksCoreMod`
appears in GTNHMixins' loaded-coremod set. No optional classes are resolved by
the loader. The client preflight is selected independently through the config's
client list. `mixins.hfr.json` is the empty GTNHGradle manifest config; both
configs share the generated `mixins.hfr.refmap.json`.

The config is required. HEAD and redirect sites require/allow exactly one match;
removal requires/allows exactly two to cover the early and final returns.
Changed/missing installed targets fail visibly. CubicChunks is a pinned,
non-transitive compile-only official dev artifact, not a runtime or published
dependency. PlayerManager overrides retain normal inherited Minecraft remapping.
NetworkChannel and sync-machine selectors use `remap=false`, while both nested
vanilla `NetHandlerPlayServer.sendPacket` targets explicitly use `remap=true`.
There is no XF instruction scanning, opcode replacement or MCP/SRG name probing.

Before subscribing a player, the bridge sends protocol 1, dimension 0, a new
session ID, and the complete validated shared profile. The client checks native
components, provider, bounds and renderer on ClientTick, then acknowledges.
Only that acknowledgement resumes native `CubicPlayerManager.addPlayer` on
ServerTick; the native sync machine retains its column -> cube -> tile
description order. Missing or unsupported clients are rejected by the loader
check or a 400-server-tick acknowledgement timeout. The loader check requires
matching pinned height mods on both peers before S01/S07: this release changes
those packets and S23 globally, even outside a managed Earth session.

For managed Earth sessions, native packets are encoded by public
`CCPacketEntry.encoder.writePacket` and decoded/applied by the same native
`readPacket` / `process` methods. XF adds a dimension/session envelope on its
existing Forge channel, with appended discriminators preserving older IDs. This
avoids the pinned native channel's direct network-callback world mutation.
Network callbacks queue detached bytes only; client world access, cube creation,
tile application, lighting and render invalidation run on ClientTick. ACK
callbacks likewise coalesce detached IDs until ServerTick. No worker handles a
Minecraft world. The envelope is limited to 1 MiB, below Forge 1614's inspected
S3F payload limit of 2,097,050 bytes (Forge uses VarShort, not vanilla's short
length). The client queue is limited to 16 MiB and 1,024 deliveries per tick;
overload disconnects explicitly instead of dropping sections.

| Path | Selected native representation / Stage 3 handling |
| --- | --- |
| Initial column | Int X/Z, native biome array and HeightMap3D intervals; count/range checks retain native capacity padding. |
| Initial cube | Int X/Y/Z cube coordinates, native Blocks/Add/Data/BlockLight/SkyLight and optional native biome data; native EBS allocation uses cubeY * 16. Cube 31 covers blocks 496..511. |
| Block update | Native cube update list and patched S23 int Y. Bounds, cube membership, IDs and metadata are checked; vanilla byte-Y S22 is not used for upper cubes. |
| Heightmap/light | Native interval updates, variable-length BitSet mask, full 2,048-byte skylight or two-byte local updates. Missing parent columns/cubes reject the connection rather than silently ignoring updates. Null-storage light updates also invalidate rendering. |
| Tile description | Native description packet bytes remain in the same ordered envelope as their cube. Registered PLAY packets and Forge proxy descriptions retain their existing handlers; unregistered/oversize descriptions fail explicitly. S35's signed-short Y safely covers 0..511, preserving NBT; its loaded cube is checked before application. |
| Unload/respawn | Native unload codecs, session retirement, connection/world checks, manager removal hooks and overworld/server cleanup. Same-dimension respawn can renew the session without replacing WorldClient. |

Native `World_HeightLimit` changes `blockExists`, block/metadata/tile access and
lighting bounds to the actual min/max range. Its Chunk/ChunkCache mixins route
upper data and render light sampling to cubes. Native `RenderGlobal` creates a
sliding vertical renderer window of `2 * verticalCubeLoadDistance + 1` sections,
centred around the viewer, and updates entity visibility at its actual Y.
Native cube updates invalidate their real 16-block render extent. XF validates
the native renderer's `RenderDistanceCubesVertical` field and reloads its sections
before acknowledgement. There is no fallback pretending an absent section is
empty. Negative Y is rejected by the shared selected 0..511 profile.

**Exact evaluation client/server requirements:** matching XF builds with this
bridge; `cubicchunks-v0.1.23-alpha-pre.jar` on both peers; GTNHLib 0.11.51 and
RegionLib v0.1.0-GTNH; UniMixins >=0.3.1 (including GTNHMixins),
Mixin >=0.8.5-GTNH and MixinExtras >=0.5.0, as required by the
pinned upstream dependency/bootstrap source. Production startup with the exact
stack has an upstream GTNHLib remapping blocker described below.
Alternate ChunkAPI/EndlessIDs codecs remain rejected.
Ordinary XF worlds retain their existing packet/render paths without these
optional height components; an active height coremod is still rejected for
ordinary Earth because it globally replaces providers. XF does not retag,
convert or activate other dimensions.

**Angelica is unsupported for extended Earth.** The pinned native mixin table
explicitly omits its RenderGlobal mixin when Angelica is present. The local
Angelica 2.2.13 development reference uses Celeritas: its world renderer returns
maximum build height 256, and cloned sections reject Y >=256 and index the
ordinary section array. There is no inspected upper-cube terrain adapter.
Stage 3 rejects Angelica before acknowledging or subscribing to extended
chunks. Angelica is optional and unaffected on ordinary XF worlds. OptiFine,
shader replacements, JourneyMap at extended height, custom tile description
handlers and the Deadhour modpack have not been validated with this stack.

The shared lifecycle/API is `XFEarthHeightSync.bind/unload/clear`, the existing
profile/identity APIs, and client-thread
`XFEarthHeightClient.getActiveProfile(world)` (null outside an accepted extended
session). Later geographic stages must continue using `XFEarthProfile` conversion,
the existing Map snapshots/data and the same claim grid. The Map item and
`YOU ARE HERE` marker are unchanged.

**Injection rewrite validation (2026-10-14):** 1.7.10 compilation,
reobfuscation, Java 8 class verification and the existing release archive verifier
passed. Both Mixin configs and the generated refmap are present in the release
JAR; no XF-owned transformers or ASM/remapper feature imports remain.
Root/common build checks passed (common tests are NO-SOURCE), and the aggregate
multiversion task graph was checked; a full two-target `buildAll` was not run.
Without CubicChunks, development and release clients reached the menu, and
development and release dedicated servers reached `Done` without client Mixin
application. The development Minecraft export puts preflight before FML startup
and save-handler access.

With the pinned CubicChunks/GTNHLib/RegionLib dev stack, client startup and
Mixin's built-in audit succeeded. Exported runtime classes confirm cancellable
HEAD hooks, both removal returns and the two independent description redirects.
With the production XF and upstream release JARs, debugger-directed loading of
all three native targets applied the same exact required injections; exported
classes confirm SRG player methods and both redirects. This validates XF's
production remapping, not full pinned-stack startup: GTNHLib 0.11.51 then fails
its own `MixinTileEntityFlowerPot` injection because its released refmap lacks
`readFromNBT`'s SRG mapping. The pinned development stack also logs an upstream
`CompatHandler` missing-event-bus-Mixin error. These dependencies were not patched
or upgraded. Temporary launch settings isolated server saves and corrected the
existing `runObfClient` game-directory mismatch; project launch tooling is unchanged.

Ordinary Earth creation/reopen, rejected-save preflight and actual handshake,
subscription, timeout, tile-ordering, respawn/dimension and rendering behavior
still need in-game checks. No extended-world gate was bypassed for validation.
Native global initialization, provider/generator integration, gameplay limits
and the Stage 2 storage/crash-recovery risks remain blockers to activation.

## Gameplay bounds and activation blockers (Stage 4)

**Stage 4 is incomplete: extended Earth creation/gameplay remains disabled.**
The active supported world remains ordinary Anvil Y=0..255. The selected native
preparation target remains CubicChunks1710 v0.1.23-alpha-pre, Y=0..511, sea level
62 for earth2000. No opt-in switch is exposed because required engine paths fail
source inspection. No existing save is converted. The earlier storage and
network/render bridges remain gated rather than being presented as a playable
512-block world.

The confirmed activation blockers are:

1. Native `MixinWorld.initWorld` initializes cubic height metadata, providers and
   lighting for every WorldServer/WorldClient. `MixinWorldServer` replaces both
   `createChunkProvider` and animal spawning without an Earth/world-format
   predicate. `CubicChunksSavedData.get` and `WorldFormatSavedData.get` create and
   immediately save native metadata when absent. The selected API exposes no
   ordinary-world opt-out. Installing this engine to enable Earth would change
   unrelated worlds/dimensions and their storage behavior, contrary to this
   stage's limits.
2. XF's world type still implements the ordinary Forge generator boundary, not
   native `ICubicWorldType`. Native fallback `VanillaWorldGenerator` copies only
   section indices 0..15 and uses uniform filler above them. It also invokes
   vanilla decoration/population. This cannot supply the pregenerated Earth
   contract for missing/edge cubes or guarantee the existing FAIL policy and
   no-population behavior. A native Earth generator/loader and creation path are
   required after engine isolation is available; detached Stage 2 storage alone
   does not provide that live-world boundary.
3. In the pinned
   [entity height mixin](https://github.com/GTNewHorizons/CubicChunks1710/blob/v0.1.23-alpha-pre/src/main/java/com/cardinalstar/cubicchunks/mixin/early/common/MixinWorld_HeightLimit.java),
   `updateEntityWithOptionalForce_chunkExists1` calls `Coords.blockToCube` and
   then shifts those cube coordinates right by four again before `cubeExists`.
   For example, entity block Y=496 maps to cube 31, but the subsequent lookup
   uses cube 1. This is an incorrect source path for entity membership/update
   checks; no corrected pinned implementation has been integrated.
4. The pinned
   [placement redirect](https://github.com/GTNewHorizons/CubicChunks1710/blob/v0.1.23-alpha-pre/src/main/java/com/cardinalstar/cubicchunks/mixin/early/common/MixinNetHandlerPlayServer.java)
   returns `!manager.activateBlockOrUseItem(...)`. The exact Forge 1614 server
   handler already negates that result to mark placement failure. The redirect
   therefore reverses Forge's placement-result/inventory correction bookkeeping.
   This source mismatch prevents claiming correct interaction support.
5. Native collision patches replace vanilla's Y=64 column-availability probe
   with the bounding box midpoint. Vanilla `getCollidingBoundingBoxes` still
   gates all block collision collection on `blockExists` at that probe. A box
   intersecting the last legal blocks while its midpoint is above the exclusive
   height can fail that check. Ceiling collision behavior needs correction and
   runtime verification before enabling the bounded 0..511 world.

These are selected-engine limitations, not problems fixed by increasing a config
value. The gameplay/profile/preflight guard now explains the global provider /
storage / spawning isolation and native Earth generator blockers. A compatible
maintained engine correction/integration is required; XF adds no second custom
chunk format or speculative engine rewrite in this stage.

**Implemented XF changes:** `XFEarthProfileLoader.forWorld(world)` resolves the
existing validated server profile only for adopted dimension-0 Earth. On the
client it uses the sided proxy's accepted Stage 3 height profile, with no client
class references in common code. Other world types, other dimensions and detached
legacy metadata readers retain their existing 0..255 rules. Sidecar JSON on an
unrelated world cannot change its bounds. `XFEarthProfile.containsBlockY` is the
shared inclusive block-Y check; the existing cache/unload ownership remains.

`XFEarthTeleportSafety.find` is the Earth-only owning-server-thread terrain search
used by player/entity border wrapping, exemption return to map center, scheduled
faction home/warp/rendezvous destinations and build-grace home/spawn recovery.
It translates the actual entity bounding box, loads its horizontal footprint,
checks collision, liquids and vanilla fire/cactus contact, and searches upward
then downward within the active profile. Grounded warps additionally require
solid non-liquid support. Clear flight positions are preserved for border wraps,
including open sky above the build limit; a block-height bound is not a flight ceiling.
An exhausted search returns no destination; it does not clamp the player into a
block. Scheduled unsafe/missing-terrain warps abort before dimension transfer.
Feet may stand at maximumY+1 on the last legal block (256 or 512), with the body
extending into open sky. This does not allocate blocks above the profile.
Custom mod hazards, vehicle bounding boxes and passengers require runtime checks.

The XShop link/unlink Y parser and TDM map/bomb selection validation now use that
same active profile. Faction market NBT still stores integer Y with the same
version/schema; its existing WorldSavedData owner supplies active bounds to the
reader, preventing a future upper terminal from being discarded solely by the
old 255 cap. Detached readers and registrations in other dimensions keep the
ordinary bound. Claims remain the existing dimension-aware X/Z grid with integer
city/home/warp positions; no separate geographic/claim registry is introduced.
The existing City Center activation/relocation restriction **Y=45..200** is an
intentional gameplay rule, not an engine storage limit, and remains unchanged.

Earth raster preparation already uses the profile's minimum Y for bedrock,
`surfaceY` for stone/filler/surface bounds and `seaLevel` for water/ice. Horizontal
scale and geographic conversion are unchanged. Increasing
`verticalElevationScale` changes elevation relative to sea level, not sea level
or saved terrain. Arithmetic checks over all 65,536 source samples preserve the
ordinary scale-1 result; scale 2 reaches Y=448 and scale 448/193 reaches Y=510,
both with the encoded sea sample remaining Y=62. These are detached preparation
results, not a supported extended pregeneration command: the existing command
continues to create ordinary worlds.

`/XRTP` is absent from the maintained Forge 1.7.10 command/source registrations
and the available local xRadar source reference. Its owning mod/source is not
available here; no height or safety compatibility is invented for that command.
The Map item, `YOU ARE HERE` marker, geographic data and optional integrations
are unchanged. Angelica remains rejected for extended rendering, ChunkAPI /
EndlessIDs codecs are unsupported, and shader, JourneyMap-at-height and Deadhour
compatibility remain unverified.

The prospective stack is unchanged: matching XF and CubicChunks1710
v0.1.23-alpha-pre on server/client, GTNHLib 0.11.51, RegionLib v0.1.0-GTNH, and an
early compatible UniMixins/GTNH bootstrap supplying Mixin >=0.8.5-GTNH and
MixinExtras >=0.5.0. Having those components installed is **insufficient** while
the source blockers above remain. Profile/range/version validation and the
pre-write gameplay gate still reject extended saves/creation before activation;
no partially implemented mode or automatic migration is enabled.

**Verification:** inspect the prior three stages, native C07/C08 int-Y codecs,
ItemBlock ceiling patch, server placement/digging bounds, World block/metadata
access, ray traversal through getBlock, collision probes, entity cube containers,
spawning, tile lookup/load and the vanilla owning-thread `updateEntity` loop.
Those native patches show intended upper-height paths, but the blockers above
prevent a complete source-integration claim. Offline `compileJava` succeeded;
the new helper is Java 8 classfile major 52. Mathematical checks cover elevation
samples and the inclusive search-loop endpoints. No test infrastructure was
added. No packaging, remapping/reobfuscation, transformer execution, runtime
launch, dedicated-server launch or in-game behavior was observed. Ordinary Earth
warp/border/market/selection behavior, vehicle safety, save/reopen and the entire
extended-height stack still require runtime validation.

## Offline geographic data (Stage 5)

`com.hfr.world.earth.geo` is the reusable Forge 1.7.10 data pipeline. It uses the
existing validated `XFEarthProfile.blockX/blockZ` projection, independent of the
height activation gate. The command wrapper snapshots profile, paths and limits
on the owning server thread. Its single worker hashes/parses local files, projects
geometry and writes detached data; it never reads or mutates Minecraft world,
chunk, entity or claim state. No network requests run during import or generation.
The Map item, `YOU ARE HERE` marker and existing claim-node system are unchanged.

The supported source is an attributed local OpenStreetMap Osmium export, prepared
as described in [installation](XFEARTH_INSTALLATION.md#local-geographic-imports-stage-5).
Google Maps content is excluded; its
[standard terms](https://cloud.google.com/maps-platform/terms) restrict exporting
and deriving geographic content. [MapChart subdivisions](https://www.mapchart.net/world-subdivisions.html)
is a first-level administrative reference, not this pipeline's road/building source.

Classification retains all supplied tags while indexing `highway` lines/areas,
`landuse` polygons, `building`/`building:part` footprints, `boundary=administrative`
lines/areas and business candidates from `shop`, `office`, `craft`, selected
`amenity` and accommodation `tourism` tags. No administrative hierarchy or business
operating status is inferred. Node business points remain source points; area
business locations are explicitly marked as derived interior points. Features can
have multiple categories. Original object type, exact integer ID, timestamp,
properties, exporter attributes and non-geometry Feature members are retained.
The derived format stores clipped projected geometry, not the original source
geometry; the input checksum and source provenance identify that original export.

Two-dimensional Point, MultiPoint, LineString, MultiLineString, Polygon,
MultiPolygon and nested GeometryCollection are supported. Polygon holes are
subtracted before clipping. Derived `Area` parts contain even-odd rings, allowing
holes and disconnected exteriors; consumers must use the fill rule, not assume
the first ring is an exterior and all others holes. `XFEarthGeoGeometry.area(part)`
reconstructs this representation without Minecraft references. Java 8 headless
`java.awt.geom.Area` supplies the clipping path; no additional GIS library is added.
Ring checks reject unclosed/degenerate rings, outside/overlapping holes and common
self-crossing or area-changing normalization. They are not a complete robust GIS
topology validator.

Longitude paths unwrap across the antimeridian using the **full longitude period**
from the profile, then periodic copies clip to its actual cropped extent. For
earth2000, that period is 21,600 blocks while the raster width is 21,504; using the
cropped width would misalign edge fragments. Lines/areas that cross edges split;
max-exclusive clipping avoids assigning boundary points to outside chunks.
Negative continuous positions are floored to blocks before chunk/region division.
The geographic profile remains authoritative; this index does not replace the
legacy claim coordinate rules.

Each immutable dataset has `dataset.json`, length-prefixed UTF-8 records in
`features.dat`, and `regions/r.<x>.<z>.xgi` candidate indices. Format, projection and
classification versions are all **1**. SHA-256 identity binds the actual sequence
bytes, canonical source metadata and full validated/defaulted profile snapshot.
The manifest retains attribution, license URL, snapshot, source metadata and import
counters. Region headers contain magic/version/coordinates and the 32-byte dataset
identity (48 bytes total); entries contain an 8-byte record offset and 4-byte length.
One feature is stored once and indexed conservatively by its projected bounding
box in 512-block Minecraft regions. Readers must filter exact geometry and avoid
duplicates when scanning multiple regions.

`XFEarthGeoDataset.open(directory, profile)` validates identity/version/profile,
completion and source attribution before reading. `readRegion(x,z,visitor)` reads
one candidate at a time and validates index identity, record lengths and offsets.
`availability` distinguishes missing, available source-only and incompatible/damaged
datasets. The manifest's `SOURCE_CONTENT_ONLY_UNVERIFIED` coverage always applies:
an empty index or successful import cannot prove that the real world has no feature
there. Checksums identify inputs; they do not provide cryptographic integrity for
every derived payload or a full disk-corruption audit.

| Bound | Default / supported maximum |
| --- | --- |
| Input feature bytes (`earthGeoMaxFeatureBytes`) | 4 MiB |
| Input/output vertices (`earthGeoMaxVertices`) | 32,768 per feature |
| Open region writers (`earthGeoOpenRegionFiles`) | 16 / 32 |
| Region references (`earthGeoMaxRegionsPerFeature`) | 4,096 per feature |
| JSON / GeometryCollection nesting | 32 / 8 |
| Derived record bytes | 8 MiB |

Parsing streams records with bounded per-feature memory and an LRU of index
writers, without retaining a planet-sized node/feature table. Oversized/malformed
records and unsupported geometry are logged to `issues.jsonl` and skipped without
aborting unrelated records. Native PBF/XML, FeatureCollections, 3D coordinates,
full-winding globe rings, excessive fan-out and unavailable exporter relation
types are unsupported. Upstream omissions remain unknown. Storage I/O failure
aborts publication rather than marking partial output complete. Input is hashed
before and during parsing to reject changes between passes.

Output is external to Git workspaces. Publication moves the completed partial
directory without replacement after all writers close. Cancellation is cooperative
between records/projection steps and interlocked with final publication; Java's
individual geometry operations cannot be interrupted internally. This does not
promise crash-atomic multi-file durability, resume or cleanup. Import publishes
without selecting or placing blocks. `/xc earth geo select <dataset-id>` validates
a named completed dataset on the same bounded worker and atomically writes
`data/xfearth-geographic-selection.json` in the save, retaining its canonical path
and dataset identity. `XFEarthGeoSelection.open` is the common selection boundary
for overview queries and road/feature previews and application revalidation.
Nonblank `earthRoadDatasetDirectory` remains authoritative and blocks the selection
command until deliberately cleared/restarted. There is no arbitrary discovery or
replacement on import. Native earth2000 pregeneration reads the explicit override
on its worker and records that selected dataset in its output save as well.

Selection changes cancel stale previews and pending map publications, clear worker
caches and deliver a bounded invalidation notice over the existing layer channel.
The client clears frame assembly, GL lists and optional adapter source frames on
its owning thread, and the open Map requests fresh data. Dataset/profile failures
retain specific reasons and `/xc earth geo status` guidance rather than becoming
empty successful results. The status command reports source/output paths and
external acquisition requirements; `geo info` reads provenance off-thread. Saved
selections revalidate profile, completion and identity whenever opened. Dataset
files remain immutable and external to Git; the small save-local selection record
does not contain source features. Moving a selected dataset requires an explicit
new selection or corrected config path.

**Verification:** source trace covers record limits, per-feature recovery,
projection/antimeridian handling, index offsets, dataset identity and worker/unload
ownership. Mathematical checks use the actual two bundled profiles and earth2000,
forward/inverse geographic reference points, negative fractional region positions,
the cropped/full longitude-period difference and 48/12-byte index layout. Offline
Forge 1.7.10 `compileJava` succeeded with the existing Java 8 target. No local
extract was available, so no import execution, Java geometry/disk round-trip,
packaging, remapping, runtime launch or in-game observation is claimed. A real
regional export is still required to validate holes/multipart fragments, source
tags/provenance, cancellation and persisted reader behavior. Stage 4 engine
isolation/generator/gameplay blockers still prevent extended-height activation;
they do not block this ordinary-height data pipeline.

## Road routes (Stage 6)

`XFEarthRoadSource` reads the Stage 5 profile-bound region index, keeping source
lines and filtering highway categories at the active scale. It caches four region
lists, each capped at 8,192 selected roads / 32,768 segments; unrelated features
stream past without filling the selected-road limit. Queries also cap segments.
Limits fail explicitly rather than silently truncating terrain generation.
Road polygons have no inferred centerline. `width` accepts plain metre values
(optionally suffixed `m`); unknown units use category defaults. See OSM's
[highway definitions](https://wiki.openstreetmap.org/wiki/Highways) and
[width convention](https://wiki.openstreetmap.org/wiki/Key:width).

Global unit-square/segment intersections preserve corner contacts and source
vertices. An 18-by-18 route footprint includes adjacent chunk cells; highway rank
then source key determines priority, independent of traversal/random seeds.
No endpoints are snapped together or missing source gaps interpolated. Coarse
rasterization can merge unrelated streets into the same Minecraft cells; exact
OSM node topology and lane simulation are not represented.

`XFEarthRoadPlanner` uses the same deterministic 3-by-3 median grade rule for
raster samples and existing-terrain snapshots. A 24-by-24 sample halo covers
neighbourhoods across chunk seams. Targets clamp to cut/fill limits and active
profile Y bounds; excessive neighbouring road grades, water/ice, claims, missing
terrain and obstructions produce gaps. Palettes reuse XF asphalt and vanilla
gravel. Source selection and defaults are in
[Stage 6 setup](XFEARTH_INSTALLATION.md#road-generation-and-bounded-imports-stage-6).

Bridges, tunnels and unresolved nonzero layers are separate from surface roads.
OSM [bridge](https://wiki.openstreetmap.org/wiki/Key:bridge) and
[tunnel](https://wiki.openstreetmap.org/wiki/Key:tunnel) tags alone do not supply
trusted Minecraft deck/tunnel elevations or approaches. Their placement is deferred
and their routes retained on the source Map layer. Fords are not carved through
water. Tunnel portals, grade-separated junctions, road areas, turn/barrier
restrictions, furniture and exact lanes remain unsupported. Protection takes
precedence over forcing continuity.

**Generation order:** the only native new Earth path remains the raster
pregenerator. Its worker prepares base columns and independent road plans, then
ServerTick applies detached column edits through `XFEarthWorkQueue`. Existing
owning-thread chunk construction, light setup and Anvil serialization follow.
The live provider's empty population method/strict missing-chunk guard remain;
pack installation/load never adds roads retroactively. Dataset/policy identity
persists in pregeneration state and installation metadata, rejecting changed-source
resume. Region completion still owns native pregeneration replay.

New native chunks receive version-1 `XFEarthRoads` NBT through the exact Forge
1614 `ChunkDataEvent.Save` path, even with roads disabled. Baselines hash block
LSB/Add/metadata arrays plus biomes; absent sections normalize to zeros, lighting
is excluded. Forge ChunkIOProvider's server-thread stage-2 load event restores
the stamp after tile entities load. Loaders that omit this event fail closed
without trusted state. Road-generated chunks also record completion identity.
Player place/break events mark edits; current fingerprint changes catch other
mutations. Old exports receive no assumed baseline or automatic migration.

**Existing terrain:** one in-world level-4 job previews an already-loaded rectangle,
maximum 64 chunks (default 16) / 65,536 changed blocks. The server incrementally
copies native arrays, reads terrain and resolves protection with the original
dimension-aware `ClowderTerritory.getMetaFromIntCoords` semantics. Workers consume
only detached snapshots/files. Chunks without matching pristine baselines, prior
edits, pending/completed roads or any tile entity are skipped. A natural-block
allowlist is an additional safeguard, not proof of natural generation.

Read-only preview supplies exclusions/counts and pageable actual changes.
`inspect` precedes backup acknowledgement. A five-minute token, operator UUID,
World instance, dataset/policy and acknowledgement bind the job. Application is
off by default and rechecks permission/session, loaded chunk identity, fingerprint,
tile entities, claims and expected block/metadata. No chunks are force-loaded.
External modifications can cause skips; this is not a cross-mod editing transaction
against APIs bypassing events and changing surrounding terrain during processing.

The user-authorized backup contract is manual: `roads backup <token>` records an
operator statement that a current backup exists; it neither creates nor verifies
one. Pending tokens are included in normal chunk saves from the first edit;
completion is recorded only after the chunk finishes. Cancellation/failure/unload
does not undo placed blocks, and interrupted pending chunks cannot silently replay.
Normal Anvil saving is not atomic whole-job durability. Manual backup/restore is
required. Completed or edited chunks are not overwritten by later datasets.

`XFEarthWorkQueue` shares one step/time budget between native road edits, existing
preview/application and Map publication. Each placement changes one block; array
copies/claim checks are bounded steps. Minecraft callbacks/saves are not hard
real-time operations. Shutdown/unload cancels jobs and clears work; stamps remain
available for Forge's final save and are cleared after it. Workers never
access Minecraft worlds. Throughput has not been measured.

**Map:** appended packet IDs preserve existing profile messages/IDs. World/profile
resolution and input capture run on the server, one bounded worker reads source
geometry, and server-tick publication rechecks the original session. Up to 16
regions were limited to 4,096 clipped segments in eight packets of 512 segments.
Stage 10 replaces that original wire/render vocabulary with typed features and
vertex budgets; see its current limits below. The
client-thread GUI rejects stale view tokens and draws lines before `YOU ARE HERE`:
surface roads gold, deferred bridges blue and tunnels purple. Attribution/coverage
notices identify source geometry, not placed-block success; wide/truncated views
are explicit. Matching updated XF client/server builds are required. Optional
Angelica paths remain unverified. Stage 10 adds optional JourneyMap adapters below.

**Verification:** inspect prior stages, exact Forge 1614 Anvil/ChunkIO events,
native height/block queries, asphalt, queue lifecycle, detached worker paths,
permission/token checks and pending/completion transitions. Arithmetic checks cover
scale ranks/widths, positive/negative seam halos and the 16,914-byte maximum packet
payload before its notice. Offline `compileJava` succeeded. No real geographic
extract is available: actual intersections/seams, water exclusions, saved markers,
repeat processing, Map networking/rendering, backup acknowledgement, cancellation
and build protections need runtime checks. No test infrastructure, packaging,
remapping or runtime/in-game observations are claimed. Stage 4's extended-height
blockers remain independent.

## Geographic fields and structures (Stage 7)

`XFEarthFeatureSource` interprets the same Stage 5 dataset exposed by
`XFEarthRoadSource`; it adds no projection, index, claim grid or location registry.
Its policy identity includes dataset/profile/road identity and feature settings.
Region caching is bounded to four lists, each 2,048 parts / 65,536 contour vertices;
queries cap parts at 8,192. Source polygon Area/even-odd rings retain multipart
geometry and holes. Supported land use follows OSM's
[farmland definition](https://wiki.openstreetmap.org/wiki/Tag:landuse=farmland),
with explicit [crop tags](https://wiki.openstreetmap.org/wiki/Key:crop) interpreted
only for supported crops. Farmyard alone never invents a barn or farmhouse.

`XFEarthFeaturePlanner` uses full-unit-square containment, preserving even sub-block
holes by skipping intersecting cells. Source building polygons take priority,
then business candidates, farmyard, farmland and meadow; source keys break ties.
Higher-priority source footprints reserve local cells, including unsupported or
unsafe buildings, preventing lower-priority fields/kiosks from replacing them.
Road corridors, water/ice, claims and non-natural blocks remain protected. Overlap
or road contact can trim a shell; no missing source connection is drawn.

Generic shells follow representable source footprints, with procedural materials,
windows, roof and opening. Explicit farm building tags choose timber, greenhouse
glass, generic buildings stone brick, shops/food/lodging brick and office/craft
workshops timber. Crop/structure palettes use only existing vanilla/XF blocks;
XF rice extends the inspected vanilla crop path. Crop metadata 7 and dry farmland
0 reuse normal growth/support behavior, without fabricated irrigation. Unknown
crops use generic terrain patterns. Fine-scale business node kiosks use their
actual projected source cell; their 3-by-3 shape is explicitly procedural.

Nine whole cells plus an interior cell, dry terrain, bounded fill/span/height and
active Y clearance are required. Coarse polygons use a low generic shell; tiny,
huge, elevated, ruined or unfinished buildings stay information only. Source
`building:levels` influences capped generic height, not calibrated elevation or
architectural reconstruction. See [setup and supported categories](XFEARTH_INSTALLATION.md#fields-buildings-and-businesses-stage-7).
Orchards/vineyards and other unsupported land use have outlines but no generated
plants; unsupported business types have source symbols/outlines, not new machines.
Business line geometry remains in the underlying dataset rather than gaining an
invented point. Mapped entrance-only nodes/access associations are absent from the
Stage 5 export pipeline. Generic openings/source-road contact do not establish
surveyed entrances; dedicated driveway routing remains unsupported.

Native raster preparation computes roads/features on the worker from detached
columns/raster samples, then applies metadata-aware edits through the **same**
server-tick budget before existing chunk light/save handling. Feature policy binds
resume and installation metadata. No live chunk population or pack-load retrogen
is added. Existing `XFEarthRoadImport` owns both command trees and a single active
job. Feature previews share its loaded snapshots, manual backup acknowledgement,
permissions, bounded edits, cancellation and expected-block checks. Existing-world
structures must fit wholly within one chunk; fields may span chunks. New native
structures evaluate the complete bounded footprint against pure raster samples,
including samples beyond the road halo. Other world types/dimensions are unchanged.

Version-1 `XFEarthRoads` NBT gains optional `featuresCompleted` and `featureInfo`
fields; absent additions default empty, retaining old baselines/road completion.
Generated records identify source object/record/part, type and generic appearance,
including explicit procedural point-kiosk versus source-footprint labels. At most
256 records fit the chunk's occupied columns. Separate feature completion permits
an untouched Stage 6 native road chunk's first feature pass, while shared pending
markers prevent partial-job replay. Completed features are not automatically
regenerated or migrated when data changes. Normal chunk-save durability and manual
backup limitations remain; cancellation does not undo changes.

Map publication reuses the existing viewport/reply packets and bounded worker.
Field/building outlines and point symbols originally shared a 4,096-primitive cap;
Stage 10 replaces this with typed contours and the limits below. Holes are
outlines, never filled over. The server snapshots bounded recorded metadata from
loaded chunks (4,096 inspected / 128 selected / 8,192 keys maximum), without loading
terrain. Matching recorded fragments get brighter styles. This is persisted
generation information, not proof of present blocks or whole-feature completeness.
Feature-layer failure leaves the road layer available and displays a notice.
Attribution, source-only unknown coverage, client-thread updates, stale request
guards, scissor restoration and `YOU ARE HERE` remain. No optional renderer/map
integration is newly required or claimed compatible.

**Verification:** inspect prior stages, geographic tag/geometry paths, existing
rice/crop/farmland and block palettes, overlap ordering, metadata-aware edits,
native save/load ownership, protected job gates and source Map serialization.
Arithmetic checks cover negative source-point cells, 3-by-3 minimum footprints,
roof clearance at Y=255, global row patterns, per-chunk record bounds and shared
packet capacity. Final offline `compileJava` succeeded using unchanged Forge
1.7.10 tooling; all six Stage 7 class headers are Java 8 major 52.
No regional extract was available, so geometry/raster alignment, holes/overlaps,
crop ticking, generic shells, repeat save/reopen, imported-road openings and Map
rendering remain runtime checks. No packaging, remapping, runtime launch or in-game
observations are claimed. Stage 4 extended-height blockers remain unchanged.

## Persistent claim nodes (Stage 8)

`ClaimNodeSystem` reuses the territorial `CoordPair` unit, not geographic source
IDs or administrative subdivisions. `ClowderTerritory.territories` remains the
authority for ownership, city origins, safezones and warzones. Commands and
protection adapters resolve metadata through `ClaimNodeSystem.metadata`; no
second ownership registry is introduced. Existing faction NBT, founding/upkeep
costs, officer/leader permissions, circular city expansion/spacing, flag rules,
war handling and spawn/build-grace exceptions are unchanged. The old commented
ocean warning is not an active classifier; Stage 8 adds no eligibility gate.

Each identity is a saved world UUID, dimension ID and the existing territorial
X/Z coordinates. `ClaimNodes` version 1 lives in the same `hfr_clowder` saved
data and contains independent classification records. On initial server binding
and saving, missing owned coordinates receive `UNCLASSIFIED` records; repeated
migration adds no duplicates, keeps existing classifications and never unclaims
territory. Removed ownership does not erase terrain information. Unvisited
wilderness can resolve to an implicit unclassified node without allocating a
world-sized grid. An unknown or malformed node payload is preserved as NBT and
disables classification access; legacy faction/territory loading continues.
Normal world-save durability applies. Back up saves before updating.

Coordinate behavior is deliberately retained: construction uses Java truncating
`(x+1)/16,(z+1)/16`, while block protection uses `x/16,(z+1)/16`.
`Geometry` exposes the exact half-open protection footprint using the existing
`TerritoryCoordinateBounds` helper with X shifted by one. Coordinate zero spans
31 blocks (`[-15,16)` on X and `[-16,15)` on Z); other units span 16.
Changing these formulas would move existing claims. Existing Dynmap/JourneyMap
owner overlays retain their construction geometry; the shared node layer exposes
the protection geometry explicitly. Optional map integrations remain optional.

`ClaimNodeSystem.store(world)`, `at(world,blockX,blockZ)` and
`ClaimOverlayData.nodeSnapshot(world,viewport,limit)` are server-thread APIs.
`atWrappedPosition(world,x,z,exempt)` shares the existing inclusive-edge border
calculation for enabled overworld boundaries; the caller must supply the existing
exemption decision. It does not rewrite stored keys or alias exempt outside
territory. Other dimensions retain their own coordinates. Normal protection
lookup still uses the entity positions supplied by the existing border handler.

Stage 9 captures actual terrain on the owning server thread, passes detached
data to `Classifier<T>`, and publishes its `Classification` through `Store.apply`
on that thread. Results include explicit eligibility, algorithm/revision, sample
tick and evidence hash. Wrong-world and ended-session application is rejected.
Classification records never modify ownership or permissions. Stage 8 introduced
the interface without terrain policy; the Stage 9 implementation is below.

The Map source-layer path originally included up to 128 tracked/owned nodes
per viewport, in deterministic coordinate order; cyan means unclassified. Queries
inspect at most 65,536 candidate coordinates and load no chunks. Node outlines
reserve space inside the existing eight-packet / 4,096-primitive limit. They work
without an OSM dataset and remain available if the geographic query fails.
`YOU ARE HERE` remains on top. The node view is bounded, not a complete census of
all ownership or wilderness. Stage 9 exposes green/red classification styles
through the same path without conflating source subdivisions with ownership.
Stage 10 supersedes this display limit with 64 nodes and independent ownership
and eligibility toggles; it leaves authoritative node identity/rules unchanged.

**Verification:** source traces cover command/flag creation, territorial NBT,
world/session release, protection lookup, shared wrapping, packet publication and
client-only rendering. Arithmetic checks cover positive/negative/zero node edges
and inclusive wrap endpoints. Map packet limits were inspected. Final offline
Forge 1.7.10 `compileJava` succeeded and all eight claim node class headers are
Java 8 major 52. Save/reopen UUID stability, actual migration, border exemptions,
dedicated-server operation and Map display require runtime
checks; no disk round-trip or in-game behavior is claimed. Stage 4's extended
height blockers remain separate, and ordinary 256-block worlds are supported.

## Terrain claim eligibility (Stage 9)

`ClaimTerrainService` captures actual loaded blocks within each Stage 8 node's
exact protection footprint. The active Earth profile supplies vertical bounds;
ordinary worlds use their existing block height. Unsupported height engines
defer classification, and Stage 4's extended-height gate remains disabled.
Biome names, sea-level assumptions and OSM polygons never authorize a claim;
imported geography remains an independent map/reference layer. No internet or
geographic extract is required for terrain decisions.

Each integer X/Z column contributes at most one qualifying surface. Capture
starts at the highest populated chunk section and searches downward within the
active block bounds. A surface needs a solid, non-replaceable block and actual
collision boxes with a flat, full-cell top, at least 1/8 block thick, inside the
block's Y interval. Coplanar box unions are supported, bounded to 32 boxes per
block. Vanilla bottom/top/double slabs retain their actual fractional top height;
modded full solid decks use the same properties rather than a dirt/stone whitelist.
Leaves/foliage, plants, cactus/gourd, liquids, ice and packed ice do not contribute
surfaces. Decoration or foliage does not replace the solid terrain below it.
Narrow supports, fences and stairs without a full top plane do not themselves
contribute a whole qualifying column. Invalid/throwing or over-limit collision
queries defer rather than reporting missing land. This is territorial surface
measurement, not a teleport clearance or architectural reconstruction check.

Liquid encountered before a qualifying surface prevents the floor beneath it
from counting. Solid terrain below that liquid is reported as submerged; liquid
with no observed solid floor is open liquid/water. A dry surface with solid support
below it is dry/filled land. A qualifying deck separated from water below by air
or excluded vegetation is a platform, even with no dirt or stone underneath.
Pylon-supported columns may appear as dry/filled support alongside deck columns.
Dry/filled measurements do not prove whether a block was natural or placed by a
player. Reclaimed land and natural land use the same qualifying surface rule.
Empty columns contribute nothing. All categories are determined from actual
blocks, including when biome/geography still describe ocean.

Connectivity uses **four** orthogonal neighbors inside the same node, with top
height difference no greater than `claimTerrainMaxStep` (default 1 block). It never
crosses a node boundary or uses diagonal contact. The largest connected component
must contain at least:

```text
max(claimTerrainMinArea, ceil(nodeColumns * claimTerrainCoveragePercent / 100))
```

Defaults are 64 columns and 25%. Ordinary 16×16 nodes therefore need 64 connected
columns; 16×31 nodes need 124, and the legacy 31×31 zero node needs 241. One block
cannot qualify even at minimum configuration (area is bounded to at least 2).
An unobstructed 8×8 deck can qualify an ordinary node; two disconnected 4×8 decks
cannot. Larger zero-coordinate footprints need larger decks. Each neighboring
ocean node must independently pass; adjacency to an eligible rig never unlocks it.
This deliberately clips city-radius/flag-selection grants to eligible nodes.

New City Center placement checks eligibility before placing/consuming its item,
then checks again before charging founding costs. Unknown/stale terrain produces
an explicit deferred message; retry the item after sampling. Expansion, legacy
capital flags and conquest-flag selections use the central territorial grant gate.
Deferred grants carry bounded, transient intents; on completion they recheck the
same tile/location, active faction owner, intended radius/selection, existing
territory and applicable flag foundation/capture state. They are discarded if
authorization changes or the source chunk unloads. Intents do not persist across restart; retry expansion or
selection afterwards. Relocation retains the existing transaction/overlap/spacing
rules and existing city nodes within the destination shape, adding only eligible
new nodes; deferred additions use the same validated intents. Costs and existing
upkeep formulas are unchanged, including selection-size based conquest upkeep.

`/c city terrain` shows the current protection node's eligibility and connected,
dry/filled, platform, water and submerged counts, or the deferred reason.
`/c city terrain retry` lets an officer/leader in their active city's territory
retry the current radius after reclamation/building. It does not buy an upgrade,
increase the radius or bypass other claim rules. Founding, upgrade and conquest
selection messages report rejected/deferred nodes. Existing faction claims and
authorized ownership transfers remain independent of classification. Changing
eligibility never unclaims or removes a flag; existing foundation/sky, faction,
war and persistence rules may still do so under their original conditions.

Eight captures at most share the existing `XFEarthWorkQueue` with generation,
imports and map publication. Each capture step reads at most one block column's
current Y position; completion evaluates at most 961 detached columns and may
publish one validated grant with the existing bounded flag-foundation checks.
Live chunks are never accessed by a worker. The
existing `earthRoadStepsPerTick` / `earthRoadTickMillis` defaults (1024 / 2 ms)
remain the shared budget, checked between bounded steps rather than interrupting
Minecraft/mod callbacks. Required footprint plus one-block halo chunks must
already be loaded, with availability rechecked during capture. No chunks are
force-loaded; block implementations that independently access distant chunks
are outside the inspected support contract.

The runtime cache/waiting set is bounded to 4,096 records per world; backpressure
defers requests rather than choosing land/ocean. Results persist through the
Stage 8 schema with algorithm `xf:connected-surface`, revision 1 and an evidence
hash. Saved results are never trusted as fresh after restart. Fresh grant results
must match the current session, policy and invalidation generation and be at most
`claimTerrainMaxAgeTicks` old (default 100 server ticks). Mutations during capture
discard the partial sample. World/session shutdown invalidates queued work.

Forge non-cancelled break/place/multi-place events invalidate affected nodes and
their one-block collision neighborhood. Standard Forge detonation invalidates
affected positions, coalescing node keys. Over 4,096 blocks or 256 distinct
affected nodes invalidate the world's cache epoch without scanning every node.
Chunk unloads also invalidate that epoch. XF live
road/feature imports call `invalidate(world,x,z)` explicitly after each edit.
Other owning-thread bulk editors can use that public hook. WorldEdit/FAWE, direct
chunk/section writes, custom explosions bypassing Forge, fluid changes and other
unobserved mod edits have no immediate integration. A bounded ordered cursor
visits `claimTerrainRefreshNodes` tracked nodes every 20 server ticks (default 2),
without copying or scanning the full registry. A pass over N nodes takes roughly
`20 * ceil(N / refreshNodes)` ticks plus queue/capture delays; there is no fixed
whole-world refresh deadline. Hook-bypassing edits can remain unnoticed until
refresh or a stale claim attempt; an edit within the freshness window is not
guaranteed immediately visible. Capture spans ticks, so hook-bypassing edits may
also produce mixed-time observations until a later refresh; this is not an atomic
world snapshot. No classification-only auto-unclaim is introduced.

Map viewport requests refresh stale node data under the same queue and expose
cyan pending/unavailable, green eligible and red insufficient surface styles.
They retain `YOU ARE HERE`. Stage 10 uses 64-node / typed packet bounds and
automatically refreshes the open Map viewport every 40 client ticks. These remain
bounded snapshots; pending colors are not an eligibility guarantee.
Optional Dynmap/JourneyMap ownership layers are unchanged and do not acquire
terrain-policy dependencies.

**Verification:** source traces cover land, submerged floor, open liquid, filled
land and deck capture; slabs/collision unions; connectivity; all production
territorial grants including relocation; invalidation, freshness, cancellation,
saved classification and independent ownership. Arithmetic checks cover area
thresholds, isolated/split decks, fractional height-step limits and endpoint
invalidation around negative/zero/positive units in three dimensions. Final
offline Forge 1.7.10 `compileJava` succeeded with the unchanged Gradle tooling;
all ten terrain class headers are Java 8 major 52. No packaging, remapping,
runtime launch, actual terrain sampling,
save/reopen, explosion/bulk-edit observation, dedicated-server or in-game Map
behavior is claimed. Those checks remain required; custom mod block collision
behavior and hook-bypassing edits remain compatibility limits.

## Geographic overview (Stage 10)

The existing Map item now has independent Roads, Fields, Buildings, Businesses,
Admin, Ownership and Eligibility toggles. Zoom, anchored mouse wheel, dragging,
coordinates, center/full controls and `YOU ARE HERE` retain their existing
projection. The marker draws last. A short click selects a source feature or node;
repeat clicks at the same position cycle overlapping features. Names, source
categories and identifiers are bounded public text. **Info** provides pageable
selection details, legend, query notices and the selected dataset's full
attribution. OSM/ODbL credit is also visible on the main map. Everything works
offline; absent base imagery leaves the marker and available vector layers usable.

`XFEarthMapLayers` is the shared version-2 point/line/area vocabulary.
`XFEarthGeoMapSource` reads the existing version-1 Stage 5 region index and reuses
the active profile, road rank/kind policy and feature generation identity.
Even-odd polygon holes and multipart contours survive clipping. Field outlines
include farmland, meadow, farmyard, orchard and vineyard; orchard/vineyard source
outlines do not imply supported crop generation. Buildings retain source
footprints and businesses retain actual source geometry/points; generic appearance
is explicitly identified. Administrative boundaries are informational and never
assign territory. Missing or incompatible data is **unavailable**, not confirmed
absence. Dataset coverage remains unverified.

Geo colors are gold roads, green fields, tan buildings, pink businesses and purple
administrative boundaries. Brighter source geometry indicates recorded generation
evidence: a generic feature fragment recorded in a loaded chunk, or a processed
surface-road chunk intersecting that route. This does not prove a whole feature
was generated or survey current blocks; unloaded chunks and later edits can make
evidence incomplete. Ownership fill uses the public faction/zone color (wilderness
gray); eligibility outlines use cyan pending/unavailable, green eligible and red
insufficient surface. Eligibility never changes ownership.

The existing `XFEarthRoadMap` service now publishes all layers. It snapshots loaded
chunk completion records on the server thread (4,096 inspected chunks, 128 stamps,
8,192 feature keys maximum). One bounded worker reads and clips detached geographic
data and processes detached generation evidence; it never accesses live chunks.
Publication through `XFEarthWorkQueue` reads fresh node metadata on the owning
thread and rechecks player/world/session. `ClaimOverlayData.maySeeClaim` applies
the existing public ownership visibility rule and current dimension/alive/server
checks before serializing owned nodes. Only public names/colors, zone, geometry
and eligibility leave the server. No faction/city UUIDs, homes, flags, membership,
balances, private war data or other players' positions are included. Tracked
wilderness nodes may display public eligibility; this is not a wilderness census.

Queries cover at most 16 Minecraft regions, visit at most 8,192 source records and
prepare at most 96 geographic features / 3,072 vertices. Node snapshots reserve up
to 64 features first; the final response contains at most 128 features / 4,096
vertices in eight parts of 16 features / 512 vertices each. Oversize or malformed
parts are skipped with notice; bounded/truncated views are reported. Larger views
require zooming in. At coarse zoom, road categories are filtered, near-collinear
line vertices simplified and subpixel polygon detail omitted. This is a geographic
overview, not an exact street/building replica. Packet protocol **2** requires
matching XF client/server builds; profile packet registration remains unchanged.
The conservative UTF-8 payload bound is 30,653 bytes, below the 32,700-byte limit.
Full source attribution over 1,024 characters makes source data unavailable
rather than silently dropping its license credit.

The worker cache and client frame cache each retain eight views, keyed by world
identity, profile, dataset identity, viewport, LOD and layer mask (plus source
policy on the worker and wire version on the client). Client frames normalize
geometry once through `XFEarthMapData.mapX/mapZ` and lazily cache seven GL display
lists, at most 56 total. View requests/assembly have tokens, consistency checks and
a 10-second timeout; requests are throttled to 40 server ticks. Logout/dimension
changes cancel server pending requests; world unload clears workers/sessions.
World/resource cleanup releases imagery textures and all vector lists and clears
client assembly. Closing the GUI cancels assembly but retains bounded reusable
frames. Scissoring still uses the **16-element** LWJGL integer buffer and restores
the first four scissor-box values, preserving the original buffer fix.

Optional JourneyMap native API **1.7.10-2.0.0** supports polygons, not polylines.
Its adapter reuses the last accepted Map viewport/layer mask as at most 128
polygon overlays, including holes, thin symbolic line strokes and source-point
symbols. The legacy 5.2 reflection draw adapter reuses the same frame and caps a
pass at 512 vertices. Neither preloads all Earth or independently queries the
JourneyMap viewport. Existing optional ownership overlays remain available;
Dynmap remains optional. The SDK and existing reflection contracts were inspected,
but no installed JourneyMap runtime was available to validate these additions.
The Xenofactions Map has no JourneyMap dependency. Angelica/shaders and optional
render integrations remain runtime-unverified.

**Verification and stage status:** source inspection traces shared projection,
holes/clipping, packet ranges, public claim filtering, client/server ownership,
cache release and optional class boundaries. Arithmetic checks compare marker and
vector projection at negative/edge/fractional positions across zoom levels and
bound packet UTF-8/geometry size. Final offline Forge 1.7.10 `compileJava` succeeded
with unchanged Gradle/Java setup and inspected class headers remain Java 8 major
52. No test files, packaging, remapping/reobfuscation, runtime launch or in-game
observations were performed.

| Stage group | Source integration and remaining validation |
| --- | --- |
| 1–4 height | Shared profile/bounds and guarded native integration exist; extended height remains disabled because the selected engine lacks Earth-only isolation/native generation and has gameplay source defects. No extended-height runtime validation. |
| 5–7 geography | Offline import and protected generation paths are source-integrated and compile. No local extract was available; real import, block generation, geometry alignment, save/reload and manual-backup/import protections remain runtime-unverified. |
| 8–9 claims | Persistent node/classification/grant paths are source-integrated and compile. Actual terrain, platform removal, reload, periodic/bulk invalidation and dedicated-server behavior remain runtime-unverified; ownership is preserved independently. |
| 10 overview | Item layers, selection, public networking, cache lifecycle and optional adapters are source-integrated and compile. Real GUI/GL clipping, zoom/drag, packet delivery, cleanup and JourneyMap behavior still require in-game observations. |

## Manual runtime plan

With real exports available, run `runClient`, create each variant, verify the
progress screen remains responsive, terrain identities are correct, no edge
terrain or second population appears, and reopen each save. For populated, use
the intended full modpack and repeat after removing one required mod; creation
must be blocked with no partial save. These tests cannot be completed without the
owner's binary exports.
