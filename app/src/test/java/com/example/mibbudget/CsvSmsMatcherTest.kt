package com.example.mibbudget

import org.junit.Assert.*
import org.junit.Test

class CsvSmsMatcherTest {
    @Test fun sameAmountDifferentMerchantMustNotBeDropped() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(-6500,"POS purchase: COFFEE BEAN",
            "2026-10-23T15:56:00",-6500,"POS Payment: OTHER STORE",
            "2026-10-23T14:00:00"))
    }
    @Test fun nearbyRepeatedMerchantPaymentIsNotConfirmed() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(-6500,"POS purchase COFFEE BEAN",
            "2026-10-23T15:56:00",-6500,"POS Payment COFFEE BEAN",
            "2026-10-23T15:58:00"))
    }
    @Test fun sameMerchantAndExactTimeIsConfirmed() {
        assertTrue(CsvSmsMatcher.isConfirmedDuplicate(-6500,"POS purchase COFFEE BEAN",
            "2026-10-23T15:56:00",-6500,"POS Payment COFFEE BEAN","2026-10-23T15:56:00"))
    }
    @Test fun genericWordsAndExactTimeAreStillAmbiguous() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(-6500,"Favara transfer account",
            "2026-10-23T15:56:00",-6500,"Internet transfer account","2026-10-23T15:56:00"))
    }
    @Test fun sameAmountDifferentDayIsNotConfirmed() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(-6500,"COFFEE BEAN",
            "2026-10-23T15:56:00",-6500,"COFFEE BEAN",
            "2026-10-20T15:56:00"))
    }
    @Test fun posApprovalLinksAcrossPostingDateAndDifferentClocks() {
        assertTrue(CsvSmsMatcher.isConfirmedDuplicate(-2600,
            "POS purchase: IHSAAN FIHAARA 3 (Approval Code: 519478)",
            "2026-11-01T11:14:00",-2600,
            "POS Payment: 02-11-2026 02-28-57 | 73601335-519478@IHSAAN FIHAARA 3",
            "2026-11-02T00:00:00"))
    }
    @Test fun posApprovalStoredAsReferenceSuffixStillMatches() {
        assertTrue(CsvSmsMatcher.isConfirmedDuplicate(-4814,
            "POS purchase: AVAS POINT (Approval Code: 519478)",
            "2026-10-29T18:23:00", -4814,
            "POS Payment: (Ref: 73601335-519478) AVAS POINT",
            "2026-10-30T00:00:00"))
    }

    @Test fun differingApprovalCodesDoNotCollapseRepeatedPurchases() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(-2600,
            "POS purchase: IHSAAN FIHAARA 3 (Approval Code: 519479)",
            "2026-11-01T11:14:00",-2600,
            "POS Payment: 02-11-2026 02-28-57 | 73601335-519478@IHSAAN FIHAARA 3",
            "2026-11-02T00:00:00"))
    }
    @Test fun favaraReferenceTailMatchesAcrossDifferentPrefixes() {
        assertTrue(CsvSmsMatcher.isConfirmedDuplicate(-45000,
            "Favara transfer (Ref: 703870621-818985107)",
            "2026-11-01T20:29:31",-45000,
            "Favara Transfer Web: 01-11-2026 20-29-31 | BML - FIYAVALHU PRINTS (Ref: 1-818985107)",
            "2026-11-01T00:00:00"))
    }
    @Test fun favaraDifferentReferenceMustRemainSeparate() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(-45000,
            "Favara transfer (Ref: 703870621-818985108)",
            "2026-11-01T20:29:31",-45000,
            "Favara Transfer Web: 01-11-2026 20-29-31 (Ref: 1-818985107)",
            "2026-11-01T00:00:00"))
    }
    @Test fun genericFavaraSameClockRemainsAmbiguous() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(35000,
            "Favara incoming transfer","2026-11-02T19:38:34",
            35000,"Favara Credit: 02-11-2026 19-38-34",
            "2026-11-02T00:00:00"))
    }

}
