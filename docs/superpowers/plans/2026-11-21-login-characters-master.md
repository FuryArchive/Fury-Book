# Login, Персонажи and Мастер — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Login first (with «Играть без входа») on all platforms; account controls in the menu; a shared Персонажи screen with archive and duplicate; a shared offline «Мастер» section (Бой, Враги, Заметки, Справочник, Партия) replacing Кампания.

**Architecture:** Shared Compose in `shared/…/ui/account`, `…/dubl/ui/characters`, `…/dubl/ui/master`. Platforms adapt their existing cloud controllers to one `FuryAccount` interface and their existing character state to one `DublCharactersHost`, and provide two small local stores (start mode + archive list, and `MasterStore`). Models are plain functions with kotlin.test tests; composables stay thin. No server changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlin.test; Python source guards in `tools/tests`.

**Spec:** `docs/superpowers/specs/2026-11-21-login-characters-master-design.md` · **Mockup:** https://claude.ai/artifact/KJ2cQGEeWMsejyWZsWedky

## Global Constraints

- **No new server functions, tables or migrations.** Only existing RPCs: auth, native sync (`native_*`), web sync (`get_web_bootstrap`, `save_web_*`), `set_profile_nickname`, `campaign_*`.
- **Unchanged:** rules, the sheet, the editor, character sync payloads, the transfer file format, the desktop data folder.
- **Keep the existing design:** build from `EditorCard`, `EditorRow`, `EditorChip`, `EditorSquareButton`, `EditorOutlineButton`, `EditorPrimaryButton`, `FuryDialogScaffold`, `FuryTextField`, `SheetSearchField` and the sheet's card look, in the five themes, at 360dp, tablet, 1100 and 1440. Layout and copy follow the mockup.
- **Copy (exact strings, from the mockup and the old screens):**
  - Login: «Вход», «Создать аккаунт», «Сброс пароля», «Email», «Пароль», «Никнейм», «Войти», «Отправить ссылку», «Забыли пароль?», «Играть без входа», «Введите email.», «Введите корректный email.», «Введите пароль.», «Пароль должен содержать минимум 8 символов.», «Никнейм должен содержать от 2 до 32 символов.», «Аккаунт создан. Подтвердите email из письма, затем войдите.».
  - Account: «Fury Account», «Синхронизировать», «Конфликт изменений», «Сохранить локальную», «Загрузить облачную», «Журнал синхронизации», «Выйти», «Выйти из аккаунта?», «Синхронизировать и выйти», «Выйти всё равно», «Войти или создать аккаунт».
  - Персонажи: «Персонажи», «+ Новый», «Импорт», «Экспорт», «Дублировать», «В архив», «Вернуть из архива», «Удалить», «Удалить персонажа?», «Мои · N», «Архив · N», «Активный», «Вступить по коду», «Код приглашения», «Вступить», «Выйти из кампании», «Пакеты контента», «Импортировать .fcp».
  - Мастер: «Мастер», «Бой», «Враги», «Заметки», «Справочник», «Партия», «Бой · раунд N», «Следующий →», «Новый бой», «+ Игрок по имени», «+ Из врагов», «+ Партия», «Бросить», «+ состояние», «В бой», «+ Новый враг», «+ Новая заметка», «Обновить», «Пригласить», «+ Кампания», «Вернуть».
- **Local checks:** `/home/claude/gw.sh <log> :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs`. Android compiles only in CI.
- **Python baseline:** ids in `/tmp/pytest-baseline-ids.txt` may only shrink.

## Review Focus

1. **A guest with characters signs in on web.** The guest characters are added to the account (not replaced by the remote pull), and the local guest copy is removed only after the upload. Test: Task 6 `guestCharactersMergeIntoRemote`.
2. **«Выйти» with unsynced changes, then «Выйти всё равно».** The device ends with no characters of that account, GM data untouched, start mode back to login. Test: Task 1 `signOutDiscardingClearsDevice`; Task 9 `masterDataSurvivesSignOut`.
3. **Archiving the active character, or the last unarchived one.** The next unarchived one becomes active, or a new character is made. Test: Task 4 `archivingActivePicksNext`.
4. **Initiative typed as blank, «-», or with ties.** Sorting stays stable, blanks go last, «Следующий» never skips or crashes on an empty fight. Test: Task 9 `fightOrderHandlesBlanksAndTies`.
5. **Partия for a player whose sheet uses a pack the GM lacks.** The read-only view still opens; unknown entries show by name with «нет пакета». Test: Task 11 `readOnlyViewToleratesMissingPack`.

---

## Part 1 — Login, account panel, Персонажи

### Task 1: `FuryAccount` interface and platform adapters

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/account/FuryAccount.kt`
- Create: `shared/src/commonTest/kotlin/com/furybook/ui/account/FakeAccount.kt`, `FuryAccountTest.kt`
- Modify: `desktopApp/…/cloud/DesktopCloudController.kt`, `app/…/cloud/AndroidCloudController.kt` (implement it); `webApp/…/web/SupabaseBackend.kt` + a new `webApp/…/web/WebAccount.kt` adapter.
- Modify: `shared/…/core/cloud/NativeCloudSync.kt`: `fun pendingCount(): Int` (metadata `dirtyIds.size`) and `fun clearLocal()` (empty snapshot, extras deleted, metadata cleared).

**Interfaces:**
- Produces:
  - `enum class FuryAccountStatus { SIGNED_OUT, CONNECTING, SYNCING, SYNCED, PENDING, OFFLINE, CONFLICT, ERROR }`
  - `data class FuryAccountConflict(val characterId: String, val name: String)`, `data class FuryAccountLogEntry(val time: String, val text: String)`
  - `interface FuryAccount { val signedIn: Boolean; val email: String; val nickname: String; val status: FuryAccountStatus; val statusText: String; val pendingChanges: Int; val conflicts: List<FuryAccountConflict>; val syncLog: List<FuryAccountLogEntry>; val campaigns: List<CampaignSummary>; suspend fun signIn(email: String, password: String): Result<Unit>; suspend fun signUp(email: String, password: String, nickname: String): Result<Boolean>; suspend fun requestPasswordReset(email: String): Result<Unit>; suspend fun updateNickname(nickname: String): Result<String>; fun requestSync(); suspend fun syncAndSignOut(): Result<Unit>; suspend fun signOutDiscarding(); suspend fun keepLocal(characterId: String); suspend fun useCloud(); suspend fun refreshCampaigns(): Result<Unit>; suspend fun createCampaign(name: String): Result<CampaignSummary>; suspend fun createInvite(campaignId: String): Result<CampaignInvite>; suspend fun joinCampaign(code: String, characterId: String): Result<CampaignSummary>; suspend fun leaveCampaign(campaignId: String): Result<Unit>; suspend fun removeMember(campaignId: String, userId: String): Result<Unit>; suspend fun dashboard(campaignId: String): Result<CampaignDashboard> }`
  - `signUp` returns true when signed in at once, false when the email must be confirmed. Properties are Compose-observable in adapters.
- **Native sign-out (spec B):** `syncAndSignOut` = sync, fail if conflicts or errors remain; then `signOutDiscarding`. That = `gateway.signOut()`, `coordinator.clearLocal()`, bump `reloadToken` so the app reloads the empty store.
- **Native nickname:** `updateNickname` calls the existing `set_profile_nickname` RPC through the gateway's `rpc`. **Ruling:** web already uses it; same auth users. Cost if the RPC rejects native callers: nickname edit stays web-only (shown read-only on native).
- **Web adapter:** wraps `WebCloudSync` and the `WebRoot` session. The web log comes from its existing history; native returns an empty log.

- [ ] **Step 1: Failing tests** (`FuryAccountTest`, using `NativeCloudSyncCoordinator` with an in-memory store and a fake transport, as existing sync tests do):
  - `pendingCountFollowsDirtyIds`: mark one character changed → `pendingCount() == 1`.
  - `signOutDiscardingClearsDevice`: a coordinator with 2 characters → `clearLocal()` → store snapshot has no characters, extras for both deleted, metadata cleared.
  - `fakeAccountSignsInAndOut`: `FakeAccount` tracks calls (used by later UI tests).
- [ ] **Step 2: Run** `--tests '*FuryAccountTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the interface, `pendingCount`, `clearLocal`, and the three adapters.
- [ ] **Step 4: Run.** Expected: PASS. Compile desktop and web.
- [ ] **Step 5: Commit** `feat(account): one account interface over the native and web cloud`.

### Task 2: Start mode and the login form model

**Files:**
- Create: `shared/…/ui/account/FuryStart.kt`, `…/ui/account/LoginForm.kt`
- Create: platform start-mode stores: desktop `DesktopStartStore` (file `start-mode.txt` in the data folder), Android (prefs `fury_start`), web (localStorage key `fury.start`).
- Test: `FuryStartTest.kt`, `LoginFormTest.kt`

**Interfaces:**
- `enum class FuryStartMode { NONE, GUEST, SIGNED_IN }`; `interface FuryStartStore { fun load(): FuryStartMode; fun save(mode: FuryStartMode) }`
- `fun furyShowsLogin(mode: FuryStartMode, signedIn: Boolean, hasLocalCharacters: Boolean): Boolean`: false when `signedIn`; false for GUEST; for NONE, true. **Update rule (spec):** NONE with local characters and no account still shows login once.
- `enum class LoginTab { SIGN_IN, SIGN_UP, RESET }`; `data class LoginForm(val tab: LoginTab, val email: String, val password: String, val nickname: String)`; `fun LoginForm.error(): String?` with the exact copy and order: email blank → «Введите email.», bad email → «Введите корректный email.», (not RESET) blank password → «Введите пароль.», short → «Пароль должен содержать минимум 8 символов.», (SIGN_UP) nickname length → «Никнейм должен содержать от 2 до 32 символов.».

- [ ] **Step 1: Failing tests:** `startModeDecidesLogin` (signed in → false; GUEST → false; NONE → true); `loginErrorsInOrder` (each case above, and a valid form → null; RESET ignores password).
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** + the three stores.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(account): start mode and login form rules`.

### Task 3: Login screen and the menu account panel

**Files:**
- Create: `shared/…/ui/account/LoginScreen.kt` (`FuryLoginScreen(account: FuryAccount, onGuest: () -> Unit, onSignedIn: () -> Unit, onBack: (() -> Unit)?)`), `…/ui/account/AccountPanel.kt` (`FuryAccountPanel(account, isGuest: Boolean, onSignIn: () -> Unit)`, `FurySignOutDialog`).
- Modify: `shared/…/ui/components/FuryShell.kt`: an `accountSlot: (@Composable () -> Unit)?` shown in the menu dialog under the sections, and a top-bar account button on wide screens (label = nickname or «Гость», a gold dot on conflict) that opens the same dialog; the phone ☰ gets a gold badge on conflict.
- Test: `AccountPanelLogicTest.kt`

**Interfaces:**
- `fun accountDotColor(status: FuryAccountStatus): AccountDot` (`enum class AccountDot { GREY, GREEN, GOLD, RED }`); `fun signOutNeedsChoice(pending: Int): Boolean`.
- Login screen per mockup: tabs, fields, inline error/info, primary button, «Забыли пароль?», divider «или», «Играть без входа» and its note. Submit runs `LoginForm.error()` first, then the account call; failures show the account's message.

- [ ] **Step 1: Failing tests:** `dotFollowsStatus` (CONFLICT → GOLD, SYNCED → GREEN, SIGNED_OUT → GREY, ERROR → RED); `signOutAsksOnlyWithPending` (0 → false, 2 → true).
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the model, screen, panel, shell slot.
- [ ] **Step 4: Run.** Expected: PASS. Compile.
- [ ] **Step 5: Commit** `feat(account): login screen and menu account panel`.

### Task 4: Персонажи model — list, archive, duplicate

**Files:**
- Create: `shared/…/dubl/ui/characters/DublCharactersHost.kt`, `…/characters/CharactersModels.kt`
- Modify: `shared/…/dubl/application/CharacterApplication.kt` + `state/CharacterSession.kt`: `fun duplicate(id: String): String?`
- Modify: `FakeSheetHost` to implement `DublCharactersHost`.
- Test: `CharactersModelsTest.kt`, `CharacterApplicationDuplicateTest.kt`

**Interfaces:**
- `interface FuryArchiveStore { fun load(): Set<String>; fun save(ids: Set<String>) }` (desktop file `archive.json`, Android prefs, web localStorage). Task 7's platform wiring clears it after `signOutDiscarding` (the account's characters left the device).
- `interface DublCharactersHost { val characters: List<DublCharacter>; val activeId: String; fun portraitOf(id: String): String?; fun select(id: String); fun create(); fun delete(id: String); fun duplicate(id: String): String?; fun exportCharacter(id: String): String; fun importCharacter(raw: String): CharacterTransferImportResult; val archive: FuryArchiveStore }`
- `data class CharacterCard(val id: String, val name: String, val concept: String, val xp: Int, val active: Boolean, val archived: Boolean, val campaign: String?)`
- `fun characterCards(host: DublCharactersHost, archived: Boolean, campaigns: List<CampaignSummary>): List<CharacterCard>` (campaign = name of a campaign whose `linkedCharacterId` is this id).
- `fun archiveCharacter(host: DublCharactersHost, id: String)`: adds to the archive; if it was active, selects the first unarchived one, or `create()`s when none is left.
- `duplicate(id)`: a deep copy with a new id, name «<имя> (копия)», extras copied, placed after the original; returns the new id.

- [ ] **Step 1: Failing tests:** `duplicateCopiesWithNewIdAndName`; `cardsSplitByArchive`; `archivingActivePicksNext`; `archivingLastCreatesNew`; `cardShowsLinkedCampaign`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(characters): list model with archive and duplicate`.

### Task 5: Персонажи screen

**Files:**
- Create: `shared/…/dubl/ui/characters/CharactersScreen.kt` (`DublCharactersScreen(host, account: FuryAccount, packs: DublPacksSection?, onOpen: () -> Unit, onNew: () -> Unit, onImportFile: () -> Unit, onExportFile: (id: String, content: String) -> Unit)`), `…/characters/JoinCampaignDialog.kt`
- `data class DublPackRow(val id: String, val name: String, val note: String, val enabled: Boolean, val locked: Boolean)`; `class DublPacksSection(val rows: List<DublPackRow>, val onToggle: (String, Boolean) -> Unit, val onImport: (() -> Unit)?)`.
- Test: `CharactersModelsTest.kt` (`joinWarnsWhenMoving`)

**Interfaces:**
- **Ruling (spec deviation):** an invite code doesn't name its campaign, so a membership move can't be known before joining. The warning shows **after** a join that replaced another character, from the returned `CampaignSummary.linkedCharacterId` versus the previous campaigns list: `fun joinReplaced(before: List<CampaignSummary>, joined: CampaignSummary, characterId: String): String?` returns the replaced character's id. Cost: the warning is after the fact.
- Screen per mockup: header, «Мои · N» / «Архив · N», card grid (3 / 2 / 1 columns), per-card actions, delete confirm «Удалить персонажа?», «Пакеты контента» card. Campaign actions only when signed in; guests get a toast «Кампании доступны после входа в аккаунт».

- [ ] **Step 1: Failing test** `joinWarnsWhenMoving`: before = summary with `linkedCharacterId = "a"`; joined same id with `linkedCharacterId = "b"` → `"a"`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** model + screen + dialog.
- [ ] **Step 4: Run.** Expected: PASS. Compile.
- [ ] **Step 5: Commit** `feat(characters): shared Персонажи screen`.

### Task 6: Web guest storage and guest → account merge

**Files:**
- Modify: `webApp/…/web/BrowserPersistence.kt`: a persistent mode (localStorage keys `fury.guest.snapshot`, `fury.guest.extras`, `fury.guest.chi`) used when the start mode is GUEST; session-memory mode unchanged when signed in.
- Create: `shared/…/core/cloud/GuestMerge.kt` (pure function, so it's testable in common code).
- Test: `GuestMergeTest.kt`

**Interfaces:**
- `fun mergeGuestIntoRemote(remote: AppSnapshot, guest: AppSnapshot, newId: () -> String): AppSnapshot`: remote characters first, then every guest character (id regenerated if it clashes with a remote id), active = the guest's active one.
- Web sign-in from guest: `pull()` → merge → `replaceRaw(merged)` → `schedule(...)` (uploads the new characters through the existing save path) → when the status reaches SYNCED, delete the `fury.guest.*` keys. A failed or interrupted upload keeps them; the next sign-in merges again (ids already uploaded are skipped because they now exist remotely).
- Guest notice on web: «Гостевой режим: всё хранится на этом устройстве. В браузере очистка данных сайта удалит их — делайте экспорт.»; a quota error on save shows «Не хватает места в браузере: уменьшите портрет или сделайте экспорт.»

- [ ] **Step 1: Failing tests:** `guestCharactersMergeIntoRemote` (remote [A], guest [B, C] → [A, B, C], active = guest active); `clashingGuestIdIsRenumbered`; `alreadyUploadedGuestIsNotDuplicated` (guest B with an id present remotely → no second copy).
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** merge + web wiring.
- [ ] **Step 4: Run.** Expected: PASS. Compile web.
- [ ] **Step 5: Commit** `feat(web): guest mode in browser storage; guest characters join the account at sign-in`.

### Task 7: Platform wiring for Part 1 and removing the old account and character screens

**Files:**
- Modify: desktop `Main.kt`, web `web/Main.kt`, Android `DublApp.kt`:
  - start gate (`furyShowsLogin`) before the shell, login as a page; «Войти» for guests opens it with `onBack`;
  - the shell gets `accountSlot = { FuryAccountPanel(...) }`;
  - the Персонажи section renders `DublCharactersScreen` (Android: the «Ещё» section becomes «Персонажи»);
  - file pickers wired to `onImportFile` / `onExportFile` (existing platform pickers).
- Delete: desktop, web and Android `CharactersScreen.kt`; the web `LoginScreen` in `web/Main.kt`; account card code; helpers only they use.
- Python guards: repoint function guards to the shared files; retire pure layout pins. Ledger each.
- Create: `tools/tests/test_login_characters_shared.py`.
- Modify: `CHANGELOG.md` (stage 6 part 1).

- [ ] **Step 1: Failing guard:**
  - every platform calls `FuryLoginScreen(` and `DublCharactersScreen(`;
  - the three old `CharactersScreen.kt` files are gone;
  - `FuryShell.kt` has `accountSlot`;
  - `GuestMerge.kt` holds `mergeGuestIntoRemote`.
- [ ] **Step 2: Run** it. Expected: FAIL.
- [ ] **Step 3: Wire, delete, repoint.**
- [ ] **Step 4: Run** full checks (BUILD SUCCESSFUL) and the Python diff (no new ids).
- [ ] **Step 5: Screens** desktop 1440 and phone 390: login errors → «Играть без входа» → Персонажи; archive and «Вернуть»; «Дублировать»; menu panel.
- [ ] **Step 6: Commit** `feat: login first, account in the menu, shared Персонажи`; push and wait for CI (part 1 is shippable on its own).

## Part 2 — Мастер

### Task 8: `MasterData` model, codec and stores

**Files:**
- Create: `shared/…/dubl/master/MasterData.kt` (model + codec), `…/dubl/master/MasterStore.kt` (interface, in-memory impl)
- Create: desktop `DesktopMasterStore` (`master.json` in the data folder, temp file + atomic move like `DesktopCharacterStore`), Android (`filesDir/master.json`), web (localStorage `fury.master`).
- Test: `MasterDataCodecTest.kt`

**Interfaces:**
- `data class MasterEnemy(val id: String, val name: String, val defense: Int, val reflexes: Int, val initiative: Int, val health: Int, val endurance: Int, val attacks: String, val notes: String)`
- `data class FightEntry(val uid: String, val name: String, val kind: FightKind, val initiative: String, val hp: Int?, val hpMax: Int?, val conditions: List<String>, val initiativeBonus: Int?, val note: String)`; `enum class FightKind { PLAYER, ENEMY }`
- `data class FightState(val entries: List<FightEntry>, val round: Int, val turnUid: String?)`
- `data class MasterNote(val id: String, val title: String, val text: String, val date: String)`
- `data class MasterData(val version: Int = 1, val enemies: List<MasterEnemy>, val fight: FightState, val notes: List<MasterNote>)`
- `object MasterDataCodec { fun encode(data: MasterData): String; fun decode(raw: String?): MasterData }`: unknown or broken input → empty data, never a crash.
- `interface MasterStore { fun load(): MasterData; fun save(data: MasterData) }`

- [ ] **Step 1: Failing tests:** `roundTripKeepsEverything`; `brokenJsonGivesEmptyData`; `unknownFieldsAreIgnored`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** model, codec, stores.
- [ ] **Step 4: Run.** Expected: PASS. Compile.
- [ ] **Step 5: Commit** `feat(master): local GM data and stores`.

### Task 9: Fight tracker model

**Files:**
- Create: `shared/…/dubl/master/FightModels.kt`
- Test: `FightModelsTest.kt`

**Interfaces:**
- `fun fightOrder(state: FightState): List<FightEntry>`: by integer initiative, highest first; blank or non-numeric last; stable for ties.
- `fun nextTurn(state: FightState): FightState`: the turn moves to the next in `fightOrder`; after the last, round + 1 and back to the first; an empty fight is unchanged.
- `fun addEnemy(state: FightState, enemy: MasterEnemy, newUid: String): FightState`: name «<враг> N» (N = how many entries already start with that name + 1), hp = hpMax = health, bonus = enemy.initiative, kind ENEMY.
- `fun addPlayer(state: FightState, name: String, bonus: Int?, newUid: String): FightState`
- `fun changeHp(state: FightState, uid: String, delta: Int): FightState` (clamped 0…max); `fun toggleCondition(state: FightState, uid: String, condition: String): FightState`
- `fun rollInitiative(entry: FightEntry, d6: () -> Int): Int` = d6() + d6() + (bonus ?: 0)
- `fun newFight(): FightState` = empty, round 1.
- `fun masterAfterSignOut(data: MasterData): MasterData = data` (Review Focus 2: GM data is never touched by sign-out; the test pins it).

- [ ] **Step 1: Failing tests:** `fightOrderHandlesBlanksAndTies` ([5, "", 7, 5, "-"] → 7, 5(first), 5(second), "", "-"); `nextTurnWrapsAndCountsRounds`; `nextTurnOnEmptyFightDoesNothing`; `enemyCopiesAreNumbered` (two goblins → «Гоблин 1», «Гоблин 2»); `hpIsClamped`; `rollUsesTheBonus` (d6 = {3,4}, bonus 3 → 10; bonus null → 7); `masterDataSurvivesSignOut`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(master): fight tracker model`.

### Task 10: Enemies, notes and rules lookup models

**Files:**
- Create: `shared/…/dubl/master/MasterModels.kt`, `…/dubl/master/RulesLookup.kt`
- Test: `MasterModelsTest.kt`, `RulesLookupTest.kt`

**Interfaces:**
- Enemies: `fun duplicateEnemy(e: MasterEnemy, newId: String): MasterEnemy` («<имя> (копия)»); `data class EnemyForm(...)` with `errors()` («Нужно имя»; numbers must be whole) and `toEnemy(base)`.
- Notes: `fun searchNotes(notes: List<MasterNote>, query: String): List<MasterNote>` (title + text, case- and ё-insensitive, newest first by list order); `fun newNote(id: String, today: String): MasterNote`.
- Lookup: `enum class LookupType(val title: String) { PERK("Навыки"), SPELL("Заклинания"), GEAR("Снаряжение"), CONDITION("Состояния"), SKILL("Умения") }`; `data class LookupEntry(val id: String, val type: LookupType, val name: String, val subtitle: String, val text: String)`; `fun lookupEntries(development: DevelopmentCatalog, magic: MagicEquipmentCatalog, conditions: ConditionCatalog, skills: List<SkillDefinition>): List<LookupEntry>` (built once); `fun searchLookup(entries: List<LookupEntry>, query: String, type: LookupType?): List<LookupEntry>` (name matches first, then text matches; at most 200).
- Gear entries reuse `gearLineText` and `gearDescriptionExtra`; spells reuse the stage 5 spell detail facts.

- [ ] **Step 1: Failing tests:** `enemyFormRejectsBadInput`; `noteSearchIgnoresCaseAndYo`; `lookupFindsAcrossTypes` (a fixture catalog: «гор» finds the condition «Горение» before a spell mentioning горение in its text); `lookupTypeFilter`; `lookupCapsResults`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(master): enemies, notes and rules lookup models`.

### Task 11: Party models and the read-only character view

**Files:**
- Create: `shared/…/dubl/master/PartyModels.kt`, `shared/…/dubl/ui/master/ReadOnlyCharacterView.kt`
- Test: `PartyModelsTest.kt`

**Interfaces:**
- `data class PartyRow(val userId: String, val nickname: String, val characterName: String, val hp: Pair<Int, Int>, val endurance: Pair<Int, Int>, val mana: Pair<Int, Int>?, val defense: Int, val reflexes: Int, val initiative: Int, val conditions: List<String>, val updatedAt: String)`
- `fun partyRows(dashboard: CampaignDashboard, conditions: ConditionCatalog): List<PartyRow>` (values from the same `DublCharacter` getters the sheet uses).
- `data class ReadOnlyView(val name: String, val vitals: List<Pair<String, String>>, val derived: List<Pair<String, String>>, val skills: List<Pair<String, String>>, val perks: List<String>, val spells: List<Pair<String, String>>, val gear: List<Pair<String, String>>)`
- `fun readOnlyView(member: CampaignMember, development: DevelopmentCatalog): ReadOnlyView`: built from the sheet's models (`sheetMetrics`, ranked skills, `sheetGear`, learned spells). A perk id missing from the GM's catalog shows as «<id> · нет пакета».

- [ ] **Step 1: Failing tests:** `partyRowsReadVitalsAndConditions`; `readOnlyViewToleratesMissingPack`; `readOnlyViewListsLearnedSpellsAndCarriedGear`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** models + the view composable (cards, no buttons).
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(master): party rows and read-only character view`.

### Task 12: Мастер screen

**Files:**
- Create: `shared/…/dubl/ui/master/MasterScreen.kt` (`DublMasterScreen(store: MasterStore, account: FuryAccount, catalogs: MasterCatalogs, onSignIn: () -> Unit)`), `…/master/FightTab.kt`, `EnemiesTab.kt`, `NotesTab.kt`, `LookupTab.kt`, `PartyTab.kt`, `EnemyFormDialog.kt`
- `class MasterState` (remembered): loads `MasterData` once, saves on every change, keeps the open tab, queries and undo snapshot for the toast («Вернуть» after delete/remove/«Новый бой»).
- Test: `MasterStateTest.kt`

**Interfaces:**
- `class MasterState(store: MasterStore) { var data: MasterData; fun update(change: (MasterData) -> MasterData, undoable: String? = null); fun undo(): Boolean }`
- Layout and copy per the mockup boards Master/MasterParty/PhoneMaster. Партия when signed out: «Для партии нужен аккаунт» card + «Войти».

- [ ] **Step 1: Failing tests:** `everyChangeIsSaved` (update → store holds it); `undoRestoresLastUndoable`; `nonUndoableChangesClearUndo`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** state + tabs.
- [ ] **Step 4: Run.** Expected: PASS. Compile.
- [ ] **Step 5: Commit** `feat(master): Мастер screen`.

### Task 13: Replace Кампания with Мастер

**Files:**
- Modify: desktop `Main.kt`, web `web/Main.kt`, Android `DublApp.kt`: sections Лист · Мастер · Персонажи; Мастер renders `DublMasterScreen` with the platform `MasterStore`.
- Delete: desktop, web and Android `CampaignScreen.kt` and helpers only they use.
- Python guards: repoint campaign function guards to `PartyTab.kt` / `CharactersScreen.kt` (join/leave), retire layout pins; ledger each.
- Create: `tools/tests/test_master_replaces_campaign.py`.
- Modify: `CHANGELOG.md` (stage 6 part 2).

- [ ] **Step 1: Failing guard:**
  - no `CAMPAIGN` section on any platform;
  - the three `CampaignScreen.kt` files are gone;
  - `PartyTab.kt` calls `createInvite`, `removeMember`, `dashboard`, `createCampaign`;
  - `JoinCampaignDialog.kt` calls `joinCampaign`;
  - `MasterData.kt` holds `MasterDataCodec`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Wire, delete, repoint.**
- [ ] **Step 4: Run** full checks and the Python diff.
- [ ] **Step 5: Commit** `feat: Мастер replaces Кампания`.

### Task 14: Verify, review, ship

- [ ] **Step 1: Screens** at desktop 1440 and 1100, tablet 834, phone 390, plus a dark and the Пергамент theme.
  - Login: errors, guest, sign in.
  - Account: conflict badge, «Выйти» with pending changes.
  - Персонажи: archive, duplicate, export/import, join by code.
  - Мастер: fight order and turns, enemies to fight, notes search, lookup, Партия and the read-only view.
- [ ] **Step 2:** Full local checks plus the Python diff.
- [ ] **Step 3:** One fresh reviewer (subagent, opus) over the diff since this plan's base, with the spec, this plan and the Review Focus. Fix Critical/Important with RED→GREEN tests; ledger minors.
- [ ] **Step 4:** Push; wait for all 5 workflows green. Update PR #32 with a stage 6 section.
