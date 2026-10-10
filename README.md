# Xenofactions

Xenofactions is a Forge factions-and-warfare mod for cities, territory, diplomacy, prestige, server shops, map editing and competitive gamemodes. The mod ID remains `hfr`; registry names and save identifiers for retained content remain stable.

## Supported targets

| Minecraft | Loader | Available behavior | Build JVM / compiler | Game runtime |
| --- | --- | --- | --- | --- |
| 1.7.10 | Forge 10.13.4.1614 or newer for 1.7.10 | Current Xenofactions implementation: factions/cities/capitals, claims/permissions, war/economy, XShop/terminals, maps/kits/TDM/FFA/Search and Destroy, editor, Earth maps, wall art, fortifications and level-one production | JDK 25 / JDK 17 with Jabel; pinned Gradle 9.7.1 | Java 8 |
| 1.16.5 | Forge 36.2.34 or newer for 1.16.5 | Native factions, cities/capitals/relocation, homes/warps, territory/protection/zones, diplomacy/war/treaties/tribute, prestige/upkeep/bankruptcy, build grace, chat, flags, faction utilities; map metadata and server-owned bounds editor/wand | JDK 21 / JDK 8; pinned Gradle 8.13 | Java 8 |

**The 1.16.5 core faction gameplay port is available for testing.** Matches, kits, shops/market terminals, Earth terrain, custom image flags/wall art, production machines and optional integrations remain pending. Use a separate test save until the new city/protection/war/economy gameplay has passed multiplayer acceptance. Imported saved match flags never start a match.

Install the matching production JAR on both client and server. Core builds require no installed optional mods. The 1.7.10 JourneyMap, Dynmap, Guide-API, NEI, HBM and MCHeli integrations remain optional; equivalent 1.16.5 integrations are pending.

## Installation and usage

Place only the JAR matching your Minecraft version in `mods/`. Use a separate test server/save for 1.16.5. Logical Xenofactions NBT compatibility does not convert Minecraft chunks, other mods' registries, item stacks or player data between Minecraft versions.

For full 1.7.10 configuration, commands and gameplay instructions, see [getting started](docs/1.7.10/getting-started.md), [configuration](docs/1.7.10/configuration.md), [commands](docs/1.7.10/commands.md), [admin editor](docs/1.7.10/ADMIN_EDITOR.md), and [permissions/automation](docs/1.7.10/gameplay-permissions-and-automation.md). Retained config keys, registry IDs, and logical save formats remain supported. Client and server must use the same build.

The native 1.16.5 target offers:

- `/c`, `/c help`, `/c list`, `/c info [faction]`, `/c comrades`: faction inspection/help and your member roster. `/clowder` is an alias.
- `/c create <name>`, `/c apply <faction>`, `/c leave`: create or apply to a faction, or leave a faction you do not lead. There is no invitation/direct-join command in the reference workflow. Faction names normalize spaces to underscores.
- Officers and leaders: `/c applicants`, `/c accept <player>`, `/c deny <player>`, `/c kick <player>`, `/c rename <name>`, `/c motd <text>`. Applicants must be online for acceptance. The leader cannot be kicked.
- Leaders: `/c promote <player>`, `/c demote <player>`, `/c owner <member>`, `/c disband <your faction name>`. Ownership transfer makes the old leader an Officer. Disband blocks new faction creation for seven days for the leader and three days for other members; applying/joining remains possible.
- Cities: `/c claim <city name>` supplies a named City Center. Place it on a solid, sky-accessible 5×5 foundation at Y 45–200. The first city is free; later cities cost prestige and need upkeep capacity. `/c city upgrade` requires Officer+ authority and a repeated confirmation. `/c nameclaim <name>` renames your current city. Cities generate Xenofactions territory with the original coordinate rules, rather than vanilla chunk claims.
- Capital and travel: the first city becomes the capital. `/c capital` inspects it; the Leader uses `/c capital set <owned city>` with the default seven-day cooldown. Officer+ `/c sethome` must be inside the capital. `/c home`, `/c allywarp <faction>` and `/c warp <name>` wait ten seconds and cancel horizontal movement. `/c setallywarp` sets your faction's rally point. Home/rally setting shares a ten-minute faction cooldown; unsafe destinations are refused.
- Relocation: the owning Leader breaks the City Center to open its move screen. Its token moves the existing city after validation; the original city remains until placement succeeds. `/c city recovermove` replaces a lost token and `/c city cancelmove` cancels. Homes that remain inside valid rebuilt capital territory retain their exact coordinates.
- Warp Tents: `/c setwarp <name>` registers a nearby operational tent for 125 prestige; `/c warps`, `/c delwarp <name>` and `/c warp <name>` manage travel. Tents need a Foundation/Asphalt/hopper floor and unobstructed overhead space, and deduct 75 hourly generation while operational. Officer Chests enforce Officer+ access in owned territory.
- Relationships: `/c alliance`, `/c befriend <faction>`, `/c acceptfriend <offering player>` and `/c unfriend <faction>` manage alliances. `/c enemy <faction>` marks a separate one-way enemy relation; `/c unenemy <faction>` starts its 72-hour removal. `/c permissions [ally|neutral] <build|destroy|container|interact|switch> <true|false>` configures visitor permissions. Explicit enemies and active wars use raid rules instead.
- Wars: declarations start disabled each session. After `/xc warenable`, Leaders use `/c declarewar <faction>` and repeat the cost confirmation. `/c listwars`, `/c peace <faction> [city]`, `/c acceptpeace <faction>`, `/c ceasefire`, `/c acceptceasefire`, `/c surrender`, `/c acceptsurrender` and `/c defendally` retain distinct treaty workflows. Surrender can transfer cities and impose timed generation tribute. Conquest Flags capture raidable border territory or its city center.
- Economy and progression: `/c balance` shows prestige, generation, upkeep and war costs; `/c deposit <amount>` consumes Prestige items. Withdrawal is removed; existing hidden Prestige tokens may still be deposited. Hourly accounting applies tribute, escalating war upkeep and bankruptcy penalties. Leader `/c gracebuild` requires a valid capital home and grants the configured one-time build grace. `/c merge <faction>` requests absorption; the receiving Leader accepts with `/c acceptmerge <faction>`.
- Chat and appearance: `/cc [public|faction|alliance|mute|unmute]` switches chat or filters public chat. Public messages show the faction prestige title and Leader/Officer/Citizen role. `/c color <hex>`, `/c flag <pattern>` and `/c listflags` select built-in appearance. Territory transitions display faction/city or zone flag popups with sound; Right Alt toggles nearby border particles. Registered player content appears in the Xenofactions Creative tab; the authorized editor supplies its hidden selection wand. `/xignore <player>` filters public/direct messages; level-2 `/xmute <player> <seconds|perm> [reason]` and `/xunmute <player>` persist moderation.
- Utilities: `/xmap` supplies the right-click claim-map item, `/xmulti` supplies a free multitool, and `/xflags` supplies Conquest Flags while configured and wars enabled. `/c stonedrops [page]` lists custom stone rewards; level-4 `/stonedrop <chance> [minY maxY]`, `list` and `remove <index>` manage held-item definitions.
- Level-3 operators: `/xc clearcreationcooldown <player-or-uuid>`, `/xc newplayerprotection`, `/xc resetnewplayerprotection`, `/xc endnewplayerprotection`. Enable `protection.enableNewPlayerProtection` in the native TOML before using the runtime toggle. The toggle starts off each server session. PvP grace prevents protected players from attacking or being attacked by other players. Existing expired records never receive fresh timers just because they reconnect.
- `/xc foundation`, `/xc cities`: level-3 administration/inspection.
- Level-3 faction administration: `/xc forcejoin <faction>`, `/xc forcekick <player>`, `/xc forcerename <name>`, `/xc hijack`, `/xc forcedisband <faction>`, `/xc deletedata`, `/xc addprestige <faction> <amount>` and war/build-grace controls. Forced disband does not impose ordinary disband penalties. `/xc setclaim <safe|war|wild> <s|c> <radius>` assigns zones; `/xc editor select <safezone|warzone|wilderness>`, `point <a|b>`, `commit` and `cancel` provide rectangular wand selection. Earth exemptions remain unsupported. `/c help [page]` and `/xc help [page]` list the core commands.
- `/tdm map list`, `/tdm map create <name>`: level-4 map metadata management. New maps start with voting disabled.
- `/tdm editor gui [map]`: level-4 map-bounds editor. F10 opens/closes the editor through this command.
- Choose **Use wand**, left-click a block for A and right-click a block for B, then reopen the editor to **Save bounds** or **Cancel selection**. Air clicks are ignored. Possession of the wand alone never authorizes selection. This initial editor has no kit editing, zoning, bomb-site editing, map-border enforcement or overlay renderer.

The last selected map persists in the existing `PlayerPersisted/xfAdminEditorContext` structure. Selection drafts and GUI tokens expire after five idle minutes and clear on death, logout, dimension change, permission loss, map change and server unload. Closing the screen to use the wand retains the authorized draft.

1.16.5 uses `config/xenofactions-foundation.toml` for identity, initial faction balances, dimension mappings and starter protection, and `config/xenofactions-gameplay.toml` for cities, claims, relocation, economy, war, build grace and stone rewards. `foundation.playerIdentityMode` defaults to `AUTO`: names authorize faction membership in development/offline servers and UUIDs authorize it in authenticated production. Use `UUID` explicitly when stable UUID authority is required. Imported name-only membership remains unresolved and never grants authority; canonical `identityMembers` records are required. `foundation.legacyDimensions` maps legacy integer IDs to native keys; defaults are `0=minecraft:overworld`, `-1=minecraft:the_nether`, `1=minecraft:the_end`. Add explicit mappings before editing or creating factions in custom dimensions; ambiguous mappings are rejected. Existing `hfr.cfg` is not consumed by 1.16.5. Starting prestige defaults to 250 and base hourly generation to 25. Starter protection defaults disabled, with four-hour PvP grace. The legacy 24-hour `keepInvUntil` timestamp is preserved, but does not retain inventory: the reference has no enforcement path for it.

The native stores can read copied `data/hfr_clowder.dat` and `data/xenofactions_tdm.dat` logical records, world-root player-protection/creation-cooldown JSON and `mutelist.json`/`ignorelist.json`, plus `config/stonedrops.json`. Faction/map edits retain unhandled fields and create a `.migration-1.16.5.bak` beside an existing file before its first edit. JSON writes retain supported records' unknown fields and back up existing files; malformed data is preserved and refuses mutation. Stone drops require native registry names for items whose old metadata variants became separate items. Logical faction migration does not materialize city/tent/conquest blocks from old Minecraft chunks. See [the migration architecture and persistence inventory](docs/MIGRATION.md) before transferring data.

## Building

Use the existing wrappers; do not install or upgrade Gradle. Install full JDKs 8, 17, 21 and 25 and expose `JAVA8_HOME`, `JAVA17_HOME`, `JAVA21_HOME`, `JAVA25_HOME`. The wrapper scripts can also discover standard JDK installations. Aggregate tasks need Python 3.9+ on PATH or an explicit `PYTHON` executable path.

```powershell
.\gradlew.bat buildAll
# Build one target from the root:
.\scripts\build-version.ps1 1.7.10
.\scripts\build-version.ps1 1.16.5
```

On Linux/macOS, use `sh ./gradlew buildAll` or the target's `sh ./gradlew build`. Both targets produce Java 8 bytecode. `buildAll` builds and verifies exactly these two targets under one production build number. `version.properties` holds the semantic version and **next** build number; successful coordinated production builds advance it once. Development runs and `testAll` do not consume numbers. Test tasks may report `NO-SOURCE`; that is not gameplay coverage.

Production outputs are `versions/<minecraft>/build/libs/hfr-<semantic>.<build>+mc<minecraft>-forge.jar`. Do not distribute a `-dev` JAR. `verification.json` configures archive identity, metadata/dependencies, resources, class versions and remapped symbol checks; `scripts/build.py verify <minecraft> --full-version <version>` rechecks a chosen artifact.

## Contributor layout and IDE

- `common/`: only the unchanged platform-neutral `FactionRole`, `FactionMemberRecord` and `BlockAreaEdges` sources. Compiled into each target; no separate library installation.
- `versions/1.7.10/`: maintained faction/gameplay target with native legacy Minecraft/Forge APIs and resources.
- `versions/1.16.5/`: native Forge lifecycle, registries, commands, persistence, networking, server sessions, container and client screen.
- `build-logic/`, `scripts/`, `verification.json`, `version.properties`, `template.properties`: retained ExampleMod build/release conventions. Template revision is separate from public mod version.
- `.run/`: portable IntelliJ shortcuts for both builds/clients, aggregate checks and optional old-build pruning.
- `docs/1.7.10/`: existing gameplay documentation; `docs/MIGRATION.md`: contributor support matrix, save boundaries and migration status.
- `tools/1.7.10/`: existing Earth source/pack authoring tools.

Import the root in IntelliJ for common code and aggregate tasks. Import `versions/1.7.10` using JDK 25 for native legacy development. Import `versions/1.16.5` using JDK 21 and run `genIntellijRuns` for native Forge launch configurations. Native `runClient` and `runServer` tasks are available in each target; root shortcuts are `runClient1_7_10`, `runServer1_7_10`, `runClient1_16_5`, `runServer1_16_5`. Native launches require the appropriate Java 8 game runtime.

Preserve version-native world/thread ownership, save compatibility and optional-mod absence handling. Port the actual 1.7.10 behavior before extracting more common logic; do not introduce a universal Minecraft facade or preprocess/merge native source trees. Other template targets have been removed for this first pass.

Removed machines, weapons, advanced materials and stock-market content are deliberately unsupported. Do not force-load an old 1.7.10 world by ignoring missing registry mappings: there is no automatic block/entity conversion. Keep the original world and use a separate staging copy. The standalone legacy repository remains the historical full-content reference. See [maintained content and save boundaries](docs/MIGRATION.md#maintained-content-cleanup) for the removal inventory.
