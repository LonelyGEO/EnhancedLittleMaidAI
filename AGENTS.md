# AGENTS.md

## Language policy

- 默认使用简体中文回答。
- 除非明确要求英文，否则不要切换英文叙述。
- 代码、命令、报错、API 名称保持原文，不要强行翻译。
- 提问澄清时也使用中文。

Repository-specific guidance for coding agents working in `EnhancedLittleMaidAI-1.21`.
All items below were verified against the current repository contents.

## 1) Project snapshot

- 项目类型: **NeoForge 附属模组（Mixin addon）**
- 父模组: `touhou_little_maid`（NeoForge 1.21.1）
- 父模组仓库: `https://github.com/TartaricAcid/TouhouLittleMaid`
- 本模组仓库: `https://github.com/LonelyGEO/EnhancedLittleMaidAI.git`
- Build system: **Gradle Wrapper** (`gradlew`, `gradlew.bat`)
- Language toolchain: **Java 21** (`build.gradle`)
- Mod platform: **NeoForge** (`net.neoforged.moddev` plugin)
- Packaging: 标准 jar（无 shadowJar）
- 父模组依赖: `implementation files("libs/touhoulittlemaid-...jar")`
- 联动模组: `mining_little_maid`（compileOnly optional）
- 联动模组仓库: `https://github.com/LonelyGEO/MiningLittleMaid.git`
- Test dependency: **JUnit 4.13.2** (`testImplementation`)

## 2) Cursor/Copilot rule files

- `.cursor/rules/`: not found
- `.cursorrules`: not found
- `.github/copilot-instructions.md`: not found

So there are no extra assistant policy files to inherit; follow existing code patterns.

## 3) Build / test / run commands

Run commands from repository root.
Use `.bat` on Windows and non-`.bat` equivalents on macOS/Linux.

### Core commands

- `./gradlew.bat clean`
- `./gradlew.bat build`
- `./gradlew.bat check`
- `./gradlew.bat test`
- `./gradlew.bat assemble`

### NeoForge dev commands

- `./gradlew.bat runClient`
- `./gradlew.bat runServer`
- `./gradlew.bat runGameTestServer`
- `./gradlew.bat runData`

> **runClient 超时说明**：`runClient` 会阻塞等待游戏窗口关闭（非短暂命令）。Agent 执行时至少用 `timeout=600000`（10分钟），确保用户在游戏内有足够操作时间完成交互测试。

### Task discovery

- `./gradlew.bat tasks --all`
- `./gradlew.bat help --task test`

### Single-test commands (important)

Gradle test filtering is supported via `--tests`.

- Single class:
  - `./gradlew.bat test --tests "com.github.lonelygeo.enhancedlittlemaidai.ExampleTest"`
- Single method:
  - `./gradlew.bat test --tests "com.github.lonelygeo.enhancedlittlemaidai.ExampleTest.shouldDoThing"`
- Method wildcard:
  - `./gradlew.bat test --tests "*ExampleTest.should*"`

Current state note: `src/test/java` now has unit tests (ChineseTokenizerTest, Bm25IndexTest).

## 4) Lint/format reality

- No Spotless config found.
- No Checkstyle config found.
- No `.editorconfig` found.
- No dedicated `lint` Gradle task.
- Use `check` as verification umbrella task.

Practical rule: avoid style churn; keep formatting aligned with nearby code.

## 5) Style conventions from source code

### Formatting

- 4-space indentation.
- K&R braces.
- Blank lines between logical blocks.
- Wrapped long builder/fluent/record declarations.

### Imports

Observed grouping pattern:
1) project/local packages
2) third-party + Minecraft/NeoForge
3) `java.*`/`javax.*`
4) static imports last

Avoid reordering imports unless required by your edit.

### Naming

- Types (`class`, `record`, `enum`): `PascalCase`
- Methods/fields/locals/params: `camelCase`
- Constants (`static final`): `UPPER_SNAKE_CASE`
- Packages: lowercase, feature-oriented

### Nullability

- Use `@Nullable` explicitly where null is valid.
- Prefer guard clauses + early returns for invalid/null state.

### Error handling

- Catch specific exception types (`IOException`, parse exceptions, etc.).
- Log with useful context; do not silently swallow broadly.
- If intentionally ignoring exceptions, keep scope narrow and comment why.

### Logging

- Main logger pattern:
  - `public static final Logger LOGGER = LogUtils.getLogger();`
- Use parameterized logging (`{}` placeholders).

### Comments

- Comments are concise and intent-focused.
- Chinese comments are common; keep local language/style consistent.
- Do not add obvious comments that restate code.

## 6) Architecture hints

- **Entry point**: `EnhancedLittleMaidAI`
- **Addon pattern**: Mixin 注入式 — 不直接修改父模组源码，通过字节码注入实现功能扩展
- **Package roles**:
  - `mixin.*`: Mixin 类，注入到父模组类中（`LLMOpenAIClient`、`LLMCallback`、`MaidAIChatData`、`EntityMaid`、`MaidAIChatManager`）
  - `util.*`: 跨 Mixin 共享工具类（`ReasoningContentStore`、`ProactiveChatManager`、`ProactiveChatCallback`）
  - `memory.*`: 记忆系统（`MemoryStore`、`MindPalace`、`MemoryCompressor` 等）
  - `context.*`: 世界上下文提供者（`BlockAwareContexts`、`MiningContextProvider`）
  - `compat.*`: 模组兼容层（`MiningCompat`）
  - `config.*`: NeoForge Config 系统（`EnhancedConfig`）
  - `command.*`: 游戏内命令（`MindPalaceCommand`）
- **跨 Mixin 通讯模式**: 使用 `ReasoningContentStore`（`IdentityHashMap<LLMMessage, String>`）在 Mixin 间共享 reasoningContent，避免跨 Mixin 的 `@Unique` 方法调用（后者需要 refMap）
- **JSON 注入模式**: `LLMOpenAIClientMixin` 在 `Gson.toJson` 后直接解析 JSON 字符串，按消息顺序匹配并注入 `reasoning_content` 字段，无需在 `ChatMessage` 上添加 Mixin 字段
- **兼容性处理**: `@Redirect` 使用 `require = 0`，在父模组已内置 thinking 支持时静默跳过

When adding code, place it in the existing feature namespace.

## 7) Agent workflow checklist

Before editing:
1. Find a nearby analogous implementation and mirror its style.
2. Confirm the right Gradle task(s) (`tasks --all` if uncertain).
3. Determine whether changes are client-only, server-only, or shared.
4. Verify the target class/method exists in the parent mod (check the `libs/` JAR).
5. For Mixin changes: confirm the injection point is stable across parent mod versions.

After editing:
1. Run targeted verification first (filtered `test --tests ...` when applicable).
2. Run `./gradlew.bat build` before handoff.
3. **`runClient` 需要用户确认**：执行 `runClient` 前必须向用户提出确认，不得自行启动。
4. Check the runtime log for `Discarding @Unique` warnings — they indicate method conflicts.
5. Ensure diff does not contain unrelated formatting churn.

## 8) Packet-specific checklist

For changes under `network.message`:
1. Keep packet `TYPE` identifier stable and unique.
2. Maintain `STREAM_CODEC` encode/decode symmetry.
3. Keep side checks explicit (`isServerbound` / `isClientbound`).
4. Follow existing enqueue/handler flow patterns.
5. Keep boundary nullability checks explicit.

## 9) Mixin-specific checklist

For Mixin changes under `mixin.*`:
1. **Target method verification**: Confirm the target method signature (name + parameter types) exists in the loaded parent mod classes.
2. **Prefer `@Inject` over `@Overwrite`**: `@Overwrite` replaces an entire method and breaks with parent mod updates. Use `@Inject` + `@Redirect` + `@ModifyVariable` instead.
3. **`require = 0` for optional injections**: When a mixin should work with both modified and unmodified versions of a class, use `require = 0` on `@Redirect`/`@Inject` to silently skip if the target doesn't exist.
4. **Avoid cross-Mixin `@Unique` method calls**: Calling a `@Unique` method defined in Mixin A from Mixin B requires a refMap. Use shared utility classes (non-Mixin, outside the mixin package) with reflection or a common data store instead.
5. **Avoid `@SerializedName` field duplication**: Do not add a Mixin field with `@SerializedName("x")` if the parent mod class already has a field with the same JSON name — Gson will throw an `IllegalArgumentException`.
6. **Check `Discarding @Unique` warnings**: If the runtime log shows this warning, the `@Unique` method/field already exists in the target class and the Mixin version was discarded. This is normal when the parent mod already has the equivalent code.

## 10) Do not assume

- Auto-format/lint tooling exists (it currently does not).
- Tests exist for every module.
- Cursor/Copilot policy files exist (none found right now).
- **refMap is generated**: This project does not generate a refMap. Avoid cross-Mixin `@Unique` calls.
- **Parent mod classes are unchanged**: The parent mod may already have some features this addon provides. Use `require = 0` to handle both cases.

## 11) Quick commands

- Build: `./gradlew.bat build`
- Check: `./gradlew.bat check`
- Test all: `./gradlew.bat test`
- Test class: `./gradlew.bat test --tests "pkg.ClassName"`
- Test method: `./gradlew.bat test --tests "pkg.ClassName.methodName"`
- Run client: `./gradlew.bat runClient`
- Run data gen: `./gradlew.bat runData`

## 12) Git commit workflow

- 仓库地址：`https://github.com/LonelyGEO/EnhancedLittleMaidAI.git`
- **Agent 主动负责提交**：每次代码改动完成后，Agent 应主动执行 `git add` + `git commit`，不等待用户提醒。提交信息用中文，简洁描述改动目的。
- **SSH / 连接报错先诊断再提问**：遇到 SSH 权限、认证失败、远程连接等问题时，先自行排查（检查 remote、分支状态等），无法解决再向用户提问协助。
- **不可逆操作必须征得用户同意**：以下操作**绝对禁止**不经用户明确同意就执行：
  - `git push --force` / `--force-with-lease`
  - `git reset --hard`
  - `git rebase`（含 `--interactive`）
  - `git branch -D` 删除分支
  - `git commit --amend`（已推送的 commit）
  - 以及其他会修改已推送历史或破坏工作区的操作
- **修改 `.gitignore` 必须征得用户同意**：Agent 不得自行增删 `.gitignore` 条目。如确需修改，先向用户说明理由并取得确认。
- **禁止绕过 `.gitignore`**：不得使用 `git add -f` 等变相手段强制添加被忽略文件。若提交时发现文件被忽略，应告知用户并征求处理方案。
- 每次提交前检查 `git status` 和 `git diff`，确保不包含敏感信息（密钥、token 等）。
- **提交前主动提出版本变更建议**：每次完成代码改动后，Agent 应主动根据 §13 的版本位规则进行判断。PATCH 级别（Bug 修复、小调整）可自行决定并变更版本号；MINOR 及以上（新功能、架构重写）必须向用户确认后变更。

### GitHub Release 发布

- Agent **不得自行发布任何 Release**（包括 Beta 和正式版），必须先向用户提出并取得确认。
- 当前版本号 < 1.0.0 时，发布一律标记为 **Pre-release（Beta）**：
  ```
  gh release create v0.x.x build/libs/*.jar --title "v0.x.x-beta" --prerelease
  ```
- 版本号达 1.0.0 后默认改为正式 Release。

## 13) Versioning

- 当前版本: `0.11.3-neoforge+mc1.21.1`
- 后缀 `-neoforge+mc1.21.1` 为平台标识，保持不变

| 版本位 | 触发条件 |
|--------|---------|
| PATCH (`0.1.x`) | Bug 修复、参数微调、语言文件补充 |
| MINOR (`0.x.0`) | 新增功能（每完成 ROADMAP 中一项） |
| MAJOR (`x.0.0`) | 功能基本完整时升至 `1.0.0`；架构重写或 MC 版本升级 |

规则：
- 版本号变更单独一条 commit，格式 `release: 0.x.y`
- PATCH 级别（Bug 修复、参数微调、语言文件补充）Agent 可自行决定并变更版本号；MINOR 及以上（新功能、架构重写）必须向用户确认后变更。
- MAJOR 版本迭代前**必须向用户确认**，不得自行决定发版
- 发版时在 `WorkingPlan.md` 记录该版本已完成的功能

Keep this file updated when tooling/rules/project conventions change.