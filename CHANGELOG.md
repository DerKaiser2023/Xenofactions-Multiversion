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
