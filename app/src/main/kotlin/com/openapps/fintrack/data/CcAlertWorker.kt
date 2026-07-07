/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class CcAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val dbFile = applicationContext.getDatabasePath("expenses_database")
        val ef = File(dbFile.path + ".xpt")
        
        // Safety: If database is encrypted at rest, background workers cannot run
        if (ef.exists() && !dbFile.exists()) {
            Log.w("CcAlertWorker", "Database is encrypted. Skipping background processing.")
            return Result.success()
        }
        
        val db = try { 
            AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.GlobalScope) 
        } catch(e: Exception) { 
            return Result.success() 
        }
        val dao = db.expenseDao()
        // Priority 1: Issue 1 - Use fixed UTC for calculations to avoid timezone shifts
        val today = LocalDate.now(ZoneId.of("UTC"))

        // 1. Credit Card & Subscription Alerts
        if (prefs.getBoolean("cc_alert_enabled", false)) {
            processAlerts(dao, today)
        }

        // 2. Loan Auto-Recording
        processLoanAutoRecords(dao, today)

        return Result.success()
    }

    private suspend fun processAlerts(dao: ExpenseDao, today: LocalDate) {
        val accounts = dao.getEnabledAccounts().first()
        val minorHeads = dao.getAllMinorHeads().first()

        accounts.filter { it.minorHeadId != null }.forEach { acc ->
            val minor = minorHeads.find { it.id == acc.minorHeadId }
            if (minor?.majorHeadId == 8) { // Credit Card
                val daysPost = acc.paymentDueDate?.toIntOrNull() ?: return@forEach
                val endDay = acc.billingCycleEnd?.toIntOrNull() ?: return@forEach
                val startDay = acc.billingCycleStart?.toIntOrNull() ?: return@forEach
                
                // Calculate current cycle end
                var cycleEnd = try {
                    val lastDay = today.lengthOfMonth()
                    LocalDate.of(today.year, today.monthValue, endDay.coerceAtMost(lastDay))
                } catch (e: Exception) { return@forEach }

                if (cycleEnd.isAfter(today)) {
                    cycleEnd = cycleEnd.minusMonths(1)
                }

                val dueDate = cycleEnd.plusDays(daysPost.toLong())

                if (ChronoUnit.DAYS.between(today, dueDate) == 2L) {
                    val amount = calculateCcPayable(dao, acc, startDay, endDay, dueDate, daysPost)
                    if (amount != 0.0) {
                        sendNotification(acc.name, amount, dueDate)
                    }
                }
            }
        }

        // Subscription Alerts
        val allTransactions = dao.getAllTransactionsWithDetails().first()
        val statuses = dao.getAllSubscriptionStatuses().first()

        allTransactions.filter { it.transaction.subName != null }
            .groupBy { it.transaction.subName!! }
            .entries.forEach { entry ->
                val name = entry.key
                val txns = entry.value
                
                val isStopped = statuses.find { it.subName == name }?.isStopped ?: false
                if (isStopped) return@forEach

                val sortedTxns = txns.sortedByDescending { it.transaction.date }
                val lastTxn = sortedTxns.first()
                val freq = lastTxn.transaction.subFrequency ?: 1
                val lastDate = LocalDate.parse(lastTxn.transaction.date)
                val nextDue = lastDate.plusMonths(freq.toLong())

                if (ChronoUnit.DAYS.between(today, nextDue) == 2L) {
                    val isTransfer = lastTxn.transaction.categoryId == null && lastTxn.transaction.toAccountId != null
                    sendSubNotification(name, lastTxn.transaction.amount, nextDue, isTransfer)
                }
            }
    }

    private suspend fun processLoanAutoRecords(dao: ExpenseDao, today: LocalDate) {
        val loans = dao.getAllActiveLoans().first()
        val allCategories = dao.getAllCategories().first()
        val intExpCat = allCategories.find { it.name.equals("Interest expense - Loans", ignoreCase = true) }
        val intIncCat = allCategories.find { it.name.equals("Interest Income - Loans", ignoreCase = true) }
        val intMiscCat = allCategories.find { it.name.equals("Interest Exp Misc", ignoreCase = true) }
        
        // Suspense Account for loans that don't update bank balance
        val suspenseAcc = dao.getSuspenseAccountInternal() ?: run {
            val othersMajorId = dao.getAllMajorHeads().first().find { it.name.equals("Others", true) }?.id
            val defaultMinorId = othersMajorId?.let { dao.getMinorHeadsByMajor(it).first().find { it.name.equals("Default", true) }?.id }
            dao.upsertAccount(Account(name = "Suspense", type = "asset", openingBalance = 0.0, minorHeadId = defaultMinorId, isEnabled = true))
            dao.getSuspenseAccountInternal()
        }

        loans.filter { it.isAutoRecordEnabled }.forEach { loan ->
            var nextDue = Instant.ofEpochMilli(loan.nextDueDate).atZone(ZoneId.of("UTC")).toLocalDate()
            val effectiveSourceId = loan.sourceAccountId ?: suspenseAcc?.id ?: return@forEach

            while (!nextDue.isAfter(today) && !loan.isClosed) {
                // ...
                recordRepayment(dao, loan, nextDue, intExpCat, intIncCat, intMiscCat, effectiveSourceId)
                
                val updatedLoan = dao.getLoanById(loan.id) ?: break
                if (updatedLoan.isClosed) break
                nextDue = Instant.ofEpochMilli(updatedLoan.nextDueDate).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }
    }

    private fun sendLoanClosureNotification(loan: Loan) {
        val channelId = "cc_alerts"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Due Date Alerts", NotificationManager.IMPORTANCE_HIGH))
        }

        val message = "Loan '${loan.name}' has reached approximately zero balance. Auto-record disabled."
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Loan Fully Repaid")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(("LOAN_CLOSE_" + loan.id).hashCode(), notification)
    }

    private suspend fun recordRepayment(
        dao: ExpenseDao, 
        loan: Loan, 
        dueDate: LocalDate, 
        intExpCat: Category?, 
        intIncCat: Category?, 
        intMiscCat: Category?,
        sourceId: Int
    ) {
        val dateStr = dueDate.format(DateTimeFormatter.ISO_DATE)
        val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        
        // 1. Calculate Principal/Interest split
        val split = LoanCalculator.calculatePaymentSplit(
            loan.outstandingBalance,
            loan.interestRateAnnual / getMultiplier(loan.frequency),
            loan.installmentAmount,
            gapInterest = loan.gapInterest,
            isFirstPayment = loan.periodsPassed == 0
        )

        val actualTotal = if (loan.isActualEmiDifferent) loan.actualRepaymentAmount else loan.installmentAmount
        val diff = actualTotal - loan.installmentAmount

        if (loan.loanType == "BORROWING") {
            // 2. Transfer: Source (Bank/Suspense) -> Loan Account (Amount = EMI)
            dao.insertTransaction(Transaction(
                date = dateStr,
                time = timeStr,
                accountId = sourceId,
                toAccountId = loan.accountId,
                categoryId = null,
                amount = loan.installmentAmount,
                note = "Loan EMI Repayment: ${loan.name}",
                subName = "LOAN:${loan.id}",
                tags = loan.tags
            ))

            // 3. Charge off Interest from Loan Account
            if (intExpCat != null) {
                dao.insertTransaction(Transaction(
                    date = dateStr,
                    time = timeStr,
                    accountId = loan.accountId,
                    categoryId = intExpCat.id,
                    amount = split.second,
                    note = "Loan Interest Accrual: ${loan.name}",
                    tags = loan.tags
                ))
            }

            // 4. Charge off difference to Interest Exp Misc (from Source)
            if (diff != 0.0 && intMiscCat != null) {
                dao.insertTransaction(Transaction(
                    date = dateStr,
                    time = timeStr,
                    accountId = sourceId,
                    categoryId = intMiscCat.id,
                    amount = diff,
                    note = "Loan Repayment Adjustment: ${loan.name}",
                    tags = loan.tags
                ))
            }
        } else {
            // LENDING: Money comes back to us.
            // 2. Transfer: Loan Account -> Source (Bank/Suspense) (Amount = EMI)
            dao.insertTransaction(Transaction(
                date = dateStr,
                time = timeStr,
                accountId = loan.accountId,
                toAccountId = sourceId,
                categoryId = null,
                amount = loan.installmentAmount,
                note = "Loan EMI Recovery: ${loan.name}",
                subName = "LOAN:${loan.id}",
                tags = loan.tags
            ))

            // 3. Add Interest Income to Loan Account (to balance principal reduction)
            if (intIncCat != null) {
                dao.insertTransaction(Transaction(
                    date = dateStr,
                    time = timeStr,
                    accountId = loan.accountId,
                    categoryId = intIncCat.id,
                    amount = split.second,
                    note = "Loan Interest Earned: ${loan.name}",
                    tags = loan.tags
                ))
            }

            // 4. Adjustment (Income or Expense based on Diff) from Source
            if (diff != 0.0 && intMiscCat != null) {
                dao.insertTransaction(Transaction(
                    date = dateStr,
                    time = timeStr,
                    accountId = sourceId,
                    categoryId = intMiscCat.id,
                    amount = diff,
                    note = "Loan Recovery Adjustment: ${loan.name}",
                    tags = loan.tags
                ))
            }
        }

        // 4. Update Loan Record
        val updatedLoan = loan.copy(
            totalInterestPaid = loan.totalInterestPaid + split.second,
            totalPrincipalRepaid = loan.totalPrincipalRepaid + split.first,
            outstandingBalance = (loan.outstandingBalance - split.first).coerceAtLeast(0.0),
            periodsPassed = loan.periodsPassed + 1,
            nextDueDate = LoanCalculator.getNextDate(loan.nextDueDate, loan.frequency),
            isClosed = abs(loan.outstandingBalance - split.first) < 1.0
        )
        dao.upsertLoan(updatedLoan)
        
        // 5. Save LoanRepayment record
        dao.upsertLoanRepayment(LoanRepayment(
            loanId = loan.id,
            amountPaid = actualTotal,
            principalPortion = split.first,
            interestPortion = split.second,
            paymentDate = dueDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            isScheduled = true
        ))
    }

    private fun getMultiplier(frequency: String) = when(frequency) {
        "MONTHLY" -> 12.0
        "QUARTERLY" -> 4.0
        "HALF_YEARLY" -> 2.0
        "YEARLY" -> 1.0
        else -> 12.0
    }

    private suspend fun calculateCcPayable(dao: ExpenseDao, acc: Account, startDay: Int, endDay: Int, dueDate: LocalDate, daysPost: Int): Double {
        // Reconstruct the specific cycle that led to this dueDate
        var cycleEnd = dueDate.minusDays(daysPost.toLong())
        
        var cycleStart = try {
            var s = cycleEnd.withDayOfMonth(startDay.coerceAtMost(cycleEnd.lengthOfMonth()))
            if (s.isAfter(cycleEnd) || s.isEqual(cycleEnd)) {
                s = s.minusMonths(1)
            }
            s
        } catch (e: Exception) { cycleEnd.minusMonths(1) }

        val startStr = cycleStart.format(DateTimeFormatter.ISO_DATE)
        val endStr = cycleEnd.format(DateTimeFormatter.ISO_DATE)
        
        val txns = dao.getAccountTransactionsByDateRange(acc.id, startStr, endStr).first()
        return txns.sumOf { 
            if (it.transaction.toAccountId == acc.id) it.transaction.amount 
            else -it.transaction.amount 
        }
    }

    private fun sendNotification(name: String, amount: Double, dueDate: LocalDate) {
        val channelId = "cc_alerts"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Due Date Alerts", NotificationManager.IMPORTANCE_HIGH))
        }

        val formattedAmount = String.format("%.2f", abs(amount))
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Credit Card Payment Due")
            .setContentText("$name payment of $formattedAmount is due on $dueDate")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(name.hashCode(), notification)
    }

    private fun sendSubNotification(name: String, amount: Double, dueDate: LocalDate, isTransfer: Boolean) {
        val channelId = "cc_alerts"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Due Date Alerts", NotificationManager.IMPORTANCE_HIGH))
        }

        val formattedAmount = String.format("%.2f", amount)
        val title = if (isTransfer) "Recurring Transfer Due" else "Subscription Payment Due"
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("$name of $formattedAmount is due on $dueDate")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(("SUB_" + name).hashCode(), notification)
    }
}
