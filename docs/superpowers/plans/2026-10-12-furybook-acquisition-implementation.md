# Fury-Book Acquisition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать только этап 1: строгое приобретение всех подтверждённых XP/ОСп-сущностей, точные новые платежи и сохранение старого баланса на Android/Desktop/Web.

**Architecture:** Типизированные требования и цены FCP/DUBL используются общей оценкой, планировщиком и финальной проверкой применения. Персонаж, финансовый журнал, receipts и очередь sync образуют одну сохраняемую единицу; платформы предоставляют устойчивое хранилище и UI. Существующие формулы эффектов, режимы боя и броски не переписываются.

**Tech Stack:** Kotlin2.4.20, KMP/commonTest, Compose1.12.0, JDK17, Python3/pytest, Android Gradle Plugin9.3.0, Android minSdk26/compileSdk37, существующий Kotlin/JS и Supabase transport. Новые тестовые зависимости добавлять только в указанной задаче; согласовывать с текущим BOM, не обновлять платформенный стек заодно.

**Spec:** [Утверждённая спецификация версии 1](../specs/2026-10-12-furybook-acquisition-design.md), [roadmap версии 1](../specs/2026-10-12-furybook-automation-roadmap.md). Пользователь утвердил документы 2026-10-12,10:48UTC. База продукта `f47a5256a66ddc5e1b7b5c1e204e57279fc7227f`; документы в локальной ветке `docs/automation-design-2026-10-12`, коммит `0444462af10300f8f05cca717b84c538e14fc8b6`. Этот план ещё требует review и выбора исполнения; его создание не запускает реализацию.

## Global Constraints

- «Старым персонажам сохраняется текущий баланс без выдуманной истории.» Незавершённое создание тоже мигрирует один раз без скидки Самоучки на прошлые платежи.
- «Неизвестное условие/цена никогда не означают „да“/0.» Неизвестные правила остаются блокерами конкретных переходов.
- «Платформенные адаптеры обеспечивают хранилище и транспорт, не решают игровые правила.» DUBL-код — `com.furybook.dubl.*`; generic FCP — `com.furybook.content`.
- «Каждый допустимый ранг и переход между рангами, вариант доступа/оплаты, новый или повторный экземпляр специализации» и скидка/стадия требуют собственного независимого эталона цены и остатка (§7).
- «В этапе 1 нет автоматического суммирования ledger или двух расходов из одного баланса.» Конфликт офлайн-ветвей сохраняет обе версии и требует выбора.
- Источник → утверждённые resolutions → shared → UI. Канонический контент только через FCP manifest; сохранять provenance. Сметание→Бычий натиск 2 не конфликт. Scavenger Mage вне области.
- Не реализовывать действия/эффекты, таймеры, боевой трекер, два новых режима или броски этим планом. Не менять applicationId/ruleset ID, не переносить код в portable fallback.
- Ни серверные изменения, ни их развёртывание не считать уже выполненными. Выпуск нового cloud-write формата требует отдельных доказанных guards всех writers/readers.
- Сейчас разрешён только этот документ и локальный docs-only commit; кодовые шаги ниже — будущая работа после review. Нет push/PR/deploy без соответствующего разрешения.

## Review Focus

1. **Сбой после durable commit до ответа/двойной tap/повтор sync:** одна операция и один расход, повтор после Undo не покупает снова (T10,T11,T15,T16).
2. **Старый или будущий формат/потерянный ledger/старый клиент:** запрет записи/понижения, сохранение исходных байтов, не новый пустой персонаж (T08,T12–T16).
3. **Смена стадии/Самоучка/недоступный старый каталог:** точный opening balance, отсутствие повторной миграции и переоценки (T06,T09,T11).
4. **Смена персонажа, import-клон и одинаковый operation ID с другим содержимым:** нельзя применить или вернуть чужую операцию, новый независимый клон не разделяет sync-идентичность (T08,T10,T15,T18).
5. **Переполнение/отрицательный баланс/неизвестная цена и общий предшественник:** валидная арифметика, unknown≠0, без двойной оплаты/бесплатного legacy-refund (T01,T06,T07,T09,T11).

---

## Проверенная среда, инструкции и команды

Checkout изолирован worktree; рабочее дерево до плана чистое. Прочитан CONTRIBUTING.md. В checkout и доступных `.agents`/`.codex` не обнаружены применимые AGENTS.md/SKILL.md. Повторить проверку при исполнении, если появятся новые инструкции. Предыдущие аудиты — материал воспроизведения, а не подтверждение работы нового кода.

Текущие Gradle-конфигурации дают `:shared:desktopTest`, `:desktopApp:compileKotlin`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:webApp:compileKotlinJs`, `:webApp:jsBrowserDevelopmentRun`. Android instrumented, Desktop Compose UI и Web browser suites ещё не настроены; задачи T13,T14,T18 создают их явно. Наличие Chromium/Android SDK в окружении не доказывает рабочий emulator/display/backend. UI-недоступность блокирует платформенную приёмку, не заменяется source grep.

Для JVM-теста команды ниже используют `./gradlew :shared:desktopTest --tests 'com.furybook.dubl.acquisition.<Class>'`; каталог тестов — `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/`. Новые pure-модели помещаются в `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/` с этим package. Для desktop-only загрузчика реальных FCP использовать `shared/src/desktopTest/kotlin/com/furybook/dubl/acquisition/`; общие тесты получают типизированные fixtures из независимого testdata, не читают JVM-файлы из commonMain.

Перед первым кодовым шагом: `git status --short`, `git rev-parse HEAD`, `./gradlew --version`, `./gradlew :shared:desktopTest :app:testDebugUnitTest`, `python3 -m pytest tools/tests/test_development_acquisition_planner.py -q`. Ожидаемый baseline последней Python-команды может падать: в текущем файле сохранены старые `com/dubl/character/android` пути; T01 исправляет воспроизводимость, не подавляет проверку. Все новые красные тесты должны падать по ожидаемому поведению/отсутствующему контракту, не из-за неразрешённых зависимостей среды.

## Файлы и единые интерфейсы между задачами

Названия ниже — предлагаемые новые контракты, не существующие API. Каждый тип принадлежит ровно одной задаче. Не создавать вторые одноимённые структуры на платформе. Минимальные DTO-поля здесь фиксируют границу; тела функций в план не включены.

|Владелец|Файл в `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/`|Контракт|
|---|---|---|
|T02|`RequirementModels.kt`|`RequirementNode` с nodeId/sourceRef и вариантами AllOf/AnyOf/Attribute/OwnedRank/SkillFamily/Specialization/BranchAccess/LearnedSpell/DistinctCount/CreationPhase/Exclusive/ExternalEvidence/Unresolved. `RequirementResult(status,witnesses,children,reason)`; статусы Satisfied,Unsatisfied,ChoiceRequired,SourceUnresolved,Conflict|
|T03|`AcquisitionIdentity.kt`|`DefinitionRef(packId,entryId)`, `AcquisitionInstance(instanceId,definition,rank,parameters,abilityOptionId,origin)`. `parameters: Map<String,String>` содержит стабильные ID, не display name; неизвестный legacy-параметр отсутствует, не wildcard|
|T04|`AcquisitionCatalog.kt`|`AcquisitionCatalog(definitions,catalogRevision,resolutionRevision)`, `AcquisitionDefinition(ref,kind,maxRank,repeatPolicy,requirements,priceRule,sourceStatus)`. kind: DEVELOPMENT,ATTRIBUTE,SKILL,MANA,SCHOOL,SPELL,CHI_BONUS; неизвестный custom тип только manual. `loadAcquisitionCatalog(packs: List<FcpContentPack>, includeChi: Boolean): CatalogLoadResult`; existing FcpContentPack и порядок claims/composition сохраняются|
|T05|`RequirementEvaluator.kt`|`evaluate(node: RequirementNode, context: AcquisitionContext): RequirementResult`; context=(character,instances,learnedSpellIds,creationPhase,externalEvidence,catalog), все поля immutable. Это единственное место смысловой оценки узлов|
|T06|`AcquisitionPricing.kt`|`quote(change: AcquisitionChange, context: AcquisitionContext): PriceResult`; Known(cost: Amount,sourceRef,discountEvidence) или Unknown(reason). `Amount(xp: Long,ap: Long)`; checked add/subtract, денежные Long сериализуются decimal strings, не JS Number. `AcquisitionChange(kind,definition,instanceId,fromRank,toRank,parameters,optionId)`|
|T07|`AcquisitionPlanner.kt`|`plan(request: AcquisitionRequest, state: StoredCharacter, catalog: AcquisitionCatalog): AcquisitionPlan`; request=(characterId,targets,choiceSelections,includeTargets); target=(definition,instanceId?,targetRank,parameters,optionId?). Plan=(request,baseRevision,catalogRevision,resolutionRevision,changes,questions,issues,cost,remaining,previewToken); previewToken — точная каноническая сериализация значимых полей, не нестабильный hashCode|
|T08|`AcquisitionLedger.kt`, `AcquisitionEnvelope.kt`|`StoredCharacter(character,extras,instances,ledger,receipts,outbox,syncMetadata,revision,storageRevision,creationPhase,formatVersion)`; `Ledger(opening,entries)`, Opening(migrationId,sourceFingerprint,balance,legacyStage,provenance); `LedgerEntry(operationId,type,changes,amountDelta,ruleVersions,reversesId?)`; `Receipt(operationId,requestCanonical,result: SavedAcquisitionResult,status,beforeRevision,afterRevision)`; status Applied/Undone. `SavedAcquisitionResult(cost,remaining,changes)` не содержит Receipt; `OutboxItem(operationId,parentServerRevision,payloadCanonical)` содержит payload без outbox/sync metadata; `SyncMetadata(serverRevision:Long,remoteConflictCanonical:String?)` хранится в envelope, не в игровом payload; provenance includes LegacyCalculated/UserConfirmed/NewCharacter. NewCharacter starts with explicit initial funding, not fabricated purchases|
|T08,T12–14|`AcquisitionStore.kt`|`suspend load(characterId: String): LoadResult`; `suspend compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult` = Committed/Conflict/StorageFailure. Запись включает ledger+receipt+outbox; публикация UI только после Committed. LoadResult=Loaded/MigrationRequired/Unsupported/Corrupt; исходные байты сохраняются при ошибке|
|T09|`LegacyAcquisitionMigration.kt`|`prepareLegacy(raw: String, origin: LegacyOrigin, legacyCatalog: LegacyCatalogSnapshot?): MigrationResult`; NeedsBalanceConfirmation/Ready/Unsupported/Corrupt. `confirmLegacyBalance(pending: PendingMigration, balance: Amount, reason: String): StoredCharacter`. Origin содержит прежний characterId, schema, sourceRevision и точные исходные байты; migration/instance IDs детерминированы относительно этого происхождения|
|T10|`AcquisitionService.kt`|`suspend preview(request: AcquisitionRequest): AcquisitionPlan`; `suspend apply(command: ConfirmAcquisition): ApplyResult`; command=(operationId,characterId,request,expectedRevision,previewToken). ApplyResult=Applied(receipt)/AlreadyApplied(receipt)/AlreadyUndone(receipt)/Stale(newPlan)/Blocked(issues)/StorageFailure/IdentityConflict. Зависимости constructor: store: AcquisitionStore,catalogProvider: () -> AcquisitionCatalog,idFactory: () -> String; каталог захватывается на всю операцию|
|T11|`AcquisitionUndo.kt`, `CharacterMutationCoordinator.kt`|`suspend undo(characterId: String, operationId: String, expectedRevision: Long): ApplyResult`; operationId здесь ID новой обратной команды, отменяемая последняя запись определяется ledger. `suspend mutate(command: CanonicalMutation): MutationResult`; command=(operationId,characterId,expectedRevision,kind,payload,reason?), kind NonFinancial/ManualCorrection/Grant/CompleteCreation/ConfirmLegacyParameter. Не покупочный путь не может менять финансовую проекцию незаметно|
|T15|`AcquisitionSyncProtocol.kt`|`suspend push(request: SyncEnvelopeRequest): SyncEnvelopeResult` через injected `AcquisitionCloudTransport`; request=(characterId,expectedServerRevision,operationIds,canonicalEnvelope,writerVersion=2,resolution:ConflictResolution?=null); result Accepted(serverRevision,operationIds)/Conflict(serverEnvelope)/Unsupported/Failure. `ConflictResolution(choice:LocalOrServer,expectedServerRevision:Long)` задаётся только явным подтверждением конфликта; capability `acquisitionLedgerV2` обязательна перед cloud migration|
|T17|`AcquisitionPresentation.kt`|`present(plan: AcquisitionPlan, catalog: AcquisitionCatalog): AcquisitionUiModel`; модель labels, требуемых выборов, цены/остатка, блокировок и receipts. Никаких вычислений цены в Compose|

`String`-ID всегда имеют область character/pack; `Long revision` — версия канонического персонажа для preview/Undo; `storageRevision` — версия записи envelope для CAS, растёт при любой записи, в том числе ack metadata. Server revision — отдельное поле. Sync ack увеличивает storageRevision, но не revision; игровое изменение увеличивает обе. `RuleSourceRef` содержит документ/вкладку/P и resolution ID. Canonical JSON определяет порядок ключей/числовые форматы в T08; безопасное сравнение токенов не зависит от локали. `DublCharacter`, `CharacterSheetExtras` и `AttributeId` используются существующие, без дублирования. В T04 `PriceRule` = Fixed/PerRank/TransitionTable/ByOption/Conditional/Unknown с RuleSourceRef; `RepeatPolicy` = Single/DistinctParameter/Unresolved; `SourceStatus` = Confirmed/NeedsResolution/Unavailable. `CreationPhase` = Creating/Finalized плюс сохранённый факт завершения. `LegacyOrigin`, `PendingMigration`, `MigrationResult` принадлежат T09, `MutationResult` — T11 (Applied/Stale/Blocked/StorageFailure), `SyncEnvelopeRequest/Result` и `LocalOrServer` — T15. Эти DTO не дублируются на платформах.

## Независимая матрица цен и численные якоря

T01 создаёт `rulesets/dubl-3.69/acquisition/expected_prices.json` и `coverage_manifest.json` как dev/test inputs, **не runtime-источник расчёта**. Строка: caseId, sourceId/sourceRef, definitionRef, instance/parameters, fromRank/toRank, optionId, creationPhase, discountConditions, ownedPrerequisites, openingXP/AP, expectedCostXP/AP, expectedRemainingXP/AP, expectedUndoXP/AP, evidenceStatus и blockingQuestion. Нет expected из вызова quote/CharacterEconomy.

Матрица конечная для фиксированного каталога: все допустимые переходы рангов, варианты, стадии и совместимые комбинации применимых скидок. Для произвольных пользовательских названий — отдельные семантические классы (совпадает ID/другое имя тот же ID/другой ID/пусто/неизвестно/две разные специализации), а не невозможное перечисление всех строк Unicode. Для повторных экземпляров: первый/второй с другим параметром/запрещённый повтор того же/повышение конкретного экземпляра. Все family members получают собственные строки; один тест механизма не закрывает группу.

|Case ID|Независимый ожидаемый результат|Основание / ограничение|
|---|---|---|
|block_dex3_speed2|Уже melee3,DEX3,SPD2:20XP;100→80; Undo100. DEX2/SPD3 также 20; DEX2/SPD2 strict direct Blocked|Блокирование P11012; не требовать одновременно обе характеристики|
|marathon_con4|Бегун 1/Выносливый 2 и их условия уже выполнены,CON4:30XP;100→70|P11791; CON3 не использовать как законный положительный predecessor fixture|
|sprinter_dex3_str3|Бегун 2 с его условиями,DEX3/STR3:30XP;100→70|P11797; не старые 245XP автоповышений|
|eagle_price_only|Котировка Орлиного взора 45XP;100→55 если допуск подтверждён. При неизвестном parent access применение Blocked, баланс 100|P13387; нельзя считать все требования выполненными только из-за исправленной цены|
|knowledge_second_instance|Два разных знания ранга 4: первая специализация 50, вторая 50;150→100→50; повтор того же экземпляра ранга 1 =0 изменений|P10528–10531|
|resistance_second_type|Первый fire rank1 и новый poison rank1 по 20;100→80→60; новый fire не дублирует fire|P11620–11623; цена rank1→2 отдельно требует подтверждения общего рангового правила, не выводится из этих примеров|
|skill_rank1_creation|Первый ранг умения 10;100→90, даже при Самоучке во время создания|P203 и Самоучка P11651 и далее|
|skill_rank1_after_creation|При уже законном Самоучке новый первый ранг 5;100→95; без Самоучки 10|Те же источники. Переходы выше первого включить полностью, но подтверждать их базовые цены отдельно|
|legacy_self_taught|Старый текущий баланс 100 → migration100 → completeCreation100 → новая первая прокачка 5 →95; повтор migration/complete не меняет 95|Утверждённая политика миграции + источник скидки|
|receipt_immutable|Opening100, покупка 20→80, новая цена каталога 30 оставляет 80, Undo→100|Утверждённая политика точных новых платежей|

Текущая таблица skill cumulative `[0,10,30,60,100,150,210,280,360,450,550]`, school25/rank, mana100/rank, Chi50/rank и диапазоны spell XP — **наблюдаемый код**, не автоматически подтверждённые эталоны книги. Найти источник/утверждённое решение для каждого значения в T01/T06. Пока его нет, соответствующий case `SOURCE_UNVERIFIED`, release-отчёт не зелёный. ОСп «рекомендуется при создании» (P11903) не превращать в автоматическую награду при каждом получении 1000XP без подтверждённой политики.

## Порядок зависимостей

`T01 → T02 → T03 → T04 → T05 → T06 → T08`; `T08 → T07` и `T08 → T09`; `T05+T06+T07+T08+T09 → T10 → T11`; `T08+T10+T11 → T12/T13/T14`; `T08+T11 → T15 → T16`; `T07+T10+T11 → T17`; `T12+T13+T14+T17 → T18`; `все → T19 → T20`. T12–14 изолированы по адаптерам, но нельзя вводить расходящиеся store-контракты. Runtime feature flag остаётся выключенным до выполнения соответствующих storage/migration guards, UI и приёмки; старый механизм временно обслуживает немигрированные данные, не пишет в новый envelope.

Каждая задача ниже заканчивается отдельным локальным кодовым commit при будущем исполнении. Commit включает только перечисленные файлы и непосредственно нужные fixtures; `git add .` не использовать. Слова RED/GREEN описывают ожидаемый будущий результат, а не уже запущенные проверки.

### T01. Независимые эталоны и рабочая регрессионная граница

**Files:** Create `rulesets/dubl-3.69/acquisition/{expected_prices,coverage_manifest,source_questions}.json`, `tools/rulebook/validate_acquisition_matrix.py`, `tools/tests/test_acquisition_matrix.py`. Modify `tools/tests/test_development_acquisition_planner.py`, `tools/tests/kotlin/DevelopmentAcquisitionHarness.kt`.

**Interfaces:** Produces dev-only matrix schema выше. Python проверяет эталоны отдельно от приложения. Kotlin price fixtures вводятся T06 только после появления нужных типов; текущая задача не оставляет некомпилируемые ссылки на будущие DTO.

- [ ] **RED:** `test_every_catalog_variant_has_independent_oracle` удаляет один case для rank/option и требует `MISSING_CASE`; `test_unknown_price_cannot_be_confirmed_zero` требует `SOURCE_UNVERIFIED`; `test_source_counts_consistent` проверяет 536/524/517 и 796/768 по manifest, отдельно drift новых catalog IDs. Зафиксировать численные якоря таблицы выше и источники всех остальных строк; не объявлять их подтверждёнными автоматически.
- [ ] **Run RED:** `python3 -m pytest tools/tests/test_acquisition_matrix.py -q`; FAIL на отсутствующем валидаторе/пропущенной строке.
- [ ] **Implement:** `validate_matrix(matrix: dict, manifest: dict) -> list[dict]` возвращает ошибки с caseId; полный cross-product допустимых перечисленных price contexts задаётся manifest, неизвестный максимум/скидка создаёт blockingQuestion. Восстановить namespace/compile list старого harness и заменить его source-string assertions поведенческими там, где контракт меняется. Сначала проверить исходные source refs и diff, не обновлять baseline по выводу приложения.
- [ ] **Run GREEN:** те же pytest плюс `python3 -m pytest tools/tests/test_development_acquisition_planner.py -q`; PASS без skip. Source-unverified case разрешён только в списке открытых вопросов, не в confirmed coverage. `python3 -m tools.rulebook.validate_acquisition_matrix --matrix rulesets/dubl-3.69/acquisition/expected_prices.json --manifest rulesets/dubl-3.69/acquisition/coverage_manifest.json` печатает counts и отдельно blocked; exit0 означает целостность, не готовность выпуска.
- [ ] **Commit:** stage только перечисленные файлы; `git commit -m "test: establish independent acquisition price oracles"`.

### T02. Типы требований и сохранение неизвестного

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/RequirementModels.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/RequirementModelsTest.kt`.

**Interfaces:** Produces RequirementNode/RequirementResult/RuleSourceRef из реестра. Atomic predicates содержат типизированный operator EQ/GE/LE/LT/GT и operand; DistinctCount задаёт predicate по definition/instance и область уникальности; нельзя заменить все условия `rank >=`.

- [ ] **RED:** `and_or_tree_preserves_scope`: AllOf(AnyOf(DEX≥3,SPD≥3),AnyOf(melee≥3,unarmed≥3)) сохраняет именно два OR. `unknown_is_not_empty_all`: тип Unresolved не превращается в пустой AllOf; статус SourceUnresolved проверяется в T05; `empty_and_requires_explicit_no_requirements` допускает пустоту только с evidence.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*RequirementModelsTest'`; FAIL на отсутствии модели.
- [ ] **Implement:** sealed nodes, immutable source metadata и stable nodeId. Нормализация текста допускается для импорта, не семантическая замена «ил/ли/и/или». Типы ещё не меняют production purchase flow.
- [ ] **Run GREEN:** та же команда; PASS, tree equality доказывает сохранение группировки и unresolved.
- [ ] **Commit:** `git commit -m "feat: define typed acquisition requirements"` с двумя файлами.

### T03. Экземпляры, варианты и повторные специализации

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/AcquisitionIdentity.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionIdentityTest.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentModels.kt` только совместимый bridge чтения.

**Interfaces:** Consumes RequirementModels. Produces `selectInstances(definition: DefinitionRef, parameters: Map<String,String>, instances: List<AcquisitionInstance>): List<AcquisitionInstance>`; никакого match по display name. Legacy OwnedDevelopment остаётся входом миграции, новый экземпляр не кодируется через optionIndex.

- [ ] **RED:** `different_knowledge_instances`: два разных knowledge IDs при rank4 дают разные instance IDs; rename сохраняет идентичность. `resistance_rank_is_not_second_type`: fire1→fire2 не создаёт poison. `unknown_weapon_parameter_not_melee`: legacy unknown не удовлетворяет melee и не выдаёт false proof. `ability_option_change_is_not_rebuy`: купленный вариант нельзя поменять повтором targetRank.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*AcquisitionIdentityTest'`; ожидаем отсутствие API/ошибку смешивания ID.
- [ ] **Implement:** определения параметров/повторяемости и validation неизвестных/пустых значений; одинаковое legacy происхождение даёт одинаковую identity. Пределы ранга хранятся на definition, rank каждого instance независим.
- [ ] **Run GREEN:** та же команда; PASS для correct/wrong/missing parameter и повторного имени с другим ID.
- [ ] **Commit:** `git commit -m "feat: identify specialized acquisition instances"`.

### T04. Типизированные FCP-требования, ссылки и исправленные данные

**Files:** Create `tools/rulebook/import_acquisition.py`, `tools/tests/test_rulebook_acquisition_import.py`, `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/AcquisitionCatalog.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionCatalogTest.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/dubl/content/{DublFcpCatalogLoader,DublChiFcpCatalogLoader}.kt`, `shared/src/commonMain/resources/fcp/dubl-3.69/manifest.json`, его `content/development_{regular,special,magic,martial,ability_roots}_catalog.json`, Chi manifest/content при наличии acquisition entries. Новый declarative payload `content/acquisition_catalog.json` в обоих pack только с manifest declaration; `tools/rulebook/build_ruleset.py` подключает импорт.

**Interfaces:** Consumes T01–03. Produces `loadAcquisitionCatalog(packs: List<FcpContentPack>, includeChi: Boolean): CatalogLoadResult`, CatalogLoadResult=(catalog,diagnostics); композиция сохраняет существующие claims/order. Старые текстовые requirements остаются для отображения/provenance, но не второй исполняемый parser.

- [ ] **RED:** importer tests на actual source P11012/11791/11797/13387 дают Block OR, CON3,DEX3/STR3,45XP и устраняют только устаревшее incomplete цены. Source question родительского access остаётся. `martial_tail_preserved` сохраняет Мощный удар/Удар по нервам после стилей. `spell_reference_not_feat_alias` различает голое spell имя и одноимённый feat. `smetanie_not_above_max` принимает Бычий натиск 2; Сжигатель миров остаётся unresolved rank2/max1.
- [ ] **Run RED:** `python3 -m pytest tools/tests/test_rulebook_acquisition_import.py -q`; `./gradlew :shared:desktopTest --tests '*AcquisitionCatalogTest'`; ожидаем отсутствие typed payload/старые значения.
- [ ] **Implement:** детерминированный IR и явные mappings стабильных IDs; missing/duplicate/ambiguous refs, SCC, ranks/options вне определения — diagnostics, а не упрощённая ссылка. Источник без подтверждения остаётся source-unverified. Не писать неутверждённое правило в resolutions ради зелёного теста.
- [ ] **Run GREEN:** обе команды; затем `python3 -m pytest tools/tests/test_fcp_contract.py tools/tests/test_rulebook_baseline.py -q`. Дважды `python3 -m tools.fcp.build_fcp --source shared/src/commonMain/resources/fcp/dubl-3.69 --output build/acquisition-core.fcp`: одинаковый checksum при тех же входах; diff baseline рассматривается вручную, `--update` не автоматический шаг.
- [ ] **Commit:** `git commit -m "feat: import typed acquisition rules with provenance"`.

### T05. Одна оценка требований

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/RequirementEvaluator.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/RequirementEvaluatorTest.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentModels.kt` (delegation существующего availability).

**Interfaces:** Consumes catalog/identity/T02; produces `evaluate(node,context)` и `AcquisitionContext`. Legacy availability форматирует structured results; нельзя оставить независимые checkAtom/regex как fallback для typed записи.

- [ ] **RED:** `weapon_specialization_matches_selected_family`: верное холодное проходит, стрелковое/unknown не проходят. `spell_learned_true_only`: absent/false/true → Unsatisfied/Unsatisfied/Satisfied. `distinct_cries_2_3`: один rank3=1;2 крика не дают 3. `knowledge_rank_before_parenthesis`: Знание 4 удовлетворяет реальному typed условию. `creation_second_rank_unresolved` не принимает старое владение как разрешение нового перехода. Recursive invalid prerequisite и actual incompatible perfect roots дают Blocked/Conflict; unrelated legacy deviation не меняет независимое условие.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*RequirementEvaluatorTest'`; FAIL по новым assertion.
- [ ] **Implement:** рекурсивный evaluator с visited IDs, явными witnesses и bounded traversal; сложные derived требования вызывают существующий pure calculation через context, не UI. Внешнее подтверждение не заменяет UnresolvedSource.
- [ ] **Run GREEN:** та же команда и `--tests '*DevelopmentRulesTest'`; осмысленно мигрировать ожидания старых ручных обходов, не удалять отрицательные случаи.
- [ ] **Commit:** `git commit -m "feat: evaluate all acquisition predicates consistently"`.

### T06. Котировка каждого ранга и независимая ценовая приёмка

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/AcquisitionPricing.kt`, `shared/src/desktopTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPriceOracleTest.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPricingTest.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPriceFixtures.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/dubl/model/{CharacterEconomy,SkillModels,MagicEquipmentModels}.kt` только мосты marginal quote без смены legacy-calculator T09.

**Interfaces:** Consumes T01,T03–05. Produces Amount/AcquisitionChange/PriceResult и `quote(change,context)`. `AcquisitionPriceFixtures.load(caseId: String): PriceFixture(change: AcquisitionChange, context: AcquisitionContext, expected: ExpectedPrice)` принадлежит этой задаче; ExpectedPrice=(cost:Amount,remaining:Amount,undoBalance:Amount,evidenceStatus:String). Fixture helper сериализует oracle, не вычисляет его; quote(blockCase) в assertions означает quote(f.change,f.context) и извлечение Known.cost. Known0 отличается от Unknown. Для новых normal purchases нельзя вызывать cumulative current-state recalculation как историю расходов.

- [ ] **RED:** фактические `assertEquals(20L, quote(blockCase).xp)`, `assertEquals(30L, quote(marathonCase).xp)`, `assertEquals(30L, quote(sprinterCase).xp)`, `assertEquals(45L, quote(eagleCase).xp)` выполняются через helper извлечения Known; полный oracle test перебирает все confirmed cases и проверяет cost/remaining/Undo expectation. `self_taught_first_rank`: creation10,postCreation5,noFeat10. `overflow_rejected`: Long.MAX_VALUE+1 даёт явную ошибку, не отрицательную цену. Для неподтверждённого второго ранга — Unknown, не значение из старого catalog.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*AcquisitionPricingTest' --tests '*AcquisitionPriceOracleTest'`; FAIL на отсутствующем quote/несовпадении oracle.
- [ ] **Implement:** правила цен по kind/подтверждённым priceRule, checked arithmetic, точное определённое источником округление. Если округление не определено — Unknown для затронутых значений. Варианты доступа/скидки/negative grant записываются явно. UI ещё не включать.
- [ ] **Run GREEN:** та же команда; pass для каждого confirmed case, отчёт missing/source-unverified отдельно. `python3 -m tools.rulebook.validate_acquisition_matrix --matrix rulesets/dubl-3.69/acquisition/expected_prices.json --manifest rulesets/dubl-3.69/acquisition/coverage_manifest.json --require-confirmed` (T01 добавить флаг) должен FAIL до разрешения всех необходимых вопросов; не ослаблять gate.
- [ ] **Commit:** `git commit -m "feat: quote acquisition costs against independent oracles"`.

### T07. Планирование на том же дереве

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/AcquisitionPlanner.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPlannerTest.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/dubl/model/DevelopmentAcquisition.kt` как compatibility adapter, убрать вторую исполняемую grammar.

**Interfaces:** Consumes evaluate/quote и уже введённый StoredCharacter T08; выполнить T08 перед этой задачей; produces request/target/plan/choice. Preview token включает персонажа, revision, каталог/resolution и все выбранные параметры/стоимости; order canonical задаёт T08.

- [ ] **RED:** `hook_trailing_requirement`: исходный catalog ID `martial_technique_880f59e730436e3f` обязательно включает Мощный удар или блокируется, но не silently success. Аналогично Выключатель и Парализующий удар. `owned_cries_no_purchase`:2/3 имеющихся крика → target-only30XP. `shared_prerequisite_once`: два target с одним missing ancestor дают одну change ancestor; existing ancestor даёт 0. `fixed_rank_idempotent` = empty changes,0cost. `two_specializations` различает новую копию и повышение. Cyclic/unknown dependency завершается issue; отрицательные/выше max target не silently clamp.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*AcquisitionPlannerTest'`; FAIL на игнорируемом AND/отсутствующем контракте.
- [ ] **Implement:** planner обходит typed nodes; OR alternatives содержат quote+issues и требуют явного разрешения значимого выбора; missing spell/умение может докупаться только с подтверждёнными ценой и параметром. Тот же параметризованный ancestor объединяется по instance+transition, а не display name.
- [ ] **Run GREEN:** команда выше; все executable projections проходят T05, incomplete plans не используются как положительные fixtures. Прогнать 796/768 реальных IDs в desktop test и разделить input-valid/manual/stress как в аудите.
- [ ] **Commit:** `git commit -m "feat: plan acquisitions from typed requirements"`.

### T08. Финансовый envelope, безопасный codec и store-контракт

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/{AcquisitionLedger,AcquisitionEnvelope,AcquisitionStore,AcquisitionEnvelopeCodec}.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionEnvelopeTest.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/dubl/data/{SnapshotCodec,CharacterTransferCodec}.kt` через explicit format routing, не небезопасное расширение schema11.

**Interfaces:** Produces T08 contracts, `encodeEnvelope(value: StoredCharacter): String`, `decodeEnvelope(raw: String): LoadResult`, `balance(ledger: Ledger): Amount`, `encodeSyncEnvelope(state: StoredCharacter): String` (исключает outbox, storageRevision и локальные metadata), `canonicalJson(raw: String): String` для валидного JSON: сортировка ключей и точная сериализация scalar values; T07/T10/T11 сериализуют свои DTO и вызывают эту функцию, не создавая обратную зависимость T08 от T07. Envelope formatVersion=1, transport writerVersion=2 — разные версии, не snapshot schema12 «для вида». Legacy schema11 остаётся входом миграции.

- [ ] **RED:** `round_trip_large_xp`:9_007_199_254_740_993L сохраняется точно как decimal string и восстанавливается JVM/JS. `unknown_format_no_fresh_character`:format99→Unsupported с исходными байтами. `same_id_different_payload_rejected`; `truncated_ledger_corrupt`; `negative_opening_preserved`:−10 остаётся−10. `rename_or_key_order_same_token`: canonical keys не зависят от Map insertion order. Нельзя дважды считать один receipt/противоречивые chain revisions.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*AcquisitionEnvelopeTest'`; затем JS variant в T14. FAIL на отсутствии codec/потере полей.
- [ ] **Implement:** fail-closed decode, validation денежных значений/receipt chain/character scope, immutable ledger и in-memory CAS test-store. Canonical JSON фиксирует UTF-8, key ordering, decimal-string Long; не придумывать финансовую историю при decode. Outbox записывается в том же envelope.
- [ ] **Run GREEN:** тот же test и `--tests '*CharacterTransferCodecTest'`; старый transfer VERSION1 сохраняет отказ от future schema, новый envelope получает отдельный version2 transfer без потери данных.
- [ ] **Commit:** `git commit -m "feat: persist versioned acquisition envelopes"`.

### T09. Одна миграция legacy-баланса

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/{LegacyAcquisitionMigration,LegacyEconomyV11}.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/LegacyAcquisitionMigrationTest.kt`, `rulesets/dubl-3.69/acquisition/legacy-v11-catalog.json` (dev provenance) и подтверждённый runtime migration snapshot внутри FCP. Modify existing FCP manifest только для pinned migration input.

**Interfaces:** Consumes envelope/identity; produces prepareLegacy/confirmLegacyBalance и LegacyCatalogSnapshot (зафиксированные цены/overrides/pack composition старого расчёта). LegacyEconomyV11 сохраняет семантику f47a до исправлений и не используется для новых покупок.

- [ ] **RED:** `preserve_old_complete_and_incomplete`: остаток 100 остаётся 100 при обоих creation states, в том числе с Самоучкой. `do_not_reprice_old_eagle`: старая цена 0 не вычитается как 45 при migration. `repeat_import_same_origin_once`: один opening и stable instances. `same_origin_two_devices_same_migration`; `different_revision_requires_conflict`; `missing_catalog_requires_confirmation`: NeedsBalanceConfirmation, не 0. `negative_balance`:−10 сохраняется, новая 20XP покупка Blocked. Ручное подтверждение 100 имеет provenance UserConfirmed и reason.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*LegacyAcquisitionMigrationTest'`; FAIL на пересчёте новым каталогом/двойном opening.
- [ ] **Implement:** immutable origin fingerprint+deterministic identity, backup raw до изменения, применение нормализации только по legacy правилам; history unknown пометка. Экономика нового envelope никогда не добавляет поверх ledger cumulative total. Неполное создание сохраняет stage и факт будущего завершения один раз.
- [ ] **Run GREEN:** та же команда; все exact opening values, исходные raw и migration IDs равны до/после повторного load. Подтверждённая миграция не зависит от последующей доступности каталога.
- [ ] **Commit:** `git commit -m "feat: migrate legacy balances without repricing"`.

### T10. Финальная проверка, атомарное применение и stale preview

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/AcquisitionService.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionServiceTest.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/dubl/application/{DevelopmentApplication,DublApplication}.kt` (новый фасад без включения старых writers на мигрированный state).

**Interfaces:** Consumes T05–09, produces ConfirmAcquisition/ApplyResult и preview/apply. После CAS conflict только из-за metadata перечитать envelope и повторить commit без изменения подтверждённого канонического плана; изменения revision требуют Stale. На apply получить тот же catalogue snapshot, который проверяется token, нормализовать результат перед финальными postconditions и monetary delta.

- [ ] **RED:** `stale_cheaper_still_reconfirm`: показано 20, стало 15→Stale,0 новых ledger entries. `double_tap_one_receipt`: op1 дважды → Applied/AlreadyApplied,100→80 только один раз. `lost_reply_after_commit`: fault после CAS, retry op1 даёт 80. `save_failure_no_publish`: failure до CAS оставляет 100 и отсутствие владения. `postcondition_failure_atomic`: forged plan/боевой AND-хвост не сохраняет цель. `wrong_character_token`: tokenA/characterB →IdentityConflict. Concurrent apply на revision7: ровно один CAS successful, другой stale.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*AcquisitionServiceTest'`; FAIL на отсутствующих исходах/двойном расходе.
- [ ] **Implement:** per-character serialized operation+CAS, durable receipt before UI success; response checks reused ID content even if old receipt reverted. Never replan changed preview and silently commit; compare token then require fresh user confirmation. Финальная оценка всех touched dependencies+global exclusions и точный cost delta обязательны.
- [ ] **Run GREEN:** та же команда + `--tests '*SharedApplicationGoldenTest'`; backend fake failures доказывают all-or-none. Поведение проверки шаблона и цены не зависит от платформы.
- [ ] **Commit:** `git commit -m "feat: apply acquisition plans atomically and idempotently"`.

### T11. Устойчивый Undo и остальные финансовые команды

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/{AcquisitionUndo,CharacterMutationCoordinator}.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionMutationTest.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/dubl/application/{ApplicationUndoManager,CharacterApplication,SkillsApplication,MagicApplication,DevelopmentApplication,DublApplication}.kt`, `shared/src/commonMain/kotlin/com/furybook/dubl/state/CharacterSession.kt`.

**Interfaces:** Consumes T08–10, produces undo/mutate. CanonicalMutation payload variants: Grant(delta:Amount), ManualCorrection(characterPatch,delta:Amount), CompleteCreation, ConfirmLegacyParameter(instanceId,parameters), NonFinancial(characterPatch); reason required for manual/user-confirmed changes. Existing nonfinancial methods stage a change on detached CharacterSession then commit via coordinator; financial projection must be unchanged. All canonical writes increment revision; no direct persistent session writes bypass envelope.

- [ ] **RED:** `undo_restart_exact`:100→80,reload,undo u1→100; повтор u1→100; повтор purchase op1→AlreadyUndone. `undo_after_hp_change_blocked`; `undo_after_sync_same_tip_allowed`; `grant_repeat_once`; `manual_legacy_refund_requires_amount_and_reason`; `complete_creation_no_reprice`:100→100 twice. ReopenCreation только manual, не повторный доступ к creation-only. `nonfinancial_patch_cannot_alter_rank_or_xp` отвергает скрытую покупку.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*AcquisitionMutationTest'`; FAIL до новых receipts/guards.
- [ ] **Implement:** обратная запись только на ожидаемом tip; исходный receipt сохраняется Undone. Покупка новой специализации через strict path, removal/retraining без подтверждённого refund — manual с явной суммой. Начисления ОСп по порогам только при подтверждённом правиле, не из рекомендации P11903. Сохранить unrelated resource formulas, изменить только границу записи.
- [ ] **Run GREEN:** команда выше + `--tests '*CharacterSessionTest'`; возвраты точные, более поздняя правка не теряется.
- [ ] **Commit:** `git commit -m "feat: persist exact acquisition undo and financial corrections"`.

### T12. Desktop: устойчивый контейнер и отказ от тихого сброса

**Files:** Create `shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopAcquisitionStore.kt`, `shared/src/desktopTest/kotlin/com/furybook/dubl/acquisition/DesktopAcquisitionStoreTest.kt`. Modify `shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopCharacterStore.kt`, `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt`.

**Interfaces:** Implements AcquisitionStore. Новый `acquisition-v1/` контейнер в текущем data directory; старый characters.json только backup/import. Journal+outbox в одном envelope; per-character locking и revision CAS, save acknowledged после durable replace. Новый каталог форматных файлов не даёт старому клиенту перезаписать envelope.

- [ ] **RED:** temp file fault до replace сохраняет старый 100; fault после replace/reopen даёт 80+receipt; corrupt/future file→Corrupt/Unsupported, не fresh. Два store instance с одной revision: один commit. Old characters.json edit после migration→conflicting legacy import, не overwrite.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*DesktopAcquisitionStoreTest'`; FAIL пока store отсутствует.
- [ ] **Implement:** temp+flush+replace и recovery/backup policy; если гарантия replace недоступна, fail или восстановимая журналируемая запись, не неподтверждённый silent fallback. Подключить только за feature gate, показать storage errors.
- [ ] **Run GREEN:** тот же test, `./gradlew :desktopApp:compileKotlin`; PASS и saved bytes после повторного открытия.
- [ ] **Commit:** `git commit -m "feat: durably store desktop acquisition envelopes"`.

### T13. Android: подтверждённая запись и instrumentation

**Files:** Create `app/src/main/java/com/furybook/android/data/AndroidAcquisitionStore.kt`, `app/src/androidTest/java/com/furybook/android/acquisition/AndroidAcquisitionStoreTest.kt`. Modify `app/src/main/java/com/furybook/android/data/CharacterRepository.kt`, `app/src/main/java/com/furybook/android/state/CharacterController.kt`, `app/build.gradle.kts`.

**Interfaces:** Implements AcquisitionStore. Отдельный versioned container, не `state_v1` legacy key. Durable snapshot write на IO dispatcher с подтверждением, CAS под единым store lock; apply() без ack не удовлетворяет контракту. Добавить AndroidJUnitRunner и Compose UI/JUnit test зависимости текущего BOM; не менять minSdk/applicationId.

- [ ] **RED:** `reopen_after_ack_keeps_receipt`, `storage_failure_does_not_publish`, `old_preferences_remain_backup`, `two_commands_same_revision_one_wins`;100→80 после store reload,0duplicate receipts. Test process recreation отдельно от activity recreation.
- [ ] **Run RED:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.furybook.android.acquisition.AndroidAcquisitionStoreTest`; отсутствие подключённого устройства фиксировать как blocker, не PASS.
- [ ] **Implement:** store с atomic file/подтверждаемой записью, adapter/controller coroutine queue, запуск будущего purchase не блокирует main thread. При corrupt load сохранить raw и показать recovery, не новый персонаж.
- [ ] **Run GREEN:** та же instrumentation команда + `./gradlew :app:testDebugUnitTest :app:assembleDebug`; PASS на minSdk26 и целевой доступной версии. Системный kill после commit/до ack воспроизводится test fault point, не только recomposition.
- [ ] **Commit:** `git commit -m "feat: acknowledge durable Android acquisition writes"`.

### T14. Web: durable offline store вместо памяти вкладки

**Files:** Create `webApp/src/jsMain/kotlin/com/furybook/web/BrowserAcquisitionStore.kt`, `webApp/src/jsTest/kotlin/com/furybook/web/BrowserAcquisitionStoreTest.kt`. Modify `webApp/src/jsMain/kotlin/com/furybook/web/BrowserPersistence.kt`, `webApp/src/jsMain/kotlin/com/furybook/desktop/DesktopAppState.kt`, `webApp/build.gradle.kts`.

**Interfaces:** Implements AcquisitionStore через IndexedDB: read/write revision, envelope,receipt,outbox в одной readwrite transaction; JS Promise completion адаптируется к suspend. Ввести jsTest kotlin-test/browser configuration. Ошибка quota/запрет storage возвращает StorageFailure и блокирует offline purchase.

- [ ] **RED:** две вкладки revision7→один CAS; reload после 100→80 сохраняет 80 и pending outbox; отказ transaction сохраняет 100; Long9_007_199_254_740_993 roundtrip точен; будущий format не декодируется в fresh. Проверить legacy BrowserSessionMemory migration только из реально имеющегося снимка, не «восстановить» уже потерянный.
- [ ] **Run RED:** `./gradlew :webApp:jsBrowserTest`; runner отсутствует до настройки, затем FAIL по отсутствующему store. Headless Chromium path задаётся средой теста, не hardcode workspace.
- [ ] **Implement:** versioned IndexedDB database/object store с per-character atomicCAS; canonical state публикуется после transaction complete. Web online не маскирует отсутствие локального durable store как offline success.
- [ ] **Run GREEN:** та же команда + `./gradlew :webApp:compileKotlinJs`; PASS, включая перезапуск browser context с тем же persistent profile и явный storage-denied кейс.
- [ ] **Commit:** `git commit -m "feat: persist web acquisitions and offline queue"`.

### T15. Единый sync-контракт, потерянный ответ и конфликт ветвей

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/AcquisitionSyncProtocol.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionSyncTest.kt`. Modify `shared/src/commonMain/kotlin/com/furybook/core/cloud/NativeCloudSync.kt`, `app/src/main/java/com/furybook/android/cloud/AndroidCloudController.kt`, `desktopApp/src/main/kotlin/com/furybook/desktop/cloud/DesktopCloudController.kt`, `webApp/src/jsMain/kotlin/com/furybook/web/SupabaseBackend.kt`.

**Interfaces:** Consumes envelope/CAS, produces push transport contract и `syncPending(characterId: String): SyncEnvelopeResult`, `resolveConflict(characterId: String, choice: LocalOrServer, expectedServerRevision: Long): SyncEnvelopeResult`. LocalOrServer выбирает целую ветвь; не merge ledger. Перед её принятием обе версии архивируются, resolution передаёт подтверждённую server revision; сервер отказывает при её изменении. Обычная push-команда не может выдавать себя за разрешение конфликта без подтверждённого UI-намерения. Migrated data идут новым протоколом только при capability, legacy продолжает старый путь изолированно.

- [ ] **RED:** сервер принял op1, ответ потерян, повтор→same ack;100→80 не 60. Две ветви из 100 тратят 80 и 70: conflict, не−50; обе сохранены до выбора. После server branch другая покупка предлагается новым preview на 30, не переносится оплаченной. Auth timeout сохраняет outbox. Clone/import с новым character ID сохраняет точные исторические суммы и provenance оригинала, но не копирует pending outbox и не исполняет старые команды. Регистрация копии — отдельная import operation; уникальность receipts имеет область character ID. У исходного100→80 и копии80 нет второго расхода20; новая покупка10 у копии даёт70, оригинал остаётся80.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*AcquisitionSyncTest'`; FAIL на отсутствии receipt-aware sync.
- [ ] **Implement:** network retry переносит envelope/receipts, не исполняет игровые команды; outbox ack и serverRevision сохраняются CAS с защитой более новых локальных изменений. Same operationId+different canonical payload →IdentityConflict. Sync ack не меняет canonical character revision/undo eligibility, только metadata и storageRevision; remote character mutation меняет обе. `ack_races_new_purchase` проверяет: ack op1 при уже pending op2 удаляет только op1, сохраняет op2 и цену/Undo op2. В push payload нет outbox и потому нет рекурсивного вложения снимка в самого себя.
- [ ] **Run GREEN:** тот же test + `--tests '*NativeCloudSyncProtocolTest'`; websocket/realtime не требуется. Native и Web adapters используют общий protocol DTO, без второго merge-алгоритма.
- [ ] **Commit:** `git commit -m "feat: synchronize acquisition receipts without replaying purchases"`.

### T16. Серверные prerequisites, guards всех writers и выпуск формата

**Files:** Create `supabase/tests/acquisition_v2.sql`, `docs/acquisition-server-compatibility.md`. Create новую миграцию через `supabase migration new acquisition_ledger_v2` (имя с timestamp возвращает CLI; записать фактический путь `supabase/migrations/<generated>_acquisition_ledger_v2.sql` в task evidence, не придумывать заранее). Проверяемые исходные RPC в `20260929190150_server_authoritative_web_functions_a.sql`, `20260929190213_server_authoritative_web_functions_b.sql`, `20260930220000_native_cloud_sync_rpc.sql`; старые migrations не редактировать.

**Interfaces:** Implements capability `acquisitionLedgerV2`, RPC `acquisition_sync_v2(p_character_id text,p_expected_revision bigint,p_operation_ids jsonb,p_envelope jsonb,p_writer_version integer,p_resolution jsonb default null)` → JSON SyncEnvelopeResult T15. Сервер хранит min-writer-format/operation uniqueness на `(user_id,character_id,operationId)` и durable tombstone при удалении; все альтернативные save/force/link/delete/read paths соблюдают эту границу.

- [ ] **RED:** на disposable local database отдельные пользователи A/B: B не пишет A; old save_web_character/native_sync_snapshot/native_force_character/native_link_device/delete_web_character/прямая разрешённая таблица не могут снизить migrated format или удалить ledger. Duplicate op1→same receipt; тот же ID другое body→reject; stale force revision→conflict; delete/recreate не снимает minversion; old bootstrap не выдаёт new envelope как редактируемый schema11. Local migration alone не подтверждает live guards.
- [ ] **Run RED:** после проверки `supabase --help`, `supabase test --help` использовать disposable DB: `psql "$ACQUISITION_TEST_DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/acquisition_v2.sql`; assert script откатывает fixtures. Ожидаем assertion failure старых guards; при отсутствии локальной DB/server schema отметить blocker. Не запускать на пользовательских данных.
- [ ] **Implement:** сначала read-only inventory реально развёрнутых RPC, grants/RLS, legacy `user_state`, campaign writers и cloud import/delete с версией schema; сохранить различие с repo. Новые guards сохраняют ownership authorization и проверяют receipt completeness/chain. Все места записи вне guards либо закрыть, либо охватить; заявленная клиентом версия не доказывает наличие ledger. Отдельно проверить old read/new read negotiation. Деплой — отдельное авторизуемое действие, не этот шаг автоматически.
- [ ] **Run GREEN:** disposable SQL assertions PASS; повторно native/web transport integration T15. Release capability остаётся false до staging/live совместимости старых binaries и подтверждённых серверных guards. Если server access/источник схемы недоступен, не обходить условие: новый общий cloud-write заблокирован, локальная изолированная копия допускается только с ясным статусом.
- [ ] **Commit:** `git commit -m "feat: guard acquisition format across cloud writers"` только после фиксации фактического migration path и локальных тестов; никаких live изменений из commit.

### T17. Общая UI-модель и подключение всех входов покупки

**Files:** Create `shared/src/commonMain/kotlin/com/furybook/dubl/acquisition/AcquisitionPresentation.kt`, `shared/src/commonTest/kotlin/com/furybook/dubl/acquisition/AcquisitionPresentationTest.kt`. Modify `app/src/main/java/com/furybook/android/state/CharacterController.kt`, `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt`, `webApp/src/jsMain/kotlin/com/furybook/desktop/DesktopAppState.kt` и application wrappers из T11.

**Interfaces:** Consumes preview/apply/undo/mutate, produces present/AcquisitionUiModel. Controller держит operationId до завершения/неопределённого результата; новые confirmed request создают новый ID. Все direct XP/ОСп mutations для migrated character идут coordinator; setter не доступен UI в обход strict/manual намерения.

- [ ] **RED:** для каждого confirmed price case `card.cost == preview.cost == receipt.cost` и readable labels не содержат внутренних IDs; Unknown отображается «Цена не определена», не 0. rank+,attribute+,school+,mana,spell learned и Chi+ создают quote, а не изменённыйлист; manual требует reason. Смена activecharacter до apply не меняет request.characterId. Двойной tap сохраняет operationId.
- [ ] **Run RED:** `./gradlew :shared:desktopTest --tests '*AcquisitionPresentationTest'`; source-only Python проверки адаптеров не достаточны.
- [ ] **Implement:** structured UI model состояния Loading/Ready/ChoiceRequired/Blocked/Applying/Committed/Failed/Stale/Conflict; показывать receipt и sync status отдельно. Повторное открытие preview не повторяет apply. Unknown author rule не превращается в checkbox внешнегоусловия.
- [ ] **Run GREEN:** команда выше + shared application golden; применимость/цена одинаковы у direct и bulk entrypoints. Ценовая матрица тестирует все rows, не только экранный happy path.
- [ ] **Commit:** `git commit -m "feat: share acquisition presentation and command routing"`.

### T18. Реальные Android/Desktop/Web экраны и переносимые сценарии

**Files:** Modify Android `app/src/main/java/com/furybook/android/ui/screens/{FeatsScreen,SkillsScreen,MagicScreen,OverviewScreen}.kt`; Desktop `desktopApp/src/main/kotlin/com/furybook/desktop/screens/{DevelopmentScreen,SkillsScreen,MagicScreen,CharacterSheetScreen}.kt`; Web одноимённые файлы в `webApp/src/jsMain/kotlin/com/furybook/desktop/screens/`. Create `app/src/androidTest/java/com/furybook/android/acquisition/AcquisitionUiTest.kt`, `desktopApp/src/test/kotlin/com/furybook/desktop/AcquisitionUiTest.kt`, `webApp/src/jsTest/kotlin/com/furybook/web/AcquisitionUiTest.kt`, `docs/acquisition-ui-acceptance.md`; Modify desktop/web Gradle test dependencies/config.

**Interfaces:** Renders AcquisitionUiModel и sends ConfirmAcquisition, не пересчитываетцены. Добавить стабильные семантические тестовые labels `acquisition.open`, `acquisition.confirm`, `acquisition.cancel`, `acquisition.undo`, `acquisition.price`, `acquisition.status`; включить в фактические Compose элементы. Browser runner должен работать с реальным рендером, не только state function.

- [ ] **RED:** живые сценарии: block DEX3/SPD2/melee3 opening100 → карточка 20 → preview20 → confirm80 → history20 → restart80 → Undo100; repeated confirm80; back/cancel100; interruptedbefore commit100/after commit80; staleprice не закрывает диалог как success. Weapon/spec choice, spell learned, cries, manual deviation и неизвестное правило источника показывают правильные подсказки. После выбора персонажа B открытый план A не покупает B. Для каждого oracle row пройти параметризованную карточку/preview/receipt view; отдельно полный пользовательский путь минимум по каждому kind, варианту и классу ошибки.
- [ ] **Run RED:** `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.furybook.android.acquisition.AcquisitionUiTest`, `./gradlew :desktopApp:test --tests '*AcquisitionUiTest'`, `./gradlew :webApp:jsBrowserTest`. Реальные restart/kill/offline/back сценарии выполняются дополнительно по docs checklist с записью buildSHA/device/browser. Пока test infrastructure не создана, FAIL/missingtask, не pass.
- [ ] **Implement:** preview/choice/error/receipt surfaces во всех входах; explicit advanced manual editor. Подключить поддерживаемый Compose UI test runner для текущего стека. Если Web renderer не предоставляет нужный browser input/semantics API, live acceptance проводится реальными вводами в browser с доказательствами по тому же caseId; не подменять это только common UI model tests и не объявлять недоступный automation suite созданным.
- [ ] **Run GREEN:** все доступные команды+ручные неизбежные системные сценарии дают ожидаемые значения; отдельный отчёт unavailable platform =NOT VERIFIED. Все price rows покрыты renderer/presentation data tests на каждой платформе; реальный UI smoke не заменяет эту матрицу, а data tests не заменяют реальные действия. Test-only injection fixtures не попадают в release runtime.
- [ ] **Commit:** коммитить по платформенным частям T18 только после их RED/GREEN gate, с общей неизменной UI model; сообщения `feat: connect <platform> acquisition UI`. Это три проверяемых подзадачи T18A/B/C, ни одна не считается готовой по результату соседней.

### T19. Полная матрица результата, regression и выпускные ограничения

**Files:** Create `tools/rulebook/report_acquisition_coverage.py`, `tools/tests/test_acquisition_release_gate.py`, `shared/src/desktopTest/kotlin/com/furybook/dubl/acquisition/AcquisitionCatalogAcceptanceTest.kt`, `docs/acquisition-release-evidence.md`; Modify CI `.github/workflows/{android-ci,web-ci,windows-desktop}.yml` только нужные checks, не packaging поведение.

**Interfaces:** Consumes manifest/oracles, runtime composition and test evidence; `report_coverage(manifest: dict, cases: list, evidence: list) -> dict`; status каждого case=VERIFIED/SOURCE_UNVERIFIED/UI_UNVERIFIED/FAILED, без conflation. Связь caseId→source occurrence→runtime instance transition→platform→receipt.

- [ ] **RED:** удаление одной rank/option/discount/stage строки делает releasegateFAIL; один missing platform evidence не зелёный; known0 и unknown раздельны. Все 536 occurrences,517 matchedunique runtimeID и 796/768 composition сверены; новые/удалённые IDs требуют diffreview, а не silent ожидаемые counts update. Остальные узлы графа ранжированы buyable/reference/use-only, ни 265 spells, ни 13 schools, ни 8attributes не теряются из области цен.
- [ ] **Run RED:** `python3 -m pytest tools/tests/test_acquisition_release_gate.py -q`; `./gradlew :shared:desktopTest --tests '*AcquisitionCatalogAcceptanceTest'`; expected failure недостающего покрытия.
- [ ] **Implement:** объединённый отчёт проверяет карточка/preview/apply/ledger/Undo для всех confirmed combinations и произвольных specialization classes. Runtime legacy стресс не считается законным positive build. Извлечённые два аудита включаются как fixtures с provenance, их старые pass counters не переносятся в новый отчёт.
- [ ] **Run GREEN:** `./gradlew :shared:desktopTest :desktopApp:compileKotlin :app:testDebugUnitTest :app:assembleDebug :webApp:compileKotlinJs`, новые UI suites и `python3 -m pytest tools/tests/test_acquisition_matrix.py tools/tests/test_acquisition_release_gate.py tools/tests/test_rulebook_acquisition_import.py -q`. Structural testsPASS допускают открытый question list; полная готовность требует соответствующих resolutions/отдельного согласования ограниченного выпуска. Никакой fake PASS при недоступном server/device.
- [ ] **Commit:** `git commit -m "test: enforce full acquisition acceptance coverage"`.

### T20. Доказать rollout/rollback до включения нового формата

**Files:** Create `docs/acquisition-rollout.md`; Modify feature gate configuration только после отдельного решения о выпуске; фактический существующий flag location фиксируется T17 в `DublApplication` construction, новое поле `AcquisitionCapabilities(localDurable:Boolean,cloudLedgerV2:Boolean,formatWritable:Boolean)` живёт в `AcquisitionEnvelope.kt`.

**Interfaces:** Consumes T12–19 evidence/capabilities. Writable требует durable local store и поддерживаемого формата; cloud отправка дополнительно cloudLedgerV2. Отсутствие cloud не запрещает согласованную локальную изолированную копию, но не выдаётся за sync.

- [ ] **RED:** `capability_missing_does_not_migrate_cloud`, `rollback_old_client_cannot_overwrite_new`, `backup_restore_retains_receipts`, `unconfirmed_price_excluded_from_verified_release` добавить в `AcquisitionServiceTest`/`AcquisitionSyncTest`/release-gate соответственно. Assert cloud requests=0 при capability=false, исходные envelope bytes сохранены при будущем формате, balances=100→80→restart80 без переоценки.
- [ ] **Run RED:** соответствующие shared tests и `python3 -m pytest tools/tests/test_acquisition_release_gate.py -q`; до guards ожидаемый FAIL.
- [ ] **Implement:** документировать поэтапное включение после серверных проверок, проверки резервной копии, ограниченной мигрированной копии и платформенной приёмки. Rollback — отключение новых покупок/просмотр/экспорт, не down-convert с потерей ledger. Guard release серверных защит снимается только доказанным inventory+tests; live deploy требует явного разрешения отдельно.
- [ ] **Run GREEN:** повтор faults/restart/retry на release-like builds; проверить фактические server capabilities в разрешённом окружении. Если недоступно — оставить gate=false и точный блокер, не обещать текущему cloud новые свойства. `git diff --check` и review полного diff ветки перед решением о выпуске.
- [ ] **Commit:** `git commit -m "docs: record acquisition rollout and rollback gates"`; фактическое включение отдельным reviewed change после разрешения, не «побочный эффект» этой задачи.

## Матрица покрытия утверждённой спецификации

|Требование spec|Задачи|Проверка/граница|
|---|---|---|
|§1 все приобретения, прямые входы, strict/manual|T01,T04,T06,T11,T17,T18,T19|Все buyable kinds из графа; direct setters не обходят price/ledger|
|§2–4 общая семантика и источник|T02,T04,T05,T07,T10|AND/OR, counts, spells, phase, exclusivity, cycle, unknown; финальная проекция|
|§5 параметры/повторы/варианты/неизвестный legacy-параметр|T03,T05,T07,T09,T18|Разные instance IDs, другой выбор не бесшумная конверсия|
|§6 preview/apply/budget/dedup|T06,T07,T10,T17,T18|Stale даже дешевле; один opID; общий предшественник один раз|
|§7 КАЖДАЯ цена/ранг/вариант/скидка/стадия|T01,T06,T18,T19|Independent oracles, card→preview→apply→receipt→Undo, unknown отдельно|
|§8 завершённый legacy-персонаж/incomplete/Самоучка/negative|T08,T09,T11,T20|Opening unchanged;10/5 будущий rank1; подтверждение при недоступном старом каталоге|
|§9 атомарность и устойчивое сохранение/restart/Undo|T08,T10–T14,T18|Failure до/после commit, сохранённый receipt, точный inverse и блокировка зависимой отмены|
|§9 offline/sync/conflict/устаревший writer|T15,T16,T20|Потеря ответа, две ветви, guards всех писателей; никаких непроверенных утверждений о сервере|
|§10 объяснимый UI без ID-knowledge|T17,T18|Реальные Android/Desktop/Web, choices и текст о неизвестной цене|
|§11 вопросы источника|T01,T04,T06,T19|Smetanie не conflict; вопросы источника не автопоправки|
|§12 все строки аудита и реальный UI|T18,T19|12 012 старых diagnostics не покрытие новой реализации; недоступные пути NOT VERIFIED|

## Самопроверка плана и оставшиеся вопросы

Проведена проверка spec→tasks, владельцев DTO/signatures, зависимостей и пяти классов риска из Review Focus. Store, receipt, preview, migration и sync используют единый envelope/ID/revision; canonical revision, storageRevision для CAS и server revision различаются; ack не инвалидирует Undo, а sync payload исключает очередь и не рекурсивен. Проверен порядок T06→T08→T07 и T08→T09: envelope использует уже определённый AcquisitionChange, canonicalJson принимает String и не зависит от будущей команды planner. Поэтому circular dependency отсутствует; миграция нужна T10, но не isolated planner fixtures T07.

Пять опасных классов имеют тесты: потеря ответа/replay (T10/T15/T16), несовместимый writer (T08/T12–16), creation/migration repricing (T06/T09/T11), чужой персонаж/идентичность копии(T08/T10/T15/T18), overflow/negative/unknown/dedup(T01/T06/T07/T09/T11). Предлагаемые тесты ещё не запускались: при подготовке этого плана код не изменён.

Открытые фактические условия, а не незаполненные проектные решения: подтверждённые цены каждого старшего ранга и модульных покупок; формальные resolutions спорных требований; фактических серверных writers inventory/доступ к изолированной тестовой DB/staging; runnable UI runners и устройства. Они не препятствуют реализации независимого shared каркаса, но препятствуют заявлению полной автоматизации/совместимости затронутой области. Прайс oracle не вправе выбрать формулу за автора. Надёжность local/sync доказывается будущими тестами, не этой спецификацией.

После проверки пользователем выбрать способ исполнения. Рекомендуется поэтапное выполнение с независимым review задач, поскольку ошибки финансовой миграции и совместимости затрагивают сохранённые персонажи. Альтернатива — последовательное выполнение одним исполнителем с общим review ветки; в обоих случаях план и спек обязательны. Ни один способ пока не выбран, реализация не начиналась.
