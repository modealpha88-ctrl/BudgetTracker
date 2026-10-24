package com.example.mibbudget

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.YearMonth

/** Offline report. Never sends financial records to a server. */
object BudgetPdfReport {
    fun write(output:OutputStream, month:YearMonth, transactions:List<Tx>, budgets:Map<String,Long>) {
        val pdf=PdfDocument()
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=12f }
        var pageNumber=0
        var page:PdfDocument.Page?=null
        var y=0f
        fun newPage() {
            page?.let { pdf.finishPage(it) }
            pageNumber++
            page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
            y=46f
        }
        fun line(text:String) {
            if(page==null || y>795f) newPage()
            page!!.canvas.drawText(text.take(90),40f,y,paint)
            y+=19f
        }
        try {
            line("Budget Tracker - Monthly Report: $month")
            line("Generated from locally saved transactions; not a bank statement.")
            val monthTx=transactions.filter { it.timestamp.startsWith(month.toString()) }
            val income=monthTx.filter { it.amountLaari>0 && it.category!="Internal Transfer" }.sumOf { it.amountLaari }
            val expenses=monthTx.filter { it.amountLaari<0 && it.category!="Internal Transfer" }.sumOf { -it.amountLaari }
            line("Income: ${Money.format(income)}")
            line("Expenses: ${Money.format(expenses)}")
            line("Net: ${Money.format(income-expenses)}")
            val previous=FinanceCalculations.monthExpenses(transactions,month.minusMonths(1))
            line("Previous month expenses: ${Money.format(previous)}")
            line("Expense change: ${Money.format(expenses-previous)}")
            val highestDay=monthTx.filter(FinanceCalculations::isExpense)
                .groupBy { it.timestamp.take(10) }
                .mapValues { (_,rows) -> rows.sumOf { -it.amountLaari } }
                .maxByOrNull { it.value }
            if(highestDay!=null) line("Highest spending day: ${highestDay.key} - ${Money.format(highestDay.value)}")
            val forecast=FinanceCalculations.spendingForecast(transactions,month,java.time.LocalDate.now())
            line("Estimated month-end expense: ${Money.format(forecast)}")
            line("Spending by category")
            monthTx.filter { it.amountLaari<0 && it.category!="Internal Transfer" }.groupBy { it.category }.toSortedMap().forEach { (cat,rows) ->
                val spent=rows.sumOf { -it.amountLaari }
                line("$cat: ${Money.format(spent)}" + (budgets[cat]?.let { " / limit ${Money.format(it)}" } ?: ""))
            }
            line("Transactions (${monthTx.size})")
            monthTx.sortedByDescending { it.timestamp }.forEach { t ->
                line("${t.timestamp.take(10)}  ${Money.format(t.amountLaari)}  ${t.category}  ${t.description.replace("\n"," ").take(35)}")
            }
            page?.let { pdf.finishPage(it) }
            pdf.writeTo(output)
        } finally { pdf.close() }
    }
}
