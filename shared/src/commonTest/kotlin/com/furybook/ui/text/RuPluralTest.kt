package com.furybook.ui.text

import kotlin.test.Test
import kotlin.test.assertEquals

class RuPluralTest {
    @Test
    fun picksTheRussianForm() {
        fun s(n: Int) = ruCount(n, "заклинание", "заклинания", "заклинаний")
        assertEquals("1 заклинание", s(1))
        assertEquals("2 заклинания", s(2))
        assertEquals("5 заклинаний", s(5))
        assertEquals("11 заклинаний", s(11))
        assertEquals("12 заклинаний", s(12))
        assertEquals("21 заклинание", s(21))
        assertEquals("104 заклинания", s(104))
        assertEquals("0 заклинаний", s(0))
        assertEquals("112 заклинаний", s(112))
    }
}
