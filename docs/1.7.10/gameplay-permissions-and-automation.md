# Gameplay permissions and automation

## Retained production and automation

Foundation crafts four blocks from `SIS / III / SIS` (`S` stone bricks, `I` iron ingots), under `enableSurvivalRecipes`. Vanilla hoppers satisfy the same foundation predicate at their footprint position for retained structures, including Grain Mills, Production Lines, Universities, Warp Tents and Statues.

* **Grain Mill:** wheat inserts into the three core input slots through horizontal or top faces of the core and structure dummy blocks. Dummies forward to the core and never store or extract items; the three flour outputs extract only below the core. The existing wheat-to-flour recipe and processing time are unchanged.
* **Production Line:** no item input or power requirement. Level-one cogs extract downward; the jam/status slot cannot be drained by automation. Foundation and clearance checks still apply.
* **University and Temple:** retain their existing level-one science/scroll generation, structure checks and inventory behavior.

Blast Furnaces, Coal Mines, Foundries, generators and the Federal Reserve are removed. The custom steel ingot is removed; retained components use vanilla iron. No obsolete processing or fuel chain is required.

## Territory permissions

Each territory-owning faction stores two policies: `ally` and `neutral`. Categories are `BUILD` (place blocks), `DESTROY` (break blocks), `CONTAINER` (inventory tile entities), `INTERACT` (other right-clicks), and `SWITCH` (doors, trapdoors, gates, buttons, levers, and pressure plates).

Defaults are:

| Relationship | BUILD | DESTROY | CONTAINER | INTERACT | SWITCH |
| --- | --- | --- | --- | --- | --- |
| Ally | false | false | false | true | true |
| Neutral | false | false | false | false | false |

Use `/c permissions` (or `/c perms`) to view both policies. Officers and the leader may use `/c permissions <ally|neutral> <build|destroy|container|interact|switch> <true|false>`, for example `/c permissions ally switch true`. Ordinary members may view but not modify policy. Relationship, permission, and boolean arguments support tab completion.

Resolution order is owner member behavior, existing war/enemy handling, the territory owner's ally policy, then its neutral policy. Policies are relationship-wide rather than per faction, so diplomacy changes take effect immediately. City Center relocation/destruction, Officer Chest rank checks, flag-foundation protection, Fallen Nation restrictions, raid/mace rules, safezones, and warzones remain stricter and are evaluated by their existing paths.

Permission keys are stored with each faction in existing Clowder NBT. Missing keys retain constructor defaults, making pre-update saves migrate safely. Every mutation marks Clowder saved data dirty.

## Stocked player traders and rice

Player Traders are separate single-offer shops alongside XShop and faction market terminals. Place one to become its UUID owner. Right-click to buy, or as the owner/level-3 XShop administrator, configure sale/payment templates from hotbar slots 1–9 and choose quantities. Template selection does not consume items. Deposit matching sale stacks; take stock or collected payments using storage slots 1–9. Both stores contain nine slots. Customers can only buy complete offers; stale revisions, denied faction INTERACT permission, missing stock/payment and insufficient destination space reject the whole purchase. Item, metadata and NBT must match exactly. Owner, templates, quantities, offer revision, stock and payments survive restarts.

Right-click mature rice (stage 7) to receive exactly one rice and reset it to stage 0 without uprooting it. Immature rice and normal breaking retain their existing behavior. Faction interaction protection still applies.
