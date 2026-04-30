# Maid Storage Management Complete Guide

This guide covers all storage-related capabilities that the maid can perform
through AI conversation. Divided into two sections: tools available directly
and tools requiring the storage manager task.

---

## 1. Directly Available (no task switch required)

### 1.1 Query Storage Contents

Tool: query_game_context
Parameter: categories=["inventory"]
Returns two sub-contexts:

- nearby_storage: Per-container item listing
  Example: "(2,64,-3) [minecraft:chest]: diamondx5, iron_ingotx64, redstonex128"

- storage_summary: Global totals
  Example: "Total: 8 kinds, 225 items. iron_ingotx64, redstonex128..."

Use for: "What's in storage?", "How many diamonds?", "Where are the iron ingots?"

Note: The maid must have scanned containers first (View behavior, automatic).
       If it returns "No storage data recorded", the maid hasn't viewed containers yet.

---

## 2. Requires Storage Manager Task

**Important**: All MSM tools below require the maid to be in the "Storage Manager" task.
**Switch method**: switch_work_task(task_id="maid_storage_manager:storage_manager")

### 2.1 Search Items

Tool: get_storage
Parameter: filter (string, fuzzy match on item name)
Example: get_storage(filter="diamond")
Returns: JSON array of matched items with id, name, count, and craftability.

### 2.2 Fetch Items to Player

Tool: storage_fetch
Parameter: list (array of {"itemId":"...","count":N})
Example: storage_fetch([{"itemId":"minecraft:diamond","count":5}])

Flow: Maid walks to container → retrieves items → walks to owner → throws items.
Items land at the player's feet and must be picked up.

Multiple items at once:
storage_fetch([{"itemId":"minecraft:diamond","count":5},{"itemId":"minecraft:iron_ingot","count":32}])

### 2.3 Locate Items

Tool: find_mark_storage
Parameter: item (string array of item IDs)
Example: find_mark_storage(item=["minecraft:diamond"])
Returns: Location and count of items in each container.

### 2.4 Simulate Crafting

Tool: simulate_crafting
Parameter: itemId (full item ID), count (number)
Example: simulate_crafting(itemId="minecraft:furnace", count=1)
Returns: {"success":true,"steps":2,"consumes":[...]} or missing materials info.

---

## 3. Complete Fetch Workflow (recommended sequence)

When the owner says "get me X of Y":

**Step 1: Confirm existence**
  Call query_game_context(categories=["inventory"])
  Check if the item exists in nearby storage.

**Step 2: Report**
  If not found → tell owner "Y is not available in any nearby container"
  If insufficient → tell owner "only Z of Y available, not enough for X"
  If enough → proceed

**Step 3: Switch task**
  Call switch_work_task(task_id="maid_storage_manager:storage_manager")

**Step 4: Confirm location and count**
  Call get_storage(filter="item name")

**Step 5: Execute fetch**
  Call storage_fetch([{"itemId":"item ID","count":N}])

**Step 6: Notify**
  Reply "Alright, fetching X of Y, please wait"
  Items will be thrown at the owner's feet when done

---

## 4. Complete Query Workflow

When the owner asks "where is X" or "how much Y":

**Step 1: Query inventory**
  Call query_game_context(categories=["inventory"])

**Step 2: Format response**
  Extract item location from nearby_storage
  Extract total count from storage_summary
  Tell owner: "oak chest at (2,64,-3) has 5 diamonds" or "total: 5 diamonds"

---

## 5. Common Item IDs

Format: "minecraft:item_name" (lowercase, underscores)

| Item | Item ID |
|------|---------|
| Diamond | minecraft:diamond |
| Iron Ingot | minecraft:iron_ingot |
| Iron Ore | minecraft:iron_ore |
| Gold Ingot | minecraft:gold_ingot |
| Redstone | minecraft:redstone |
| Coal | minecraft:coal |
| Lapis Lazuli | minecraft:lapis_lazuli |
| Emerald | minecraft:emerald |
| Nether Quartz | minecraft:quartz |
| Oak Log | minecraft:oak_log |
| Oak Planks | minecraft:oak_planks |
| Stick | minecraft:stick |
| Crafting Table | minecraft:crafting_table |
| Furnace | minecraft:furnace |
| Chest | minecraft:chest |
| Iron Pickaxe | minecraft:iron_pickaxe |
| Diamond Pickaxe | minecraft:diamond_pickaxe |
| Apple | minecraft:apple |
| Bread | minecraft:bread |
| Torch | minecraft:torch |
| Bucket | minecraft:bucket |
| Bow | minecraft:bow |
| Arrow | minecraft:arrow |
| Iron Sword | minecraft:iron_sword |
| Diamond Sword | minecraft:diamond_sword |
| Shield | minecraft:shield |
| Enchanted Book | minecraft:enchanted_book |

Other item IDs can be obtained from get_storage() results.

---

## 6. Tool Role Reference

| Tool | Source | Needs Task Switch | Purpose |
|------|--------|-------------------|---------|
| query_game_context(inventory) | TLM built-in | No | Query inventory contents |
| switch_work_task | TLM built-in | No | Switch to storage task |
| get_storage | MSM | Yes | Search items |
| storage_fetch | MSM | Yes | Fetch items to owner |
| find_mark_storage | MSM | Yes | Locate items |
| simulate_crafting | MSM | Yes | Check crafting feasibility |

---

## 7. Important Notes

- The maid must have scanned containers first (View behavior, automatic) to have inventory data
- MSM search/fetch/locate tools require the player to be online for JEI/EMI item name matching
- storage_fetch throws items at the owner's feet, not directly into inventory — must be picked up
- Attempting MSM tools while NOT in the storage manager task will fail
- After fetch completes, the maid automatically returns to previous work mode or idle

---

## 8. Error Handling

| Situation | Suggested Response |
|-----------|-------------------|
| Inventory empty | "I haven't scanned any containers yet. Let me wander around the chests first." |
| Item not found | "{item} is not available in any nearby container" |
| Insufficient quantity | "I only have {available} of {item}, not enough for {requested}" |
| Task switch failed | "I ran into an issue switching to the storage manager task" |
| Fetch failed | "There was a problem retrieving the items. Please try again later." |

---

## 9. Example Dialogues

Q: "What's in the storage?"
A: Call query_game_context(inventory)
   → "Master, the oak chest has 5 diamonds, 64 iron ingots and 128 redstone. The barrel has 12 apples and 5 bread."

Q: "Get me 3 diamonds"
A: ① query_game_context(inventory) → diamonds found, enough count
   ② switch_work_task("maid_storage_manager:storage_manager")
   ③ storage_fetch([{"itemId":"minecraft:diamond","count":3}])
   → "Alright master, I'll fetch 3 diamonds and throw them to you."

Q: "Do we have iron? Where?"
A: Call query_game_context(inventory)
   → "Yes master! The oak chest at (2,64,-3) has 64 iron ingots."

Q: "How many chests can we make?"
A: Call simulate_crafting("minecraft:chest",1)
   → "Master, we have enough wood planks to make 4 chests."
