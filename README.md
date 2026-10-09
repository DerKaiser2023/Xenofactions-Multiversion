# Xenofactions

Xenofactions is a Forge factions-and-warfare mod for cities, territory, diplomacy, prestige, server shops, map editing and competitive gamemodes. The mod ID remains `hfr`; legacy registry names and save identifiers are retained.

## Supported targets

| Minecraft | Loader | Available behavior | Build JVM / compiler | Game runtime |
| --- | --- | --- | --- | --- |
| 1.7.10 | Forge 10.13.4.1614 or newer for 1.7.10 | Current Xenofactions implementation: factions/cities/capitals, claims/permissions, war/economy, XShop/terminals, maps/kits/TDM/FFA/Search and Destroy, editor, builders, Earth maps, wall art and legacy content | JDK 25 / JDK 17 with Jabel; pinned Gradle 9.7.1 | Java 8 |
| 1.16.5 | Forge 36.2.34 or newer for 1.16.5 | Native faction creation/membership/applications/ranks/disband, creation cooldowns and starter PvP grace; city inspection, map metadata and server-owned bounds editor/wand | JDK 21 / JDK 8; pinned Gradle 8.13 | Java 8 |

**1.16.5 does not yet implement claim protection, city gameplay, passive prestige/economy, diplomacy, matches, kits, shops, builders, Earth terrain, wall art or map integrations.** Native membership gameplay is available for testing, but this target is not a replacement for a production 1.7.10 factions server. Imported saved match flags never start a match.

Install the matching production JAR on both client and server. Core builds require no installed optional mods. The 1.7.10 JourneyMap, Dynmap, Guide-API, NEI, HBM and MCHeli integrations remain optional; equivalent 1.16.5 integrations are pending.

## Installation and usage

Place only the JAR matching your Minecraft version in `mods/`. Use a separate test server/save for 1.16.5. Logical Xenofactions NBT compatibility does not convert Minecraft chunks, other mods' registries, item stacks or player data between Minecraft versions.

For full 1.7.10 configuration, commands and gameplay instructions, see [getting started](docs/1.7.10/getting-started.md), [configuration](docs/1.7.10/configuration.md), [commands](docs/1.7.10/commands.md), [admin editor](docs/1.7.10/ADMIN_EDITOR.md), and [permissions/automation](docs/1.7.10/gameplay-permissions-and-automation.md). Its existing `config/hfr.cfg`, registry IDs, packet discriminators, NBT and JSON formats remain intact.

The native 1.16.5 target offers:

- `/c`, `/c help`, `/c list`, `/c info [faction]`, `/c comrades`: faction inspection/help and your member roster. `/clowder` is an alias.
- `/c create <name>`, `/c apply <faction>`, `/c leave`: create or apply to a faction, or leave a faction you do not lead. There is no invitation/direct-join command in the reference workflow. Faction names normalize spaces to underscores.
- Officers and leaders: `/c applicants`, `/c accept <player>`, `/c deny <player>`, `/c kick <player>`, `/c rename <name>`, `/c motd <text>`. Applicants must be online for acceptance. The leader cannot be kicked.
- Leaders: `/c promote <player>`, `/c demote <player>`, `/c owner <member>`, `/c disband <your faction name>`. Ownership transfer makes the old leader an Officer. Disband blocks new faction creation for seven days for the leader and three days for other members; applying/joining remains possible.
- Level-3 operators: `/xc clearcreationcooldown <player-or-uuid>`, `/xc newplayerprotection`, `/xc resetnewplayerprotection`, `/xc endnewplayerprotection`. Enable `protection.enableNewPlayerProtection` in the native TOML before using the runtime toggle. The toggle starts off each server session. PvP grace prevents protected players from attacking or being attacked by other players. Existing expired records never receive fresh timers just because they reconnect.
- `/xc foundation`, `/xc cities`: level-3 administration/inspection.
- `/tdm map list`, `/tdm map create <name>`: level-4 map metadata management. New maps start with voting disabled.
- `/tdm editor gui [map]`: level-4 map-bounds editor. F10 opens/closes the editor through this command.
- Choose **Use wand**, left-click a block for A and right-click a block for B, then reopen the editor to **Save bounds** or **Cancel selection**. Air clicks are ignored. Possession of the wand alone never authorizes selection. This initial editor has no kit editing, zoning, bomb-site editing, map-border enforcement or overlay renderer.

The last selected map persists in the existing `PlayerPersisted/xfAdminEditorContext` structure. Selection drafts and GUI tokens expire after five idle minutes and clear on death, logout, dimension change, permission loss, map change and server unload. Closing the screen to use the wand retains the authorized draft.

1.16.5 uses `config/xenofactions-foundation.toml`. `foundation.playerIdentityMode` defaults to `AUTO`: names authorize faction membership in development/offline servers and UUIDs authorize it in authenticated production. Use `UUID` explicitly when stable UUID authority is required. Imported name-only membership remains unresolved and never grants authority; canonical `identityMembers` records are required. `foundation.legacyDimensions` maps legacy integer IDs to native keys; defaults are `0=minecraft:overworld`, `-1=minecraft:the_nether`, `1=minecraft:the_end`. Add explicit mappings before editing or creating factions in custom dimensions; ambiguous mappings are rejected. Existing `hfr.cfg` is not consumed by 1.16.5. `factions.startingPrestige` (250) and `basePrestigeGeneration` (25) initialize stored values only; passive economy is pending. Starter protection defaults disabled, with four-hour PvP grace. The legacy 24-hour `keepInvUntil` timestamp is preserved, but does not retain inventory: the reference has no enforcement path for it.

The native stores can read copied `data/hfr_clowder.dat` and `data/xenofactions_tdm.dat` logical records, plus world-root `clowder_player_protection.json` and `clowder_faction_creation_cooldowns.json`. Faction/map edits retain unhandled fields and create a `.migration-1.16.5.bak` beside an existing file before its first edit. Timer writes also retain unknown entry fields and back up existing files; malformed data is preserved and refuses loading/mutation. See [the migration architecture and persistence inventory](docs/MIGRATION.md) before transferring data.

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
- `versions/1.7.10/`: complete behavioral reference with native legacy Minecraft/Forge APIs and resources.
- `versions/1.16.5/`: native Forge lifecycle, registries, commands, persistence, networking, server sessions, container and client screen.
- `build-logic/`, `scripts/`, `verification.json`, `version.properties`, `template.properties`: retained ExampleMod build/release conventions. Template revision is separate from public mod version.
- `.run/`: portable IntelliJ shortcuts for both builds/clients, aggregate checks and optional old-build pruning.
- `docs/1.7.10/`: existing gameplay documentation; `docs/MIGRATION.md`: contributor support matrix, save boundaries and migration status.
- `tools/1.7.10/`: existing Earth source/pack authoring tools.

Import the root in IntelliJ for common code and aggregate tasks. Import `versions/1.7.10` using JDK 25 for native legacy development. Import `versions/1.16.5` using JDK 21 and run `genIntellijRuns` for native Forge launch configurations. Native `runClient` and `runServer` tasks are available in each target; root shortcuts are `runClient1_7_10`, `runServer1_7_10`, `runClient1_16_5`, `runServer1_16_5`. Native launches require the appropriate Java 8 game runtime.

Preserve version-native world/thread ownership, save compatibility and optional-mod absence handling. Port the actual 1.7.10 behavior before extracting more common logic; do not introduce a universal Minecraft facade or preprocess/merge native source trees. Other template targets have been removed for this first pass.
