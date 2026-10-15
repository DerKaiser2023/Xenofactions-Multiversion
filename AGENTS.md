# Required Xenofactions implementation workflow

These rules apply to XF feature implementation and supplement other applicable repository instructions. Preserve unrelated behavior and instructions; inspect the supported target version before reusing an implementation from another version.

## Inspect before implementing

Read applicable repository instructions and trace the authoritative command handlers, runtime state, persistence/loading, client synchronization, affected displays, command menu, help, tab completion, and language resources before changing a feature. Use the 1.7.10 references below to understand the existing pattern; verify equivalent paths for other targets rather than assuming their APIs match.

## Command-based administration

- Implement routine feature administration through commands first.
- Provide commands to add, inspect/list/show, edit, remove, and enable or disable entries where applicable.
- Apply successful changes immediately without server restarts or manual reloads, and persist them across server restarts through the feature's save and startup loading paths.
- Treat configuration files as secondary options. Do not make file editing the only way to manage runtime content.
- Identify concrete startup constraints in the actual implementation before requiring a restart for any setting; explain the constraint when a restart is necessary.
- Follow existing command namespaces, aliases, and permission conventions. Keep authoritative validation and authorization in server command handlers.

## StoneDrops workflow for item-based administration

The **StoneDrops pattern** means: capture a copy of the administrator's held item, validate the proposed change, commit it to persistent and active server state, synchronize affected clients, and refresh affected GUIs and displays. Successful changes take effect immediately and survive restart; rejected or unsaved changes retain the previous state.

- Let administrators capture held items where applicable. Require an in-game player and a valid nonempty held stack for those actions, while retaining console support for actions that do not need a held item where applicable.
- Copy the held stack without consuming, replacing, or changing the original. Preserve item identity, count, metadata, and NBT in runtime state, persistence, loading, and relevant client previews. Do not silently strip tags or normalize the captured count; any intentional count override must be explicit in the command syntax.
- Validate the complete proposed change before applying it, including arguments, entry references, item data, feature constraints, and relevant persistence/network limits.
- Preserve the previous active and saved state when validation or saving fails. Use a save operation that reports failure and a commit/rollback strategy that prevents publishing an unsaved change or destroying the previous saved data. Report success only after persistence succeeds.
- Save successful changes, apply them immediately on the authoritative server, synchronize affected clients, and refresh affected GUIs and displays. Include login or other lifecycle synchronization where clients need an initial or refreshed snapshot.
- Reuse suitable existing capture, persistence, and synchronization code without copying unrelated limitations or assuming the reference implementation meets every rule above.

### Inspected 1.7.10 references

- `versions/1.7.10/src/main/java/com/hfr/command/CommandStoneDrop.java`: `/stonedrop <rarity> [minY maxY]` captures `player.getHeldItem().copy()`; `list` inspects entries and `remove <index>` removes one. Mutating actions update runtime lists, call `MainRegistry.saveCustomDrops()`, and broadcast through `StoneDropSnapshotSync.sendToAll()`.
- `versions/1.7.10/src/main/java/com/hfr/main/MainRegistry.java`: `saveCustomDrops()` serializes item registry identity, count, metadata, NBT, chance, and Y bounds to `config/stonedrops.json`; `loadCustomDrops()` restores them during pre-initialization. Post-initialization also handles optional HBM default recovery and snapshot synchronization. Inspect these loading/compatibility paths before changing persistence semantics.
- `versions/1.7.10/src/main/java/com/hfr/stonedrops/StoneDropSnapshotSync.java`: broadcasts runtime display snapshots and sends them on player login and dimension changes. `StoneDropSnapshotPacket` delivers them to `StoneDropDisplaySnapshot.replaceClientSnapshot()` for client displays.

The reference is an interaction pattern, not a complete transactional implementation: `saveCustomDrops()` currently returns `void` and catches/logs failures, while the command mutates runtime state before saving and then broadcasts. It does not roll back failed saves. New or changed administration must satisfy the failure-preservation rule above rather than reproduce that limitation. Likewise, its current action set and generic menu entry do not exempt other features from complete administration and menu integration.

## Command menu integration

- Update the GUI command menu whenever commands or subcommands are added, changed, or removed. Missing menu integration makes the implementation incomplete.
- Update `versions/1.7.10/src/main/java/com/hfr/xfgui/XFCommandCatalog.java` and related menu code where required; inspect the equivalent implementation for other supported targets.
- Include usable entries for distinct command actions. Do not rely on a generic free-text entry to hide newly added administration actions.
- Match the handlers' actual syntax, aliases, required and optional arguments, argument ordering, permissions, and feature availability. Update `XFCommandDefinition.java` and `XFCommandArgument.java` where necessary to represent and build valid commands.
- Add translated names, descriptions, argument labels, and usage text in the target's language resources, updating menu translation support where needed. For 1.7.10, inspect `versions/1.7.10/src/main/resources/assets/hfr/lang/en_US.lang`, including the `xf.command.*`, `xf.arg.*`, and command help/usage keys. Keep literal command tokens unchanged by translation.
- Preserve existing confirmation behavior for destructive actions, including the definition's `dangerous` flag, GUI confirmation, and any confirmation required by the server handler.
- Keep authorization in server command handlers. Menu filtering and client validation are presentation aids and must never replace server permission or validity checks.
- Make menu actions use the same command execution path as typed commands. In 1.7.10, `XFCommandDefinition.buildCommand()` assembles the command and `versions/1.7.10/src/main/java/com/hfr/inventory/gui/GUIXenofactionsMenu.java` sends it through `mc.thePlayer.sendChatMessage(cmd)`; preserve server dispatch rather than introducing a menu-only mutation path.
- Update command help and tab completion alongside command changes.
- Verify handlers, catalog entries, argument definitions, translations, confirmation, and execution together before declaring command work complete. The inspected catalog has no daily reward or chat reward entries despite their server commands and language strings; do not infer menu coverage from command registration or translations alone.
