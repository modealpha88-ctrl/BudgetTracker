package com.example.mibbudget

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Pure conservative matcher. Ambiguous same-amount payments remain separate for review. */
object CsvSmsMatcher {
    private val generic=setOf("transfer","favara","web","pos","purchase","payment","mvr","bank","transaction","account","from","your","processed","internet")
    private fun words(s:String):Set<String> =
        Regex("[A-Z0-9]{3,}").findAll(s.uppercase(Locale.ROOT))
            .map { it.value }.filter { it.lowercase(Locale.ROOT) !in generic && !it.all(Char::isDigit) }.toSet()
    fun isConfirmedDuplicate(smsAmount:Long,smsDescription:String,smsTime:String,
                             csvAmount:Long,csvDescription:String,csvTime:String):Boolean {
        if(smsAmount!=csvAmount || smsAmount==0L) return false
        val smsDate=smsTime.take(10)
        val csvDate=csvTime.take(10)
        val distance=try { kotlin.math.abs(ChronoUnit.DAYS.between(
            java.time.LocalDate.parse(smsDate),java.time.LocalDate.parse(csvDate))) }
            catch(_:Exception) { return false }
        if(distance>1) return false
        val reference = Regex("(?i)(?:ref(?:erence)?(?:\\.| no\\.)?|approval code)[: .#-]*([A-Z0-9][A-Z0-9-]{5,})")
        val aRef = reference.find(smsDescription)?.groupValues?.get(1)
        val bRef = reference.find(csvDescription)?.groupValues?.get(1)
        if(aRef != null && bRef != null) return aRef.equals(bRef, true) && smsDate == csvDate
        // Merchant/date or a three-minute window cannot distinguish repeated purchases.
        val shared = words(smsDescription).intersect(words(csvDescription))
        val exactTime = try { LocalDateTime.parse(smsTime.replace(' ', 'T')) == LocalDateTime.parse(csvTime.replace(' ', 'T')) } catch(_:Exception) { false }
        return exactTime && shared.any { it.length >= 5 } && smsDate == csvDate
    }
}
