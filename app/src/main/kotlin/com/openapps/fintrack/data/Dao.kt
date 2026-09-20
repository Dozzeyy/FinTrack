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

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    // Accounts
    @Query("SELECT * FROM accounts WHERE name != 'Suspense' ORDER BY name")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY name")
    fun getAllAccountsInternal(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE name = 'Suspense' LIMIT 1")
    suspend fun getSuspenseAccountInternal(): Account?

    @Query("SELECT * FROM accounts WHERE isEnabled = 1 AND name != 'Suspense' ORDER BY name")
    fun getEnabledAccounts(): Flow<List<Account>>

    @Upsert
    suspend fun upsertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)

    // Categories
    @Query("SELECT * FROM categories ORDER BY name")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isEnabled = 1 ORDER BY name")
    fun getEnabledCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE type = :type AND isEnabled = 1 ORDER BY name")
    fun getEnabledCategoriesByType(type: String): Flow<List<Category>>

    @Upsert
    suspend fun upsertCategory(category: Category)

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    // Tags
    @Query("SELECT * FROM tags ORDER BY name")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE isEnabled = 1 ORDER BY name")
    fun getEnabledTags(): Flow<List<Tag>>

    @Upsert
    suspend fun upsertTag(tag: Tag)

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    // Budgets
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<Budget>>

    @Upsert
    suspend fun upsertBudget(budget: Budget): Long

    @Delete
    suspend fun deleteBudget(budget: Budget)

    // Normalized Transactions
    @Transaction
    @Query("SELECT * FROM transaction_headers ORDER BY date DESC, time DESC")
    fun getTransactionsWithLines(): Flow<List<TransactionWithLines>>

    @Transaction
    @Query("SELECT * FROM transaction_headers ORDER BY date DESC, time DESC")
    fun getTransactionsDetailed(): Flow<List<TransactionWithLinesAndDetails>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionHeader(header: TransactionHeader): Long

    @Insert
    suspend fun insertTransactionLines(lines: List<TransactionLine>)

    @Insert
    suspend fun insertTransactionTags(tags: List<TransactionTag>)

    @Query("DELETE FROM transaction_headers WHERE id = :id")
    suspend fun deleteTransactionHeader(id: Int)

    @Query("DELETE FROM transaction_lines WHERE headerId = :headerId")
    suspend fun deleteTransactionLines(headerId: Int)

    @Query("DELETE FROM transaction_tags WHERE headerId = :headerId")
    suspend fun deleteTransactionTags(headerId: Int)

    @Query("UPDATE transaction_lines SET isReconciled = :reconciled, reconciliationStatus = :status WHERE headerId = :headerId")
    suspend fun updateLinesStatusByHeader(headerId: Int, reconciled: Boolean, status: String)

    @Query("UPDATE transactions SET isReconciled = :reconciled, reconciliationStatus = :status WHERE id = :headerId")
    suspend fun updateLegacyStatusByHeader(headerId: Int, reconciled: Boolean, status: String)

    @Query("UPDATE transaction_lines SET isReconciled = :reconciled WHERE id = :lineId")
    suspend fun updateLineReconciliation(lineId: Int, reconciled: Boolean)

    @Query("SELECT transactionNumber FROM transaction_headers WHERE transactionNumber LIKE :prefix || '%' ORDER BY id DESC LIMIT 1")
    suspend fun getLastTransactionHeaderNumber(prefix: String): String?

    // Normalized Budgets
    @Transaction
    @Query("SELECT * FROM budgets")
    fun getBudgetsWithRelations(): Flow<List<BudgetWithRelations>>

    @Insert
    suspend fun insertBudgetCategory(junction: BudgetCategory)

    @Insert
    suspend fun insertBudgetAccount(junction: BudgetAccount)

    @Query("DELETE FROM budget_categories WHERE budgetId = :budgetId")
    suspend fun deleteBudgetCategories(budgetId: Int)

    @Query("DELETE FROM budget_accounts WHERE budgetId = :budgetId")
    suspend fun deleteBudgetAccounts(budgetId: Int)

    // Normalized Templates
    @Transaction
    @Query("SELECT * FROM template_headers ORDER BY name")
    fun getTemplatesWithLines(): Flow<List<TemplateWithLines>>

    @Insert
    suspend fun insertTemplateHeader(header: TemplateHeader): Long

    @Insert
    suspend fun insertTemplateLines(lines: List<TemplateLine>)

    @Insert
    suspend fun insertTemplateTags(tags: List<TemplateTag>)

    @Query("DELETE FROM template_lines WHERE headerId = :headerId")
    suspend fun deleteTemplateLines(headerId: Int)

    @Query("DELETE FROM template_tags WHERE headerId = :headerId")
    suspend fun deleteTemplateTags(headerId: Int)

    @Query("DELETE FROM template_headers WHERE id = :id")
    suspend fun deleteTemplateHeader(id: Int)

    // Legacy Transactions
    @Query("""
        SELECT h.id as id, h.date, h.time, l.accountId, l.toAccountId, l.categoryId, 
               l.amount, l.amountMinorUnits, h.note, h.transactionNumber, h.partyId, h.toPartyId,
               h.subName, h.subFrequency, l.amountOriginal, l.amountOriginalMinorUnits,
               l.currencyCode, l.amountBase, l.amountBaseMinorUnits, h.editedAt,
               l.isNegotiated, l.negotiationAmountOriginal, l.negotiationAmountOriginalMinorUnits,
               h.merchantName, l.isDiscretionary, h.invoiceNumber, h.dueDays,
               l.isReconciled, l.reconciliationStatus,
               c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               COALESCE(p.name, a.name) as partyName, 
               COALESCE(p2.name, a2.name) as toPartyName
        FROM transaction_headers h
        JOIN transaction_lines l ON h.id = l.headerId
        LEFT JOIN categories c ON l.categoryId = c.id 
        JOIN accounts a ON l.accountId = a.id
        LEFT JOIN accounts a2 ON l.toAccountId = a2.id
        LEFT JOIN parties p ON h.partyId = p.id
        LEFT JOIN parties p2 ON h.toPartyId = p2.id
        ORDER BY h.date DESC, h.time DESC
    """)
    fun getAllTransactionsWithDetails(): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT h.id as id, h.date, h.time, l.accountId, l.toAccountId, l.categoryId, 
               l.amount, l.amountMinorUnits, h.note, h.transactionNumber, h.partyId, h.toPartyId,
               h.subName, h.subFrequency, l.amountOriginal, l.amountOriginalMinorUnits,
               l.currencyCode, l.amountBase, l.amountBaseMinorUnits, h.editedAt,
               l.isNegotiated, l.negotiationAmountOriginal, l.negotiationAmountOriginalMinorUnits,
               h.merchantName, l.isDiscretionary, h.invoiceNumber, h.dueDays,
               l.isReconciled, l.reconciliationStatus,
               c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               COALESCE(p.name, a.name) as partyName, 
               COALESCE(p2.name, a2.name) as toPartyName
        FROM transaction_headers h
        JOIN transaction_lines l ON h.id = l.headerId
        LEFT JOIN categories c ON l.categoryId = c.id 
        JOIN accounts a ON l.accountId = a.id
        LEFT JOIN accounts a2 ON l.toAccountId = a2.id
        LEFT JOIN parties p ON h.partyId = p.id
        LEFT JOIN parties p2 ON h.toPartyId = p2.id
        WHERE h.date BETWEEN :startDate AND :endDate
        ORDER BY h.date DESC, h.time DESC
    """)
    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT h.id as id, h.date, h.time, l.accountId, l.toAccountId, l.categoryId, 
               l.amount, l.amountMinorUnits, h.note, h.transactionNumber, h.partyId, h.toPartyId,
               h.subName, h.subFrequency, l.amountOriginal, l.amountOriginalMinorUnits,
               l.currencyCode, l.amountBase, l.amountBaseMinorUnits, h.editedAt,
               l.isNegotiated, l.negotiationAmountOriginal, l.negotiationAmountOriginalMinorUnits,
               h.merchantName, l.isDiscretionary, h.invoiceNumber, h.dueDays,
               l.isReconciled, l.reconciliationStatus,
               c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               COALESCE(p.name, a.name) as partyName, 
               COALESCE(p2.name, a2.name) as toPartyName
        FROM transaction_headers h
        JOIN transaction_lines l ON h.id = l.headerId
        LEFT JOIN categories c ON l.categoryId = c.id 
        JOIN accounts a ON l.accountId = a.id
        LEFT JOIN accounts a2 ON l.toAccountId = a2.id
        LEFT JOIN parties p ON h.partyId = p.id
        LEFT JOIN parties p2 ON h.toPartyId = p2.id
        WHERE (l.accountId = :accountId OR l.toAccountId = :accountId)
        AND h.date BETWEEN :startDate AND :endDate
        ORDER BY h.date DESC, h.time DESC
    """)
    fun getAccountTransactionsByDateRange(accountId: Int, startDate: String, endDate: String): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT a.id, a.name, a.type, a.openingBalanceMinorUnits as openingBalance,
        (
            (CASE WHEN a.name = 'On Account' 
                 THEN COALESCE((SELECT SUM(openingBalanceMinorUnits) FROM parties WHERE isEnabled = 1), 0) 
                 ELSE a.openingBalanceMinorUnits 
            END)
            + COALESCE((SELECT SUM(l.amountMinorUnits) 
                FROM transaction_lines l 
                JOIN transaction_headers h ON l.headerId = h.id
                LEFT JOIN categories c ON l.categoryId = c.id 
                WHERE l.accountId = a.id AND LOWER(c.type) = 'income' AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
            - COALESCE((SELECT SUM(l.amountMinorUnits) 
                FROM transaction_lines l 
                JOIN transaction_headers h ON l.headerId = h.id
                LEFT JOIN categories c ON l.categoryId = c.id 
                WHERE l.accountId = a.id AND LOWER(c.type) = 'expense' AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
            - COALESCE((SELECT SUM(l.amountMinorUnits) 
                FROM transaction_lines l 
                JOIN transaction_headers h ON l.headerId = h.id
                WHERE l.accountId = a.id AND l.toAccountId IS NOT NULL AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
            + COALESCE((SELECT SUM(l.amountMinorUnits) 
                FROM transaction_lines l 
                JOIN transaction_headers h ON l.headerId = h.id
                WHERE l.toAccountId = a.id AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
        ) as balance,
        a.minorHeadId, a.billingCycleStart, a.billingCycleEnd, a.paymentDueDate, a.icon, a.isEmergencyFund, a.creditLimitMinorUnits as creditLimit, a.minimumBalance
        FROM accounts a
        WHERE a.isEnabled = 1 AND a.name != 'Suspense'
    """)
    fun getAccountBalances(asOfDate: String): Flow<List<AccountBalance>>

    @Insert
    suspend fun insertTransaction(transaction: TransactionLegacy): Long

    @Insert
    suspend fun insertTransactions(transactions: List<TransactionLegacy>)

    @Update
    suspend fun updateTransaction(transaction: TransactionLegacy)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)

    @Query("SELECT transactionNumber FROM transactions WHERE transactionNumber LIKE :prefix || '%' ORDER BY id DESC LIMIT 1")
    suspend fun getLastTransactionNumber(prefix: String): String?

    @Query("SELECT MIN(date) FROM transaction_headers")
    suspend fun getFirstTransactionDate(): String?

    // SMS Automation
    @Query("SELECT * FROM sms_logs WHERE bodyHash = :hash LIMIT 1")
    suspend fun getSmsLogByHash(hash: String): SmsLog?

    @Insert
    suspend fun insertSmsLog(log: SmsLog)

    @Query("SELECT * FROM sms_drafts ORDER BY date DESC, time DESC")
    fun getAllSmsDrafts(): Flow<List<SmsTransactionDraft>>

    @Insert
    suspend fun insertSmsDraft(draft: SmsTransactionDraft)

    @Delete
    suspend fun deleteSmsDraft(draft: SmsTransactionDraft)

    // Parties (Payer/Payee)
    @Query("SELECT * FROM parties ORDER BY name")
    fun getAllParties(): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE isEnabled = 1 ORDER BY name")
    fun getEnabledParties(): Flow<List<Party>>

    @Upsert
    suspend fun upsertParty(party: Party)

    @Update
    suspend fun updateParty(party: Party)

    @Delete
    suspend fun deleteParty(party: Party)

    @Query("""
        SELECT p.id, p.name,
        (
            p.openingBalanceMinorUnits
            + COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id LEFT JOIN categories c ON l.categoryId = c.id 
                WHERE (h.partyId = p.id AND c.type = 'income' AND h.date <= :asOfDate) OR (h.toPartyId = p.id AND h.date <= :asOfDate)), 0)
            - COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id LEFT JOIN categories c ON l.categoryId = c.id 
                WHERE (h.partyId = p.id AND (c.type = 'expense' OR (l.categoryId IS NULL AND l.toAccountId IS NOT NULL)) AND h.date <= :asOfDate)), 0)
        ) as balance
        FROM parties p
        WHERE p.isEnabled = 1
    """)
    fun getPartyBalances(asOfDate: String): Flow<List<PartyBalance>>

    // Templates
    @Query("SELECT * FROM templates ORDER BY name")
    fun getAllTemplates(): Flow<List<TemplateLegacy>>

    @Upsert
    suspend fun upsertTemplate(template: TemplateLegacy)

    @Delete
    suspend fun deleteTemplate(template: TemplateLegacy)

    // Major Heads
    @Query("SELECT * FROM major_heads ORDER BY name")
    fun getAllMajorHeads(): Flow<List<MajorHead>>

    @Upsert
    suspend fun upsertMajorHead(head: MajorHead): Long

    @Update
    suspend fun updateMajorHead(head: MajorHead)

    @Delete
    suspend fun deleteMajorHead(head: MajorHead)

    // Minor Heads
    @Query("SELECT * FROM minor_heads ORDER BY name")
    fun getAllMinorHeads(): Flow<List<MinorHead>>

    @Query("SELECT * FROM minor_heads WHERE majorHeadId = :majorHeadId ORDER BY name")
    fun getMinorHeadsByMajor(majorHeadId: Int): Flow<List<MinorHead>>

    @Upsert
    suspend fun upsertMinorHead(head: MinorHead)

    @Update
    suspend fun updateMinorHead(head: MinorHead)

    @Delete
    suspend fun deleteMinorHead(head: MinorHead)

    @Query("""
        SELECT mh.id, mh.name,
        (
            SELECT COALESCE(SUM(
                (CASE WHEN a.name = 'On Account' 
                     THEN COALESCE((SELECT SUM(openingBalanceMinorUnits) FROM parties WHERE isEnabled = 1), 0) 
                     ELSE a.openingBalanceMinorUnits 
                END)
                + COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id LEFT JOIN categories c ON l.categoryId = c.id WHERE l.accountId = a.id AND LOWER(c.type) = 'income' AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
                - COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id LEFT JOIN categories c ON l.categoryId = c.id WHERE l.accountId = a.id AND LOWER(c.type) = 'expense' AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
                - COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id WHERE l.accountId = a.id AND l.toAccountId IS NOT NULL AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
                + COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id WHERE l.toAccountId = a.id AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
            ), 0)
            FROM accounts a
            LEFT JOIN minor_heads mih ON a.minorHeadId = mih.id
            WHERE mih.majorHeadId = mh.id AND a.isEnabled = 1 AND a.name != 'Suspense'
        ) as balance
        FROM major_heads mh
        WHERE mh.isEnabled = 1
    """)
    fun getMajorHeadBalances(asOfDate: String): Flow<List<MajorHeadBalance>>

    @Query("""
        SELECT mih.id, mih.name, mih.majorHeadId,
        (
            SELECT COALESCE(SUM(
                (CASE WHEN a.name = 'On Account' 
                     THEN COALESCE((SELECT SUM(openingBalanceMinorUnits) FROM parties WHERE isEnabled = 1), 0) 
                     ELSE a.openingBalanceMinorUnits 
                END)
                + COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id LEFT JOIN categories c ON l.categoryId = c.id WHERE l.accountId = a.id AND LOWER(c.type) = 'income' AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
                - COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id LEFT JOIN categories c ON l.categoryId = c.id WHERE l.accountId = a.id AND LOWER(c.type) = 'expense' AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
                - COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id WHERE l.accountId = a.id AND l.toAccountId IS NOT NULL AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
                + COALESCE((SELECT SUM(l.amountMinorUnits) FROM transaction_lines l JOIN transaction_headers h ON l.headerId = h.id WHERE l.toAccountId = a.id AND h.date <= :asOfDate AND l.reconciliationStatus != 'VOID'), 0)
            ), 0)
            FROM accounts a
            WHERE a.minorHeadId = mih.id AND a.isEnabled = 1 AND a.name != 'Suspense'
        ) as balance
        FROM minor_heads mih
        WHERE mih.isEnabled = 1
    """)
    fun getMinorHeadBalances(asOfDate: String): Flow<List<MinorHeadBalance>>

    // Subscription Status
    @Query("SELECT * FROM subscription_status")
    fun getAllSubscriptionStatuses(): Flow<List<SubscriptionStatus>>

    @Upsert
    suspend fun upsertSubscriptionStatus(status: SubscriptionStatus)

    // Notes
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, createdAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE notebookId = :notebookId ORDER BY isPinned DESC, createdAt DESC")
    fun getNotesByNotebook(notebookId: Int): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY isPinned DESC, createdAt DESC")
    fun searchNotes(query: String): Flow<List<Note>>

    @Upsert
    suspend fun upsertNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE notebookId = :notebookId")
    suspend fun deleteNotesByNotebook(notebookId: Int)

    // Notebooks
    @Query("SELECT * FROM notebooks ORDER BY name")
    fun getAllNotebooks(): Flow<List<Notebook>>

    @Upsert
    suspend fun upsertNotebook(notebook: Notebook)

    @Delete
    suspend fun deleteNotebook(notebook: Notebook)

    // Exchange Rates
    @Query("SELECT * FROM exchange_rates WHERE currencyCode = :code AND baseCurrency = :base LIMIT 1")
    suspend fun getRate(code: String, base: String): ExchangeRate?

    @Query("SELECT * FROM exchange_rates")
    fun getAllExchangeRates(): Flow<List<ExchangeRate>>

    @Upsert
    suspend fun upsertExchangeRate(rate: ExchangeRate)

    @Query("DELETE FROM exchange_rates")
    suspend fun clearAllExchangeRates()

    // Loans
    @Query("SELECT * FROM loans WHERE isClosed = 0 ORDER BY nextDueDate ASC")
    fun getAllActiveLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loans ORDER BY id DESC")
    fun getAllLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoanById(id: Long): Loan?

    @Upsert
    suspend fun upsertLoan(loan: Loan): Long

    @Delete
    suspend fun deleteLoan(loan: Loan)

    // Loan Repayments
    @Query("SELECT * FROM loan_repayments WHERE loanId = :loanId ORDER BY paymentDate DESC")
    fun getRepaymentsForLoan(loanId: Long): Flow<List<LoanRepayment>>

    @Upsert
    suspend fun upsertLoanRepayment(repayment: LoanRepayment)

    // Subscriptions
    @Query("SELECT * FROM subscriptions_master ORDER BY name")
    fun getAllSubscriptionsMaster(): Flow<List<Subscription>>

    @Upsert
    suspend fun upsertSubscriptionMaster(subscription: Subscription)

    @Delete
    suspend fun deleteSubscriptionMaster(subscription: Subscription)

    // Rules
    @Query("SELECT * FROM rules ORDER BY name")
    fun getAllRules(): Flow<List<Rule>>

    @Query("SELECT * FROM rules WHERE isEnabled = 1")
    suspend fun getEnabledRulesInternal(): List<Rule>

    @Upsert
    suspend fun upsertRule(rule: Rule)

    @Delete
    suspend fun deleteRule(rule: Rule)

    // Invoice Clearances
    @Insert
    suspend fun insertInvoiceClearance(clearance: InvoiceClearance)

    @Query("DELETE FROM invoice_clearances WHERE transferTransactionId = :transferId")
    suspend fun deleteClearancesByTransfer(transferId: Int)

    @Query("""
        SELECT h.id as id, h.date, h.time, l.accountId, l.toAccountId, l.categoryId, 
               l.amount, l.amountMinorUnits, h.note, h.transactionNumber, h.partyId, h.toPartyId,
               h.subName, h.subFrequency, l.amountOriginal, l.amountOriginalMinorUnits,
               l.currencyCode, l.amountBase, l.amountBaseMinorUnits, h.editedAt,
               l.isNegotiated, l.negotiationAmountOriginal, l.negotiationAmountOriginalMinorUnits,
               h.merchantName, l.isDiscretionary, h.invoiceNumber, h.dueDays,
               l.isReconciled, l.reconciliationStatus,
               c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               COALESCE(p.name, a.name) as partyName, 
               COALESCE(p2.name, a2.name) as toPartyName,
               COALESCE((SELECT SUM(amountClearedMinorUnits) FROM invoice_clearances ic JOIN transaction_headers th ON ic.transferTransactionId = th.id WHERE ic.invoiceTransactionId = h.id AND th.date <= :asOfDate), 0) as totalCleared
        FROM transaction_headers h
        JOIN transaction_lines l ON h.id = l.headerId
        LEFT JOIN categories c ON l.categoryId = c.id 
        JOIN accounts a ON l.accountId = a.id
        LEFT JOIN accounts a2 ON l.toAccountId = a2.id
        LEFT JOIN parties p ON h.partyId = p.id
        LEFT JOIN parties p2 ON h.toPartyId = p2.id
        WHERE h.invoiceNumber IS NOT NULL 
        AND (h.partyId = :partyId OR l.accountId = :partyId)
        AND h.date <= :asOfDate
        ORDER BY h.date ASC, h.time ASC
    """)
    fun getInvoicesForParty(partyId: Int, asOfDate: String): Flow<List<TransactionWithInvoiceDetails>>

    @Query("""
        SELECT ic.*, h.transactionNumber as otherTxnNumber, h.date as otherDate, 
               (SELECT SUM(amount) FROM transaction_lines WHERE headerId = h.id) as otherTotalAmount,
               a1.name as accountName, a2.name as toAccountName
        FROM invoice_clearances ic
        JOIN transaction_headers h ON ic.transferTransactionId = h.id
        JOIN transaction_lines l ON h.id = l.headerId
        JOIN accounts a1 ON l.accountId = a1.id
        LEFT JOIN accounts a2 ON l.toAccountId = a2.id
        WHERE ic.invoiceTransactionId = :invoiceId
        GROUP BY ic.id
    """)
    suspend fun getClearingsForInvoice(invoiceId: Int): List<InvoiceClearanceExtended>

    @Query("""
        SELECT ic.*, h.transactionNumber as otherTxnNumber, h.date as otherDate, 
               (SELECT SUM(amount) FROM transaction_lines WHERE headerId = h.id) as otherTotalAmount,
               h.invoiceNumber as otherInvoiceNumber
        FROM invoice_clearances ic
        JOIN transaction_headers h ON ic.invoiceTransactionId = h.id
        WHERE ic.transferTransactionId = :transferId
    """)
    suspend fun getInvoicesClearedByTransfer(transferId: Int): List<InvoiceClearanceExtended>

    @Query("""
        SELECT h.id as id, h.date, h.time, l.accountId, l.toAccountId, l.categoryId, 
               l.amount, l.amountMinorUnits, h.note, h.transactionNumber, h.partyId, h.toPartyId,
               h.subName, h.subFrequency, l.amountOriginal, l.amountOriginalMinorUnits,
               l.currencyCode, l.amountBase, l.amountBaseMinorUnits, h.editedAt,
               l.isNegotiated, l.negotiationAmountOriginal, l.negotiationAmountOriginalMinorUnits,
               h.merchantName, l.isDiscretionary, h.invoiceNumber, h.dueDays,
               l.isReconciled, l.reconciliationStatus,
               c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               COALESCE(p.name, a.name) as partyName, 
               COALESCE(p2.name, a2.name) as toPartyName
        FROM transaction_headers h
        JOIN transaction_lines l ON h.id = l.headerId
        LEFT JOIN categories c ON l.categoryId = c.id 
        JOIN accounts a ON l.accountId = a.id
        LEFT JOIN accounts a2 ON l.toAccountId = a2.id
        LEFT JOIN parties p ON h.partyId = p.id
        LEFT JOIN parties p2 ON h.toPartyId = p2.id
        WHERE h.id = :id
        LIMIT 1
    """)
    suspend fun getTransactionWithDetails(id: Int): TransactionWithDetails?

    // Goals
    @Query("SELECT * FROM goals WHERE isDeleted = 0 ORDER BY name")
    fun getAllGoals(): Flow<List<Goal>>

    @Upsert
    suspend fun upsertGoal(goal: Goal): Long

    @Delete
    suspend fun deleteGoal(goal: Goal)

    @Query("SELECT * FROM goal_account_allocations WHERE goalId = :goalId")
    fun getAllocationsForGoal(goalId: Int): Flow<List<GoalAccountAllocation>>

    @Query("SELECT * FROM goal_account_allocations")
    fun getAllAllocations(): Flow<List<GoalAccountAllocation>>

    @Query("SELECT * FROM goal_account_allocations WHERE goalId = :goalId AND accountId = :accountId")
    suspend fun getAllocation(goalId: Int, accountId: Int): GoalAccountAllocation?

    @Upsert
    suspend fun upsertAllocation(allocation: GoalAccountAllocation)

    @Query("DELETE FROM goal_account_allocations WHERE goalId = :goalId AND accountId = :accountId")
    suspend fun deleteAllocation(goalId: Int, accountId: Int)

    @Query("SELECT * FROM goal_rules")
    fun getAllGoalRules(): Flow<List<GoalRule>>

    @Upsert
    suspend fun upsertGoalRule(rule: GoalRule): Long

    @Delete
    suspend fun deleteGoalRule(rule: GoalRule)

    @Upsert
    suspend fun insertGoalTransaction(gt: GoalTransaction)

    @Query("""
        SELECT h.id as id, h.date, h.time, l.accountId, l.toAccountId, l.categoryId, 
               l.amount, l.amountMinorUnits, h.note, h.transactionNumber, h.partyId, h.toPartyId,
               h.subName, h.subFrequency, l.amountOriginal, l.amountOriginalMinorUnits,
               l.currencyCode, l.amountBase, l.amountBaseMinorUnits, h.editedAt,
               l.isNegotiated, l.negotiationAmountOriginal, l.negotiationAmountOriginalMinorUnits,
               h.merchantName, l.isDiscretionary, h.invoiceNumber, h.dueDays,
               l.isReconciled, l.reconciliationStatus,
               c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               COALESCE(p.name, a.name) as partyName, 
               COALESCE(p2.name, a2.name) as toPartyName
        FROM transaction_headers h
        JOIN transaction_lines l ON h.id = l.headerId
        JOIN goal_transactions gt ON h.id = gt.transactionId
        LEFT JOIN categories c ON l.categoryId = c.id 
        JOIN accounts a ON l.accountId = a.id
        LEFT JOIN accounts a2 ON l.toAccountId = a2.id
        LEFT JOIN parties p ON h.partyId = p.id
        LEFT JOIN parties p2 ON h.toPartyId = p2.id
        WHERE gt.goalId = :goalId
        ORDER BY h.date DESC, h.time DESC
    """)
    fun getTransactionsForGoal(goalId: Int): Flow<List<TransactionWithDetails>>

    @Query("SELECT * FROM goal_allocation_history WHERE goalId = :goalId ORDER BY timestamp DESC")
    fun getAllocationHistoryForGoal(goalId: Int): Flow<List<GoalAllocationHistory>>

    @Upsert
    suspend fun insertAllocationHistory(history: GoalAllocationHistory)

    @Insert
    suspend fun insertFdClearance(clearance: FdClearance): Long

    @Query("DELETE FROM fd_clearances WHERE redemptionHeaderId = :redemptionHeaderId")
    suspend fun deleteFdClearancesByRedemption(redemptionHeaderId: Int)

    @Query("SELECT * FROM fd_clearances WHERE redemptionHeaderId = :redemptionHeaderId")
    suspend fun getFdClearancesForRedemption(redemptionHeaderId: Int): List<FdClearance>

    @Query("SELECT * FROM fd_clearances")
    fun getAllFdClearances(): Flow<List<FdClearance>>
}

data class TransactionWithLinesAndDetails(
    @Embedded val header: TransactionHeader,
    @Relation(parentColumn = "partyId", entityColumn = "id")
    val party: Party?,
    @Relation(parentColumn = "toPartyId", entityColumn = "id")
    val toParty: Party?,
    @Relation(
        entity = TransactionLine::class,
        parentColumn = "id",
        entityColumn = "headerId"
    )
    val lines: List<TransactionLineDetailed>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TransactionTag::class, parentColumn = "headerId", entityColumn = "tagId")
    )
    val tags: List<Tag>
)

data class TransactionLineDetailed(
    @Embedded val line: TransactionLine,
    @Relation(parentColumn = "accountId", entityColumn = "id")
    val account: Account,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: Category?,
    @Relation(parentColumn = "toAccountId", entityColumn = "id")
    val toAccount: Account?
)

data class TransactionWithLines(
    @Embedded val header: TransactionHeader,
    @Relation(
        parentColumn = "id",
        entityColumn = "headerId"
    )
    val lines: List<TransactionLine>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TransactionTag::class, parentColumn = "headerId", entityColumn = "tagId")
    )
    val tags: List<Tag>
)

data class BudgetWithRelations(
    @Embedded val budget: Budget,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(BudgetCategory::class, parentColumn = "budgetId", entityColumn = "categoryId")
    )
    val categories: List<Category>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(BudgetAccount::class, parentColumn = "budgetId", entityColumn = "accountId")
    )
    val accounts: List<Account>
)

data class TemplateWithLines(
    @Embedded val header: TemplateHeader,
    @Relation(
        parentColumn = "id",
        entityColumn = "headerId"
    )
    val lines: List<TemplateLine>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TemplateTag::class, parentColumn = "headerId", entityColumn = "tagId")
    )
    val tags: List<Tag>
)

data class InvoiceClearanceExtended(
    @Embedded val clearance: InvoiceClearance,
    val otherTxnNumber: String?,
    val otherDate: String,
    val otherTotalAmount: Double,
    val otherInvoiceNumber: String? = null,
    val accountName: String? = null,
    val toAccountName: String? = null
)

data class TransactionWithInvoiceDetails(
    @Embedded val detail: TransactionWithDetails,
    val totalCleared: Long
)

data class TransactionWithDetails(
    @Embedded val transaction: TransactionLegacy,
    val categoryName: String?,
    val categoryType: String?,
    val categoryIcon: String?,
    val accountName: String,
    val accountIcon: String?,
    val toAccountName: String?,
    val toAccountIcon: String?,
    val partyName: String?,
    val toPartyName: String?
)

data class AccountBalance(
    val id: Int,
    val name: String,
    val type: String,
    val openingBalance: Long,
    val balance: Long,
    val minorHeadId: Int? = null,
    val billingCycleStart: String? = null,
    val billingCycleEnd: String? = null,
    val paymentDueDate: String? = null,
    val icon: String? = null,
    val isEmergencyFund: Boolean = false,
    val creditLimit: Long? = null,
    val minimumBalance: Double? = null
)

data class BudgetWithDetails(
    @Embedded val budget: Budget,
    val categoryName: String,
    val categoryType: String
)

data class PartyBalance(
    val id: Int,
    val name: String,
    val balance: Long
)

data class MajorHeadBalance(
    val id: Int,
    val name: String,
    val balance: Long
)

data class MinorHeadBalance(
    val id: Int,
    val name: String,
    val majorHeadId: Int,
    val balance: Long
)
