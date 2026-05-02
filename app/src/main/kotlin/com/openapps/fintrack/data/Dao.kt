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
    @Query("""
        SELECT b.*, c.name as categoryName, c.type as categoryType
        FROM budgets b
        JOIN categories c ON b.categoryId = c.id
    """)
    fun getAllBudgets(): Flow<List<BudgetWithDetails>>

    @Upsert
    suspend fun upsertBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)

    // Transactions
    @Query("""
        SELECT t.*, c.name as categoryName, c.type as categoryType, a.name as accountName, a2.name as toAccountName
        FROM transactions t 
        LEFT JOIN categories c ON t.categoryId = c.id 
        JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts a2 ON t.toAccountId = a2.id
        ORDER BY t.date DESC, t.time DESC
    """)
    fun getAllTransactionsWithDetails(): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT t.*, c.name as categoryName, c.type as categoryType, a.name as accountName, a2.name as toAccountName
        FROM transactions t 
        LEFT JOIN categories c ON t.categoryId = c.id 
        JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts a2 ON t.toAccountId = a2.id
        WHERE t.date BETWEEN :startDate AND :endDate
        ORDER BY t.date DESC, t.time DESC
    """)
    fun getTransactionsByDateRange(startDate: String, endDate: String): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT t.*, c.name as categoryName, c.type as categoryType, a.name as accountName, a2.name as toAccountName
        FROM transactions t 
        LEFT JOIN categories c ON t.categoryId = c.id 
        JOIN accounts a ON t.accountId = a.id
        LEFT JOIN accounts a2 ON t.toAccountId = a2.id
        WHERE (t.accountId = :accountId OR t.toAccountId = :accountId)
        AND t.date BETWEEN :startDate AND :endDate
        ORDER BY t.date DESC, t.time DESC
    """)
    fun getAccountTransactionsByDateRange(accountId: Int, startDate: String, endDate: String): Flow<List<TransactionWithDetails>>

    @Query("""
        SELECT a.id, a.name, a.type, a.openingBalance,
        (a.openingBalance + 
            COALESCE((SELECT SUM(amount) FROM transactions WHERE accountId = a.id AND date <= :asOfDate), 0) +
            COALESCE((SELECT SUM(amount) FROM transactions WHERE toAccountId = a.id AND date <= :asOfDate), 0)
        ) as balance
        FROM accounts a
        WHERE a.isEnabled = 1
    """)
    fun getAccountBalances(asOfDate: String): Flow<List<AccountBalance>>

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)
}

data class TransactionWithDetails(
    @Embedded val transaction: Transaction,
    val categoryName: String?,
    val categoryType: String?,
    val accountName: String,
    val toAccountName: String?
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
