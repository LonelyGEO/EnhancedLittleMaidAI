# Maid Storage Management Guide

This guide covers how a maid manages warehouse items. Split into two parts: directly available
abilities, and operations requiring the storage manager task.

See the quick reference skill for exact technical syntax.

---

## 1. Directly Available

### View Inventory

The maid can check what items are stored in nearby containers. Results show items per container
with counts, plus a global summary of all known storage.

Prerequisite: The maid must have walked near containers and looked inside them first. Unscanned
containers have no data yet.

Use for: "What's in storage?", "How much wood?", "Which chest has iron?"

---

## 2. Requires Storage Manager Task

The following operations require the maid to first switch to the "Storage Manager" task.
After switching, the maid automatically begins storage behavior.

### Search Items

The maid can search known inventory for specific items. Both Chinese and English names work for
matching. Results include item ID, name, count, and craftability.

Item IDs don't need to be memorized — use fuzzy search to find the correct ID.

### Fetch Items to Owner

The maid can retrieve specific items from containers and deliver them to the owner.

Flow: Maid walks to container → extracts items → walks to owner → throws items.
Items land at the owner's feet and must be picked up.

Multiple item types can be requested at once. It's best to search first to confirm quantities.

### Locate Items

The maid can pinpoint which specific container holds a particular item.

### Simulate Crafting

The maid can calculate offline whether an item can be crafted, what materials are needed,
and how many steps. No actual materials are consumed. Requires holding a portable crafting
calculator bauble.

---

## 3. Fetch Workflow

When the owner says "get me some of ___":

1. Confirm existence: Check inventory for the target item.
2. Report: If not found, tell owner. If insufficient, state the shortage. If enough, continue.
3. Switch task: Change to storage manager.
4. Confirm details: Search for the item to get its exact ID and count.
5. Execute fetch: Fetch by ID and quantity.
6. Notify: Say "Alright, fetching for you." Items land at the owner's feet when done.

---

## 4. Query Workflow

When the owner asks "where is ___" or "how much ___":

Directly check inventory, extract position and count from results, tell the owner.

---

## 5. Important Notes

- The maid must have scanned containers first to have inventory data.
- Fetched items land at the owner's feet, not in inventory — must be picked up.
- Search and fetch operations fail if not in the storage manager task.
- After fetching completes, the maid automatically returns to previous work or idle.
- Search and fetch require the player to be online for item name matching.

---

## 6. Example Dialogues

Q: "What's in storage?"
A: Check inventory → "Master, the chest has diamonds and iron. The barrel has apples and bread."

Q: "Get me the diamonds."
A: Confirm diamonds exist and count is enough → switch to storage task → search to confirm → execute fetch
   → "Alright master, I'll get the diamonds."

Q: "How many chests can we make?"
A: Simulate crafting → "Master, we have materials for four chests."
