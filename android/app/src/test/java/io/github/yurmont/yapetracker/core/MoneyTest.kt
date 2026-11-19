package io.github.yurmont.yapetracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test fun parse() {
        assertEquals(40L, Money.parseCents("0.40"))
        assertEquals(125050L, Money.parseCents("1,250.50"))
        assertEquals(1250L, Money.parseCents("12,50"))
        assertEquals(500L, Money.parseCents("S/ 5"))
        assertEquals(1L, Money.parseCents("0.005"))
    }

    @Test fun rejectsNonAmounts() {
        assertNull(Money.parseCents(""))
        assertNull(Money.parseCents("abc"))
        assertNull(Money.parseCents("0"))
        assertNull(Money.parseCents("-3.00"))
    }

    @Test fun format() {
        assertEquals("0.40", Money.format(40))
        assertEquals("1,250.50", Money.format(125050))
        assertEquals("-0.40", Money.format(-40))
    }
}
