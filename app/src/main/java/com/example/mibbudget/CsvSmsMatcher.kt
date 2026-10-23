package com.example.mibbudget

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Pure conservative matcher. Ambiguous same-amount payments remain separate for review. */
object CsvSmsMatcher {
    private val generic=setOf("transfer","favara","web","pos","purchase","payment","mvr","bank","transaction","account","from","your","processed","internet")
    private fun words(s:String):Set<String> =
        Regex("[A-Z0-9]{3,}").findAll(s.uppercase(Locale.ROOT))
            .map { it.value }.filter { it !in generic && !it.all(Char::isDigit) }.toSet()
    fun isConfirmedDuplicate(smsAmount:Long,smsDescription:String,smsTime:String,
                             csvAmount:Long,csvDescription:String,csvTime:String):Boolean {
        if(smsAmount!=csvAmount || smsAmount==0L) return false
        val smsDate=smsTime.take(10)
        val csvDate=csvTime.take(10)
        val distance=try { kotlin.math.abs(ChronoUnit.DAYS.between(
            java.time.LocalDate.parse(smsDate),java.time.LocalDate.parse(csvDate))) }
            catch(_:Exception) { return false }
        if(distance>1) return false
        val shared=words(smsDescription).intersect(words(csvDescription))
        if(shared.any { it.length>=5 }) return true
        val precise=try {
            val a=LocalDateTime.parse(smsTime)
            val b=LocalDateTime.parse(csvTime)
            kotlin.math.abs(ChronoUnit.MINUTES.between(a,b))<=3
        } catch(_:Exception) { false }
        return precise && smsDate==csvDate
    }
}
