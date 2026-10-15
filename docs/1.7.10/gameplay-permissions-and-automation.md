# Gameplay permissions and automation

## Retained production and automation

Foundation crafts four blocks from `SIS / III / SIS` (`S` stone bricks, `I` iron ingots), under `enableSurvivalRecipes`. Vanilla hoppers satisfy the same foundation predicate at their footprint position for retained structures, including Grain Mills, Production Lines, Universities, Warp Tents and Statues.

* **Grain Mill:** wheat inserts into the three core input slots through horizontal or top faces of the core and structure dummy blocks. Dummies forward to the core and never store or extract items; the three flour outputs extract only below the core. The existing wheat-to-flour recipe and processing time are unchanged.
* **Production Line:** no item input or power requirement. Level-one cogs extract downward; the jam/status slot cannot be drained by automation. Foundation and clearance checks still apply.
* **University and Temple:** retain their existing level-one science/scroll generation, structure checks and inventory behavior.

Blast Furnaces, Coal Mines, Foundries, generators and the Federal Reserve are removed. The custom steel ingot is removed; retained components use vanilla iron. No obsolete processing or fuel chain is required.

## Territory permissions

Stage 8 claim nodes retain the existing territorial units and ownership. Their
versioned terrain state starts `UNCLASSIFIED` and is separate from faction rights.
A new node is rejected only if every surface column is open water at least three
consecutive water blocks deep. Still and flowing water count. Any exposed solid
land, solid platform above water, or shallower water column allows it immediately.
Submerged seabed, leaves, plants and floating items do not count as exposed land.
There is no land coverage, connected area or height-step requirement; biome names
have no effect, including in deep rivers and lakes. Unloaded or unsupported terrain
is reported separately from water rejection. The owning server reads bounded,
loaded surface data; background sampling, roads, datasets and map overlays are
independent of grants, and no deferred ownership intents remain.
Existing ownership, transfers, costs, radius/spacing, spawn exceptions, foundation
rules and protection permissions are preserved; classification never unclaims.
`/c city terrain` reports the immediate check. Officers/leaders may use
`/c city terrain retry` after terrain changes or loading unavailable terrain,
without buying another level. Normal loaded-land founding resolves its available
territory immediately. Upgrades distinguish added, rejected, unavailable and
occupied/protected nodes while retaining existing city territory and other zones.
See [terrain eligibility](XFEARTH_WORLD_TYPE.md#terrain-claim-eligibility-stage-9).

Each territory-owning faction stores two policies: `ally` and `neutral`. Categories are `BUILD` (place blocks), `DESTROY` (break blocks), `CONTAINER` (inventory tile entities), `INTERACT` (other right-clicks), and `SWITCH` (doors, trapdoors, gates, buttons, levers, and pressure plates).

Defaults are:

| Relationship | BUILD | DESTROY | CONTAINER | INTERACT | SWITCH |
| --- | --- | --- | --- | --- | --- |
| Ally | false | false | false | true | true |
| Neutral | false | false | false | false | false |

Use `/c permissions` (or `/c perms`) to view both policies. Officers and the leader may use `/c permissions <ally|neutral> <build|destroy|container|interact|switch> <true|false>`, for example `/c permissions ally switch true`. Ordinary members may view but not modify policy. Relationship, permission, and boolean arguments support tab completion.

Resolution order is owner member behavior, existing war/enemy handling, the territory owner's ally policy, then its neutral policy. Policies are relationship-wide rather than per faction, so diplomacy changes take effect immediately. City Center relocation/destruction, Officer Chest rank checks, flag-foundation protection, Fallen Nation restrictions, raid/mace rules, safezones, and warzones remain stricter and are evaluated by their existing paths.

Permission keys are stored with each faction in existing Clowder NBT. Missing keys retain constructor defaults, making pre-update saves migrate safely. Every mutation marks Clowder saved data dirty.

## Global player market and rice

Every Player Trader opens the same server-wide player market, across dimensions. It remains separate from XShop and faction market terminals. Offers show the sale item and quantity, item payment and quantity, seller, and remaining lots, with three offers per page. Item tooltips show full item details; hovering the seller shows their name and UUID. An empty market displays `No offers are available.`

Choose **Make a Trade** to open the trader's creation screen. Select a hotbar slot (1–9) and **Qty**, then use **Set sale** and **Set payment** to copy templates from your actual server inventory. Quantities are per lot. Choose **Lots** and press **MAKE OFFER**: matching sale items are removed from your main inventory into independent reserved offer storage. Templates alone never reserve items. Each publication creates a new independent offer, so one player can publish several offers. **Back to Market** returns without consuming any items from an unpublished draft.

**My offers** includes active, sold-out and cancelled offers with retained items. **Cancel** stops further purchases and retains stock for **Return**; **Collect** retrieves item payments. Return and collection each transfer the entire corresponding storage only when it fits. Returning stock also cancels the offer. Full inventories retain all items in market storage for later retrieval. Empty offers are removed after their final collection. Each offer has nine stock slots and nine payment slots; a full payment store rejects further purchases until collected. Offers remain available while their seller is offline and after their original block is broken. There is no expiration, listing fee or new currency.

Trading requires proximity to a valid Player Trader and the existing faction INTERACT permission. The server validates item, metadata and NBT identity, quantities, payment and destination space; failed checks commit no partial inventory transfer. Requests use a server session and fresh page revision. All actions run on the server thread, and changed offers refresh open market views at every trader. Level-3 XShop administrators have an **Admin** view to cancel offers and retrieve their stock/payments, including those of offline sellers. Normal players can manage only their own offers. The kit editor's borrowed inventory cannot trade.

The world-owned store and preserved legacy-inventory migration are described in [server administration](server-administration.md#global-player-market-storage).

Right-click mature rice (stage 7) to receive exactly one rice and reset it to stage 0 without uprooting it. Immature rice and normal breaking retain their existing behavior. Faction interaction protection still applies.
