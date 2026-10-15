# Survival Recipes and Content Audit

The optional recipes below are ordinary Forge crafting recipes, so standard crafting-table viewers such as NEI discover them automatically. They are registered only when
`XENOFACTIONS_01_MODULES.enableSurvivalRecipes=true` (the default).

## Survival-worthy content

The audit followed block/item registration into tile entities, GUIs, faction commands, claims, prestige accounting, war handling, and machine processing.

* **Core faction loop:** City Center (`clowder_flag`), Conquest Flag (`clowder_conquerer`), Officer Chest, and Warp Tent. City Centers found named cities and claims; `/c claim <name>` supplies the named placement item. Conquest Flags are established war infrastructure. The chest and tents enforce faction roles or provide claimed-land services.
* **Developed production/prestige support:** Production Line (`machine_factory`), University, Temple, and Statue. These have operational multiblocks/tile entities, player-facing lore or NEI processing information, and active outputs or prestige effects.
* **Already survival-obtainable and therefore not duplicated:** Grain Mill, wooden/scaffold/steel/mechanical/electronic components, and faction banner retain their pre-existing recipes. These existing recipes are intentionally not controlled by the new compatibility toggle.

## Recipes added

Each diagram is a 3x3 crafting grid; a space is empty.

| Output | Layout | Key | Balance rationale |
| --- | --- | --- | --- |
| Foundation (4) | `SIS / III / SIS` | S stone bricks, I iron ingots | Large multiblock footprints need an early industrial building material; four blocks per craft controls the iron cost. |
| Conquest Flag | ` W  /  R  /  R ` | W wool, R fence | Replaceable war infrastructure. |
| Officer Chest | `I I / ICI / IRI` | I iron ingot, C chest, R redstone | A basic role-gated utility priced near other protected storage. |
| Global Market Terminal | `IRI / ECE / IRI` | I iron ingot, R redstone, E emerald, C chest | One active terminal per faction, placed by Officers or leaders inside the designated capital. All faction members can use it. |
| Warp Tent | `WWW / PEP / SRS` | W wool, P diamond block, E beacon, S stick, R redstone | Late-game capital travel infrastructure. |
| Production Line | `SCS / MEM / SPS` | S steel component, C crafting table, M mechanical component, E electronic component, P piston | Developed automated production requires the retained iron-based component recipes. |
| University | `BEB / SDS / BKB` | B bookshelf, E beacon, S redstone block, D diamond block, K crafting table | Original science output is gated by books and developed infrastructure. |
| Temple | `QGQ / SBS / ODO` | Q quartz block, G gold block, S stone bricks, B bookshelf, O beacon, D diamond block | The strongest configured prestige generator uses late-Nether materials and valuable blocks. |
| Statue | ` Q  / QEQ / SGS` | Q quartz block, E emerald, S stone bricks, G gold block | A smaller passive prestige source is cheaper than major machines but remains an investment. |

All recipe leaves are obtainable vanilla materials or existing craftable Xenofactions components. The dependency graph is one-way (vanilla materials → components → infrastructure), with no recipe output required to make itself.

## Fortification recipes

`XenoRecipes` also registers these seven fortification recipes under the same `enableSurvivalRecipes` gate. No other building props gain recipes.

| Output | Layout | Ingredients |
| --- | --- | --- |
| Sandbags (`barricade`), 4 | ` S  / SWS /  S ` | Four sand blocks (normal or red sand), one wool block of any color. |
| HESCO Terrablock, 2 | `BSB / ISI / BSB` | Four iron bars, three sandbags, two OreDictionary `ingotIron` ingots. |
| Stone Wall, 4 | `S S / S S / S S` | Six OreDictionary `cobblestone` blocks; vertical columns avoid the vanilla cobblestone-wall recipe. |
| Brick Wall, 4 | `BBB / BBB` | Six vanilla brick blocks. |
| Great Wall, 4 | `SSS / SSS` | Six vanilla stone-brick blocks; the masonry variant sharing `PropWall` with Stone Wall and Brick Wall. |
| Palisade, 4 | `LLL / SSS / LLL` | Six OreDictionary `logWood` logs and three sticks. |
| Berlin Wall, 2 | `SBS / SBS / SBS` | Six stone blocks and three iron bars for the taller reinforced wall. |

Outputs use item metadata 0. Sandbag facing and wall/HESCO/Palisade/Berlin Wall multiblock core/dummy metadata are assigned during placement, not separate craftable variants. Rubber-based recipes are removed; sand and wool are the supported sandbag ingredients.

## Supported recipe boundaries

The Production Line creates only level-one cogs. University output remains original research; higher science/cog materials and all production paths for them are removed. The steel ingot and its smelting recipe are removed. Historically named steel components use iron ingots and scaffold components, keeping retained infrastructure obtainable without a metallurgy chain.

Admin XShop/debug/selection tools and generated city-cap internals remain non-craftable. Fishing Net (`WWW / RAR / BBB`: W ladder, R iron bars, A trapped chest, B fence) and Grain Mill remain supported. The survival toggle controls `XenoRecipes`, including the fortifications above, including Fishing Net, while existing component/rice/mill/banner and furnace recipes remain independent. The maintained `/c claim` continues to supply named city tokens.

The Xenofactions achievement page adds **Windmill** for obtaining a Grain Mill and **Fishing Net** for obtaining a Fishing Net. Both are independent achievements with no prerequisites and use their registered block items as icons. Crafting or successfully picking up the item awards only the receiving player through vanilla achievement statistics, persistence and synchronization, regardless of other achievement progress. Existing saved progress and page positions are preserved. Neither block is an output of the retained machines.

Excluded weapons, oil/RF/nuclear chains, vehicle parts, farming contracts, obsolete buildings, masks, stock/tax items and advanced production materials have no recipes or machine outputs. See [the maintained inventory](../MIGRATION.md#maintained-content-cleanup) for exact removals and save boundaries.

## Player Trader

The Player Trader access point for the server-wide player market crafts as `IRI / ECE / IRI`: four iron ingots, two redstone, two emeralds and one trapped chest. It follows `enableSurvivalRecipes`. This is distinct from the faction market recipe, which uses a normal chest. No removed material or machine is required. All Player Traders share the same offers; breaking an access point does not remove offers or release their reserved items.
