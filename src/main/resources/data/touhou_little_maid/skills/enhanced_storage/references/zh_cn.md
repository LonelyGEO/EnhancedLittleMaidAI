# 女仆仓储管理完整工作指南

本指南涵盖女仆通过 AI 对话管理仓库物品的所有能力。
分为两部分：不需要切换职业的功能 和 需要切换职业的功能。

---

## 1. 可直接使用的功能（不需要切换职业）

### 1.1 查询库存内容

工具: query_game_context
参数: categories=["inventory"]
返回两个子信息:

- nearby_storage (附近存储): 按容器位置列出物品
  示例: "(2,64,-3) [minecraft:chest]: 钻石x5, 铁锭x64, 红石x128; (5,64,0) [minecraft:barrel]: 苹果x12, 面包x5"

- storage_summary (库存摘要): 总计信息
  示例: "Total: 8 kinds, 225 items. 铁锭x64, 红石x128, 苹果x12, 钻石x5..."

适用场景: "仓库里有什么"、"有多少钻石"、"哪个箱子有铁锭"、"还剩下多少木头"

注意: 女仆必须先扫描过容器（在她箱子附近走动查看过），才能查询到库存数据。
      如果返回 "No storage data recorded"，说明女仆还没有查看过容器。

---

## 2. 需要切换职业后使用的功能

**重要**: 以下所有 MSM 工具都要求女仆处于"仓储管理"职业。
**切换方法**: switch_work_task(task_id="maid_storage_manager:storage_manager")
切换成功后女仆会自动开始仓储管理行为。

### 2.1 搜索物品

工具: get_storage
参数: filter (字符串，模糊匹配物品名，也支持英文物品ID)
示例: get_storage(filter="钻石")
返回: JSON 格式的物品列表，包含 id、name、count、是否可合成等信息。

### 2.2 取物给主人

工具: storage_fetch
参数: list (数组)，每项格式 {"itemId":"物品ID","count":数量}
示例: storage_fetch([{"itemId":"minecraft:diamond","count":5}])

流程: 女仆自动走到容器前 → 取物 → 走回主人 → 扔出物品。
物品会掉落在玩家脚下，需要拾取。

支持一次请求多种物品:
storage_fetch([{"itemId":"minecraft:diamond","count":5},{"itemId":"minecraft:iron_ingot","count":32}])

### 2.3 定位物品位置

工具: find_mark_storage
参数: item (字符串数组，物品 ID 列表)
示例: find_mark_storage(item=["minecraft:diamond"])
返回: 物品在每个容器中的坐标和数量。

### 2.4 模拟合成可行性

工具: simulate_crafting
参数: itemId (物品完整ID), count (数量)
示例: simulate_crafting(itemId="minecraft:furnace", count=1)
返回: {"success":true,"steps":2,"consumes":[{"id":"minecraft:cobblestone","count":8}]}
      或者缺少材料信息。

---

## 3. 完整取物工作流（推荐按此顺序执行）

当主人说"帮我拿 X 个 Y":

**第1步: 确认物品存在**
  调用 query_game_context(categories=["inventory"])
  检查 storage_summary 中是否有目标物品。

**第2步: 报告结果**
  如果库存中没有 → 告知主人"附近容器里没有 Y"
  如果数量不够 → 告知主人"只有 Z 个 Y，不够 X 个"
  如果有足够数量 → 进入下一步

**第3步: 切换职业**
  调用 switch_work_task(task_id="maid_storage_manager:storage_manager")

**第4步: 确认位置和数量**
  调用 get_storage(filter="物品名")

**第5步: 发起取物**
  调用 storage_fetch([{"itemId":"物品ID","count":数量}])

**第6步: 告知主人**
  回复"好的，正在帮您拿 X 个 Y，稍等一下"
  取物完成后，物品会扔在主人脚边

---

## 4. 完整查询工作流

当主人问"哪里有 X" 或 "还有多少 Y":

**第1步: 直接查询库存**
  调用 query_game_context(categories=["inventory"])

**第2步: 整理回复**
  从 nearby_storage 中提取物品位置信息
  从 storage_summary 中提取总量信息
  告知主人: "橡木箱(2,64,-3)里有 5 个钻石" 或 "总共还有 5 个钻石"

---

## 5. 物品 ID 查询

物品 ID 格式: "minecraft:物品英文名" (小写，下划线分隔)。

**不要硬编码物品 ID。** 当你不知道某物品的 ID 时:
1. 在取物前先调用 get_storage(filter="物品中文名") — 它会匹配并返回正确的 ID
2. 或者先调用 query_game_context(inventory) — 从返回的物品描述中获取 ID

常见物品 ID 示例: 钻石=minecraft:diamond，铁锭=minecraft:iron_ingot，工作台=minecraft:crafting_table

---

## 6. 工具角色对照

| 工具名 | 归属 | 需切换职业 | 功能 |
|--------|------|-----------|------|
| query_game_context(inventory) | TLM 内置 | 否 | 查询库存内容 |
| switch_work_task | TLM 内置 | 否 | 切换到仓储职业 |
| get_storage | MSM | 是 | 搜索物品 |
| storage_fetch | MSM | 是 | 取物给主人 |
| find_mark_storage | MSM | 是 | 定位物品位置 |
| simulate_crafting | MSM | 是 | 模拟合成可行性 |

---

## 7. 注意事项

- 女仆必须先扫描过容器（View 行为，自动执行），才能查询到库存数据
- MSM 的搜索/取物/定位工具在生产环境（neoforge）下使用，与 JEI/EMI 联动的名称搜索需要玩家在线
- storage_fetch 取出的物品扔在玩家脚下，不是直接放入背包，需要拾取
- 如果女仆不在仓储职业就尝试调用 get_storage 等工具会失败
- 取物完成后女仆会自动回到原来的工作模式或空闲状态

---

## 8. 错误处理指南

| 情况 | 回复建议 |
|------|---------|
| 库存为空 | "主人，我还没有查看过任何容器，请让我先在箱子附近走一走" |
| 物品不存在 | "主人，附近容器里没有 {物品名}" |
| 数量不足 | "主人，只有 {现有数量} 个 {物品名}，不够 {需求数量} 个" |
| 切换职业失败 | "主人，我好像切换到仓储管理时出了点问题" |
| 取物失败 | "主人，取物时出现了一些问题。请稍后重试" |

---

## 9. 对话示例

Q: "仓库里有什么"
A: 调用 query_game_context(inventory)
   → "主人，橡木箱里有5个钻石、64个铁锭和128个红石，木桶里有12个苹果和5个面包"

Q: "帮我把3个钻石拿出来"
A: ① query_game_context(inventory) 确认有钻石 → 数量够
   ② switch_work_task("maid_storage_manager:storage_manager")
   ③ storage_fetch([{"itemId":"minecraft:diamond","count":3}])
   → "好的主人，我去拿3个钻石，回来扔给你"

Q: "有没有铁锭？在哪里？"
A: 调用 query_game_context(inventory)
   → "有的主人！橡木箱(2,64,-3)里有64个铁锭"

Q: "能做多少个箱子？"
A: 调用 simulate_crafting("minecraft:chest",1)
   → "主人，我有足够的木材，材料够做4个箱子"
