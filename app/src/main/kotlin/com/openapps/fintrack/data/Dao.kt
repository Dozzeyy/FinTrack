/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
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
    suspend fun upsertAccount(account: Account)

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
    suspend fun upsertBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    // Transactions
    @Query("""
        SELECT t.*, c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               p.name as partyName, p2.name as toPartyName
        FROM transactions t 
        LEFT JOIN categories c ON t.categoryId = c.id 
        JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts a2 ON t.toAccountId = a2.id
        LEFT JOIN parties p ON t.partyId = p.id
        LEFT JOIN parties p2 ON t.toPartyId = p2.id
        ORDER BY t.date DESC, t.time DESC
    """)
    fun getAllTransactionsWithDetails(): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT t.*, c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               p.name as partyName, p2.name as toPartyName
        FROM transactions t 
        LEFT JOIN categories c ON t.categoryId = c.id 
        JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts a2 ON t.toAccountId = a2.id
        LEFT JOIN parties p ON t.partyId = p.id
        LEFT JOIN parties p2 ON t.toPartyId = p2.id
        WHERE t.date BETWEEN :startDate AND :endDate
        ORDER BY t.date DESC, t.time DESC
    """)
    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT t.*, c.name as categoryName, c.type as categoryType, c.icon as categoryIcon, 
               a.name as accountName, a.icon as accountIcon, 
               a2.name as toAccountName, a2.icon as toAccountIcon, 
               p.name as partyName, p2.name as toPartyName
        FROM transactions t 
        LEFT JOIN categories c ON t.categoryId = c.id 
        JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts a2 ON t.toAccountId = a2.id
        LEFT JOIN parties p ON t.partyId = p.id
        LEFT JOIN parties p2 ON t.toPartyId = p2.id
        WHERE (t.accountId = :accountId OR t.toAccountId = :accountId)
        AND t.date BETWEEN :startDate AND :endDate
        ORDER BY t.date DESC, t.time DESC
    """)
    fun getAccountTransactionsByDateRange(accountId: Int, startDate: String, endDate: String): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT a.id, a.name, a.type, a.openingBalance,
        (
            (CASE WHEN a.name = 'On Account' 
                 THEN COALESCE((SELECT SUM(openingBalance) FROM parties WHERE isEnabled = 1), 0.0) 
                 ELSE a.openingBalance 
            END)
            + COALESCE((SELECT SUM(t.amount) 
                FROM transactions t 
                LEFT JOIN categories c ON t.categoryId = c.id 
                WHERE t.accountId = a.id AND c.type = 'income' AND t.date <= :asOfDate), 0.0)
            - COALESCE((SELECT SUM(t.amount) 
                FROM transactions t 
                LEFT JOIN categories c ON t.categoryId = c.id 
                WHERE t.accountId = a.id AND c.type = 'expense' AND t.date <= :asOfDate), 0.0)
            - COALESCE((SELECT SUM(t.amount) 
                FROM transactions t 
                WHERE t.accountId = a.id AND t.toAccountId IS NOT NULL AND t.date <= :asOfDate), 0.0)
            + COALESCE((SELECT SUM(t.amount) 
                FROM transactions t 
                WHERE t.toAccountId = a.id AND t.date <= :asOfDate), 0.0)
        ) as balance,
        a.minorHeadId, a.billingCycleStart, a.billingCycleEnd, a.paymentDueDate, a.icon
        FROM accounts a
        WHERE a.isEnabled = 1 AND a.name != 'Suspense'
    """)
    fun getAccountBalances(asOfDate: String): Flow<List<AccountBalance>>

    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    @Insert
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)

    @Query("SELECT transactionNumber FROM transactions WHERE transactionNumber LIKE :prefix || '%' ORDER BY id DESC LIMIT 1")
    suspend fun getLastTransactionNumber(prefix: String): String?

    @Query("SELECT MIN(date) FROM transactions")
    suspend fun getFirstTransactionDate(): String?

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
            p.openingBalance
            + COALESCE((SELECT SUM(t.amount) FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id 
                WHERE (t.partyId = p.id AND c.type = 'income' AND t.date <= :asOfDate) OR (t.toPartyId = p.id AND t.date <= :asOfDate)), 0)
            - COALESCE((SELECT SUM(t.amount) FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id 
                WHERE (t.partyId = p.id AND (c.type = 'expense' OR (t.categoryId IS NULL AND t.toAccountId IS NOT NULL)) AND t.date <= :asOfDate)), 0)
        ) as balance
        FROM parties p
        WHERE p.isEnabled = 1
    """)
    fun getPartyBalances(asOfDate: String): Flow<List<PartyBalance>>

    // Templates
    @Query("SELECT * FROM templates ORDER BY name")
    fun getAllTemplates(): Flow<List<Template>>

    @Upsert
    suspend fun upsertTemplate(template: Template)

    @Delete
    suspend fun deleteTemplate(template: Template)

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
                     THEN COALESCE((SELECT SUM(openingBalance) FROM parties WHERE isEnabled = 1), 0.0) 
                     ELSE a.openingBalance 
                END)
                + COALESCE((SELECT SUM(t.amount) FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id WHERE t.accountId = a.id AND c.type = 'income' AND t.date <= :asOfDate), 0.0)
                - COALESCE((SELECT SUM(t.amount) FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id WHERE t.accountId = a.id AND c.type = 'expense' AND t.date <= :asOfDate), 0.0)
                - COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.accountId = a.id AND t.toAccountId IS NOT NULL AND t.date <= :asOfDate), 0.0)
                + COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.toAccountId = a.id AND t.date <= :asOfDate), 0.0)
            ), 0.0)
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
                     THEN COALESCE((SELECT SUM(openingBalance) FROM parties WHERE isEnabled = 1), 0.0) 
                     ELSE a.openingBalance 
                END)
                + COALESCE((SELECT SUM(t.amount) FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id WHERE t.accountId = a.id AND c.type = 'income' AND t.date <= :asOfDate), 0.0)
                - COALESCE((SELECT SUM(t.amount) FROM transactions t LEFT JOIN categories c ON t.categoryId = c.id WHERE t.accountId = a.id AND c.type = 'expense' AND t.date <= :asOfDate), 0.0)
                - COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.accountId = a.id AND t.toAccountId IS NOT NULL AND t.date <= :asOfDate), 0.0)
                + COALESCE((SELECT SUM(t.amount) FROM transactions t WHERE t.toAccountId = a.id AND t.date <= :asOfDate), 0.0)
            ), 0.0)
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
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE notebookId = :notebookId ORDER BY createdAt DESC")
    fun getNotesByNotebook(notebookId: Int): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY createdAt DESC")
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
}

data class TransactionWithDetails(
    @Embedded val transaction: Transaction,
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
    val openingBalance: Double,
    val balance: Double,
    val minorHeadId: Int? = null,
    val billingCycleStart: String? = null,
    val billingCycleEnd: String? = null,
    val paymentDueDate: String? = null,
    val icon: String? = null
)

data class BudgetWithDetails(
    @Embedded val budget: Budget,
    val categoryName: String,
    val categoryType: String
)

data class PartyBalance(
    val id: Int,
    val name: String,
    val balance: Double
)

data class MajorHeadBalance(
    val id: Int,
    val name: String,
    val balance: Double
)

data class MinorHeadBalance(
    val id: Int,
    val name: String,
    val majorHeadId: Int,
    val balance: Double
)
