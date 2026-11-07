package com.example.mibbudget

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Unique bank identifiers take priority over posted dates and merchant text.
 * No amount-only, approximate-time or shared-merchant automatic matching.
 */
object CsvSmsMatcher {
    private val generic=setOf("transfer","favara","web","pos","purchase","payment","mvr","bank","transaction","account","from","your","processed","internet","approval","code")
    private fun words(s:String):Set<String> =
        Regex("[A-Z0-9]{3,}").findAll(s.uppercase(Locale.ROOT))
            .map { it.value }.filter { it.lowercase(Locale.ROOT) !in generic && !it.all(Char::isDigit) }.toSet()

    /** SMS: Approval Code: 519478. CSV: 73601335-519478@IHSAAN FIHAARA. */
    fun approvalCode(value:String): String? {
        val sms=Regex("""(?i)\bapproval\s*(?:code)?\s*[:#-]?\s*([A-Z0-9]{6,12})\b""")
            .find(value)?.groupValues?.get(1)
        val csv=Regex("""(?i)\b[A-Z0-9]+-([A-Z0-9]{6,12})@""")
            .find(value)?.groupValues?.get(1)
        return (sms ?: csv)?.uppercase(Locale.ROOT)
    }

    /** SMS long reference and CSV shorter reference may share a stable final numeric component. */
    fun transferReference(value:String):String? {
        val explicit=Regex("""(?i)\b(?:ref(?:erence)?(?:\.?\s*no\.?)?)\s*[:.#-]?\s*([A-Z0-9-]{6,60})\b""")
            .find(value)?.groupValues?.get(1)
        return explicit?.uppercase(Locale.ROOT)
    }
    private fun refTail(value:String):String? =
        value.substringAfterLast('-').takeIf { it.length>=7 && it.all(Char::isDigit) }

    /** Embedded bank transaction clock, distinct from CSV posting date. */
    fun actualCsvTime(description:String):String? {
        val m=Regex("""\b(\d{2})-(\d{2})-(\d{4})\s+(\d{2})-(\d{2})-(\d{2})\b""").find(description) ?: return null
        val iso="${m.groupValues[3]}-${m.groupValues[2]}-${m.groupValues[1]}T${m.groupValues[4]}:${m.groupValues[5]}:${m.groupValues[6]}"
        return runCatching { LocalDateTime.parse(iso).toString() }.getOrNull()
    }

    fun isConfirmedDuplicate(smsAmount:Long,smsDescription:String,smsTime:String,
                             csvAmount:Long,csvDescription:String,csvTime:String):Boolean {
        if(smsAmount!=csvAmount || smsAmount==0L) return false
        val actual=actualCsvTime(csvDescription) ?: csvTime
        val diff=runCatching { kotlin.math.abs(ChronoUnit.DAYS.between(
            LocalDate.parse(smsTime.take(10)),LocalDate.parse(actual.take(10)))) }.getOrNull() ?: return false
        if(diff>2) return false

        val smsApproval=approvalCode(smsDescription)
        val csvApproval=approvalCode(csvDescription)
        if(smsApproval!=null || csvApproval!=null) {
            return smsApproval!=null && smsApproval==csvApproval && smsAmount<0L
        }

        val smsRef=transferReference(smsDescription)
        val csvRef=transferReference(csvDescription)
        if(smsRef!=null && csvRef!=null) {
            val smsTail=refTail(smsRef)
            val csvTail=refTail(csvRef)
            return smsRef==csvRef || (smsTail!=null && smsTail==csvTail)
        }
        // A missing unique identifier must never override a conflicting identifier.
        if(smsRef!=null || csvRef!=null) return false

        val exactTime=runCatching {
            LocalDateTime.parse(smsTime.replace(' ','T'))==LocalDateTime.parse(actual.replace(' ','T'))
        }.getOrDefault(false)
        val shared=words(smsDescription).intersect(words(csvDescription))
        return exactTime && smsTime.take(10)==actual.take(10) && shared.any { it.length>=5 }
            && !smsDescription.contains("Favara",true) && !csvDescription.contains("Favara",true)
    }
}
