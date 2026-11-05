package com.example.mibbudget

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionPresentationTest {
    @Test fun favaraCsvDescriptionProvidesRecipient() {
        val detail = "Favara Transfer Web: 01-11-2026 20-29-31 | MADVIPS20261101202929 129b545f7b3 | BML - FIYAVALHU PRINTS, -"
        assertEquals("Transfer · FIYAVALHU PRINTS", TransactionPresentation.label(detail))
    }

    @Test fun savedCsvRecipientEnrichesGenericSmsDisplay() {
        assertEquals("Transfer · FIYAVALHU PRINTS",
            TransactionPresentation.displayLabel("Favara transfer", "Transfers", "FIYAVALHU PRINTS"))
    }

    @Test fun unknownTransferRemainsGenericRatherThanGuessing() {
        assertEquals("Favara transfer",
            TransactionPresentation.displayLabel("Favara transfer", "Transfers", null))
    }
}
