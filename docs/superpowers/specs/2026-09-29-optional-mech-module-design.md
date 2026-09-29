# Опциональный модуль мехов для Fury Book — архитектурная спецификация

**Статус:** переписано после архитектурного ревью  
**Источник правил:** «Мехбук — правила мехов для DUBL 3.69 (плейтест v0.5)»  
**Область:** встроенный необязательный DUBL-модуль для Android и Desktop

## Цель

Добавить мехов в Fury Book как настоящий optional FCP, не превращая их в специальный режим приложения.

При активном пакете игрок получает каталог мехов, отдельный ростер, конструктор и игровой экран. При выключенном пакете меховая capability, навигация, каталоги и механики полностью исчезают из активного runtime, но сохранённые мехи остаются на диске и возвращаются после повторного включения.

Обычный `DublCharacter`, его snapshot и `dubl.character` transfer-формат не получают меховых полей.

## Архитектурный контракт

Эта фича обязана следовать тем же границам, что и остальная FCP-архитектура Fury Book.

### Fury Book host

Host владеет только общими механизмами:

- загрузкой и composition FCP;
- dependency/activation lifecycle;
- безопасными UI surface/component families;
- навигационным контейнером;
- платформенным Compose-рендерингом;
- общими persistence primitives.

В host/core не добавляются проверки вида `if (pack == "dubl-mechs-3.69")`, `if (ruleset == "dubl")` или отдельные меховые правила.

### DUBL adapter

DUBL-слой владеет семантикой мехов:

- typed catalog models;
- интерпретацией binding `dubl.mechs`;
- правилами сборки и игры;
- связью с `DublCharacter` как с пилотом;
- использованием существующего DUBL roll pipeline;
- shared render/application models для Android/Desktop.

Сложные правила остаются исполняемым кодом DUBL adapter. FCP не содержит Kotlin/JS/Python и не становится вторым rules engine.

### FCP

`dubl-mechs-3.69` владеет декларативными данными и объявляет, какую capability/UI contribution он добавляет.

Пакет:

- зависит от `dubl-3.69 / 3.69`;
- является bundled optional pack;
- не включается автоматически только потому, что установлен;
- содержит typed mech content;
- объявляет поддержанный host UI contribution для мехового module destination;
- не содержит platform-specific layout code.

Внутренний `modules[].enabledByDefault` не используется как флаг включения самого FCP. Активность пакета определяется общей `FcpComposition` и persisted enabled pack IDs, как у остальных optional FCP.

## Источник истины

Мехбук v0.5 — источник меховых чисел и правил. DUBL 3.69 остаётся источником общих DUBL-механик, на которые мехбук явно ссылается.

До переноса каталога в JSON должен быть закреплён точный исходный артефакт мехбука: файл/версия и контрольная сумма либо другой однозначный repository reference. Агент не имеет права восстанавливать числа по памяти, описаниям задач или предыдущему сгенерированному коду.

Если мехбук и DUBL противоречат друг другу или формулировка неоднозначна, реализация не придумывает трактовку. Такой кейс фиксируется отдельным вопросом.

## FCP contract

Bundled pack ID:

`dubl-mechs-3.69`

Минимальные typed entry families:

- `dubl.mechs.frames`;
- `dubl.mechs.engines`;
- `dubl.mechs.cores`;
- `dubl.mechs.weapons`;
- `dubl.mechs.systems`;
- `dubl.mechs.maneuvers`.

Если мехбук требует отдельной структурированной сущности, она добавляется отдельным typed entry kind. Runtime не извлекает числа, ограничения или action costs из prose.

Каждая запись имеет стабильный ID. Saved builds хранят IDs, а не копии catalog objects.

### UI contribution

Меховый пакет должен объявлять navigation/module contribution через существующий FCP UI contract, расширенный общей host capability, например:

- surface: `app.modules`;
- component: `module-page`;
- binding: `dubl.mechs`;
- label/icon/order: manifest-owned presentation.

Точное имя surface/component можно скорректировать при реализации под существующие naming conventions, но принцип фиксирован: **появление мехового destination идёт из активной FCP composition, а не из platform-specific проверки pack ID**.

Shared DUBL adapter переводит raw binding в typed feature/render model. Android/Desktop получают typed model и не знают строку `dubl.mechs`.

Pack manager только включает/выключает пакет. Он не является основным входом в меховый UI.

## Activation lifecycle

При включении:

1. общий pack activation path проверяет manifest/dependencies/content;
2. `FcpComposition` включает pack;
3. DUBL adapter регистрирует меховую capability, typed catalogs и module contribution;
4. host монтирует меховый destination;
5. сохранённый mech roster становится доступен приложению.

При выключении:

1. pack исчезает из active composition;
2. меховый destination закрывается, если был открыт;
3. меховые catalogs/render models/mechanics больше не доступны активному UI;
4. persisted mech records остаются нетронутыми;
5. `DublCharacter` и обычный character runtime не изменяются.

Не должно существовать отдельной второй истины вроде `mechPackEnabled`, если состояние уже выражено `FcpComposition.isActive(packId)`.

## Domain model

### MechDraft

Конструктор работает с отдельным `MechDraft`, где выборы могут быть незавершёнными.

Это позволяет:

- создавать мех постепенно;
- показывать validation по мере сборки;
- не заполнять фиктивными IDs обязательные поля;
- не путать незавершённый UI draft с сохранённым валидным build.

### MechBuild

`MechBuild` — сохранённая mechanically valid сборка со стабильными component IDs и текущим игровым состоянием.

Сборка не сохраняется, если нарушает ограничения мехбука. При редактировании существующего меха невалидный draft не перезаписывает последнюю валидную версию.

Оператор — отдельная ось от валидности сборки:

- к build можно привязать `pilotCharacterId`;
- при наличии поддержанного мехбуком AI system управление может быть передано ИИ;
- mechanically valid мех без пилота/доступного ИИ может храниться в ростере как `UNCREWED`, но не получает operator-dependent checks.

Удаление DUBL-персонажа не удаляет мех.

## Shared rules

Все формулы и transitions находятся в common DUBL code. Платформы не дублируют их.

Shared rules отвечают за:

- derived stats;
- mount/class/load/capacity/energy validation;
- weapon/system compatibility;
- pilot/mech checks;
- direct connection, если она явно определена источником;
- AI operator profile и handoff, если они явно определены источником;
- action points/reactions;
- energy/ammunition;
- heat/stress/structure;
- damage/armor penetration;
- repair;
- DUBL roll/follow-up/target comparison integration.

`MechRules` должен быть детерминированным и тестируемым отдельно от UI/storage.

## Application boundary

UI не получает право произвольно записывать `MechCombatState`.

`MechApplication` — единственная shared boundary для изменения сохранённого меха. Она предоставляет typed commands, например:

- create/edit/save draft;
- delete/copy/rename;
- bind/unbind pilot;
- hand off control;
- start turn;
- perform weapon/maneuver/system action;
- apply damage/heat;
- repair;
- typed manual correction tracked resource, если такая коррекция нужна UX.

Каждая команда проверяет инварианты и только потом сохраняет результат.

Публичного `updateCombatState(id, arbitraryState)` или аналогичного raw setter быть не должно.

## Persistence

Мехи хранятся отдельно от DUBL character snapshot и character transfer/export.

Shared code владеет versioned codec для одной mech card и metadata/index contract. Platform stores владеют только физическим I/O.

Хранилище должно быть record-oriented либо давать эквивалентную изоляцию ошибок:

- повреждение одного меха не мешает загрузке остальных;
- нечитабельная запись не удаляется автоматически;
- сохранение другого меха не перезаписывает и не теряет повреждённую запись;
- unknown IDs/version errors возвращаются как typed load problem;
- пользователь может явно удалить или заменить повреждённую карточку.

Desktop использует отдельное mech storage namespace под существующим data directory. Android использует отдельное app-local mech storage namespace. Ни один из них не читает и не переписывает `DublApplication` snapshot.

## UX

Android остаётся UX reference. Desktop повторяет ту же информационную архитектуру, порядок действий и возможности; responsive layout может отличаться.

При активном pack пользователь имеет first-class меховый destination, полученный из typed module contribution. Он не должен быть спрятан как специальная кнопка внутри строки FCP manager.

Меховый раздел содержит:

1. roster;
2. создание/редактирование;
3. builder с понятным validation;
4. mech sheet/play view;
5. pilot/operator state;
6. tracked combat resources;
7. доступные действия/проверки с DUBL-style breakdown.

Конкретное размещение destination в compact Android navigation остаётся host-owned UI решением. Важный контракт — обе платформы получают один и тот же typed module destination из FCP composition, а не две независимые ручные интеграции.

## Failure behavior

- Несовместимый или неполный bundled mech pack не активируется частично.
- Unknown component IDs не превращаются в нули/default objects.
- Невалидный edit не уничтожает сохранённый build.
- Ошибка одной mech card не уничтожает остальные.
- Missing pilot оставляет mech record живым.
- Выключение FCP не удаляет мехов.
- Если активный mech destination исчез из composition, UI возвращается в безопасный host destination.
- Никакая меховая ошибка не мигрирует или не чинит `DublCharacter` автоматически.

## Scope

В этот этап входят:

- bundled `dubl-mechs-3.69`;
- typed catalog;
- shared build/rules/application;
- отдельное безопасное persistence;
- roster/builder/play screen;
- DUBL pilot binding;
- поддержанные источником AI/control rules;
- pack-driven module UI;
- Android implementation first;
- Desktop parity;
- tests и FCP documentation.

Не входят:

- произвольные внешние mech FCP;
- executable code inside FCP;
- сторонние mech format converters;
- изменение `dubl.character` transfer;
- tactical battle map/GM scene automation;
- универсальная RPG/mech nullable model в Fury core.

## Verification

Обязательные проверки:

- canonical source fixture/golden examples привязаны к точной версии мехбука;
- catalog parser rejects missing/duplicate/invalid entries;
- `FcpComposition` активирует мехи только при enabled pack ID и корректной dependency;
- module destination появляется/исчезает через FCP UI contribution;
- disabled module оставляет persistence нетронутым;
- shared rules покрыты golden tests;
- commands не позволяют обойти action/resource invariants;
- invalid draft не перезаписывает valid build;
- corrupted record изолирован;
- missing pilot/AI cases определены;
- Android проходит полный flow первым;
- Desktop повторяет тот же flow через те же shared application/rules/render models;
- полный Android/Desktop build и существующие FCP tests проходят без regression.

## Non-negotiable review checks

Перед merge проверить четыре вопроса:

1. Можно ли удалить `dubl-mechs-3.69` из active composition и получить приложение без единого мехового UI/runtime следа, сохранив данные на диске?
2. Можно ли добавить следующий optional DUBL pack без копирования меховых `if/when` по Android/Desktop?
3. Может ли UI изменить боевой state в обход shared commands/rules?
4. Может ли повреждение одной mech card уничтожить другую?

Если ответ на любой вопрос «да» в плохом смысле — граница реализована неправильно.
