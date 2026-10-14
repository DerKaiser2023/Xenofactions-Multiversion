# Changelog

2026-10-05 13:13 â€” Establish the multi-version ExampleMod architecture

- Replace the single 1.7.10 template with eight independently buildable Forge/NeoForge adapters and portable shared domain sources, tests and assets.
- Select maintained tooling per Minecraft generation, including GTNHGradle/RetroFuturaGradle with Jabel for standalone Java 8-compatible Forge 1.7.10 output.
- Add pinned wrappers, automatic build-JDK selection, shared metadata generation and transactional root production build numbering.
- Add coordinated build/test commands, independent CI builds and an eight-target release workflow using one version per release.

2026-10-05 14:18 â€” Add Windows setup and IntelliJ multiversion launchers

- Document Windows JDK/Python/Git setup, per-JDK environment variables and the distinction between build JVM, source language and mod runtime.
- Expose per-target build/client/server tasks and aggregate build/test tasks in the root Gradle multiversion group, delegating to isolated target wrappers through the existing coordinator.
- Run common tests in the parent aggregate build to avoid recursively launching the root project; preserve the existing production allocator and non-production numbering behavior.
- Add portable IntelliJ Gradle shortcuts and document separate adapter imports for native sources, classpaths and debugging.

2026-10-05 14:54 â€” Reduce build warning noise without hiding failures

- Suppress only obsolete javac options on the three Unimined Java 8 targets and grant native-library access only to the 1.7.10 Gradle wrapper/daemon JVMs.
- Replace the common project's deprecated Java convention API and remove task-time Project access from shared version/resource actions without changing the production allocator.
- Supply Legacy Forge's exact MCP ZIPs through ModDevGradle's artifact-manifest API, enable the 1.16.5 idea plugin and remove redundant 1.8.9/1.12.2 SRG mapping declarations.
- Forward full/failing warning diagnostics through root launchers and document retained upstream deprecations; keep normal compiler warnings, Gradle diagnostics and build failures visible.

2026-10-05 16:34 â€” Make archive expectations reusable and clarify native development

- Centralize project identity, production archive naming, generated version-class paths, required shared/adapter classes and resources, metadata profiles and per-target remapping symbols in verification.json.
- Replace ExampleMod-specific verifier rules with configured expectations; check exact loader metadata, generated/manifest versions, target bytecode levels and remapped symbols while preserving isolated builds and the existing version transaction.
- Extend only the intentional Java 8 compiler-options warning suppression to common main/test compilation and ignore generated legacy IDEA project/workspace files while retaining portable .run configurations.
- Document native IntelliJ synchronization by toolchain, template customization and feature-dependent server-only/client compatibility expectations.

2026-10-05 18:02 â€” Repair legacy development mod discovery

- Select ASM 5.0.3 for Forge 1.6.4 development so its scanner accepts Java 8 classes. Keep generated BuildVersion source in normal compilation and enforce Java 8 APIs on newer compilers while retaining native javac 8 and GTNH/Jabel contracts.
- Verify every development class under build/classes and main class outputs before native runs, packaging and checks on all five Java 8 targets, including generated and auxiliary classes.
- Keep Unimined's JetBrains compiler annotations off the Minecraft runtime classpath on 1.6.4, 1.8.9 and 1.12.2.
- Isolate 1.6.4 remapping and disable synthesized parameter metadata incompatible with FML's old visitors. Launch an unchanged copy of its Minecraft JAR under a safe filename to avoid FML's plus-to-space URL-decoding error; retain upstream certificate and binary-patch diagnostics.
- Enable native 1.6.4 server console input and optional isolated development port/config/world directories. Document the ASM launch-library requirement for installed Java 8 environments without introducing mod-specific template logic.

2026-10-05 20:04 â€” Restore stock Forge 1.6.4 bytecode compatibility

- Compile 1.6.4 production sources with JDK 17 and `--release 7` to Java 7 APIs/bytecode (major 51), keeping the existing Gradle and game JVMs separate.
- Remove the development ASM 5 override so both installed and development Forge retain stock ASM 4.1.
- Verify every class header in the final remapped artifact through build/check and CI; inspect all archive classes and reject bundled dependencies or bootstrap metadata on 1.6.4.
- Document the per-target source, bytecode and runtime requirements without downgrading other targets.

2026-10-06 21:59 â€” Correct Forge 1.16.5 resource-pack packaging

- Require and parse root-level Minecraft 1.16.5 pack metadata during production archive verification, checking format 6 and the resource description. Document that utilities must retain this target metadata when removing demonstration assets.

2026-10-07 00:28 â€” Restore Recommended Forge compatibility (1.0.1.1)

- Compile Minecraft 1.12.2, 1.16.5 and 1.18.2 against Recommended Forge 14.23.5.2859, 36.2.34 and 40.3.0; align loader metadata, target records and existing archive expectations with those compatibility floors. Preserve the existing Recommended Forge pins for other Forge targets and the stable NeoForge 21.11.45 baseline.
- Correct legacy mcmod.info version-reference syntax and required Forge dependencies, preserving current gameplay and optional-client behavior.
- Use the string dependency-information flag required by FML 1.6.4; keep the Boolean representation on later targets.
- Use the lowercase forge dependency ID introduced by FML 1.12.2; retain the native Forge ID on earlier targets.
- Bump the semantic patch version and start the corrected release at 1.0.1.1.

2026-10-07 01:29 â€” Separate template revisions and add manual build pruning

- Restore the example mod metadata to version 1.0.0 and initial build 1. Treat the stored build number as the next production number, advancing only after successful packaging; align direct builds, all-target coordination and CI release persistence.
- Add template.properties with internal templateRevision=1, independent of filenames and all public mod metadata. Document manual template revision updates and downstream counter migration.
- Add opt-in root pruneBuilds and the portable IntelliJ "ExampleMod - Prune Old Builds" configuration. Retain the newest production JAR independently in each target's build/libs directory using creation time, with a last-modified fallback and deterministic timestamp ties; exclude classifier/unrelated archives and redirected paths, and share the production lock.

2026-10-07 18:01 — Establish the two-target Xenofactions migration foundation

- Replace demonstration content with the current Xenofactions 1.7.10 implementation and all existing resources, retaining hfr registry/save identity, packet order, migrations, gameplay and optional integrations. Correct display/version metadata and the absent legacy config GUI factory reference.
- Limit aggregate/CI/IDE build targets to Forge 1.7.10 and 1.16.5 while preserving pinned wrappers, independent builds, shared release numbering and archive verification. Remove unused target scaffolds; start Xenofactions releases at semantic 2.3.6 with the template build counter.
- Share only the unchanged platform-neutral faction role/member models and inclusive block-edge geometry.
- Add native 1.16.5 bootstrap/config/lifecycle, read-only faction/city save inspection and a server-owned map-bounds container/screen/wand editor. Keep packet actions on the owning thread and validate level-4 permission, session/container identity, dimension, target state and expiry.
- Retain unhandled faction/map NBT fields, preserve player editor context, require explicit dimension mappings, back up existing maps before edits and reject unreadable existing data. Native match simulation, faction/claim gameplay, shops, kits and integrations remain outside this initial foundation.
- Preserve and scope existing 1.7.10 gameplay documentation/Earth authoring tools; document native commands, configuration, support boundaries and persistence architecture.

2026-10-09 02:49 — Sync city relocation fix and begin native faction gameplay

- Bring the main repository's city-home relocation correction into the 1.7.10 target: keep homes that remain inside rebuilt city territory, preserving the translated-position and center fallback for homes outside it. Earlier XShop/logout, Builder Contract, Earth-boundary, voting and FFA fixes were already present.
- Replace native inspection-only membership with server-owned faction creation, UUID applications, online acceptance, denial, leaving/kicking, ranks, leadership transfer, rename, MOTD and confirmed disband. Retain legacy roles, normalized names, stable faction/member IDs and unported NBT; mirror legacy member fields and update affected territory/diplomacy/terminal references.
- Port world-root creation cooldowns and new-player PvP protection with original expiry values, UUID/name fallback handling, expired-cooldown cleanup, administrator controls and per-server lifecycle ownership. Disband applies seven-day leader and three-day member creation penalties without blocking applications. Preserve the reference's timer-only keep-inventory field rather than introducing inventory retention.
- Back up existing faction and timer stores before the first mutation, refuse malformed/future identity data and ambiguous authorization, and use temporary-file replacement for timer writes. Persist native applications and reject acceptance into a second faction. Name-only imported staging data stays unresolved; city gameplay, claims, diplomacy, economy and matches remain subsequent native migration work.

2026-10-09 13:08 — Port native core faction gameplay to Forge 1.16.5

- Implement real City Centers with named founding, stable city identity, five upgrade levels, original territory geometry, capital/home rules, transfer/removal and authorized relocation. Preserve exact homes still inside rebuilt city territory and legacy translation/center fallback otherwise; retain first-write backups and opaque save fields.
- Bind faction/ally/neutral/enemy permissions, raid gates, safezones/warzones, explosions, city foundations, Officer Chests, bankruptcy and build grace to native Forge events. Add administrator zoning and rectangular wand selection through the existing server-owned editor lifecycle; the Debug inventory item remains the explicit protection bypass.
- Port UUID alliances, separate one-way enemy markers and declared wars, confirmations/costs/online gates, cooldowns, allied defense, multi-city peace offers, ceasefires, surrender/transfers and timed generation tribute. Restore persisted logical wars with their timestamps while administrator war toggles remain session-owned.
- Add native hourly prestige generation/upkeep/escalating war costs/deposits and crisis/collapse/fallen penalties. Keep withdrawal disabled. Register minimal faction building dependencies, operational Warp Tent accounting/travel, conquest capture, Foundation/Asphalt, recipes, built-in flags and claim-map/free-tool utilities without importing unrelated production machines.
- Port faction/alliance chat and public faction prestige/rank prefixes, native mute/ignore persistence/enforcement, remaining core force/merge/admin commands and stone-drop definitions/rewards. Require receiving-leader merge acceptance, preserve unresolved identity staging, and clean affected city/relationship/travel references on lifecycle changes.
- Keep 1.7.10, platform-neutral common sources and pinned build islands intact. Shops/terminals, matches/kits, builders, custom image flags/wall art, machines/weapons, Earth systems and optional integrations remain separate migration work. Document native command/configuration/save boundaries and the corrected ten-minute home/rally setting cooldown.

2026-10-09 22:36 — Restore native faction content discovery and border feedback

- Assign all registered player-facing 1.16.5 items and block items to the Xenofactions Creative tab, retaining registry IDs and the legacy hidden server-issued selection wand.
- Restore server-owned faction/city/zone transition tracking, five-second flag popups, the original border-crossing sound and nearby exposed-edge firework particles. Suppress repeated notifications, seed login/respawn silently, clean session state and retain Right Alt particle toggling. Require matching native client/server protocol versions.
- Restore non-opaque City Center and Conquest Flag blocks and sample raised cloth lighting at its world position. Preserve normal lighting, selected faction tint and untinted pattern overlays without full-bright rendering.

2026-10-09 23:16 — Preserve MCHeli ownership of private steel

- Remove the 1.7.10 compatibility hook that exported MCHeli's private steel ingot into
  the generic ingotSteel OreDictionary namespace. This preserves MCHeli's resource
  boundary during extraction of the vehicle furnace/foundry chain.
- Leave XF metallurgy blocks, items, processing, recipes and world data in place for
  the subsequent cleanup pass. The standalone Xenofactions repository is unchanged.

2026-10-10 01:36 — Maintained gameplay content cleanup

- Removed obsolete RF/oil/nuclear, weapons, stock/tax, block health, NPC/Builder and abandoned building/material/equipment paths from the multiversion targets, including registrations, recipes, packets, configuration, renderers and unused assets.
- Kept the Production Line power-free with level-one cogs, original University research and active faction/map/shop/TDM/world systems. Removed the custom steel ingot and its acquisition paths; retained components use iron. Removed rubber sandbag recipes.
- Removed withdrawal in both targets; existing hidden Prestige tokens retain deposit compatibility. Removed registry IDs are deliberately unsupported, old excluded stores remain untouched, and native unknown-NBT preservation continues. Clients and servers must match builds.
- Updated maintained feature, recipe, configuration and migration documentation; the standalone historical repository is unchanged.

2026-10-10 04:27 — Preserve styled client chat through the 1.7.10 filter

- Replace formatted-string reconstruction with recursive filtering of text components and translation arguments, copying each component's original style and siblings. Preserve URL/command click events, hover events, formatting and translation keys without modifying the received component.
- Return the filtered component through Forge's normal client chat event instead of manually printing plain text and canceling delivery. Keep the filter toggle, substitutions and intentional chat-mute behavior.

2026-10-10 19:17 — Finalize public chat for optional Discord bridges

- Add the optional 1.7.10 PublicChat callback API after mute checks, public routing, filtering and Minecraft delivery. Faction/alliance and command/private routes never publish external messages; no Nilcord dependency is required.
- Apply the existing configured substitutions to public message bodies on the server. Mark filtered public components so clients do not substitute them twice; keep client filtering and styled components for other messages.

2026-10-11 22:39 — Recover retained legacy automation, traders and deposit rules

- Recover grain mill hopper inputs through inventory-free structure proxies and mature-rice right-click harvesting on 1.7.10, preserving core inventory NBT, processing and faction protection.
- Add craftable UUID-owned stocked single-offer Player Traders on both targets alongside the existing XShop architecture. Keep templates, stock and collected payments separate; validate exact item/NBT identity, offer revision, fresh permissions and all capacities before committing whole purchases.
- Add disabled-by-default server ender chest deposit restrictions with blacklist/whitelist, optional portable-storage rules and quantity caps. Validate each vanilla click against its prior state and restore player/ender/cursor state when rejected; retain withdrawals and vanilla save format. Legacy metadata rules are supported only on 1.7.10; modern rules require flattened registry names.
- Store new alliance offers by faction UUID on both targets, resolve faction/player command targets, retain resolvable old pending keys and unresolved native migration records, and remove only the accepted offer after saving both relationships and refreshing presentation.
- Keep removed systems absent; preserve native adapters, optional HBM absence handling, existing XShop saves and target-specific item serialization. Update durable usage, configuration, recipes and migration documentation; modern networking now requires matching foundation-3 peers.

2026-10-12 19:40 — Add persistent faction invitations to both maintained targets

- Add Officer/Leader `/c invite <player>` and `/c uninvite <player>` commands, rejecting missing online targets, existing faction membership and equivalent pending invitations. Saved invitation records allow offline revocation.
- Add `/c join <faction>` to accept invitations alongside the existing application workflow. Save invitations under the owning faction UUID in existing faction NBT, preserving faction renames and configured player identity semantics.
- Clear invitations only after successful membership changes, including application acceptance, faction creation, forced joins and merges; remove disbanded factions’ invitations. Keep legacy TDM membership restrictions.
- Include commands in native help, completion, the legacy command catalog and durable command documentation.

2026-10-14 07:19 — Start custom flag cooldowns after successful installation

- Start the 1.7.10 faction UUID import cooldown only after server-thread acceptance and atomic image installation. Keep active imports separate, allow retries after failures without a rejection cooldown, retain earlier success cooldowns, and report remaining cooldown seconds.
- Recheck current player authority and faction existence before applying prepared images. Cancel superseded imports on successful built-in selection, clear or reload; release active state on completion/submission failure/cancellation and discard callbacks on server stop or overworld unload.
- Preserve the previous file/hash when installation or clear fails. Read requested flag bytes on the server thread, verify their hash, and handle client flag packets on the client thread to reject delayed bytes against current metadata without racing texture installation. Bound the sanitized PNG by the configured byte limit as well. Preserve HTTPS/host/image/timeout restrictions, config keys, faction saves, commands, Java 8 and pinned build tooling; shared and 1.16.5 sources remain unchanged.
- Update existing flag configuration documentation. The 1.7.10 compile task succeeded; no remap, packaging or game runtime was performed. Dedicated-server retry, concurrent/superseded request, authority loss, failed replacement, client synchronization and same-process restart checks remain required.

2026-10-14 07:54 — Guard optional Dynmap discovery and add acquisition achievements

- Check Forge's Dynmap mod detection before optional class discovery. Missing Dynmap or missing Dynmap classes disable marker updates for the server session with one `Dynmap unavailable` error and no attached exception. Reset discovery and marker caches through server start/stop; retain API-readiness retries and existing marker generation. Unrelated reflection, marker and programming errors propagate instead of being suppressed.
- Add Windmill (the registered Grain Mill, as confirmed) and Fishing Net achievements on a Xenofactions page, with their block-item icons, localized names/descriptions and vanilla Acquire Hardware parents. Reuse the registered CommonEventHandler for server-side crafting and successful inventory pickup, awarding only the recipient through vanilla statistics. No retained machine recipe outputs either block, so no machine collection hooks are added. Recipes and 1.16.5 remain unchanged.
- Update existing Dynmap configuration and recipe documentation. Source inspection confirms acquisition event timing and vanilla parent/persistence behavior; the existing offline 1.7.10 `compileJava` task succeeded. Runtime checks still required: absent/available/initializing Dynmap, integrated-server restart, achievement page layout, crafting/pickup and achievement save/sync. No game runtime, remap or packaging performed.

2026-10-14 08:14 — Add the 1.7.10 Earth Map item and offline tiled viewer

- Register `hfr:earth_map` as Map in the Xenofactions tab, reusing the existing map icon without changing vanilla maps, the Claim Map or recipes. Right-click opens a client-only full-map view with a live YOU ARE HERE marker, X/Z, supported latitude/longitude, wheel/button zoom, drag, player centering and a full-view reset.
- Describe the active dimension-0 Earth profile through appended read-only packets and the existing server tick queue. Use shared bounds/projection conversion and prepared-image metadata/cache abstractions for later JourneyMap use, with no JourneyMap requirement. Show unavailable messages for non-Earth dimensions, invalid profiles and missing/mismatched imagery. Discard stale descriptions on world/dimension changes.
- Prepare and bundle 256-pixel image pyramids from the existing MinecraftEarthMapXF GlobCover 10k/20k/40k terrain rasters through the extended offline source preparation tool. Record source hashes and validate source dimensions against profile resize. Support custom final-grid rasters through optional mapImageId and client resource packs. No online gameplay fetches, chunk scanning or terrain generation.
- Align +X with raster columns and +Z with rows; use the source's scale/120000 degrees per block and cropped 358.4-by-179.2-degree extent for standard profiles. Follow actual player positions across existing border teleports without duplicating wrap arithmetic or changing borders/exemptions. Handle negative coordinates, rectangular bounds and explicit offscreen/outside indicators.
- Decode requested tiles on a bounded client worker, upload on the client thread, and cap RGBA texture storage at 8 MiB/32 fixed slots. Eviction frees GL storage and replaces the registered texture to release its Java pixels under 1.7.10's TextureManager semantics. Cancel pending work and reject stale uploads on world unload, disconnect and resource reload. Keep common items/packets/data free of client classes and leave 1.16.5 unchanged.
- Update existing Earth documentation. The final offline 1.7.10 compileJava task succeeded; all 126 tile PNGs, dimensions and source hashes were verified, and coordinate arithmetic was checked against the source calculator/preprocessing regional references across smoke/8000/4000/2000 profiles. Dedicated-server startup/opening, in-game alignment/movement/wrapping, controls, custom imagery and lifecycle/memory behavior remain runtime checks. No runtime launch, remap or packaging was performed.

2026-10-14 08:30 — Preload Earth backgrounds into optional JourneyMap 6.0.7

- Inspect the actual Deadhour Forge 1.7.10 JourneyMap 6.0.7 jar/API/storage and add an optional reflection-only day/night surface importer. Request the existing authoritative Earth profile on entry and resample the shared offline imagery with the Map item's X-east/Z-south bounds conversion. Use native 512-block region holders, locks, PNG writer, LOD invalidation and client-thread texture publication; leave chunk generation, exploration flags, waypoints, other map types and JourneyMap configuration untouched. Leave 1.16.5 unchanged.
- Preserve existing detailed terrain and replace only empty or exactly owned 16-block areas. Tag backgrounds with alpha 254 so native opaque terrain updates relinquish importer ownership even for identical colors. Journal ownership and per-region completion, cache by endpoint/world/dimension/profile/actual imagery bytes, refresh changed backgrounds and reduced bounds, cancel stale jobs, release unbound clean image buffers and report one completion or useful failure. The Map item remains independent.
- Limit preload support to the inspected build. JourneyMap's saved-server-name fallback can alias different endpoints without server world IDs; reject such an import with a clear error instead of mixing or relocating exploration data. Document that native identity limitation and the separate Earth preload behavior.
- The existing offline 1.7.10 compileJava task succeeded using the repository's Java 25 build runtime and Java 8 target toolchain. Inspect native storage/rendering/thread ownership and import coordinate/cache/cancellation paths. In-game visibility/alignment, LOD refresh, exploration replacement, reconnect/reload, profile changes and server identity collisions remain runtime checks; no runtime launch, remap or packaging performed.

2026-10-14 08:49 — Fix the 1.7.10 Map scissor query buffer

- Replace the GUI's four-integer scissor buffer with a reusable direct sixteen-integer buffer: LWJGL 2.9.4 requires sixteen remaining elements for `glGetInteger` even though `GL_SCISSOR_BOX` returns four. Clear before each query and retain indexed reads without `flip()`, matching native writes that do not advance the position.
- Include clipping setup in the existing `try/finally` and explicitly restore the saved four-component scissor box and prior scissor-test enabled state. Preserve imagery, coordinates, marker, zoom, dragging and JourneyMap integration; 1.16.5 is unchanged.
- Inspect the cached LWJGL implementation and the GUI's sole buffer-based OpenGL query, repeated-use buffer state and restoration paths. The existing offline 1.7.10 `compileJava` task succeeded. Opening the Map, repeated rendering and clipping restoration still require in-game validation; no runtime launch, remap or packaging performed.

2026-10-14 09:06 — Load local development mods and remove acquisition prerequisites

- Add repository-root `devmods/` for local Forge 1.7.10 JARs, ignored by Git. Pass them only to `runClient` through FML's existing `--mods` normal/coremod discovery; keep dependency configurations, release archive inputs and the configured run directory unchanged. Empty or absent folders add no arguments, and manually installed mods remain untouched.
- Skip byte-identical development JARs already on the launch classpath, in the run directory's `mods/` or `mods/1.7.10/`, or repeated in `devmods/`. Reject conflicting filenames and comma-containing paths instead of selecting a mod version or passing an ambiguous FML list. No mods are downloaded.
- Remove the Windmill/Grain Mill and Fishing Net achievement parents. Existing server-side crafting and successful pickup handlers already have no prerequisite checks. Preserve acquisition conditions, IDs, localization, icons, positions and vanilla saved statistics; leave other achievements, recipes, Java 8, pinned build tooling and 1.16.5 unchanged.
- Update existing development and achievement documentation. Offline 1.7.10 `compileJava` succeeded; inspect native FML discovery, vanilla parent gating, archive inputs and Git ignore rules. No runtime launch, remap or packaging performed. Actual development mod/coremod loading, duplicate suppression, crafting/pickup without prior progress and achievement save/sync remain runtime checks. The existing batch wrapper rejected its positional arguments in this environment; compilation used its pinned Gradle wrapper main and JDK directly without changing build tooling.

2026-10-14 09:27 — Establish the shared 1.7.10 Earth profile and height gate

- Centralize forward/inverse geographic conversion, continuous raster bounds and nominal elevation decoding in the existing XFEarthProfile. Add optional format-1 minimumY=0, maximumY=255, heightImplementation=vanilla-anvil and verticalElevationScale=1.0 defaults, preserving existing field meanings, saved terrain, bundled profiles, Map item/marker, claim grid, border wrapping and optional JourneyMap integration. Reject taller profiles until storage, networking, rendering and gameplay integration is complete; no save migration or height dependency is enabled.
- Reuse the canonical earth2000 profile for source validation, native raster conversion, region iteration and emitted metadata. Separate horizontal map scale from the vertical elevation multiplier; nominal heightmap values are not calibrated metres. Delegate existing MapData conversion to the profile and clear server profile caches on overworld unload/server stop.
- Move native pregenerator chunk construction, lighting/Forge callbacks and world-dependent Anvil serialization from its worker to the existing server tick queue. Keep at most one prepared chunk awaiting the server; snapshot world settings on the server, cancel pending work on shutdown/unload and serialize progress-file writes. Raster preparation and detached disk processing remain on the worker. Tick-time/throughput effects are unmeasured and require runtime checks.
- Inspect the exact Forge 1614/MCP stable 12 engine and pin CubicChunks1710 v0.1.23-alpha-pre as an evaluation candidate, with a future 512-block Y=0..511 target. Document its cube storage, packets, rendering/lighting, client/server components and required Earth adapter. Upstream alpha status, incomplete compatibility/conversion support, bootstrap distribution verification and untested optional renderers/Deadhour prevent declaring a production-supported taller world; ordinary-height geographic stages can proceed.
- Update existing Earth format, world-type and installation documentation with the shared interfaces and height blockers. Offline 1.7.10 compileJava succeeded using the existing Java 25 build runtime/Java 8 target toolchain. Inspect both unchanged bundled profile JSONs; mathematical checks preserve all 65,536 default elevation samples and cropped extents/forward-inverse coordinates at five standard scales. No runtime launch, remap, packaging or height-stack validation performed. Map alignment/default synchronization, pregenerator pause/resume/cancel, save/reopen and dedicated-server lifecycle remain in-game checks.

2026-10-14 10:06 — Add gated native Earth cube storage preparation

- Implement Y=0..511 storage preparation for the pinned CubicChunks1710 v0.1.23-alpha-pre format using optional RegionLib v0.1.0-GTNH's supported non-shadow-paging driver. Reuse native column/cube NBT, integer cube coordinates, sparse ExtendedBlockStorage sections, metadata/light nibble arrays and native HeightMap3D interval encoding; add no custom region format or height-mod runtime dependency. Keep new storage creation separate from gameplay, refuse existing destinations/columns and reopen only for inspection.
- Replace the Earth raster generator's dense fixed-height buffers with shared sparse section preparation and guard the ordinary Anvil conversion against upper sections. Retain full detached tile entity NBT and integer positions, existing cube payload/lighting bookkeeping and source geography. Reject malformed/out-of-range arrays, coordinates, intervals, versions, unknown blocks/light identities and unverified ChunkAPI/EndlessIDs codecs. Empty cube records establish column completeness without allocating block arrays.
- Record exact implementation/version/range/dimension in WorldSavedData metadata and native cubic height metadata. Default old profiles to ordinary Anvil, tag ordinary Earth worlds on normal save and record identity before new pregeneration finalization. Validate identity/layout before opening storage; reject mixed formats and incompatible pack payloads rather than converting or overwriting them. Keep Map/marker, claims, border behavior and other dimensions unchanged.
- Add server preflight before world construction and a narrowly targeted client coremod guard at Minecraft.launchIntegratedServer HEAD, before its earlier SaveHandler/session-lock writes. Use the existing coreModClass convention without upgrading Gradle, Java or enabling mixins. Inspect MCP/SRG targets and dedicated-server class safety. Keep extended gameplay/pack installation disabled until Stages 3 and 4; reject an active CubicChunks coremod on ordinary Earth startup/pregeneration because this pinned release initializes providers globally.
- Update existing Earth documentation. Final offline 1.7.10 compileJava succeeded; inspect the five new core/storage class headers as Java 8 major 52, confirm the cached SRG launch method/field, check Y=0..511 section/cell arithmetic and inspect both unchanged bundled profiles/storage paths. Trace native NBT, tile entity, lighting and RegionLib read/write/flush/close source contracts. No packaging, remap, runtime launch or native disk round-trip was performed. Native save/reopen, interruption, production coremod loading/remapping, server preflight and real tile entity lifecycle require runtime validation. Multi-entry atomicity/crash recovery, world-dependent custom opacity, global CubicChunks initialization and Deadhour/renderer compatibility remain limitations.

2026-10-14 10:46 — Connect gated Earth cube networking and native rendering

- Add a dimension/session/profile handshake for the same Stage 2 CubicChunks1710 v0.1.23-alpha-pre Y=0..511 identity. Validate native providers, exact component versions, bounds and renderer before acknowledging; defer native player subscriptions until acknowledgement and reject missing/incompatible peers. Require matching pinned height peers before the globally patched login/block packet protocol. Keep ordinary Earth paths and all extended gameplay/installation gates intact; no saves are migrated, height dependencies bundled or other dimensions activated.
- Reuse the native packet table and public encoders/decoders inside an ordered XF Forge envelope. Append packet IDs, preserve integer cube/block Y and native block/metadata/light/heightmap data, and send native tile descriptions after their cubes in the same queue. Validate packet lengths/counts, Y range, block/cube membership and parent section presence; reject unsupported data instead of hiding missing sections. Apply client world/tile/lighting/render changes on ClientTick and acknowledgements on ServerTick, with bounded queues, timeout, stale-session handling, same-dimension respawn and shutdown/unload cleanup.
- Add optional exact native subscription/send/description transformer hooks using the existing coremod registration. Keep client implementations behind the sided proxy. Require the selected native RenderGlobal mixin, reload its vertical section window before acknowledgement and invalidate null-storage light changes. Explicitly reject Angelica for extended rendering: the pinned height mod excludes that renderer mixin, and the inspected local Angelica 2.2.13 Celeritas reference still uses a 256-block range. Preserve the Map item, YOU ARE HERE marker, geography, claims and ordinary optional integrations.
- Update existing Earth documentation with the shared lifecycle interfaces, protocol/source trace, exact evaluation dependencies, unsupported integrations and Stage 4 activation blockers. Final offline 1.7.10 compileJava succeeded; all six new feature/core class headers are Java 8 major 52. Inspect the pinned production artifact's SRG method signatures and two tile-description send sites in memory, native world/section/light/render source and Forge 1614 packet limits. No packaging, reobfuscation/remap, transformer execution, runtime launch, dedicated-server launch or in-game observations were performed. Native global world initialization, gameplay/provider integration, storage crash recovery, bootstrap distribution and actual upper-section synchronization/rendering remain validation or activation blockers; Deadhour, shaders and extended JourneyMap compatibility are unverified.

2026-10-14 11:13 — Prepare Earth gameplay bounds and retain the engine safety gate

- Reuse the validated Earth profile through an owning-thread, dimension/world-type-aware lookup and sided client height-profile view. Add inclusive block-Y checks; retain cached active profiles and ordinary bounds for unrelated worlds/dimensions. Use these bounds for XShop Y parsing, TDM map/bomb selections and faction market registration loading/placement. Retain integer Y persistence, claim grid/node identity, geographic conversion and the existing Y=45..200 City Center gameplay rule.
- Add an Earth-only server-thread destination search using the actual entity bounding box, collision/liquid checks, vanilla fire/cactus exclusion and optional grounded support. Apply it to border wraps, exemption return, scheduled home/warp/rendezvous travel and build-grace home/spawn recovery. Search the profile's vertical range, allow feet above the last legal support block, preserve clear flight above build height and abort unavailable destinations instead of clamping into terrain. Leave unprofiled/non-Earth paths unchanged; keep optional vehicle integrations and the Map/YOU ARE HERE marker.
- Keep extended Earth gameplay/creation disabled and replace the generic future-stage gate message with the confirmed engine-isolation/generator blocker. The pinned CubicChunks release initializes providers, metadata/storage and spawning globally; its fallback generation only copies 16 vanilla sections, not Earth's native pregenerated cube contract. Source inspection also finds a second division of entity cube coordinates, reversed Forge placement-result bookkeeping and a problematic ceiling collision probe. No supported correction/per-world isolation path has been integrated, so dependency presence is insufficient and no partial opt-in switch or save conversion is introduced.
- Update existing Earth documentation with Stage 4's incomplete activation status, scoped migration, source traces, proposed Y=0..511 / sea level 62 target and unchanged component requirements. The existing converter already obeys profile elevation/sea-level bounds; arithmetic checks across all 65,536 samples preserve scale-1 terrain and sea level, reach Y=448 at vertical scale 2 and Y=510 at scale 448/193. Check inclusive standing-search loop endpoints and Java 8 classfile major 52. Final offline Forge 1.7.10 compileJava succeeded. No test infrastructure, packaging, remapping/reobfuscation, runtime launch or in-game observations occurred. Ordinary teleport/vehicle/terminal/selection behavior and the full extended stack require runtime validation. XRTP's owning implementation is absent; Angelica, alternate codecs, shaders, extended JourneyMap and Deadhour limitations remain documented.

2026-10-14 12:07 — Import offline Earth geographic extracts

- Add the Forge 1.7.10 local Osmium GeoJSON-sequence import path for roads, land use, building footprints, business candidates and administrative boundaries. Retain original object IDs, supplied tags/attributes/timestamps, source provenance and OSM/ODbL attribution. Reuse the authoritative Earth profile for projection, longitude-period wrapping and clipping, with polygon holes and multipart even-odd areas; preserve the Map marker and claim-node system. Add no block placement, network acquisition or height activation.
- Store versioned, profile-bound derived features and conservative Minecraft region indices outside Git workspaces. Bound records, vertices, writer handles and per-feature fan-out; stream processing on one independent worker, report recoverable feature failures and retain coverage as unverified. Reject changed inputs/incompatible identities and existing destinations. Interlock cancellation with publication and cancel on world unload/server stop; snapshot Minecraft-owned inputs on the server and keep world reads/writes out of the worker.
- Add level-4 `/xc earth geo import <dataset-id>`, `status`, `cancel` and `info <dataset-id>`, source/output configuration and shared dataset reader APIs. Document the exact two-file source package, external regional-PBF preparation, attribution, output format, limits and lifecycle in the existing Earth documents. Ordinary 256-block Earth remains supported independently of the unresolved Stage 4 extended-height engine blockers.
- Offline Forge 1.7.10 `compileJava` succeeded using unchanged Gradle tooling and the Java 8 target; all 16 geographic class headers are major 52. Inspect source/projection/index/identity/thread paths and check bundled/earth2000 reference coordinates, negative fractional region indexing, full-versus-cropped longitude period and binary header/entry sizes mathematically. No local extract was available, so import execution, actual Java geometry/disk round-trip, cancellation behavior and later in-game consumers remain unvalidated. No test infrastructure, packaging, remapping or runtime launch was performed.
