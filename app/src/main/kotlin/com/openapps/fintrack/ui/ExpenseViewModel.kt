package com.openapps.fintrack.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openapps.fintrack.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val database: AppDatabase by lazy { AppDatabase.getDatabase(application, viewModelScope) }
    private val dao: ExpenseDao by lazy { database.expenseDao() }

    var editingCategory by mutableStateOf<Category?>(null)
    var editingAccount by mutableStateOf<Account?>(null)
    var editingTag by mutableStateOf<Tag?>(null)
    var editingBudget by mutableStateOf<BudgetWithDetails?>(null)
    var selectedTransactionDetail by mutableStateOf<TransactionWithDetails?>(null)

    // Settings
    var autoReadEnabled by mutableStateOf(prefs.getBoolean("auto_read_enabled", false))
        private set
    
    var currentTheme by mutableStateOf(prefs.getString("theme", "Dark") ?: "Dark")
        private set

    var smsCurrencies by mutableStateOf(prefs.getString("sms_currencies", "Rs, USD") ?: "Rs, USD")
        private set
    var smsKeywords by mutableStateOf(prefs.getString("sms_keywords", "debit, credit, spent") ?: "debit, credit, spent")
        private set
    var smsConditionType by mutableStateOf(prefs.getString("sms_condition_type", "OR") ?: "OR")
        private set
    
    var multiTagEnabled by mutableStateOf(prefs.getBoolean("multi_tag_enabled", false))
        private set
    
    var appLockEnabled by mutableStateOf(prefs.getBoolean("app_lock_enabled", false))
        private set

    var useMillionsSystem by mutableStateOf(prefs.getBoolean("use_millions_system", false))
        private set
    
    var showAssetsOnHome by mutableStateOf(prefs.getBoolean("show_assets_on_home", false))
        private set

    fun updateAutoReadEnabled(enabled: Boolean) {
        autoReadEnabled = enabled
        prefs.edit().putBoolean("auto_read_enabled", enabled).apply()
    }

    fun updateTheme(theme: String) {
        currentTheme = theme
        prefs.edit().putString("theme", theme).apply()
    }

    fun updateSmsCurrencies(value: String) {
        smsCurrencies = value
        prefs.edit().putString("sms_currencies", value).apply()
    }

    fun updateSmsKeywords(value: String) {
        smsKeywords = value
        prefs.edit().putString("sms_keywords", value).apply()
    }

    fun updateSmsConditionType(value: String) {
        smsConditionType = value
        prefs.edit().putString("sms_condition_type", value).apply()
    }
    
    fun updateMultiTagEnabled(enabled: Boolean) {
        multiTagEnabled = enabled
        prefs.edit().putBoolean("multi_tag_enabled", enabled).apply()
    }

    fun updateAppLockEnabled(enabled: Boolean) {
        appLockEnabled = enabled
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    }

    fun updateNumberSystem(useMillions: Boolean) {
        useMillionsSystem = useMillions
        prefs.edit().putBoolean("use_millions_system", useMillions).apply()
    }

    fun updateHomeScreenView(showAssets: Boolean) {
        showAssetsOnHome = showAssets
        prefs.edit().putBoolean("show_assets_on_home", showAssets).apply()
    }

    fun formatAmount(amount: Double): String {
        return if (useMillionsSystem) {
            java.text.NumberFormat.getCurrencyInstance(Locale.US).format(amount).replace("$", "")
        } else {
            formatIndianStyle(amount)
        }
    }

    private fun formatIndianStyle(amount: Double): String {
        val formatter = java.text.DecimalFormat("##,##,##,###.##")
        return formatter.format(amount)
    }

    // Data access helpers
    fun getAllAccounts(): Flow<List<Account>> = dao.getAllAccounts()
    fun getEnabledAccounts(): Flow<List<Account>> = dao.getEnabledAccounts()
    fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    fun getEnabledCategories(): Flow<List<Category>> = dao.getEnabledCategories()
    fun getEnabledCategoriesByType(type: String): Flow<List<Category>> = dao.getEnabledCategoriesByType(type)
    
    fun getAllTags(): Flow<List<Tag>> = dao.getAllTags()
    fun getEnabledTags(): Flow<List<Tag>> = dao.getEnabledTags()
    
    fun getAllBudgets(): Flow<List<BudgetWithDetails>> = dao.getAllBudgets()
    
    val allTransactions: Flow<List<TransactionWithDetails>> = dao.getAllTransactionsWithDetails()
    
    fun getFilteredTransactions(start: String, end: String): Flow<List<TransactionWithDetails>> {
        return dao.getTransactionsByDateRange(start, end)
    }

    fun getAccountBalances(asOfDate: String): Flow<List<AccountBalance>> {
        return dao.getAccountBalances(asOfDate)
    }

    fun getAccountTransactions(accountId: Int, start: String, end: String): Flow<List<TransactionWithDetails>> {
        return dao.getAccountTransactionsByDateRange(accountId, start, end)
    }
    
    fun getTransactionsByTags(tagIds: List<Int>, start: String, end: String): Flow<List<TransactionWithDetails>> {
        return dao.getTransactionsByDateRange(start, end).map { list ->
            list.filter { transactionWithDetails ->
                val tTags = transactionWithDetails.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                tagIds.any { it in tTags }
            }
        }
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun getBudgetVsActual(asOfDate: String): Flow<List<BudgetVsActual>> {
        val date = try { LocalDate.parse(asOfDate) } catch(e: Exception) { LocalDate.now() }
        return dao.getAllBudgets().flatMapLatest { budgets ->
            val budgetFlows = budgets.map { budgetDetail ->
                val range = getRangeForDuration(date, budgetDetail.budget.duration)
                dao.getTransactionsByDateRange(range.first, range.second).map { transactions ->
                    val actual = transactions
                        .filter { it.transaction.categoryId == budgetDetail.budget.categoryId }
                        .sumOf { if (it.categoryType == "expense") it.transaction.amount else -it.transaction.amount }
                    
                    BudgetVsActual(
                        categoryName = budgetDetail.categoryName,
                        categoryType = budgetDetail.categoryType,
                        budgetAmount = budgetDetail.budget.amount,
                        actualAmount = actual,
                        duration = budgetDetail.budget.duration
                    )
                }
            }
            if (budgetFlows.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
            else combine(budgetFlows) { it.toList() }
        }
    }

    private fun getRangeForDuration(date: LocalDate, duration: String): Pair<String, String> {
        val formatter = DateTimeFormatter.ISO_DATE
        return when (duration) {
            "Daily" -> Pair(date.format(formatter), date.format(formatter))
            "Weekly" -> {
                val start = date.with(java.time.DayOfWeek.MONDAY)
                val end = date.with(java.time.DayOfWeek.SUNDAY)
                Pair(start.format(formatter), end.format(formatter))
            }
            "Monthly" -> {
                val start = date.with(TemporalAdjusters.firstDayOfMonth())
                val end = date.with(TemporalAdjusters.lastDayOfMonth())
                Pair(start.format(formatter), end.format(formatter))
            }
            "Half Yearly" -> {
                val month = date.monthValue
                val startMonth = if (month <= 6) 1 else 7
                val start = date.withMonth(startMonth).with(TemporalAdjusters.firstDayOfMonth())
                val end = date.withMonth(if (startMonth == 1) 6 else 12).with(TemporalAdjusters.lastDayOfMonth())
                Pair(start.format(formatter), end.format(formatter))
            }
            "Yearly" -> {
                val start = date.with(TemporalAdjusters.firstDayOfYear())
                val end = date.with(TemporalAdjusters.lastDayOfYear())
                Pair(start.format(formatter), end.format(formatter))
            }
            else -> Pair(date.format(formatter), date.format(formatter))
        }
    }

    fun saveAccount(name: String, openingBalance: Double, description: String?, isEnabled: Boolean) {
        viewModelScope.launch {
            val account = editingAccount?.copy(
                name = name,
                openingBalance = openingBalance,
                description = description,
                isEnabled = isEnabled
            ) ?: Account(
                name = name,
                type = "asset",
                openingBalance = openingBalance,
                description = description,
                isEnabled = isEnabled
            )
            dao.upsertAccount(account)
            editingAccount = null
        }
    }

    fun saveCategory(name: String, type: String, description: String?, isEnabled: Boolean) {
        viewModelScope.launch {
            val category = editingCategory?.copy(
                name = name,
                type = type,
                description = description,
                isEnabled = isEnabled
            ) ?: Category(
                name = name,
                type = type,
                description = description,
                isEnabled = isEnabled
            )
            dao.upsertCategory(category)
            editingCategory = null
        }
    }
    
    fun saveTag(name: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val tag = editingTag?.copy(
                name = name,
                isEnabled = isEnabled
            ) ?: Tag(
                name = name,
                isEnabled = isEnabled
            )
            dao.upsertTag(tag)
            editingTag = null
        }
    }

    fun saveBudget(categoryId: Int, amount: Double, duration: String, note: String?) {
        viewModelScope.launch {
            val budget = editingBudget?.budget?.copy(
                categoryId = categoryId,
                amount = amount,
                duration = duration,
                note = note
            ) ?: Budget(
                categoryId = categoryId,
                amount = amount,
                duration = duration,
                note = note
            )
            dao.upsertBudget(budget)
            editingBudget = null
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { dao.deleteCategory(category) }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { dao.deleteAccount(account) }
    }
    
    fun deleteTag(tag: Tag) {
        viewModelScope.launch { dao.deleteTag(tag) }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { dao.deleteBudget(budget) }
    }

    fun toggleCategoryEnabled(category: Category) {
        viewModelScope.launch {
            dao.updateCategory(category.copy(isEnabled = !category.isEnabled))
        }
    }

    fun toggleAccountEnabled(account: Account) {
        viewModelScope.launch {
            dao.updateAccount(account.copy(isEnabled = !account.isEnabled))
        }
    }
    
    fun toggleTagEnabled(tag: Tag) {
        viewModelScope.launch {
            dao.updateTag(tag.copy(isEnabled = !tag.isEnabled))
        }
    }

    fun addTransaction(date: String, time: String, accountId: Int, categoryId: Int?, amount: Double, note: String?, toAccountId: Int? = null, tags: String? = null) {
        viewModelScope.launch {
            val txnNumber = "FT${System.currentTimeMillis()}"
            dao.insertTransaction(
                Transaction(
                    date = date,
                    time = time,
                    accountId = accountId,
                    categoryId = categoryId,
                    amount = amount,
                    note = note,
                    toAccountId = toAccountId,
                    tags = tags,
                    transactionNumber = txnNumber
                )
            )
        }
    }

    fun prepareForBackup() {
        database.checkpoint()
    }

    fun closeDatabase() {
        AppDatabase.closeDatabase()
    }

    fun refreshDatabase(context: Context) {
        AppDatabase.closeDatabase()
    }
}

data class BudgetVsActual(
    val categoryName: String,
    val categoryType: String,
    val budgetAmount: Double,
    val actualAmount: Double,
    val duration: String
)
