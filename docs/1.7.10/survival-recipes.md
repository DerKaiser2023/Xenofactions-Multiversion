# Survival Recipes and Content Audit

The optional recipes below are ordinary Forge crafting recipes, so standard crafting-table viewers such as NEI discover them automatically. They are registered only when
`XENOFACTIONS_01_MODULES.enableSurvivalRecipes=true` (the default).

## Survival-worthy content

The audit followed block/item registration into tile entities, GUIs, faction commands, claims, prestige accounting, war handling, and machine processing.

* **Core faction loop:** City Center (`clowder_flag`), Conquest Flag (`clowder_conquerer`), Officer Chest, and Warp Tent. City Centers found named cities and claims; `/c claim <name>` supplies the named placement item. Conquest Flags are established war infrastructure. The chest and tents enforce faction roles or provide claimed-land services.
* **Developed production/prestige support:** Production Line (`machine_factory`), University, Temple, and Statue. These have operational multiblocks/tile entities, player-facing lore or NEI processing information, and active outputs or prestige effects.
* **Already survival-obtainable and therefore not duplicated:** Grain Mill, wooden/scaffold/steel/mechanical/electronic components, and the Wearable Claim Banner retain their pre-existing recipes. These existing recipes are intentionally not controlled by the new compatibility toggle.

## Recipes added

Each diagram is a 3x3 crafting grid; a space is empty.

| Output | Layout | Key | Balance rationale |
| --- | --- | --- | --- |
| Foundation (4) | `SIS / III / SIS` | S stone bricks, I iron ingots | Large multiblock footprints need an early industrial building material; four blocks per craft controls the iron cost. |
| Conquest Flag | ` W  /  R  /  R ` | W wool, R fence | Replaceable war infrastructure. |
| Faction Banner | `SSS / WWW / WWW` | S stick, W white wool | Wall-mounted faction decoration. |
| Large Faction Flag | ` WW / IWW / IB ` | W white wool, I iron ingot, B iron block | Tall decorative pole with double-size cloth. |
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

Faction Banner (`hfr:faction_banner`) and Large Faction Flag (`hfr:large_faction_flag`)
are also available in the Xenofactions Creative tab. Their recipes follow
`enableSurvivalRecipes`. Both require faction membership to place, including in
creative mode, and bind to the placing player's faction UUID. Normal build/break
protection applies. Right-click a solid wall's side for the banner. Place the large
flag on a solid upper face with an eleven-block clear pole column and loaded,
unobstructed space for the cloth at its top. It faces according to player yaw;
harvesting any pole segment with a suitable tool removes the whole structure and
yields one item in survival. Cloth and banners have no collision; the large pole and footing do.
The banner cloth is 0.625 by 0.9375 blocks; the large cloth is 2.25 by 1.5 blocks,
twice the ordinary flag's width and height, on a 10.25-block pole. Other blocks
can subsequently be built into the non-colliding cloth's space.

Placed decorations retain their faction across chunk unloads, restarts and faction
renames; interaction cannot rebind them. Breaking and placing an item binds it anew.
They show the bound faction's built-in pattern/color or custom flag image, refreshing
within one second after design changes. Deleted/missing factions display plain gray
cloth and retain the original UUID, so a new faction with the same name cannot take
them over. Decorations share the existing hash-based client image cache, request
missing PNGs through the existing flag protocol, and release textures through its
replacement/disconnect lifecycle. They never claim territory, sample terrain,
load neighboring chunks for clearance, contribute prestige or open the flag editor.

The separate **Claim Territory Flag** (`hfr:tile.clowder_flag_big`) remains unchanged
in identity and territory behavior. It is a legacy territory provider with a
hatchet-defined claim set, nearby-faction capture, conquest transfer and a reward
inventory. It is not the decorative Large Faction Flag and has no new recipe.
Its existing model asset is reused without edits; the claim renderer shares only
the cloth texture/tint/overlay drawing passes with decorations. Actual legacy
capture, rewards and persistence behavior still require runtime verification.

The Production Line creates only level-one cogs. University output remains original research; higher science/cog materials and all production paths for them are removed. The steel ingot and its smelting recipe are removed. Historically named steel components use iron ingots and scaffold components, keeping retained infrastructure obtainable without a metallurgy chain.

Admin XShop/debug/selection tools and generated city-cap internals remain non-craftable. Fishing Net (`WWW / RAR / BBB`: W ladder, R iron bars, A trapped chest, B fence) and Grain Mill remain supported. The survival toggle controls `XenoRecipes`, including the fortifications above, including Fishing Net, while existing component/rice/mill/banner and furnace recipes remain independent. The maintained `/c claim` continues to supply named city tokens.

The Xenofactions achievement page includes **Grain Mill** and **Fishing Net** for obtaining their respective blocks. Both retain their independent native statistics, IDs, icons and page positions. Crafting or successfully picking up an item awards only its receiving player. The page also includes mining, cumulative player kills, successful city/building placement and distance-based **Map Inspector**, with progress and one earned wheel spin per original achievement. **Guns don't kill people, I do** requires 1,000 cumulative player kills, counts existing recorded kills and grants no reward. See [achievement tracking and earned spins](configuration.md#achievements-and-earned-wheel-spins). Neither block is an output of the retained machines.

Excluded weapons, oil/RF/nuclear chains, vehicle parts, farming contracts, obsolete buildings, masks, stock/tax items and advanced production materials have no recipes or machine outputs. See [the maintained inventory](../MIGRATION.md#maintained-content-cleanup) for exact removals and save boundaries.

## Player Trader

The Player Trader access point for the server-wide player market crafts as `IRI / ECE / IRI`: four iron ingots, two redstone, two emeralds and one trapped chest. It follows `enableSurvivalRecipes`. This is distinct from the faction market recipe, which uses a normal chest. No removed material or machine is required. All Player Traders share the same offers; breaking an access point does not remove offers or release their reserved items.
