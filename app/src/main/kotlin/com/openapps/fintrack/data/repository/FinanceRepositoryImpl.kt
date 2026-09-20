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

package com.openapps.fintrack.data.repository

import android.content.Context
import com.openapps.fintrack.data.*
import com.openapps.fintrack.domain.repository.FinanceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FinanceRepository {
    private val database: AppDatabase
        get() = AppDatabase.getDatabase(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val dao: ExpenseDao
        get() = database.expenseDao()
    // Database maintenance
    override fun checkpoint() = database.checkpoint()
    override fun prepareForBackup() = database.prepareForBackup()
    
    // Accounts
    override fun getAllAccounts() = dao.getAllAccounts()
    override fun getAllAccountsInternal() = dao.getAllAccountsInternal()
    override suspend fun getSuspenseAccountInternal() = dao.getSuspenseAccountInternal()
    override fun getEnabledAccounts() = dao.getEnabledAccounts()
    override suspend fun upsertAccount(account: Account) = dao.upsertAccount(account)
    override suspend fun updateAccount(account: Account) = dao.updateAccount(account)
    override suspend fun deleteAccount(account: Account) = dao.deleteAccount(account)
    override fun getAccountBalances(asOfDate: String) = dao.getAccountBalances(asOfDate)

    // Categories
    override fun getAllCategories() = dao.getAllCategories()
    override fun getEnabledCategories() = dao.getEnabledCategories()
    override fun getEnabledCategoriesByType(type: String) = dao.getEnabledCategoriesByType(type)
    override suspend fun upsertCategory(category: Category) = dao.upsertCategory(category)
    override suspend fun updateCategory(category: Category) = dao.updateCategory(category)
    override suspend fun deleteCategory(category: Category) = dao.deleteCategory(category)

    // Tags
    override fun getAllTags() = dao.getAllTags()
    override fun getEnabledTags() = dao.getEnabledTags()
    override suspend fun upsertTag(tag: Tag) = dao.upsertTag(tag)
    override suspend fun updateTag(tag: Tag) = dao.updateTag(tag)
    override suspend fun deleteTag(tag: Tag) = dao.deleteTag(tag)

    // Goals
    override fun getAllGoals() = dao.getAllGoals()
    override suspend fun upsertGoal(goal: Goal) = dao.upsertGoal(goal)
    override suspend fun deleteGoal(goal: Goal) = dao.deleteGoal(goal)
    override fun getAllocationsForGoal(goalId: Int) = dao.getAllocationsForGoal(goalId)
    override fun getAllAllocations() = dao.getAllAllocations()
    override suspend fun getAllocation(goalId: Int, accountId: Int) = dao.getAllocation(goalId, accountId)
    override suspend fun upsertAllocation(allocation: GoalAccountAllocation) = dao.upsertAllocation(allocation)
    override suspend fun deleteAllocation(goalId: Int, accountId: Int) = dao.deleteAllocation(goalId, accountId)
    override fun getAllGoalRules() = dao.getAllGoalRules()
    override suspend fun upsertGoalRule(rule: GoalRule) = dao.upsertGoalRule(rule)
    override suspend fun deleteGoalRule(rule: GoalRule) = dao.deleteGoalRule(rule)
    override suspend fun insertGoalTransaction(gt: GoalTransaction) = dao.insertGoalTransaction(gt)
    override fun getTransactionsForGoal(goalId: Int) = dao.getTransactionsForGoal(goalId)
    override suspend fun insertAllocationHistory(history: GoalAllocationHistory) = dao.insertAllocationHistory(history)
    override fun getAllocationHistoryForGoal(goalId: Int) = dao.getAllocationHistoryForGoal(goalId)

    // Budgets
    override fun getAllBudgets() = dao.getAllBudgets()
    override suspend fun upsertBudget(budget: Budget) = dao.upsertBudget(budget)
    override suspend fun deleteBudget(budget: Budget) = dao.deleteBudget(budget)
    override fun getBudgetsWithRelations() = dao.getBudgetsWithRelations()
    override suspend fun insertBudgetCategory(junction: BudgetCategory) = dao.insertBudgetCategory(junction)
    override suspend fun insertBudgetAccount(junction: BudgetAccount) = dao.insertBudgetAccount(junction)
    override suspend fun deleteBudgetCategories(budgetId: Int) = dao.deleteBudgetCategories(budgetId)
    override suspend fun deleteBudgetAccounts(budgetId: Int) = dao.deleteBudgetAccounts(budgetId)

    // Transactions
    override fun getTransactionsDetailed() = dao.getTransactionsDetailed()
    override suspend fun insertTransactionHeader(header: TransactionHeader) = dao.insertTransactionHeader(header)
    override suspend fun insertTransactionLines(lines: List<TransactionLine>) = dao.insertTransactionLines(lines)
    override suspend fun insertTransactionTags(tags: List<TransactionTag>) = dao.insertTransactionTags(tags)
    override suspend fun deleteTransactionHeader(id: Int) = dao.deleteTransactionHeader(id)
    override suspend fun deleteTransactionLines(headerId: Int) = dao.deleteTransactionLines(headerId)
    override suspend fun deleteTransactionTags(headerId: Int) = dao.deleteTransactionTags(headerId)
    override suspend fun getLastTransactionHeaderNumber(prefix: String) = dao.getLastTransactionHeaderNumber(prefix)
    override suspend fun updateLineReconciliation(lineId: Int, reconciled: Boolean) = dao.updateLineReconciliation(lineId, reconciled)
    override suspend fun updateTransactionStatus(headerId: Int, reconciled: Boolean, status: String) {
        dao.updateLinesStatusByHeader(headerId, reconciled, status)
        dao.updateLegacyStatusByHeader(headerId, reconciled, status)
    }
    
    // Legacy Transactions
    override fun getAllTransactionsWithDetails() = dao.getAllTransactionsWithDetails()
    override fun getTransactionsByDateRange(startDate: String, endDate: String) = dao.getTransactionsByDateRange(startDate, endDate)
    override fun getAccountTransactionsByDateRange(accountId: Int, startDate: String, endDate: String) = dao.getAccountTransactionsByDateRange(accountId, startDate, endDate)
    override suspend fun insertTransactionLegacy(transaction: TransactionLegacy) = dao.insertTransaction(transaction)
    override suspend fun insertTransactionsLegacy(transactions: List<TransactionLegacy>) = dao.insertTransactions(transactions)
    override suspend fun updateTransactionLegacy(transaction: TransactionLegacy) = dao.updateTransaction(transaction)
    override suspend fun deleteTransactionLegacy(id: Int) = dao.deleteTransaction(id)
    override suspend fun getLastTransactionNumber(prefix: String) = dao.getLastTransactionNumber(prefix)
    override suspend fun getFirstTransactionDate() = dao.getFirstTransactionDate()
    override suspend fun getTransactionWithDetails(id: Int) = dao.getTransactionWithDetails(id)

    // Parties
    override fun getAllParties() = dao.getAllParties()
    override fun getEnabledParties() = dao.getEnabledParties()
    override suspend fun upsertParty(party: Party) = dao.upsertParty(party)
    override suspend fun updateParty(party: Party) = dao.updateParty(party)
    override suspend fun deleteParty(party: Party) = dao.deleteParty(party)
    override fun getPartyBalances(asOfDate: String) = dao.getPartyBalances(asOfDate)

    // Templates
    override fun getTemplatesWithLines() = dao.getTemplatesWithLines()
    override suspend fun insertTemplateHeader(header: TemplateHeader) = dao.insertTemplateHeader(header)
    override suspend fun insertTemplateLines(lines: List<TemplateLine>) = dao.insertTemplateLines(lines)
    override suspend fun insertTemplateTags(tags: List<TemplateTag>) = dao.insertTemplateTags(tags)
    override suspend fun deleteTemplateLines(headerId: Int) = dao.deleteTemplateLines(headerId)
    override suspend fun deleteTemplateTags(headerId: Int) = dao.deleteTemplateTags(headerId)
    override suspend fun deleteTemplateHeader(id: Int) = dao.deleteTemplateHeader(id)
    
    override fun getAllTemplatesLegacy() = dao.getAllTemplates()
    override suspend fun upsertTemplateLegacy(template: TemplateLegacy) = dao.upsertTemplate(template)
    override suspend fun deleteTemplateLegacy(template: TemplateLegacy) = dao.deleteTemplate(template)

    // Major/Minor Heads
    override fun getAllMajorHeads() = dao.getAllMajorHeads()
    override suspend fun upsertMajorHead(head: MajorHead) = dao.upsertMajorHead(head)
    override suspend fun updateMajorHead(head: MajorHead) = dao.updateMajorHead(head)
    override suspend fun deleteMajorHead(head: MajorHead) = dao.deleteMajorHead(head)
    override fun getAllMinorHeads() = dao.getAllMinorHeads()
    override fun getMinorHeadsByMajor(majorHeadId: Int) = dao.getMinorHeadsByMajor(majorHeadId)
    override suspend fun upsertMinorHead(head: MinorHead) = dao.upsertMinorHead(head)
    override suspend fun updateMinorHead(head: MinorHead) = dao.updateMinorHead(head)
    override suspend fun deleteMinorHead(head: MinorHead) = dao.deleteMinorHead(head)
    override fun getMajorHeadBalances(asOfDate: String) = dao.getMajorHeadBalances(asOfDate)
    override fun getMinorHeadBalances(asOfDate: String) = dao.getMinorHeadBalances(asOfDate)

    // Notes
    override fun getAllNotes() = dao.getAllNotes()
    override fun getNotesByNotebook(notebookId: Int) = dao.getNotesByNotebook(notebookId)
    override fun searchNotes(query: String) = dao.searchNotes(query)
    override suspend fun upsertNote(note: Note) = dao.upsertNote(note)
    override suspend fun deleteNote(note: Note) = dao.deleteNote(note)
    override suspend fun deleteNotesByNotebook(notebookId: Int) = dao.deleteNotesByNotebook(notebookId)
    override fun getAllNotebooks() = dao.getAllNotebooks()
    override suspend fun upsertNotebook(notebook: Notebook) = dao.upsertNotebook(notebook)
    override suspend fun deleteNotebook(notebook: Notebook) = dao.deleteNotebook(notebook)

    // Exchange Rates
    override suspend fun getRate(code: String, base: String) = dao.getRate(code, base)
    override fun getAllExchangeRates() = dao.getAllExchangeRates()
    override suspend fun upsertExchangeRate(rate: ExchangeRate) = dao.upsertExchangeRate(rate)
    override suspend fun clearAllExchangeRates() = dao.clearAllExchangeRates()

    // Loans
    override fun getAllActiveLoans() = dao.getAllActiveLoans()
    override fun getAllLoans() = dao.getAllLoans()
    override suspend fun getLoanById(id: Long) = dao.getLoanById(id)
    override suspend fun upsertLoan(loan: Loan) = dao.upsertLoan(loan)
    override suspend fun deleteLoan(loan: Loan) = dao.deleteLoan(loan)
    override fun getRepaymentsForLoan(loanId: Long) = dao.getRepaymentsForLoan(loanId)
    override suspend fun upsertLoanRepayment(repayment: LoanRepayment) = dao.upsertLoanRepayment(repayment)

    // Subscriptions
    override fun getAllSubscriptionsMaster() = dao.getAllSubscriptionsMaster()
    override suspend fun upsertSubscriptionMaster(subscription: Subscription) = dao.upsertSubscriptionMaster(subscription)
    override suspend fun deleteSubscriptionMaster(subscription: Subscription) = dao.deleteSubscriptionMaster(subscription)
    override fun getAllSubscriptionStatuses() = dao.getAllSubscriptionStatuses()
    override suspend fun upsertSubscriptionStatus(status: SubscriptionStatus) = dao.upsertSubscriptionStatus(status)

    // Rules
    override fun getAllRules() = dao.getAllRules()
    override suspend fun getEnabledRulesInternal() = dao.getEnabledRulesInternal()
    override suspend fun upsertRule(rule: Rule) = dao.upsertRule(rule)
    override suspend fun deleteRule(rule: Rule) = dao.deleteRule(rule)

    // Invoice Clearances
    override suspend fun insertInvoiceClearance(clearance: InvoiceClearance) = dao.insertInvoiceClearance(clearance)
    override suspend fun deleteClearancesByTransfer(transferId: Int) = dao.deleteClearancesByTransfer(transferId)
    override fun getInvoicesForParty(partyId: Int, asOfDate: String) = dao.getInvoicesForParty(partyId, asOfDate)
    override suspend fun getClearingsForInvoice(invoiceId: Int) = dao.getClearingsForInvoice(invoiceId)
    override suspend fun getInvoicesClearedByTransfer(transferId: Int) = dao.getInvoicesClearedByTransfer(transferId)

    // SMS Automation
    override suspend fun getSmsLogByHash(hash: String) = dao.getSmsLogByHash(hash)
    override suspend fun insertSmsLog(log: SmsLog) = dao.insertSmsLog(log)
    override fun getAllSmsDrafts() = dao.getAllSmsDrafts()
    override suspend fun insertSmsDraft(draft: SmsTransactionDraft) = dao.insertSmsDraft(draft)
    override suspend fun deleteSmsDraft(draft: SmsTransactionDraft) = dao.deleteSmsDraft(draft)

    // Fixed Deposits
    override fun getAllActiveFdsForAccount(accountId: Int): Flow<List<FdDashboardItem>> {
        return combine(dao.getTransactionsDetailed(), dao.getAllAccounts(), dao.getAllMinorHeads(), dao.getAllFdClearances()) { txns, accounts, minorHeads, clearances ->
            val items = mutableListOf<FdDashboardItem>()
            for (txn in txns) {
                if (txn.header.isDeleted == true) continue
                for (line in txn.lines) {
                    val l = line.line
                    if ((l.toAccountId == accountId || l.accountId == accountId) && !l.fdLast4.isNullOrBlank()) {
                        val acc = accounts.find { it.id == (l.toAccountId ?: l.accountId) }
                        val minor = minorHeads.find { it.id == acc?.minorHeadId }
                        val totalCleared = clearances.filter { it.creationHeaderId == txn.header.id || it.creationHeaderId == l.id }.sumOf { it.amountCleared }
                        val outstanding = l.amount - totalCleared
                        if (outstanding > 0.001) {
                            items.add(
                                FdDashboardItem(
                                    lineId = l.id,
                                    accountName = acc?.name ?: "",
                                    minorHeadName = minor?.name ?: "",
                                    fdLast4 = l.fdLast4,
                                    maturityDate = l.fdMaturityDate,
                                    initialAmount = l.amount,
                                    totalCleared = totalCleared,
                                    outstandingAmount = outstanding,
                                    creationHeaderId = txn.header.id
                                )
                            )
                        }
                    }
                }
            }
            items.sortedBy { it.maturityDate ?: "9999-12-31" }
        }
    }

    override fun getFixedDeposits(): Flow<List<FdDashboardItem>> {
        return combine(dao.getTransactionsDetailed(), dao.getAllAccounts(), dao.getAllMinorHeads(), dao.getAllFdClearances()) { txns, accounts, minorHeads, clearances ->
            val items = mutableListOf<FdDashboardItem>()
            for (txn in txns) {
                if (txn.header.isDeleted == true) continue
                for (line in txn.lines) {
                    val l = line.line
                    val acc = accounts.find { it.id == (l.toAccountId ?: l.accountId) }
                    val minor = minorHeads.find { it.id == acc?.minorHeadId }
                    val isFdAccount = minor?.name?.contains("Fixed Deposit", true) == true ||
                                      minor?.name?.contains("FD", true) == true ||
                                      acc?.name?.contains("Fixed Deposit", true) == true ||
                                      acc?.name?.contains("FD", true) == true
                    if (isFdAccount && !l.fdLast4.isNullOrBlank()) {
                        val totalCleared = clearances.filter { it.creationHeaderId == txn.header.id || it.creationHeaderId == l.id }.sumOf { it.amountCleared }
                        val outstanding = l.amount - totalCleared
                        if (outstanding > 0.001) {
                            items.add(
                                FdDashboardItem(
                                    lineId = l.id,
                                    accountName = acc?.name ?: "",
                                    minorHeadName = minor?.name ?: "",
                                    fdLast4 = l.fdLast4,
                                    maturityDate = l.fdMaturityDate,
                                    initialAmount = l.amount,
                                    totalCleared = totalCleared,
                                    outstandingAmount = outstanding,
                                    creationHeaderId = txn.header.id
                                )
                            )
                        }
                    }
                }
            }
            items.sortedBy { it.maturityDate ?: "9999-12-31" }
        }
    }

    override suspend fun saveFdClearance(redemptionHeaderId: Int, creationHeaderId: Int, amountCleared: Double) {
        dao.insertFdClearance(FdClearance(redemptionHeaderId = redemptionHeaderId, creationHeaderId = creationHeaderId, amountCleared = amountCleared))
    }

    override suspend fun deleteFdClearancesByRedemption(redemptionHeaderId: Int) {
        dao.deleteFdClearancesByRedemption(redemptionHeaderId)
    }

    override suspend fun getFdClearancesForRedemption(redemptionHeaderId: Int): List<FdClearance> {
        return dao.getFdClearancesForRedemption(redemptionHeaderId)
    }
}
