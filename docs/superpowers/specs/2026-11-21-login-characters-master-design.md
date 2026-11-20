# Login, Персонажи and Мастер (revamp stage 6)

Status: draft for owner approval · 2026-11-21
Builds on: `2026-11-17-ui-revamp-design.md` (stage list, leftovers) and stages 2–5 (shared screens, the editor, the sheet).

## Why

Кампания was a first idea that never became useful, and login only exists on web: desktop and Android sign in from a card buried in Персонажи. The three platforms have also drifted apart:
- **Web** has a sync log and «Профиль», but no sign-up or password reset in the app.
- **Desktop and Android** have sign-up and reset but no log.
- **Android** calls the section «Ещё».

What the owner wants:
- **Login first,** on every platform, with an option to **play without an account** and save on the device.
- **A GM tool anyone can use.** "Anyone can decide that they want to GM at one point", so it must work **offline and without an account**. It is mainly a place to see players' sheets, plus useful session tools.
- **Персонажи** is simple: switch characters, look at older ones, export them, plus something useful.
- **The server is a last resort,** mainly for logging in. **No new server functions** this stage.
- **Dice are a backup:** at the table almost everything is rolled with real dice. Nothing is built around app rolls.

## Goals

- **Login screen first** on Android, desktop and web, with «Играть без входа». Guests save locally on every platform, web included.
- **One «Мастер» section replaces Кампания:**
  - Бой, Враги, Заметки, Справочник work offline with no account;
  - Партия shows players' synced sheets and manages invites, using only the existing campaign server functions.
- **Персонажи:** a list with «Архив», «Дублировать», import/export, content packs, and joining a campaign by code on a character.
- **Account controls** (nickname, sync status, conflicts, «Войти»/«Выйти») move to the app menu.
- **Navigation:** Лист · Мастер · Персонажи on all platforms. Phones keep no bottom bar (stage 5).

## Non-goals

- **No new server functions, tables or migrations.** Character sync stays exactly as it is.
- **Dropped (owner):** XP awards to players and handouts.
- **Dropped (owner):** group checks and other roll-centred GM features (dice are a backup).
- **Not changed:** rules, the sheet, the editor, the transfer file format and character sync payloads.
- **Not moved:** the desktop data folder on Windows (still `~\.local\share\dubl-character`; moving it means migrating saves; a later stage).

## Anti-goals

- **Any function today's Персонажи, Кампания or web login has becoming unreachable.**
- **GM data going to the server,** or a GM tool that needs an account or internet (except Партия).
- **A guest losing characters** when they sign in later.
- **Restyling the stage 2–5 design.** New screens use the existing parts and the five themes.

## Approaches

1. **Picked (one sensible way):** shared screens in `shared` that adapt by width, with platforms keeping only their wiring (auth, storage, file pickers). It is the same split as stages 3–5.
   - GM data lives in a **separate local store** per device behind one shared interface: a JSON file next to the characters on desktop, app-private storage on Android, browser storage on web.
2. **Rejected:** GM data inside the characters' save data. That would put it into sync and the transfer format, so it needs server work and a saved-data change.
3. **Rejected:** keeping Кампания and adding a separate Мастер (owner chose one section). It gives the GM two places to switch between mid-session.

## Where each function goes

| Today | New home |
|---|---|
| Web login screen (sign in, sign up with nickname) | Shared login screen, all platforms |
| Desktop/Android account card: sign in, sign up, «Забыли пароль?» | Shared login screen |
| Nickname edit (web «Профиль»), sync status, «Синхронизировать», «Выйти» | Menu → account panel |
| Conflict «Сохранить локальную» / «Загрузить облачную» | Menu → account panel (also a badge on the menu button when a conflict waits) |
| Web sync log «Последние синхронизации» | Account panel, collapsed «Журнал синхронизации» |
| Character list: open, create, delete (with confirm) | Персонажи |
| Import / export character file | Персонажи |
| Content packs: import .fcp, on/off, status lines | Персонажи → «Пакеты контента» |
| Campaign: join by code with a character, leave | Персонажи → the character's «Кампании» |
| Campaign: list, create, invite code, remove player, GM Screen sheets | Мастер → Партия |
| Android «Ещё» section | Персонажи (the section is renamed) |

## Design

### Login (first screen)

- Shown on **first launch** and **after «Выйти»**. Otherwise the app opens straight to the sheet: signed in, or as a guest, as last time (builder's call). The choice is stored per platform: Android prefs, a small file on desktop, localStorage on web.
- **Fields and actions:**
  - email, password, «Войти»;
  - «Создать аккаунт» (email, password, nickname 2–32 characters; the «подтвердите email» message);
  - «Забыли пароль?»;
  - **«Играть без входа»**.
- The same validation and messages the desktop card has today («Введите корректный email.», «Минимум 8 символов.», …).
- **A guest signs in later** from the menu (or Персонажи): the login screen opens as a dialog or page.
  - **Desktop and Android:** the guest's local characters join the account. Their first sign-in already links local characters (`firstLinkAllowed`).
  - **Web:** today it keeps nothing locally. Guest characters must be uploaded into the account through the existing save path before the remote pull, never replaced by it. *Builder:* show what will happen («N персонажей будут добавлены в аккаунт») and keep a local copy until the upload succeeds.
- **Web guest mode:** characters, extras and GM data persist in localStorage. A one-line notice in guest mode warns that clearing site data deletes them and points to export.
- **Offline at start:** a signed-in user opens the app offline exactly as today (desktop and Android keep local copies). Web signed-in behaviour stays as it is.

### Menu (all platforms)

- Account panel:
  - **signed in:** nickname (editable), email, sync status, «Синхронизировать», conflicts, «Журнал синхронизации», «Выйти»;
  - **guest:** «Войти или создать аккаунт».
- «Сменить персонажа» and theme stay.

### Персонажи

- **List of characters** (excluding archived): name, concept, XP and a portrait if set. The active one is marked. Tap → make active and go to the sheet.
- **Actions:** «Новый» (opens the editor at Основа, as today), «Импорт» (file), and per character «Экспорт», «Дублировать», «В архив», «Удалить» (confirm, as today).
- **«Архив · N»** shows archived characters with «Вернуть из архива» and «Удалить».
  - *Builder's call:* archive is a **local list of character ids per device**, not synced and not a saved-data change.
  - An archived character can't be active. If the active one is archived, the next unarchived character becomes active, or a new one is made when none is left.
- **«Дублировать»** makes a copy with a new id and name «<имя> (копия)», with the same extras. It is a new application method (`CharacterApplication.duplicate`). Sync treats it as a new character.
- **Кампании on a character** (signed in only):
  - the campaigns this character is linked to;
  - «Вступить по коду» (code → `joinCampaign(code, characterId)`);
  - «Выйти из кампании».
- **«Пакеты контента»:** the existing .fcp import and on/off list, unchanged in function.

### Мастер

One section, five tabs. **Бой, Враги, Заметки and Справочник work offline, with no account.** Their data lives in the local GM store.

- **Бой (initiative tracker):**
  - Entries: name, initiative (typed), optional HP current/max, conditions (from the condition catalog plus free text) and a note.
  - Add an entry three ways: from Враги (a copy, so two goblins are two entries), as a quick typed name (a player), or from Партия members (name only; their real sheet stays theirs).
  - Sorted by initiative, highest first. Ties keep insertion order, and the GM can drag or nudge to reorder.
  - «Раунд N», «Следующий» moves the turn marker and bumps the round after the last entry; «Новый бой» clears.
  - An optional «Бросить» per entry rolls 2d6 + its initiative bonus. It is never required.
  - HP and conditions here are the GM's; they never touch a player's sheet.
- **Враги (bestiary):** a list of enemy templates.
  - Fields: name, Защита, Рефлексы, Инициатива, Здоровье, Выносливость, attacks/abilities (text), notes.
  - «Новый», «Дублировать», «Удалить» (with «Вернуть»), «В бой».
  - *Builder's call:* typed numbers only, with no derivation from attributes.
- **Заметки:** dated entries (title, text, date). Newest first, with search. «Новая заметка» gets today's date, which can be edited.
- **Справочник:** one search over the rulebook catalogs already loaded (perks, spells, gear, conditions, skills), with type chips. A result opens a read-only card using the same detail layouts as the editor and sheet.
- **Партия (needs an account and internet):**
  - **Signed out or offline:** a short message and «Войти».
  - **Signed in:**
    - campaigns you own («Создать кампанию», «Пригласить» → code, «Убрать игрока»);
    - for the chosen campaign, a **party table**: per player, nickname, character, HP/Выносливость/Мана, Защита/Рефлексы/Инициатива, active conditions and «обновлено». Tapping a row opens the player's sheet read-only, as the GM Screen does today;
    - «Обновить».
  - Campaigns you're a player in show here read-only too, without GM data. The player side lives in Персонажи.
- **GM store:** one shared interface (`MasterStore`: load/save a `MasterData` of enemies, tracker state and notes). It is versioned JSON and saved atomically (temp file then swap, as character saves do), and it isn't synced or exported.

### Navigation

- Sections **Лист · Мастер · Персонажи** on desktop, web and Android. Phones: no bottom bar; the sections and the account panel are in «Меню».
- Delete the per-platform `CampaignScreen.kt` and `CharactersScreen.kt` (desktop, web, Android), the web login screen and the account cards, plus anything only they use.

### Build order

Two parts, each buildable and pushed on its own:
1. **Login + menu account panel + Персонажи** (including web guest storage).
2. **Мастер** (store, Бой, Враги, Заметки, Справочник, Партия) and removing Кампания.

## Constraints

- **Shared code only:** platforms keep auth wiring, storage, file pickers and window chrome.
- **Server:** only the calls that already exist (auth, character sync, campaign functions).
- **Keep the existing design:** new screens are built from the stage 2–5 parts (`EditorCard`, `EditorRow`, `EditorChip`, `FuryDialogScaffold`, the sheet's cards) in the five themes, at 360dp, tablet and desktop.
- **Python guards:** guards pinning the old screens are repointed when they guard a function and retired when they pinned old layout. The failing baseline may only shrink.
- **Mockup first:** a Design canvas for login, Персонажи, Мастер (Бой, Враги, Партия) and the menu account panel, before the plan (owner asked for this in stage 5).

## Left to the builder

- Exact layouts and copy for empty states.
- `MasterData` field names and its JSON schema version.
- Where «Войти» appears for guests besides the menu.
- How the web guest upload reports progress.
- Test file layout.
