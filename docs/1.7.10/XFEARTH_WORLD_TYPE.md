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

The common `XFEarthMapData` owns bounds/projection conversion. The client
`EarthMapImages` and `EarthMapTileCache` provide reusable image metadata and tiles
for a later JourneyMap adapter; the item does not depend on that integration.
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

## Manual runtime plan

With real exports available, run `runClient`, create each variant, verify the
progress screen remains responsive, terrain identities are correct, no edge
terrain or second population appears, and reopen each save. For populated, use
the intended full modpack and repeat after removing one required mod; creation
must be blocked with no partial save. These tests cannot be completed without the
owner's binary exports.
