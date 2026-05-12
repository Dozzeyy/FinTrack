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
    @Query("SELECT * FROM accounts ORDER BY name")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE isEnabled = 1 ORDER BY name")
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
        SELECT t.*, c.name as categoryName, c.type as categoryType, a.name as accountName, a2.name as toAccountName, p.name as partyName, p2.name as toPartyName
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
        SELECT t.*, c.name as categoryName, c.type as categoryType, a.name as accountName, a2.name as toAccountName, p.name as partyName, p2.name as toPartyName
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
        SELECT t.*, c.name as categoryName, c.type as categoryType, a.name as accountName, a2.name as toAccountName, p.name as partyName, p2.name as toPartyName
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
                 THEN COALESCE((SELECT SUM(openingBalance) FROM parties WHERE isEnabled = 1), 0) 
                 ELSE a.openingBalance 
            END)
            + COALESCE((SELECT SUM(t.amount) 
                FROM transactions t 
                JOIN categories c ON t.categoryId = c.id 
                WHERE t.accountId = a.id AND c.type = 'income' AND t.date <= :asOfDate), 0)
            - COALESCE((SELECT SUM(t.amount) 
                FROM transactions t 
                JOIN categories c ON t.categoryId = c.id 
                WHERE t.accountId = a.id AND c.type = 'expense' AND t.date <= :asOfDate), 0)
            - COALESCE((SELECT SUM(t.amount) 
                FROM transactions t 
                WHERE t.accountId = a.id AND t.toAccountId IS NOT NULL AND t.date <= :asOfDate), 0)
            + COALESCE((SELECT SUM(t.amount) 
                FROM transactions t 
                WHERE t.toAccountId = a.id AND t.date <= :asOfDate), 0)
        ) as balance
        FROM accounts a
        WHERE a.isEnabled = 1
    """)
    fun getAccountBalances(asOfDate: String): Flow<List<AccountBalance>>

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)

    @Query("SELECT transactionNumber FROM transactions WHERE transactionNumber LIKE :prefix || '%' ORDER BY id DESC LIMIT 1")
    suspend fun getLastTransactionNumber(prefix: String): String?

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
}

data class TransactionWithDetails(
    @Embedded val transaction: Transaction,
    val categoryName: String?,
    val categoryType: String?,
    val accountName: String,
    val toAccountName: String?,
    val partyName: String?,
    val toPartyName: String?
)

data class AccountBalance(
    val id: Int,
    val name: String,
    val type: String,
    val openingBalance: Double,
    val balance: Double
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
