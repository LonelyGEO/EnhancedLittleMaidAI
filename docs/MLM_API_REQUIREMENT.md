# EnhancedLittleMaidAI <-> MiningLittleMaid API Requirement

> Version: 1.0 | Date: 2026-04-29 | From: EnhancedLittleMaidAI | Status: ✅ Delivered

---

## 1. Background

EnhancedLittleMaidAI is a TouhouLittleMaid addon providing LLM AI features.
When MLM is loaded, we want to use LLM to generate natural dialogue during mining,
replacing the current hardcoded fixed text + kaomoji.

## 2. Problem

MLM sends hardcoded messages:

| Scene | Current Text |
|-------|-------------|
| Ore above/below unreachable | Chat bubble "⬆iron_ore(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧" + system message "Maid: Found iron ore above!" |
| Ore blocked, no exposed face | System message "Maid: Found iron ore, but cannot reach!" |
| Inventory full | System message "Maid: Inventory full, stopping mining" |
| No torches | System message "Maid: Needs torches, but has none" |

Mixin injection risks: Lambda method names unstable (`lambda$start$2`), cross-mod maintenance cost.

## 3. Solution

**Add a NeoForge event in MLM** fired when mining chat/system messages are about to be sent.
Addon mods can intercept and replace the text.

### 3.1 Event Class

Package: `com.github.lonelygeo.mininglittlemaid.api.event`

```java
public class MiningMessageEvent extends net.neoforged.bus.api.Event
        implements net.neoforged.neoforge.event.ICancellableEvent {

    private final EntityMaid maid;
    private final MiningMessageType type;
    private final String oreName;
    private final Component originalBubbleText;
    private final Component originalSystemText;
    private final Map<String, Object> context;

    public EntityMaid getMaid() { ... }
    public MiningMessageType getType() { ... }
    public String getOreName() { ... }
    public Component getOriginalBubbleText() { ... }
    public Component getOriginalSystemText() { ... }
    public Map<String, Object> getContext() { ... }
    // constructor: (maid, type, oreName, originalBubble, originalSystem, context)
}
```

### 3.2 Message Type Enum

```java
public enum MiningMessageType {
    ORE_ABOVE,
    ORE_BELOW,
    ORE_UNREACHABLE,
    INVENTORY_FULL,
    NO_TORCH
}
```

### 3.3 Context Map (optional keys)

| Key | Type | Scene | Notes |
|-----|------|-------|-------|
| "target_pos" | BlockPos | ORE_* | Ore coordinates |
| "y_diff" | Integer | ORE_ABOVE/BELOW | Vertical distance to maid |
| "block_state" | BlockState | ORE_* | Ore BlockState |
| "kaomoji" | String | ORE_* | Random kaomoji (if needed) |

### 3.4 Trigger Points

| Event Type | File | Insert Point |
|-----------|------|-------------|
| ORE_ABOVE / ORE_BELOW | MaidMineBreakTask.lambda$start$2 | Before addTextChatBubble() + sendSystemMessage() |
| ORE_UNREACHABLE | MaidMineBreakTask.lambda$start$2 | Before sendSystemMessage() |
| INVENTORY_FULL | MaidMineInventoryCheckTask.start() | Before sendSystemMessage() |
| NO_TORCH | MaidMineTorchPlaceTask.start() | Before sendSystemMessage() |

### 3.5 Firing

```java
MiningMessageEvent event = new MiningMessageEvent(...);
net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
if (event.isCanceled()) return; // skip sending original message
```

## 4. Expected Usage by Addon

```java
@SubscribeEvent
public void onMiningMessage(MiningMessageEvent event) {
    if (!llmEnabled) return;
    event.setCanceled(true);
    EntityMaid maid = event.getMaid();
    String prompt = buildPrompt(event.getType(), event.getOreName(), event.getContext());
    long bubbleId = maid.getChatBubbleManager().addThinkingText("...");
    llmClient.chat(prompt, cb -> maid.getChatBubbleManager().addLLMChatText(cb.getText(), bubbleId));
}
```

## 5. Compatibility Requirements

| Requirement | Detail |
|------------|--------|
| Backward compat | Event with zero listeners: MLM behavior unchanged (only adds post() call) |
| Cancel check | If event.isCanceled() == true, skip sending original message |
| Zero deps | Event class uses pure MC/NeoForge types, no EnhancedLittleMaidAI import |
| Performance | post() overhead < 1us with no listeners, no mining tick impact |
| API stability | New fields via context Map; enum values appendable; no breaking changes |

## 6. Checklist

- [x] MiningMessageEvent class + enum definition    (MLM v1.0.0)
- [x] Four trigger points insert post() call         (MLM v1.0.0)
- [x] Cancel check: skip original message if event cancelled (MLM v1.0.0)
- [ ] Integration test: EnhancedLittleMaidAI subscribes, verifies LLM takeover (待 ELMAI 侧实现)
- [x] MLM version bump (MINOR, new API)              (MLM v1.0.0)

---

**Repo**: https://github.com/LonelyGEO/EnhancedLittleMaidAI
**Dev**: LonelyGEO
