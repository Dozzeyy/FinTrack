/*
 * FinTrack
 * Copyright (C) 2026 Bhuvan (app.upstream242@passmail.com)
 * SPDX-License-Identifier: GPL-3.0-or-later

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 */

package com.openapps.fintrack.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openapps.fintrack.data.TransactionLegacy
import com.openapps.fintrack.domain.repository.FinanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@HiltWorker
class CcAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FinanceRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val today = LocalDate.now(ZoneId.of("UTC"))

        if (prefs.getBoolean("cc_alert_enabled", false)) {
            processAlerts(repository, today)
        }

        processLoanAutoRecords(repository, today)

        processSubscriptionAutoRecords(repository, today)

        return Result.success()
    }

    private suspend fun processSubscriptionAutoRecords(repository: FinanceRepository, today: LocalDate) {
        val masterSubs = repository.getAllSubscriptionsMaster().first().filter { it.isEnabled }
        val statuses = repository.getAllSubscriptionStatuses().first()
        val allTxns = repository.getAllTransactionsWithDetails().first()
        
        for (sub in masterSubs) {
            val status = statuses.find { it.subName == sub.name }
            val isStopped = status?.isStopped ?: false
            val isAutoRecordEnabled = status?.isAutoRecordEnabled ?: true
            
            if (isStopped || !isAutoRecordEnabled) continue
            
            val subTxns = allTxns.filter { it.transaction.subName == sub.name }
            if (subTxns.isEmpty()) continue
            
            val lastTxn = subTxns.maxBy { it.transaction.date }
            val lastDate = LocalDate.parse(lastTxn.transaction.date)
            val freq = sub.frequency.toLong().coerceAtLeast(1)
            
            var nextDue = lastDate.plusMonths(freq)
            
            while (!nextDue.isAfter(today)) {
                val dateStr = nextDue.format(DateTimeFormatter.ISO_DATE)
                val timeStr = lastTxn.transaction.time
                
                val type = if (sub.isTransfer) "transfer" else (lastTxn.categoryType ?: "expense")
                val prefix = when(type) { "income"->"INC"; "expense"->"EXP"; "transfer"->"TNF"; else->"TXN" }
                
                val lastNum = repository.getLastTransactionNumber(prefix)
                val nextSerial = (lastNum?.split("/")?.last()?.toIntOrNull() ?: 99999) + 1
                val year = nextDue.year
                val txnNumber = "$prefix/$year/$nextSerial"
                
                val originalNote = lastTxn.transaction.note ?: sub.name
                val autoNote = if (originalNote.startsWith("[Auto-Recorded]")) originalNote else "[Auto-Recorded] $originalNote"

                val newTxn = lastTxn.transaction.copy(
                    id = 0,
                    date = dateStr,
                    time = timeStr,
                    transactionNumber = txnNumber,
                    note = autoNote,
                    editedAt = System.currentTimeMillis()
                )
                
                repository.insertTransactionLegacy(newTxn)
                Log.d("CcAlertWorker", "Auto-recorded subscription: ${sub.name} for date $dateStr")
                
                nextDue = nextDue.plusMonths(freq)
            }
        }
    }

    private suspend fun processAlerts(repository: FinanceRepository, today: LocalDate) {
        val accounts = repository.getEnabledAccounts().first()
        val minorHeads = repository.getAllMinorHeads().first()

        accounts.filter { it.minorHeadId != null }.forEach { acc ->
            val minor = minorHeads.find { it.id == acc.minorHeadId }
            if (minor?.majorHeadId == 8) { 
                val daysPost = acc.paymentDueDate?.toIntOrNull() ?: return@forEach
                val endDay = acc.billingCycleEnd?.toIntOrNull() ?: return@forEach
                val startDay = acc.billingCycleStart?.toIntOrNull() ?: return@forEach
                
                var cycleEnd = try {
                    val lastDay = today.lengthOfMonth()
                    LocalDate.of(today.year, today.monthValue, endDay.coerceAtMost(lastDay))
                } catch (e: Exception) { return@forEach }

                if (cycleEnd.isAfter(today)) {
                    cycleEnd = cycleEnd.minusMonths(1)
                }

                val dueDate = cycleEnd.plusDays(daysPost.toLong())

                if (ChronoUnit.DAYS.between(today, dueDate) == 2L) {
                    val amount = calculateCcPayable(repository, acc, startDay, endDay, dueDate, daysPost)
                    if (amount != 0.0) {
                        sendNotification(acc.name, amount, dueDate)
                    }
                }
            }
        }

        val allTransactions = repository.getAllTransactionsWithDetails().first()
        val statuses = repository.getAllSubscriptionStatuses().first()

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

    private suspend fun processLoanAutoRecords(repository: FinanceRepository, today: LocalDate) {
        val loans = repository.getAllActiveLoans().first()
        val allCategories = repository.getAllCategories().first()
        val intExpCat = allCategories.find { it.name.equals("Interest expense - Loans", ignoreCase = true) }
        val intIncCat = allCategories.find { it.name.equals("Interest Income - Loans", ignoreCase = true) }
        val intMiscCat = allCategories.find { it.name.equals("Interest Exp Misc", ignoreCase = true) }
        
        // Suspense Account for loans that don't update bank balance
        val suspenseAcc = repository.getSuspenseAccountInternal() ?: run {
            val othersMajorId = repository.getAllMajorHeads().first().find { it.name.equals("Others", true) }?.id
            val defaultMinorId = othersMajorId?.let { repository.getMinorHeadsByMajor(it).first().find { it.name.equals("Default", true) }?.id }
            repository.upsertAccount(Account(name = "Suspense", type = "asset", openingBalance = 0.0, minorHeadId = defaultMinorId, isEnabled = true))
            repository.getSuspenseAccountInternal()
        }

        loans.filter { it.isAutoRecordEnabled }.forEach { loan ->
            var nextDue = Instant.ofEpochMilli(loan.nextDueDate).atZone(ZoneId.of("UTC")).toLocalDate()
            val effectiveSourceId = loan.sourceAccountId ?: suspenseAcc?.id ?: return@forEach

            while (!nextDue.isAfter(today) && !loan.isClosed) {

                recordRepayment(repository, loan, nextDue, intExpCat, intIncCat, intMiscCat, effectiveSourceId)
                
                val updatedLoan = repository.getLoanById(loan.id) ?: break
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
        repository: FinanceRepository, 
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
            repository.insertTransactionLegacy(TransactionLegacy(
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
                repository.insertTransactionLegacy(TransactionLegacy(
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
                repository.insertTransactionLegacy(TransactionLegacy(
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
            // LENDING:
            // 2. Transfer: Loan Account -> Source (Bank/Suspense) (Amount = EMI)
            repository.insertTransactionLegacy(TransactionLegacy(
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
                repository.insertTransactionLegacy(TransactionLegacy(
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
                repository.insertTransactionLegacy(TransactionLegacy(
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
        repository.upsertLoan(updatedLoan)
        
        // 5. Save LoanRepayment record
        repository.upsertLoanRepayment(LoanRepayment(
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

    private suspend fun calculateCcPayable(repository: FinanceRepository, acc: Account, startDay: Int, endDay: Int, dueDate: LocalDate, daysPost: Int): Double {

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
        
        val txns = repository.getAccountTransactionsByDateRange(acc.id, startStr, endStr).first()
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
