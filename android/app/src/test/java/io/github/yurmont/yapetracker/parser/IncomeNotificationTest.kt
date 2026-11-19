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

    /** Copied from a real Yape notification (2026-11-19). */
    @Test fun realYapeNotification() {
        val income = IncomeNotification.parse(
            "Confirmación de Pago",
            "Ronald Yur* te envió un pago por S/ 0.4. El cód. de seguridad es: 826",
        )!!
        assertEquals(40L, income.amountCents)
        assertEquals("Ronald Yur*", income.person)
        assertEquals(
            "Confirmación de Pago Ronald Yur* te envió un pago por S/ 0.4. El cód. de seguridad es: 826",
            income.text,
        )
    }

    @Test fun realPromoIsSkipped() {
        assertNull(
            IncomeNotification.parse("¡El dólar subió! 📈", "Vende tus dólares y recibe más soles ¡No pierdas esta oportunidad! 💸✨"),
        )
    }

    @Test fun maskedSurname() {
        val income = IncomeNotification.parse("Confirmación de Pago", "Yape! Juan Per* te envió un pago por S/ 0.4")!!
        assertEquals(40L, income.amountCents)
        assertEquals("Juan Per*", income.person)
    }

    @Test fun nameInTitle() {
        val income = IncomeNotification.parse("Juan Pérez", "te envió un pago por S/ 5")!!
        assertEquals("Juan Pérez", income.person)
    }

    @Test fun recibisteDe() {
        val income = IncomeNotification.parse(
            "Yape",
            "Recibiste un yapeo de S/ 0.40 de Rosa Quis*. El cód. de seguridad es: 581",
        )!!
        assertEquals(40L, income.amountCents)
        assertEquals("Rosa Quis*", income.person)
    }

    @Test fun recibisteWithoutNameHasNoPerson() {
        val income = IncomeNotification.parse("Yape", "Recibiste S/ 3.00 en tu Yape")!!
        assertEquals(300L, income.amountCents)
        assertNull(income.person)
    }

    @Test fun personFromStoredText() {
        assertEquals("Juan Per*", IncomeNotification.personFrom("Yape! Juan Per* te envió un pago por S/ 0.4"))
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
