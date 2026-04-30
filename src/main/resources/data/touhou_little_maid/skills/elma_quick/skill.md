---
name: elma_quick
description: >
  Technical command reference for ELMAI. Use this instead of the full guides
  when you need exact syntax for game context queries or storage operations.
  Returns instantly without LLM extraction.
---

## 上下文查询 / Context Queries

工具: query_game_context(categories=[...])

| 想知道什么 | 命令 |
|---|---|
| 周围方块 | categories=["nearby_blocks"] |
| 光照/室内外/红石 | categories=["environment_detail"] |
| 附近生物详情 | categories=["nearby_entities"] |
| 工具耐久 | categories=["equipment"] |
| 饥饿值 | categories=["status"] |
| 附近玩家 | 包含在 nearby_entities 中 |
| 采矿信息 | categories=["mining_info"] (需 MiningLittleMaid) |
| 库存内容 | categories=["inventory"] (需 MaidStorageManager) |
| 一次查多种 | categories=["nearby_blocks","environment_detail","status"] |

多类别用逗号分隔。

---

## 仓储操作 / Storage Operations

先切换职业，再执行操作。

| 步骤 | 工具/命令 |
|------|---------|
| 切换仓储职业 | switch_work_task(task_id="maid_storage_manager:storage_manager") |
| 搜索物品 | get_storage(filter="物品中文名或英文名") |
| 取物给主人 | storage_fetch([{"itemId":"物品ID","count":数量}]) |
| 定位物品位置 | find_mark_storage(item=["物品ID"]) |
| 模拟合成 | simulate_crafting(itemId="物品ID",count=数量) |

### 取物完整流程

1. query_game_context(categories=["inventory"]) — 确认有物品
2. switch_work_task(task_id="maid_storage_manager:storage_manager") — 切换职业
3. get_storage(filter="物品名") — 搜索确认ID和数量
4. storage_fetch([{"itemId":"物品ID","count":数量}]) — 发起取物
5. 告知主人"正在帮您拿" — 完成后物品扔在主人脚下

---

## 其他技能 / Other Skills

| 技能名 | 用途 |
|--------|------|
| elmai | 世界感知、记忆系统、自主行为完整指南 |
| enhanced_storage | 仓储管理的详细工作流和注意事项 |
| touhou_little_maid | 原版女仆模组功能（驯服、祭坛、职业等） |
| elma_quick | 本快速参考卡（当前） |
