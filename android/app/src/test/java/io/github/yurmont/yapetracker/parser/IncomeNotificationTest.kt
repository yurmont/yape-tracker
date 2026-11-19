package io.github.yurmont.yapetracker.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomeNotificationTest {

    @Test fun titleAndText() {
        val income = IncomeNotification.parse("Yape!", "Ana te envió un pago por S/ 1")!!
        assertEquals("Yape! Ana te envió un pago por S/ 1", income.text)
        assertEquals(100L, income.amountCents)
        assertEquals("Ana", income.person)
    }

    @Test fun fullNameDecimalsAndSecurityCode() {
        val income = IncomeNotification.parse(
            "Confirmación de Pago",
            "Yape! JUAN PÉREZ te envió un pago por S/ 25.50. El cód. de seguridad es: 123",
        )!!
        assertEquals(2550L, income.amountCents)
        assertEquals("JUAN PÉREZ", income.person)
    }

    @Test fun thousands() {
        assertEquals(125050L, IncomeNotification.parse(null, "Ana te yapeó S/ 1,250.50")!!.amountCents)
    }

    @Test fun promoWithAmountIsSkipped() {
        assertNull(IncomeNotification.parse("Yape", "¡Gana hasta S/ 50 pagando tus servicios con Yape!"))
    }

    @Test fun incomePhraseWithoutAmountIsSkipped() {
        assertNull(IncomeNotification.parse("Yape", "Ana te envió un mensaje"))
    }

    @Test fun nothing() {
        assertNull(IncomeNotification.parse(null, null))
    }
}
