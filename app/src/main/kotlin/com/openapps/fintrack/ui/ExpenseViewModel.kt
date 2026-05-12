/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openapps.fintrack.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.TimeUnit

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val database: AppDatabase by lazy { AppDatabase.getDatabase(application, viewModelScope) }
    private val dao: ExpenseDao by lazy { database.expenseDao() }

    var editingCategory by mutableStateOf<Category?>(null)
    var editingAccount by mutableStateOf<Account?>(null)
    var editingParty by mutableStateOf<Party?>(null)
    var editingTag by mutableStateOf<Tag?>(null)
    var editingBudgetRaw by mutableStateOf<Budget?>(null)
    var editingTemplate by mutableStateOf<Template?>(null)
    var selectedTransactionDetail by mutableStateOf<TransactionWithDetails?>(null)
    var summaryInitialTab by mutableStateOf("Transactions")
    var summaryInitialAccountId by mutableStateOf<Int?>(null)

    // Draft Transaction State
    var draftTransaction by mutableStateOf<DraftTransaction?>(null)

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

    var inactivityTimeout by mutableStateOf(prefs.getString("inactivity_timeout", "30 seconds") ?: "30 seconds")
        private set

    // Dashboard Settings
    var dashboardAccountIds by mutableStateOf(prefs.getString("dashboard_account_ids", "")?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toIntOrNull() } ?: emptyList())
        private set

    var dashboardBudgetIds by mutableStateOf(prefs.getString("dashboard_budget_ids", "")?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toIntOrNull() } ?: emptyList())
        private set
    
    var bottomTabOrder by mutableStateOf(prefs.getString("bottom_tab_order", "home,analysis,transactions,budgets")?.split(",") ?: listOf("home", "analysis", "transactions", "budgets"))
        private set

    // Notification Settings
    var remindersEnabled by mutableStateOf(prefs.getBoolean("reminders_enabled", false))
        private set
    var reminderFrequency by mutableStateOf(prefs.getInt("reminder_frequency", 1))
        private set
    var reminderUnit by mutableStateOf(prefs.getString("reminder_unit", "day/s") ?: "day/s")
        private set
    var reminderTime by mutableStateOf(prefs.getString("reminder_time", "20:00") ?: "20:00")
        private set
    var reminderMessage by mutableStateOf(prefs.getString("reminder_message", "Hey, Time to record expenses now") ?: "Hey, Time to record expenses now")
        private set

    // WebDAV Settings
    var remoteSyncEnabled by mutableStateOf(prefs.getBoolean("remote_sync_enabled", false))
        private set
    
    var webdavUrl by mutableStateOf(EncryptedPrefsHelper.getString("webdav_url", "") ?: "")
        private set
    var webdavUsername by mutableStateOf(EncryptedPrefsHelper.getString("webdav_username", "") ?: "")
        private set
    var webdavPassword by mutableStateOf(EncryptedPrefsHelper.getString("webdav_password", "") ?: "")
        private set
    var syncFrequency by mutableStateOf(prefs.getString("sync_frequency", "1 day") ?: "1 day")
        private set

    var lastSyncTime by mutableStateOf(prefs.getString("last_sync_time", "Never") ?: "Never")
        private set
    var lastSyncSize by mutableStateOf(prefs.getString("last_sync_size", "") ?: "")
        private set

    // E2EE Remote Encryption
    var encryptRemoteEnabled by mutableStateOf(prefs.getBoolean("encrypt_remote_enabled", false))
        private set
    var remoteMasterPassword by mutableStateOf(EncryptedPrefsHelper.getString("remote_master_password", "") ?: "")
        private set

    // Template Customization Settings
    var templateFields by mutableStateOf(prefs.getStringSet("template_fields", setOf("type", "accountId", "categoryId", "amount", "note", "tags")) ?: setOf("type", "accountId", "categoryId", "amount", "note", "tags"))
        private set

    // Progress State for UI
    var syncProgress by mutableStateOf(0f)
    var syncTotalSize by mutableStateOf(0L)
    var syncProcessedSize by mutableStateOf(0L)
    var isSyncing by mutableStateOf(false)
    var syncMessage by mutableStateOf("")
    var isTestingConnection by mutableStateOf(false)

    // Import/Decryption Progress
    var importProgress by mutableStateOf(0f)
    var isImporting by mutableStateOf(false)

    // Local Server State
    private val serverManager: ServerManager by lazy { 
        ServerManager.getInstance(application, dao).also {
            it.onDatabaseChange = { triggerRefresh() }
        }
    }
    val isServerRunning = serverManager.isRunning
    val isStopping = serverManager.isStopping
    val serverError = serverManager.serverError
    val httpUrl = serverManager.httpUrl
    val httpsUrl = serverManager.httpsUrl
    val activeClients = serverManager.activeClients
    val terminationLogs = serverManager.terminationLogs

    fun authorizePairing(pairingId: String): Boolean {
        return serverManager.authorizePairing(pairingId)
    }

    fun disconnectClient(ip: String) {
        serverManager.disconnectClient(ip)
    }

    fun toggleServer(running: Boolean) {
        if (running) {
            serverManager.startServer()
        } else {
            serverManager.stopServer()
        }
    }

    // UI Refresh Trigger
    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "last_sync_time" -> lastSyncTime = p.getString(key, "Never") ?: "Never"
            "last_sync_size" -> lastSyncSize = p.getString(key, "") ?: ""
            "inactivity_timeout" -> inactivityTimeout = p.getString(key, "30 seconds") ?: "30 seconds"
            "theme" -> currentTheme = p.getString(key, "Dark") ?: "Dark"
            "auto_read_enabled" -> autoReadEnabled = p.getBoolean(key, false)
            "multi_tag_enabled" -> multiTagEnabled = p.getBoolean(key, false)
            "app_lock_enabled" -> appLockEnabled = p.getBoolean(key, false)
            "use_millions_system" -> useMillionsSystem = p.getBoolean(key, false)
            "show_assets_on_home" -> showAssetsOnHome = p.getBoolean(key, false)
            "remote_sync_enabled" -> remoteSyncEnabled = p.getBoolean(key, false)
            "sync_frequency" -> syncFrequency = p.getString(key, "1 day") ?: "1 day"
            "encrypt_remote_enabled" -> encryptRemoteEnabled = p.getBoolean(key, false)
            "template_fields" -> templateFields = p.getStringSet(key, setOf("type", "accountId", "categoryId", "amount", "note", "tags")) ?: setOf("type", "accountId", "categoryId", "amount", "note", "tags")
            "reminders_enabled" -> remindersEnabled = p.getBoolean(key, false)
            "reminder_frequency" -> reminderFrequency = p.getInt(key, 1)
            "reminder_unit" -> reminderUnit = p.getString(key, "day/s") ?: "day/s"
            "reminder_time" -> reminderTime = p.getString(key, "20:00") ?: "20:00"
            "reminder_message" -> reminderMessage = p.getString(key, "Hey, Time to record expenses now") ?: "Hey, Time to record expenses now"
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun triggerRefresh() {
        _refreshTrigger.value += 1
    }

    fun updateRemoteSyncEnabled(enabled: Boolean) {
        remoteSyncEnabled = enabled
        prefs.edit().putBoolean("remote_sync_enabled", enabled).apply()
    }

    fun updateEncryptRemote(enabled: Boolean) {
        encryptRemoteEnabled = enabled
        prefs.edit().putBoolean("encrypt_remote_enabled", enabled).apply()
    }

    fun updateRemoteMasterPassword(password: String) {
        remoteMasterPassword = password
        EncryptedPrefsHelper.putString("remote_master_password", password)
    }

    fun updateWebdavUrl(url: String) {
        webdavUrl = url
        EncryptedPrefsHelper.putString("webdav_url", url)
    }

    fun updateWebdavUsername(username: String) {
        webdavUsername = username
        EncryptedPrefsHelper.putString("webdav_username", username)
    }

    fun updateWebdavPassword(password: String) {
        webdavPassword = password
        EncryptedPrefsHelper.putString("webdav_password", password)
    }

    fun updateSyncFrequency(frequency: String) {
        syncFrequency = frequency
        prefs.edit().putString("sync_frequency", frequency).apply()
    }

    fun updateDashboardAccounts(ids: List<Int>) {
        dashboardAccountIds = ids.take(3)
        prefs.edit().putString("dashboard_account_ids", ids.take(3).joinToString(",")).apply()
    }

    fun updateDashboardBudgets(ids: List<Int>) {
        dashboardBudgetIds = ids.take(3)
        prefs.edit().putString("dashboard_budget_ids", ids.take(3).joinToString(",")).apply()
    }

    fun updateTabOrder(order: List<String>) {
        bottomTabOrder = order
        prefs.edit().putString("bottom_tab_order", order.joinToString(",")).apply()
    }

    fun updateReminderEnabled(enabled: Boolean) {
        remindersEnabled = enabled
        prefs.edit().putBoolean("reminders_enabled", enabled).apply()
        scheduleReminders()
    }

    fun updateReminderFrequency(freq: Int) {
        reminderFrequency = freq
        prefs.edit().putInt("reminder_frequency", freq).apply()
        scheduleReminders()
    }

    fun updateReminderUnit(unit: String) {
        reminderUnit = unit
        prefs.edit().putString("reminder_unit", unit).apply()
        scheduleReminders()
    }

    fun updateReminderTime(time: String) {
        reminderTime = time
        prefs.edit().putString("reminder_time", time).apply()
        scheduleReminders()
    }

    fun updateReminderMessage(msg: String) {
        reminderMessage = msg
        prefs.edit().putString("reminder_message", msg).apply()
    }

    private fun scheduleReminders() {
        val context = getApplication<Application>()
        if (!remindersEnabled) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("expense_reminder")
            return
        }

        val frequencyMillis = when (reminderUnit) {
            "day/s" -> reminderFrequency * 24 * 60 * 60 * 1000L
            "week/s" -> reminderFrequency * 7 * 24 * 60 * 60 * 1000L
            "month/s" -> reminderFrequency * 30 * 24 * 60 * 60 * 1000L
            "year/s" -> reminderFrequency * 365 * 24 * 60 * 60 * 1000L
            else -> 24 * 60 * 60 * 1000L
        }

        val timeParts = reminderTime.split(":")
        val targetHour = timeParts[0].toIntOrNull() ?: 20
        val targetMinute = timeParts[1].toIntOrNull() ?: 0

        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            set(java.util.Calendar.MINUTE, targetMinute)
            set(java.util.Calendar.SECOND, 0)
            if (before(now)) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        val initialDelay = target.timeInMillis - now.timeInMillis

        val reminderRequest = androidx.work.PeriodicWorkRequestBuilder<com.openapps.fintrack.data.ReminderWorker>(
            frequencyMillis, TimeUnit.MILLISECONDS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "expense_reminder",
            androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
            reminderRequest
        )
    }

    suspend fun testWebdavConnection(): Result<String> = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { isTestingConnection = true }
        try {
            val baseWebdavUrl = webdavUrl.toHttpUrlOrNull() 
                ?: return@withContext Result.failure(Exception("Invalid WebDAV URL format"))

            if (baseWebdavUrl.scheme != "https") {
                return@withContext Result.failure(Exception("HTTPS required for security."))
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(baseWebdavUrl)
                .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                .header("User-Agent", "FinTrack-Android")
                .method("PROPFIND", null)
                .header("Depth", "0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success("Connection Successful!")
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            withContext(Dispatchers.Main) { isTestingConnection = false }
        }
    }

    suspend fun syncNow(): Result<String> = withContext(Dispatchers.IO) {
        val tempSnapshot = File(getApplication<Application>().cacheDir, "sync_snapshot.db")
        val finalFileToUpload = File(getApplication<Application>().cacheDir, "sync_final.db")
        try {
            withContext(Dispatchers.Main) { 
                isSyncing = true
                syncProgress = 0f
                syncMessage = "Initializing..."
                syncProcessedSize = 0L
                syncTotalSize = 0L
            }
            
            val dbFile = getApplication<Application>().getDatabasePath("expenses_database")
            database.checkpoint()
            
            withContext(Dispatchers.Main) { syncMessage = "Creating snapshot..." }
            FileInputStream(dbFile).use { input ->
                FileOutputStream(tempSnapshot).use { output ->
                    input.copyTo(output)
                }
            }
            
            val snapshotSize = tempSnapshot.length()
            
            if (encryptRemoteEnabled) {
                if (remoteMasterPassword.isEmpty()) {
                    return@withContext Result.failure(Exception("Encryption enabled but password is empty"))
                }
                
                withContext(Dispatchers.Main) { 
                    syncMessage = "Encrypting..." 
                    syncTotalSize = snapshotSize
                }
                
                val encResult = EncryptionService.encryptFile(tempSnapshot, finalFileToUpload, remoteMasterPassword) { p ->
                    viewModelScope.launch(Dispatchers.Main) {
                        syncProgress = p
                        syncProcessedSize = (p * snapshotSize).toLong()
                    }
                }
                
                if (encResult.isFailure) {
                    return@withContext Result.failure(Exception("Encryption failed: ${encResult.exceptionOrNull()?.message}"))
                }
            } else {
                tempSnapshot.copyTo(finalFileToUpload, overwrite = true)
            }
            
            val actualFinalSize = finalFileToUpload.length()
            
            withContext(Dispatchers.Main) { 
                syncTotalSize = actualFinalSize
                syncProgress = 0f
                syncProcessedSize = 0L
                syncMessage = "Uploading..."
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS) 
                .retryOnConnectionFailure(true)
                .build()

            val baseWebdavUrl = webdavUrl.toHttpUrlOrNull() 
                ?: return@withContext Result.failure(Exception("Invalid WebDAV URL format"))

            if (baseWebdavUrl.scheme != "https") {
                return@withContext Result.failure(Exception("HTTPS required for security."))
            }

            val fileName = "expenses_database_sync.db"
            val fullUrl = baseWebdavUrl.newBuilder().addPathSegment(fileName).build().toString()
            val tmpUrl = "$fullUrl.tmp"

            var lastError: Exception? = null
            for (attempt in 1..3) {
                try {
                    if (attempt > 1) {
                        withContext(Dispatchers.Main) {
                            syncMessage = "Failed and retrying attempt $attempt..."
                            syncProgress = 0f
                            syncProcessedSize = 0L
                        }
                        kotlinx.coroutines.delay(2000)
                    }

                    val requestBody = object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                        override fun contentLength() = actualFinalSize
                        override fun writeTo(sink: BufferedSink) {
                            val buffer = ByteArray(8192)
                            var uploaded = 0L
                            FileInputStream(finalFileToUpload).use { input ->
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    sink.write(buffer, 0, read)
                                    uploaded += read
                                    val p = uploaded.toFloat() / actualFinalSize
                                    viewModelScope.launch(Dispatchers.Main) {
                                        syncProcessedSize = uploaded
                                        syncProgress = p
                                        if (p >= 0.99f) syncMessage = "Finalizing..."
                                    }
                                }
                            }
                            sink.flush()
                        }
                    }

                    val putRequest = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                        .header("User-Agent", "FinTrack-Android")
                        .put(requestBody)
                        .build()

                    client.newCall(putRequest).execute().use { response ->
                        if (!response.isSuccessful && response.code != 201 && response.code != 204) {
                            throw Exception("PUT stage failed: HTTP ${response.code}")
                        }
                    }

                    withContext(Dispatchers.Main) { syncMessage = "Finalizing (atomic move)..." }
                    val moveRequest = Request.Builder()
                        .url(tmpUrl)
                        .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                        .header("User-Agent", "FinTrack-Android")
                        .header("Destination", fullUrl)
                        .header("Overwrite", "T")
                        .method("MOVE", null)
                        .build()

                    client.newCall(moveRequest).execute().use { response ->
                        if (response.isSuccessful || response.code == 201 || response.code == 204) {
                            val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            val sizeStr = String.format("%.2f KB", actualFinalSize / 1024.0)
                            withContext(Dispatchers.Main) {
                                lastSyncTime = time
                                lastSyncSize = sizeStr
                                prefs.edit().putString("last_sync_time", time).putString("last_sync_size", sizeStr).apply()
                                syncMessage = "Done"
                            }
                            return@withContext Result.success("Sync Successful!")
                        } else {
                            throw Exception("MOVE stage failed: HTTP ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                    Log.e("ExpenseViewModel", "Attempt $attempt failed", e)
                    try {
                        val cleanupReq = Request.Builder()
                            .url(tmpUrl)
                            .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                            .delete().build()
                        client.newCall(cleanupReq).execute().close()
                    } catch (ignore: Exception) {}
                    
                    if (attempt == 3) break
                }
            }
            Result.failure(lastError ?: Exception("Unknown sync error"))
        } catch (e: Exception) {
            Log.e("ExpenseViewModel", "Sync error", e)
            Result.failure(e)
        } finally {
            if (tempSnapshot.exists()) tempSnapshot.delete()
            if (finalFileToUpload.exists()) finalFileToUpload.delete()
            withContext(Dispatchers.Main) { isSyncing = false }
        }
    }

    suspend fun downloadRemoteToLocal(destUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val tempDownload = File(getApplication<Application>().cacheDir, "remote_dl.db")
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val fileName = "expenses_database_sync.db"
            val baseWebdavUrl = webdavUrl.toHttpUrlOrNull() 
                ?: return@withContext Result.failure(Exception("Invalid WebDAV URL format"))

            if (baseWebdavUrl.scheme != "https") {
                return@withContext Result.failure(Exception("HTTPS required."))
            }

            val fullUrl = baseWebdavUrl.newBuilder().addPathSegment(fileName).build().toString()

            val request = Request.Builder()
                .url(fullUrl)
                .header("Authorization", Credentials.basic(webdavUsername, webdavPassword))
                .header("User-Agent", "FinTrack-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.let { body ->
                        FileOutputStream(tempDownload).use { out ->
                            body.source().use { source ->
                                out.write(source.readByteArray())
                            }
                        }
                    }
                    getApplication<Application>().contentResolver.openOutputStream(destUri)?.use { out ->
                        FileInputStream(tempDownload).use { it.copyTo(out) }
                    }
                    Result.success("Backup file copied to local path!")
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempDownload.exists()) tempDownload.delete()
        }
    }

    fun updateInactivityTimeout(value: String) {
        inactivityTimeout = value
        prefs.edit().putString("inactivity_timeout", value).apply()
    }

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

    fun updateTemplateField(field: String, enabled: Boolean) {
        val newSet = templateFields.toMutableSet()
        if (enabled) newSet.add(field) else newSet.remove(field)
        templateFields = newSet
        prefs.edit().putStringSet("template_fields", newSet).apply()
    }

    fun formatAmount(amount: Double): String {
        return if (useMillionsSystem) {
            java.text.NumberFormat.getCurrencyInstance(Locale.US).format(amount).replace("$", "")
        } else {
            java.text.DecimalFormat("##,##,##,###.##").format(amount)
        }
    }

    // Data access helpers
    fun getAllAccounts(): Flow<List<Account>> = dao.getAllAccounts()
    fun getEnabledAccounts(): Flow<List<Account>> = dao.getEnabledAccounts()
    fun getAllCategories(): Flow<List<Category>> = dao.getAllCategories()
    fun getEnabledCategories(): Flow<List<Category>> = dao.getEnabledCategories()
    fun getEnabledCategoriesByType(type: String): Flow<List<Category>> = dao.getEnabledCategoriesByType(type)
    
    fun getAllTags(): Flow<List<Tag>> = dao.getAllTags()
    fun getEnabledTags(): Flow<List<Tag>> = dao.getEnabledTags()
    
    fun getAllParties(): Flow<List<Party>> = dao.getAllParties()
    fun getEnabledParties(): Flow<List<Party>> = dao.getEnabledParties()
    fun getPartyBalances(asOfDate: String): Flow<List<PartyBalance>> = dao.getPartyBalances(asOfDate)
    
    fun getAllBudgets(): Flow<List<Budget>> = dao.getAllBudgets()
    
    fun getAllTemplates(): Flow<List<Template>> = dao.getAllTemplates()
    
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
            val budgetFlows = budgets.map { budget ->
                val range = getRangeForDuration(date, budget.duration)
                dao.getTransactionsByDateRange(range.first, range.second).map { transactions ->
                    val catIds = budget.categoryIds.split(",").mapNotNull { it.toIntOrNull() }
                    val relevantTransactions = transactions.filter { it.transaction.categoryId in catIds }
                    
                    val actual = relevantTransactions.sumOf { 
                        if (it.categoryType == "expense") it.transaction.amount else -it.transaction.amount 
                    }
                    
                    val categories = runBlocking { dao.getEnabledCategories().first() }
                    val catNames = catIds.mapNotNull { id -> categories.find { it.id == id }?.name }.joinToString(", ")
                    
                    BudgetVsActual(
                        categoryName = budget.name ?: catNames,
                        categoryType = if (relevantTransactions.any { it.categoryType == "expense" }) "expense" else "income",
                        budgetAmount = budget.amount,
                        actualAmount = actual,
                        duration = budget.duration
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
                val end = date.withMonth(if (startMonth == 1) { 6 } else { 12 }).with(TemporalAdjusters.lastDayOfMonth())
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
            triggerRefresh()
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
            triggerRefresh()
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
            triggerRefresh()
        }
    }

    fun saveParty(name: String, openingBalance: Double, isEnabled: Boolean) {
        viewModelScope.launch {
            val party = editingParty?.copy(
                name = name,
                openingBalance = openingBalance,
                isEnabled = isEnabled
            ) ?: Party(
                name = name,
                openingBalance = openingBalance,
                isEnabled = isEnabled
            )
            dao.upsertParty(party)
            editingParty = null
            triggerRefresh()
        }
    }

    fun saveBudgetRaw(name: String?, categoryIds: String, amount: Double, duration: String, note: String?) {
        viewModelScope.launch {
            val budget = editingBudgetRaw?.copy(
                name = name,
                categoryIds = categoryIds,
                amount = amount,
                duration = duration,
                note = note
            ) ?: Budget(
                name = name,
                categoryIds = categoryIds,
                amount = amount,
                duration = duration,
                note = note
            )
            dao.upsertBudget(budget)
            editingBudgetRaw = null
            triggerRefresh()
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { dao.deleteCategory(category); triggerRefresh() }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch { dao.deleteAccount(account); triggerRefresh() }
    }
    
    fun deleteTag(tag: Tag) {
        viewModelScope.launch { dao.deleteTag(tag); triggerRefresh() }
    }

    fun deleteParty(party: Party) {
        viewModelScope.launch { dao.deleteParty(party); triggerRefresh() }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { dao.deleteBudget(budget); triggerRefresh() }
    }

    suspend fun saveTemplate(template: Template): Boolean {
        val existing = dao.getAllTemplates().first()
        val duplicate = existing.find { it.name.equals(template.name, ignoreCase = true) && it.id != template.id }
        if (duplicate != null) return false
        
        dao.upsertTemplate(template)
        return true
    }

    fun deleteTemplate(template: Template) {
        viewModelScope.launch { dao.deleteTemplate(template) }
    }

    fun toggleCategoryEnabled(category: Category) {
        viewModelScope.launch {
            dao.updateCategory(category.copy(isEnabled = !category.isEnabled))
            triggerRefresh()
        }
    }

    fun toggleAccountEnabled(account: Account) {
        viewModelScope.launch {
            val updatedAccount = account.copy(isEnabled = !account.isEnabled)
            dao.updateAccount(updatedAccount)
            triggerRefresh()
        }
    }
    
    fun toggleTagEnabled(tag: Tag) {
        viewModelScope.launch {
            val updatedTag = tag.copy(isEnabled = !tag.isEnabled)
            dao.updateTag(updatedTag)
            triggerRefresh()
        }
    }

    fun togglePartyEnabled(party: Party) {
        viewModelScope.launch {
            val updated = party.copy(isEnabled = !party.isEnabled)
            dao.updateParty(updated)
            triggerRefresh()
        }
    }

    fun addTransaction(date: String, time: String, accountId: Int, categoryId: Int?, amount: Double, note: String?, toAccountId: Int? = null, tags: String? = null, type: String, partyId: Int? = null, toPartyId: Int? = null) {
        viewModelScope.launch {
            val prefix = when (type) {
                "income" -> "INC"
                "expense" -> "EXP"
                "transfer" -> "TNF"
                else -> "TXN"
            }
            
            val lastNum = dao.getLastTransactionNumber(prefix)
            val nextSerial = if (lastNum != null) {
                val parts = lastNum.split("/")
                val lastSerial = parts.last().toIntOrNull() ?: 99999
                lastSerial + 1
            } else {
                100000
            }
            
            val year = try { LocalDate.parse(date).year } catch(e: Exception) { LocalDate.now().year }
            val txnNumber = "$prefix/$year/$nextSerial"

            dao.insertTransaction(
                Transaction(
                    date = date,
                    time = time,
                    accountId = accountId,
                    categoryId = if (type == "transfer") null else categoryId,
                    amount = amount,
                    note = note,
                    toAccountId = toAccountId,
                    tags = tags,
                    transactionNumber = txnNumber,
                    partyId = partyId,
                    toPartyId = toPartyId
                )
            )
            triggerRefresh()
        }
    }

    fun addMultiEntryTransaction(date: String, time: String, accountId: Int, entries: List<Triple<Int, Double, String?>>, tags: String?, type: String, partyId: Int? = null) {
        viewModelScope.launch {
            val prefix = when (type) {
                "income" -> "MINC"
                "expense" -> "MEXP"
                else -> "MTXN"
            }
            
            val lastNum = dao.getLastTransactionNumber(prefix)
            var nextSerial = if (lastNum != null) {
                val parts = lastNum.split("/")
                val lastSerial = parts.last().toIntOrNull() ?: 99999
                lastSerial + 1
            } else {
                100000
            }
            
            val year = try { LocalDate.parse(date).year } catch(e: Exception) { LocalDate.now().year }
            
            entries.forEach { (catId, amt, entryNote) ->
                val txnNumber = "$prefix/$year/$nextSerial"
                dao.insertTransaction(
                    Transaction(
                        date = date,
                        time = time,
                        accountId = accountId,
                        categoryId = catId,
                        amount = amt,
                        note = entryNote,
                        transactionNumber = txnNumber,
                        tags = tags,
                        partyId = partyId
                    )
                )
                nextSerial++
            }
            triggerRefresh()
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

data class DraftTransaction(
    val type: String,
    val amount: String,
    val note: String,
    val date: String,
    val time: String,
    val accountId: Int?,
    val toAccountId: Int?,
    val categoryId: Int?,
    val selectedTagIds: List<Int>,
    val isMultiEntry: Boolean = false,
    val multiEntryRows: List<DraftMultiEntryRow> = emptyList()
)

data class DraftMultiEntryRow(
    val categoryId: Int?,
    val amount: String,
    val note: String? = null
)
