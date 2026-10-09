# Xenofactions

Xenofactions is a Forge factions-and-warfare mod for cities, territory, diplomacy, prestige, server shops, map editing and competitive gamemodes. The mod ID remains `hfr`; legacy registry names and save identifiers are retained.

## Supported targets

| Minecraft | Loader | Available behavior | Build JVM / compiler | Game runtime |
| --- | --- | --- | --- | --- |
| 1.7.10 | Forge 10.13.4.1614 or newer for 1.7.10 | Current Xenofactions implementation: factions/cities/capitals, claims/permissions, war/economy, XShop/terminals, maps/kits/TDM/FFA/Search and Destroy, editor, builders, Earth maps, wall art and legacy content | JDK 25 / JDK 17 with Jabel; pinned Gradle 9.7.1 | Java 8 |
| 1.16.5 | Forge 36.2.34 or newer for 1.16.5 | Migration foundation: faction/city inspection, native save bindings, map metadata creation, server-owned map-bounds editor and selection wand | JDK 21 / JDK 8; pinned Gradle 8.13 | Java 8 |

**1.16.5 does not yet implement faction mutations, claim protection, city gameplay, prestige, diplomacy, matches, kits, shops, builders, Earth terrain, wall art or map integrations.** It is a foundation for migration and testing, not a replacement for a production 1.7.10 factions server. Imported saved match flags never start a match in this target.

Install the matching production JAR on both client and server. Core builds require no installed optional mods. The 1.7.10 JourneyMap, Dynmap, Guide-API, NEI, HBM and MCHeli integrations remain optional; equivalent 1.16.5 integrations are pending.

## Installation and usage

Place only the JAR matching your Minecraft version in `mods/`. Use a separate test server/save for 1.16.5. Logical Xenofactions NBT compatibility does not convert Minecraft chunks, other mods' registries, item stacks or player data between Minecraft versions.

For full 1.7.10 configuration, commands and gameplay instructions, see [getting started](docs/1.7.10/getting-started.md), [configuration](docs/1.7.10/configuration.md), [commands](docs/1.7.10/commands.md), [admin editor](docs/1.7.10/ADMIN_EDITOR.md), and [permissions/automation](docs/1.7.10/gameplay-permissions-and-automation.md). Its existing `config/hfr.cfg`, registry IDs, packet discriminators, NBT and JSON formats remain intact.

The native 1.16.5 foundation offers:

- `/c list`, `/c info`, `/c info <faction>`: read-only faction inspection. It does not create factions or grant permissions from imported names.
- `/xc foundation`, `/xc cities`: level-3 administration/inspection.
- `/tdm map list`, `/tdm map create <name>`: level-4 map metadata management. New maps start with voting disabled.
- `/tdm editor gui [map]`: level-4 map-bounds editor. F10 opens/closes the editor through this command.
- Choose **Use wand**, left-click a block for A and right-click a block for B, then reopen the editor to **Save bounds** or **Cancel selection**. Air clicks are ignored. Possession of the wand alone never authorizes selection. This initial editor has no kit editing, zoning, bomb-site editing, map-border enforcement or overlay renderer.

The last selected map persists in the existing `PlayerPersisted/xfAdminEditorContext` structure. Selection drafts and GUI tokens expire after five idle minutes and clear on death, logout, dimension change, permission loss, map change and server unload. Closing the screen to use the wand retains the authorized draft.

1.16.5 generates `config/xenofactions-foundation.toml`. `foundation.playerIdentityMode` defaults to `AUTO`, following the legacy development/offline NAME vs authenticated-production UUID choice for **inspection only**. `foundation.legacyDimensions` maps legacy integer IDs to native dimension keys; defaults are `0=minecraft:overworld`, `-1=minecraft:the_nether`, `1=minecraft:the_end`. Add explicit mappings before editing in custom dimensions; ambiguous mappings are rejected. Existing `hfr.cfg` is not consumed by 1.16.5, and unported settings are not advertised as functional.

The native stores can read copied `data/hfr_clowder.dat` and `data/xenofactions_tdm.dat` logical records. Faction data is read-only. Map edits retain unhandled fields and create `xenofactions_tdm.dat.migration-1.16.5.bak` before the first edit of an existing file. Invalid existing data blocks loading/editing instead of being replaced with an empty catalog. See [the migration architecture and persistence inventory](docs/MIGRATION.md) before transferring data.

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
