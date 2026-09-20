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

package com.openapps.fintrack.domain.repository

import com.openapps.fintrack.data.*
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {
    // Accounts
    fun getAllAccounts(): Flow<List<Account>>
    fun getAllAccountsInternal(): Flow<List<Account>>
    suspend fun getSuspenseAccountInternal(): Account?
    fun getEnabledAccounts(): Flow<List<Account>>
    suspend fun upsertAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun deleteAccount(account: Account)
    fun getAccountBalances(asOfDate: String): Flow<List<AccountBalance>>

    // Categories
    fun getAllCategories(): Flow<List<Category>>
    fun getEnabledCategories(): Flow<List<Category>>
    fun getEnabledCategoriesByType(type: String): Flow<List<Category>>
    suspend fun upsertCategory(category: Category)
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)

    // Tags
    fun getAllTags(): Flow<List<Tag>>
    fun getEnabledTags(): Flow<List<Tag>>
    suspend fun upsertTag(tag: Tag)
    suspend fun updateTag(tag: Tag)
    suspend fun deleteTag(tag: Tag)

    // Goals
    fun getAllGoals(): Flow<List<Goal>>
    suspend fun upsertGoal(goal: Goal): Long
    suspend fun deleteGoal(goal: Goal)
    fun getAllocationsForGoal(goalId: Int): Flow<List<GoalAccountAllocation>>
    fun getAllAllocations(): Flow<List<GoalAccountAllocation>>
    suspend fun getAllocation(goalId: Int, accountId: Int): GoalAccountAllocation?
    suspend fun upsertAllocation(allocation: GoalAccountAllocation)
    suspend fun deleteAllocation(goalId: Int, accountId: Int)
    fun getAllGoalRules(): Flow<List<GoalRule>>
    suspend fun upsertGoalRule(rule: GoalRule): Long
    suspend fun deleteGoalRule(rule: GoalRule)
    suspend fun insertGoalTransaction(gt: GoalTransaction)
    fun getTransactionsForGoal(goalId: Int): Flow<List<TransactionWithDetails>>
    suspend fun insertAllocationHistory(history: GoalAllocationHistory)
    fun getAllocationHistoryForGoal(goalId: Int): Flow<List<GoalAllocationHistory>>

    // Budgets
    fun getAllBudgets(): Flow<List<Budget>>
    suspend fun upsertBudget(budget: Budget): Long
    suspend fun deleteBudget(budget: Budget)
    fun getBudgetsWithRelations(): Flow<List<BudgetWithRelations>>
    suspend fun insertBudgetCategory(junction: BudgetCategory)
    suspend fun insertBudgetAccount(junction: BudgetAccount)
    suspend fun deleteBudgetCategories(budgetId: Int)
    suspend fun deleteBudgetAccounts(budgetId: Int)

    // Transactions
    fun getTransactionsDetailed(): Flow<List<TransactionWithLinesAndDetails>>
    suspend fun insertTransactionHeader(header: TransactionHeader): Long
    suspend fun insertTransactionLines(lines: List<TransactionLine>)
    suspend fun insertTransactionTags(tags: List<TransactionTag>)
    suspend fun deleteTransactionHeader(id: Int)
    suspend fun deleteTransactionLines(headerId: Int)
    suspend fun deleteTransactionTags(headerId: Int)
    suspend fun getLastTransactionHeaderNumber(prefix: String): String?
    suspend fun updateLineReconciliation(lineId: Int, reconciled: Boolean)
    suspend fun updateTransactionStatus(headerId: Int, reconciled: Boolean, status: String)
    
    // Legacy Transactions support (if still needed by some logic)
    fun getAllTransactionsWithDetails(): Flow<List<TransactionWithDetails>>
    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<TransactionWithDetails>>
    fun getAccountTransactionsByDateRange(accountId: Int, startDate: String, endDate: String): Flow<List<TransactionWithDetails>>
    suspend fun insertTransactionLegacy(transaction: TransactionLegacy): Long
    suspend fun insertTransactionsLegacy(transactions: List<TransactionLegacy>)
    suspend fun updateTransactionLegacy(transaction: TransactionLegacy)
    suspend fun deleteTransactionLegacy(id: Int)
    suspend fun getLastTransactionNumber(prefix: String): String?
    suspend fun getFirstTransactionDate(): String?
    suspend fun getTransactionWithDetails(id: Int): TransactionWithDetails?

    // Parties
    fun getAllParties(): Flow<List<Party>>
    fun getEnabledParties(): Flow<List<Party>>
    suspend fun upsertParty(party: Party)
    suspend fun updateParty(party: Party)
    suspend fun deleteParty(party: Party)
    fun getPartyBalances(asOfDate: String): Flow<List<PartyBalance>>

    // Templates
    fun getTemplatesWithLines(): Flow<List<TemplateWithLines>>
    suspend fun insertTemplateHeader(header: TemplateHeader): Long
    suspend fun insertTemplateLines(lines: List<TemplateLine>)
    suspend fun insertTemplateTags(tags: List<TemplateTag>)
    suspend fun deleteTemplateLines(headerId: Int)
    suspend fun deleteTemplateTags(headerId: Int)
    suspend fun deleteTemplateHeader(id: Int)
    
    fun getAllTemplatesLegacy(): Flow<List<TemplateLegacy>>
    suspend fun upsertTemplateLegacy(template: TemplateLegacy)
    suspend fun deleteTemplateLegacy(template: TemplateLegacy)

    // Major/Minor Heads
    fun getAllMajorHeads(): Flow<List<MajorHead>>
    suspend fun upsertMajorHead(head: MajorHead): Long
    suspend fun updateMajorHead(head: MajorHead)
    suspend fun deleteMajorHead(head: MajorHead)
    fun getAllMinorHeads(): Flow<List<MinorHead>>
    fun getMinorHeadsByMajor(majorHeadId: Int): Flow<List<MinorHead>>
    suspend fun upsertMinorHead(head: MinorHead)
    suspend fun updateMinorHead(head: MinorHead)
    suspend fun deleteMinorHead(head: MinorHead)
    fun getMajorHeadBalances(asOfDate: String): Flow<List<MajorHeadBalance>>
    fun getMinorHeadBalances(asOfDate: String): Flow<List<MinorHeadBalance>>

    // Notes/Notebooks
    fun getAllNotes(): Flow<List<Note>>
    fun getNotesByNotebook(notebookId: Int): Flow<List<Note>>
    fun searchNotes(query: String): Flow<List<Note>>
    suspend fun upsertNote(note: Note)
    suspend fun deleteNote(note: Note)
    suspend fun deleteNotesByNotebook(notebookId: Int)
    fun getAllNotebooks(): Flow<List<Notebook>>
    suspend fun upsertNotebook(notebook: Notebook)
    suspend fun deleteNotebook(notebook: Notebook)

    // Exchange Rates
    suspend fun getRate(code: String, base: String): ExchangeRate?
    fun getAllExchangeRates(): Flow<List<ExchangeRate>>
    suspend fun upsertExchangeRate(rate: ExchangeRate)
    suspend fun clearAllExchangeRates()

    // Loans
    fun getAllActiveLoans(): Flow<List<Loan>>
    fun getAllLoans(): Flow<List<Loan>>
    suspend fun getLoanById(id: Long): Loan?
    suspend fun upsertLoan(loan: Loan): Long
    suspend fun deleteLoan(loan: Loan)
    fun getRepaymentsForLoan(loanId: Long): Flow<List<LoanRepayment>>
    suspend fun upsertLoanRepayment(repayment: LoanRepayment)

    // Subscriptions
    fun getAllSubscriptionsMaster(): Flow<List<Subscription>>
    suspend fun upsertSubscriptionMaster(subscription: Subscription)
    suspend fun deleteSubscriptionMaster(subscription: Subscription)
    fun getAllSubscriptionStatuses(): Flow<List<SubscriptionStatus>>
    suspend fun upsertSubscriptionStatus(status: SubscriptionStatus)

    // Rules
    fun getAllRules(): Flow<List<Rule>>
    suspend fun getEnabledRulesInternal(): List<Rule>
    suspend fun upsertRule(rule: Rule)
    suspend fun deleteRule(rule: Rule)

    // Invoice Clearances
    suspend fun insertInvoiceClearance(clearance: InvoiceClearance)
    suspend fun deleteClearancesByTransfer(transferId: Int)
    fun getInvoicesForParty(partyId: Int, asOfDate: String): Flow<List<TransactionWithInvoiceDetails>>
    suspend fun getClearingsForInvoice(invoiceId: Int): List<InvoiceClearanceExtended>
    suspend fun getInvoicesClearedByTransfer(transferId: Int): List<InvoiceClearanceExtended>
    
    // SMS Automation
    suspend fun getSmsLogByHash(hash: String): SmsLog?
    suspend fun insertSmsLog(log: SmsLog)
    fun getAllSmsDrafts(): Flow<List<SmsTransactionDraft>>
    suspend fun insertSmsDraft(draft: SmsTransactionDraft)
    suspend fun deleteSmsDraft(draft: SmsTransactionDraft)

    // Fixed Deposits
    fun getAllActiveFdsForAccount(accountId: Int): Flow<List<FdDashboardItem>>
    fun getFixedDeposits(): Flow<List<FdDashboardItem>>
    suspend fun saveFdClearance(redemptionHeaderId: Int, creationHeaderId: Int, amountCleared: Double)
    suspend fun deleteFdClearancesByRedemption(redemptionHeaderId: Int)
    suspend fun getFdClearancesForRedemption(redemptionHeaderId: Int): List<FdClearance>

    // Database maintenance
    fun checkpoint()
    fun prepareForBackup()
}
