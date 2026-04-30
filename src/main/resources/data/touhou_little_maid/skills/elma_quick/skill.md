---
name: elma_quick
description: >
  Quick reference card for ELMAI context query commands. Use when you need
  a quick reminder of available game context categories without the full
  detailed guide. Returns instantly without LLM extraction.
---

## ELMAI 快速命令参考 / Quick Command Reference

Use query_game_context(categories=[...]) with these category IDs:

### 查询命令 / Query Commands

| 想问什么 / Question | 命令 / Command |
|---|---|
| 附近有什么方块 | query_game_context(categories=["nearby_blocks"]) |
| 环境光照/室内外 | query_game_context(categories=["environment_detail"]) |
| 附近生物/玩家 | query_game_context(categories=["nearby_entities"]) |
| 工具耐久 | query_game_context(categories=["equipment"]) |
| 饥饿值 | query_game_context(categories=["status"]) |
| 所有环境信息 | query_game_context(categories=["nearby_blocks","environment_detail","nearby_entities"]) |
| 采矿信息 (*) | query_game_context(categories=["mining_info"]) |
| 库存信息 (**) | query_game_context(categories=["inventory"]) |

- 多类别逗号分隔: categories=["A","B","C"]
- (*) 需要 MiningLittleMaid 模组
- (**) 需要 MaidStorageManager 模组，详细操作见 enhanced_storage 技能

### 仓储取物速查 / Storage Quick Ref

| 操作 | 命令 |
|---|---|
| 切换仓储职业 | switch_work_task(task_id="maid_storage_manager:storage_manager") |
| 搜索物品 | get_storage(filter="物品名") |
| 取物给主人 | storage_fetch([{"itemId":"物品ID","count":数量}]) |

For full multi-step workflows, use: use_skill("enhanced_storage")

### 自身能力查询 / Self-capability Reference

| 操作 | 命令 |
|---|---|
| 世界感知完整指南 | use_skill("elmai") |
| 仓储管理完整指南 | use_skill("enhanced_storage") |
| TLM 本体功能 | use_skill("touhou_little_maid") |
