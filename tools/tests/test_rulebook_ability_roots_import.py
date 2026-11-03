import pytest


def test_import_ability_root_parses_sources_requirement_and_text():
    from tools.rulebook.import_ability_roots import import_ability_roots
    raw={"blocks":[
        {"id":"h","kind":"paragraph","headingPath":["Особые навыки","Боевые крики"],"headingLevel":3,"text":"Боевые крики"},
        {"id":"t","kind":"table","headingPath":["Особые навыки","Боевые крики"],"rows":[
            ["Тип / Источник","Стоимость"],
            ["Мораль","1"],
            ["Ци","2"],
            ["Требование: Командирский голос","Требование: Командирский голос"],
            ["Описание: Крики работают.","Описание: Крики работают."],
            ["Особое: Двужильный действует.","Особое: Двужильный действует."],
        ]},
    ]}
    bindings={"bindings":[{"id":"access","name":"Боевые крики"}]}
    payload,diagnostics=import_ability_roots(raw,bindings,"core")
    assert diagnostics == []
    entry=payload["entries"][0]
    assert entry["abilityOptions"] == [{"source":"Мораль","value":1},{"source":"Ци","value":2}]
    assert entry["requirements"] == "Командирский голос"
    assert entry["benefit"] == "Описание: Крики работают.\nОсобое: Двужильный действует."
    assert entry["sourceRefs"] == ["core:h","core:t"]


def test_import_ability_root_chooses_heading_with_ability_table_over_same_name_spell():
    from tools.rulebook.import_ability_roots import import_ability_roots
    raw={"blocks":[
        {"id":"root","kind":"paragraph","headingPath":["Особые навыки","Удача"],"headingLevel":3,"text":"Удача"},
        {"id":"table","kind":"table","headingPath":["Особые навыки","Удача"],"rows":[["Тип / Источник","Стоимость"],["Мистическая сила","2"]]},
        {"id":"spell","kind":"paragraph","headingPath":["Магия","Удача"],"headingLevel":5,"text":"Удача"},
        {"id":"spelltext","kind":"paragraph","headingPath":["Магия","Удача"],"text":"Стоимость: 6"},
    ]}
    payload,diagnostics=import_ability_roots(raw,{"bindings":[{"id":"luck","name":"Удача"}]},"core")
    assert diagnostics == []
    assert payload["entries"][0]["sourceRefs"] == ["core:root","core:table"]
    assert payload["entries"][0]["abilityOptions"] == [{"source":"Мистическая сила","value":2}]


def test_import_ability_root_keeps_empty_template_fields_as_diagnostic_not_guessed_text():
    from tools.rulebook.import_ability_roots import import_ability_roots
    raw={"blocks":[
        {"id":"h","kind":"paragraph","headingPath":["Особые навыки","Броненосец"],"headingLevel":3,"text":"Броненосец"},
        {"id":"t","kind":"table","headingPath":["Особые навыки","Броненосец"],"rows":[
            ["Тип / Источник","Стоимость"],["Мастерство","1"],["Описание: ","Описание: "],["Особое: ","Особое: "]
        ]},
    ]}
    payload,diagnostics=import_ability_roots(raw,{"bindings":[{"id":"armor","name":"Броненосец"}]},"core")
    entry=payload["entries"][0]
    assert entry["benefit"] == ""
    assert any(d["kind"] == "ability-root-template-incomplete" for d in diagnostics)


def test_import_ability_root_tolerates_empty_heading_path_reset_before_table():
    from tools.rulebook.import_ability_roots import import_ability_roots
    raw={"blocks":[
        {"id":"h","kind":"paragraph","headingPath":["Особые навыки","Мастер оружия"],"headingLevel":3,"text":"Мастер оружия"},
        {"id":"empty-heading","kind":"paragraph","headingPath":["Особые навыки"],"headingLevel":3,"text":""},
        {"id":"t","kind":"table","headingPath":["Особые навыки"],"rows":[["Тип / Источник","Стоимость"],["Мастерство","1"]]},
    ]}
    payload,diagnostics=import_ability_roots(raw,{"bindings":[{"id":"master","name":"Мастер оружия"}]},"core")
    assert diagnostics == []
    assert payload["entries"][0]["sourceRefs"] == ["core:h","core:t"]


def test_import_ability_root_preserves_multiline_description_cells():
    from tools.rulebook.import_ability_roots import import_ability_roots
    raw={"blocks":[
        {"id":"h","kind":"paragraph","headingPath":["Особые навыки","Берсерк"],"headingLevel":3,"text":"Берсерк"},
        {"id":"t","kind":"table","headingPath":["Особые навыки","Берсерк"],"rows":[
            ["Тип / Источник","Ценность"],
            ["Мораль","1"],
            ["Описание: Первая строка\nВторая строка","Описание: Первая строка\nВторая строка"],
            ["Особое: Конфликт","Особое: Конфликт"],
        ]},
    ]}
    payload,diagnostics=import_ability_roots(raw,{"bindings":[{"id":"rage","name":"Берсерк"}]},"core")
    assert diagnostics == []
    assert payload["entries"][0]["benefit"] == "Описание: Первая строка\nВторая строка\nОсобое: Конфликт"


def test_compare_ability_roots_reports_mechanical_drift_but_ignores_labels():
    from tools.rulebook.import_ability_roots import compare_ability_roots_runtime
    imported={"entries":[
        {"id":"rage","name":"Боевой азарт","abilityOptions":[{"source":"Мораль","value":1}],"requirements":"","benefit":"","sourceRefs":["core:r"]},
        {"id":"thief","name":"Чарокрад","abilityOptions":[{"source":"Магия","value":1}],"requirements":"","benefit":"Описание: Кража магии.\nОсобое: Только похищение.","sourceRefs":["core:t"]},
    ]}
    runtime={"entries":[
        {"id":"rage","abilityOptions":[{"source":"Мораль","value":1}],"requirements":"Атлетика 3","benefit":""},
        {"id":"thief","abilityOptions":[{"source":"Магия","value":1}],"requirements":"","benefit":"Кража магии.\nТолько похищение."},
    ]}
    diagnostics=compare_ability_roots_runtime(imported,runtime)
    assert len(diagnostics) == 1
    assert diagnostics[0]["subject"] == "ability-root:rage"
    assert diagnostics[0]["details"]["differences"] == {
        "requirements":{"rulebook":"","runtime":"Атлетика 3"}
    }


def test_promote_ability_roots_uses_source_mechanics_and_marks_drafts_incomplete():
    from tools.rulebook.import_ability_roots import promote_ability_roots
    imported={"entries":[
        {
            "id":"connections","name":"Связи","abilityOptions":[{"source":"???","value":1}],
            "requirements":"","benefit":"","sourceRefs":["core:h","core:t"],
            "emptyFields":["описание"],
        },
        {
            "id":"rage","name":"Берсерк","abilityOptions":[{"source":"Мораль","value":1}],
            "requirements":"","benefit":"Описание: Ярость.\nОсобое: Неистовство.","sourceRefs":["core:r"],
            "emptyFields":[],
        },
    ]}
    bindings={"bindings":[
        {"id":"connections","name":"Связи","category":"Особые способности"},
        {"id":"rage","name":"Берсерк","category":"Особые способности"},
    ]}
    payload=promote_ability_roots(imported,bindings,"3.69")
    by_id={e["id"]:e for e in payload["entries"]}
    assert by_id["rage"]["costType"] == "ability"
    assert by_id["rage"]["ranks"] == 1
    assert by_id["rage"]["abilityOptions"] == [{"source":"Мораль","value":1}]
    assert by_id["rage"]["requirements"] == ""
    assert by_id["rage"]["benefit"] == "Описание: Ярость.\nОсобое: Неистовство."
    assert "incomplete" not in by_id["rage"]
    assert by_id["connections"]["abilityOptions"] == [{"source":"???","value":1}]
    assert by_id["connections"]["incomplete"] is True
    assert "локаль" in by_id["connections"]["conflictNote"].lower()


def test_promote_ability_roots_does_not_block_branch_for_empty_template_rows_only():
    from tools.rulebook.import_ability_roots import promote_ability_roots
    imported={"entries":[{
        "id":"sneak","name":"Подлая атака","abilityOptions":[{"source":"Мастерство","value":1}],
        "requirements":"","benefit":"","sourceRefs":["core:h","core:t"],
        "emptyFields":["описание","особое"],
    }]}
    bindings={"bindings":[{"id":"sneak","name":"Подлая атака","category":"Особые способности"}]}
    entry=promote_ability_roots(imported,bindings,"3.69")["entries"][0]
    assert "incomplete" not in entry
    assert "mechanicsConflict" not in entry


def test_compare_ability_roots_ignores_application_only_generic_access_helper():
    from tools.rulebook.import_ability_roots import compare_ability_roots_runtime
    imported={"entries":[{"id":"root","name":"Корень","abilityOptions":[{"source":"Мастерство","value":1}],"requirements":"","benefit":"","sourceRefs":["core:r"]}]}
    runtime={"entries":[{"id":"root","abilityOptions":[{"source":"Мастерство","value":1}],"requirements":"","benefit":"Открывает доступ к навыкам этой ветки. Навыки приобретаются отдельно за опыт."}]}
    assert compare_ability_roots_runtime(imported,runtime) == []


def test_import_ability_root_includes_root_prose_outside_empty_table_but_stops_before_child_card():
    from tools.rulebook.import_ability_roots import import_ability_roots
    raw={"blocks":[
        {"id":"h","kind":"paragraph","headingPath":["Особые навыки","Связи"],"headingLevel":3,"text":"Связи"},
        {"id":"intro","kind":"paragraph","headingPath":["Особые навыки","Связи"],"text":"У Вас есть связи."},
        {"id":"t","kind":"table","headingPath":["Особые навыки","Связи"],"rows":[["Тип / Источник","Стоимость"],["???","1"],["Описание:","Описание:"]]},
        {"id":"root-rule","kind":"paragraph","headingPath":["Особые навыки","Связи"],"text":"Вы можете брать связи несколько раз."},
        {"id":"child","kind":"paragraph","headingPath":["Особые навыки","Связи"],"text":"Связи"},
        {"id":"tag","kind":"paragraph","headingPath":["Особые навыки","Связи"],"text":"[Связи]"},
        {"id":"cost","kind":"paragraph","headingPath":["Особые навыки","Связи"],"text":"Стоимость: 50"},
        {"id":"child-benefit","kind":"paragraph","headingPath":["Особые навыки","Связи"],"text":"Выгода: Это уже XP-карточка."},
    ]}
    payload,_=import_ability_roots(raw,{"bindings":[{"id":"connections","name":"Связи"}]},"core")
    assert payload["entries"][0]["benefit"] == "У Вас есть связи.\nВы можете брать связи несколько раз."
    assert "XP-карточка" not in payload["entries"][0]["benefit"]
