/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

@Database(entities = [Account::class, Category::class, Tag::class, Budget::class, Transaction::class, Template::class, Party::class, MajorHead::class, MinorHead::class, SubscriptionStatus::class, Note::class, ExchangeRate::class, Loan::class, LoanRepayment::class, Subscription::class, Notebook::class, Rule::class, InvoiceClearance::class], version = 39, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    fun checkpoint() {
        try {
            val db = this.openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            db.query("PRAGMA journal_mode=DELETE").close()
            db.query("PRAGMA journal_mode=WAL").close()
            Log.d("AppDatabase", "Checkpoint Success: WAL fully merged and zeroed")
        } catch (e: Exception) {
            Log.e("AppDatabase", "Checkpoint failed", e)
        }
    }

    companion object {
        private const val DB_NAME = "expenses_database"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        val databaseMutex = Mutex()

        val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) {} }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL)")
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tags` TEXT") } catch (e: Exception) {}
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `amount` REAL NOT NULL, `duration` TEXT NOT NULL, `note` TEXT)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transactionNumber` TEXT") } catch (e: Exception) {}
            }
        }
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `budgets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `categoryIds` TEXT NOT NULL, `amount` REAL NOT NULL, `duration` TEXT NOT NULL, `note` TEXT)")
                db.execSQL("INSERT INTO budgets_new (id, categoryIds, amount, duration, note) SELECT id, CAST(categoryId AS TEXT), amount, duration, note FROM budgets")
                db.execSQL("DROP TABLE budgets")
                db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `accountId` INTEGER, `toAccountId` INTEGER, `categoryId` INTEGER, `amount` REAL, `note` TEXT, `tags` TEXT)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `templates` ADD COLUMN `multiEntries` TEXT") } catch (e: Exception) {}
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `parties` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL)")
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `partyId` INTEGER") } catch (e: Exception) {}
                db.execSQL("INSERT INTO accounts (name, type, openingBalance, isEnabled) VALUES ('On Account', 'asset', 0.0, 1)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `parties` ADD COLUMN `openingBalance` REAL NOT NULL DEFAULT 0.0") } catch (e: Exception) {}
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `toPartyId` INTEGER") } catch (e: Exception) {}
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `major_heads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `minor_heads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `majorHeadId` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL)")
                
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `minorHeadId` INTEGER")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `creditLimit` REAL")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `billingCycleStart` TEXT")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `billingCycleEnd` TEXT")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `paymentDueDate` TEXT")

                val majorHeads = listOf("Investments", "Bank Accounts", "Digital Wallets", "Crypto", "Precious metals", "On Account (Loan)", "Cash", "credit cards", "Bank Loan", "Others")
                majorHeads.forEach { db.execSQL("INSERT INTO major_heads (name, isEnabled) VALUES ('$it', 1)") }

                val minorMap = mapOf(
                    1 to listOf("Equity", "mutual funds", "Fixed Deposit", "Recurring Deposit", "Retirement funds"),
                    2 to listOf("Savings account", "Current Account", "Overdraft Account"),
                    3 to listOf("PayPal", "Paytm", "UPI Lite", "Amazon"),
                    4 to listOf("XMR", "BTC", "USDT"),
                    5 to listOf("Gold", "Silver", "Diamond"),
                    6 to listOf("Relative", "Friend", "My Ex", "Business party"),
                    7 to listOf("Default"),
                    8 to listOf("US Bank", "Wells Fargo", "JP Morgan", "HDFC", "ICICI", "Axis", "Kotak"),
                    9 to listOf("US Bank", "Wells Fargo", "JP Morgan", "HDFC", "ICICI", "Axis", "Kotak"),
                    10 to listOf("Default")
                )
                
                minorMap.forEach { (majorId, minors) ->
                    minors.forEach { db.execSQL("INSERT INTO minor_heads (name, majorHeadId, isEnabled) VALUES ('$it', $majorId, 1)") }
                }
                
                db.execSQL("UPDATE accounts SET minorHeadId = (SELECT id FROM minor_heads WHERE majorHeadId = 2 LIMIT 1) WHERE name = 'Savings Bank'")
                db.execSQL("UPDATE accounts SET minorHeadId = (SELECT id FROM minor_heads WHERE majorHeadId = 7 LIMIT 1) WHERE name = 'Cash'")
                db.execSQL("UPDATE accounts SET minorHeadId = (SELECT id FROM minor_heads WHERE majorHeadId = 1 LIMIT 1) WHERE name = 'Investments'")
                db.execSQL("UPDATE accounts SET minorHeadId = (SELECT id FROM minor_heads WHERE majorHeadId = 8 LIMIT 1) WHERE name = 'Credit Cards'")
                db.execSQL("UPDATE accounts SET minorHeadId = (SELECT id FROM minor_heads WHERE majorHeadId = 6 LIMIT 1) WHERE name = 'On Account'")
                db.execSQL("UPDATE accounts SET minorHeadId = (SELECT id FROM minor_heads WHERE majorHeadId = 10 LIMIT 1) WHERE minorHeadId IS NULL")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `subName` TEXT")
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `subFrequency` INTEGER")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `subscription_status` (`subName` TEXT NOT NULL, `isStopped` INTEGER NOT NULL, PRIMARY KEY(`subName`))")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `notes` ADD COLUMN `tags` TEXT") } catch (e: Exception) {}
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `budgets` ADD COLUMN `higherIsBetter` INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `baseCurrency` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`currencyCode`))")
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `amountOriginal` REAL") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `currencyCode` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `amountBase` REAL") } catch (e: Exception) {}
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `loanType` TEXT NOT NULL, `principalAmount` REAL NOT NULL, `interestRateAnnual` REAL NOT NULL, `frequency` TEXT NOT NULL, `installmentAmount` REAL NOT NULL, `paymentTiming` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `totalInterestPaid` REAL NOT NULL DEFAULT 0.0, `totalPrincipalRepaid` REAL NOT NULL DEFAULT 0.0, `outstandingBalance` REAL NOT NULL, `nextDueDate` INTEGER NOT NULL, `periodsTotal` INTEGER NOT NULL, `periodsPassed` INTEGER NOT NULL DEFAULT 0, `accountId` INTEGER NOT NULL, `partyId` INTEGER NOT NULL, `notes` TEXT, `isClosed` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `loan_repayments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `loanId` INTEGER NOT NULL, `amountPaid` REAL NOT NULL, `principalPortion` REAL NOT NULL, `interestPortion` REAL NOT NULL, `paymentDate` INTEGER NOT NULL, `transactionId` INTEGER, `isScheduled` INTEGER NOT NULL DEFAULT 1)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM categories WHERE id NOT IN (SELECT MIN(id) FROM categories GROUP BY name, type)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name_type` ON `categories` (`name`, `type`)")
                db.execSQL("DELETE FROM tags WHERE id NOT IN (SELECT MIN(id) FROM tags GROUP BY name)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `loans_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `loanType` TEXT NOT NULL, `principalAmount` REAL NOT NULL, `interestRateAnnual` REAL NOT NULL, `frequency` TEXT NOT NULL, `installmentAmount` REAL NOT NULL, `disbursementDate` INTEGER NOT NULL, `firstRepaymentDate` INTEGER NOT NULL, `totalInterestPaid` REAL NOT NULL DEFAULT 0.0, `totalPrincipalRepaid` REAL NOT NULL DEFAULT 0.0, `outstandingBalance` REAL NOT NULL, `nextDueDate` INTEGER NOT NULL, `periodsTotal` INTEGER NOT NULL, `periodsPassed` INTEGER NOT NULL DEFAULT 0, `accountId` INTEGER NOT NULL, `partyId` INTEGER NOT NULL, `notes` TEXT, `isClosed` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("INSERT INTO loans_new (id, name, loanType, principalAmount, interestRateAnnual, frequency, installmentAmount, disbursementDate, firstRepaymentDate, totalInterestPaid, totalPrincipalRepaid, outstandingBalance, nextDueDate, periodsTotal, periodsPassed, accountId, partyId, notes, isClosed) SELECT id, name, loanType, principalAmount, interestRateAnnual, frequency, installmentAmount, startDate, startDate, totalInterestPaid, totalPrincipalRepaid, outstandingBalance, nextDueDate, periodsTotal, periodsPassed, accountId, partyId, notes, isClosed FROM loans")
                db.execSQL("DROP TABLE loans")
                db.execSQL("ALTER TABLE loans_new RENAME TO loans")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE loans ADD COLUMN gapMethod TEXT NOT NULL DEFAULT 'DAYS'") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE loans ADD COLUMN gapInterest REAL NOT NULL DEFAULT 0.0") } catch (e: Exception) {}
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE loans ADD COLUMN isActualEmiDifferent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE loans ADD COLUMN actualRepaymentAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE loans ADD COLUMN isAutoRecordEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE loans ADD COLUMN sourceAccountId INTEGER")
                db.execSQL("ALTER TABLE loans ADD COLUMN tags TEXT")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `subscriptions_master` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `frequency` INTEGER NOT NULL, `note` TEXT, `isTransfer` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `templates` ADD COLUMN `subName` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `templates` ADD COLUMN `subFrequency` INTEGER") } catch (e: Exception) {}
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `editedAt` INTEGER") } catch (e: Exception) {}
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `budgets` ADD COLUMN `accountIds` TEXT") } catch (e: Exception) {}
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR IGNORE INTO major_heads (name, isEnabled) VALUES ('Others', 1)")
                db.execSQL("INSERT OR IGNORE INTO minor_heads (name, majorHeadId, isEnabled) SELECT 'Default', id, 1 FROM major_heads WHERE name = 'Others'")
                db.execSQL("UPDATE accounts SET minorHeadId = (SELECT mih.id FROM minor_heads mih JOIN major_heads mah ON mih.majorHeadId = mah.id WHERE mah.name = 'Others' AND mih.name = 'Default' LIMIT 1) WHERE minorHeadId IS NULL AND name != 'Suspense'")
                db.execSQL("INSERT OR IGNORE INTO accounts (name, type, openingBalance, isEnabled, minorHeadId) SELECT 'Others', 'asset', 0.0, 1, mih.id FROM minor_heads mih JOIN major_heads mah ON mih.majorHeadId = mah.id WHERE mah.name = 'Others' AND mih.name = 'Default' LIMIT 1")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `notes` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'text'") } catch (e: Exception) {}
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `notebooks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                try { db.execSQL("ALTER TABLE `notes` ADD COLUMN `notebookId` INTEGER") } catch (e: Exception) {}
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `notes` ADD COLUMN `editedAt` INTEGER") } catch (e: Exception) {}
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `msgFrom` TEXT, `textContaining` TEXT NOT NULL, `type` TEXT NOT NULL, `categoryId` INTEGER, `accountId` INTEGER, `toAccountId` INTEGER, `partyId` INTEGER, `toPartyId` INTEGER, `note` TEXT, `tags` TEXT, `isEnabled` INTEGER NOT NULL DEFAULT 1)")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `icon` TEXT")
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `icon` TEXT")
                db.execSQL("UPDATE accounts SET icon = '🏦' WHERE name LIKE '%Bank%'")
                db.execSQL("UPDATE accounts SET icon = '💵' WHERE name = 'Cash'")
                db.execSQL("UPDATE accounts SET icon = '📈' WHERE name = 'Investments'")
                db.execSQL("UPDATE accounts SET icon = '💳' WHERE name LIKE '%Card%'")
                db.execSQL("UPDATE categories SET icon = '🍔' WHERE name = 'Food'")
                db.execSQL("UPDATE categories SET icon = '🛒' WHERE name = 'Groceries'")
                db.execSQL("UPDATE categories SET icon = '🚗' WHERE name = 'Travel'")
                db.execSQL("UPDATE categories SET icon = '📱' WHERE name = 'Telephone'")
                db.execSQL("UPDATE categories SET icon = '🎬' WHERE name = 'Entertainment'")
                db.execSQL("UPDATE categories SET icon = '🎓' WHERE name = 'Education'")
                db.execSQL("UPDATE categories SET icon = '👔' WHERE name = 'Clothing'")
                db.execSQL("UPDATE categories SET icon = '🏠' WHERE name = 'Rent'")
                db.execSQL("UPDATE categories SET icon = '💡' WHERE name = 'Utilities'")
                db.execSQL("UPDATE categories SET icon = '💰' WHERE name = 'Salary'")
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `isEmergencyFund` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isNegotiated` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `negotiationAmountOriginal` REAL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `merchantName` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isDiscretionary` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `trackingType` TEXT NOT NULL DEFAULT 'Both'")
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `targetNumber` REAL")
            }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `subscription_status` ADD COLUMN `isAutoRecordEnabled` INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_major_heads_name` ON `major_heads` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `minor_heads_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `majorHeadId` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, FOREIGN KEY(`majorHeadId`) REFERENCES `major_heads`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO `minor_heads_new` (`id`, `name`, `majorHeadId`, `isEnabled`) SELECT `id`, `name`, `majorHeadId`, `isEnabled` FROM `minor_heads`")
                db.execSQL("DROP TABLE `minor_heads`")
                db.execSQL("ALTER TABLE `minor_heads_new` RENAME TO `minor_heads`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_minor_heads_name_majorHeadId` ON `minor_heads` (`name`, `majorHeadId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name_minorHeadId` ON `accounts` (`name`, `minorHeadId`)")
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `invoiceNumber` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `dueDays` INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `invoice_clearances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transferTransactionId` INTEGER NOT NULL, `invoiceTransactionId` INTEGER NOT NULL, `amountCleared` REAL NOT NULL, FOREIGN KEY(`transferTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`invoiceTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `defaultDueDays` INTEGER")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            val dbFile = context.getDatabasePath(DB_NAME)
            val encryptedFile = File(dbFile.path + ".xpt")
            if (encryptedFile.exists() && !dbFile.exists()) {
                Log.e("AppDatabase", "Blocked attempt to open plain DB while encrypted file exists.")
                throw IllegalStateException("Database is currently encrypted at rest.")
            }
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: let {
                    val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                        }
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            scope.launch(Dispatchers.IO) {
                                populateDatabaseIfEmpty(db)
                            }
                        }
                    })
                    .build()
                    INSTANCE = instance
                    instance
                }
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                try {
                    INSTANCE?.checkpoint()
                    INSTANCE?.close()
                } catch (e: Exception) {
                    Log.e("AppDatabase", "Error during close", e)
                }
                INSTANCE = null
            }
        }

        private fun populateDatabaseIfEmpty(db: SupportSQLiteDatabase) {
            val countCursor = db.query("SELECT COUNT(*) FROM major_heads")
            countCursor.moveToFirst()
            val count = countCursor.getInt(0)
            countCursor.close()
            
            if (count == 0) {
                Log.d("AppDatabase", "Database is empty. Populating with fresh default data.")
                db.beginTransaction()
                try {
                    val majorHeads = listOf("Investments", "Bank Accounts", "Digital Wallets", "Crypto", "Precious metals", "On Account (Loan)", "Cash", "credit cards", "Others")
                    majorHeads.forEach { db.execSQL("INSERT OR IGNORE INTO major_heads (name, isEnabled) VALUES ('$it', 1)") }

                    val expenseCategories = listOf("Apps" to "📱", "Clothing" to "👔", "Education" to "🎓", "Electronics" to "💻", "Entertainment" to "🎬", "Food" to "🍔", "Groceries" to "🛒", "Social" to "🥂", "Telephone" to "📞", "Travel" to "🚗", "Travel Distance" to "⛽", "Misc" to "📦", "Rent" to "🏠", "Utilities" to "💡")
                    val incomeCategories = listOf("Interest Income" to "📈", "Investment Income" to "💹", "Prof Fees" to "👨‍💼", "Lucky Reward" to "🍀", "Salary" to "💰")
                    expenseCategories.forEach { (name, icon) -> db.execSQL("INSERT OR IGNORE INTO categories (name, type, isEnabled, icon) VALUES ('$name', 'expense', 1, '$icon')") }
                    incomeCategories.forEach { (name, icon) -> db.execSQL("INSERT OR IGNORE INTO categories (name, type, isEnabled, icon) VALUES ('$name', 'income', 1, '$icon')") }

                    val minorMap = mapOf(
                        "Investments" to listOf("Equity", "mutual funds", "Fixed Deposit", "Recurring Deposit", "Retirement funds"),
                        "Bank Accounts" to listOf("Savings account", "Current Account", "Overdraft Account"),
                        "Digital Wallets" to listOf("PayPal", "Paytm", "UPI Lite", "Amazon"),
                        "Crypto" to listOf("XMR", "BTC", "USDT"),
                        "Precious metals" to listOf("Gold", "Silver", "Diamond"),
                        "On Account (Loan)" to listOf("Relative", "Friend", "My Ex", "Business party"),
                        "Cash" to listOf("Cash"),
                        "credit cards" to listOf("US Bank", "Wells Fargo", "JP Morgan", "HDFC", "ICICI", "Axis", "Kotak"),
                        "Others" to listOf("Default")
                    )
                    
                    minorMap.forEach { (majorName, minors) ->
                        minors.forEach { db.execSQL("INSERT OR IGNORE INTO minor_heads (name, majorHeadId, isEnabled) SELECT '$it', id, 1 FROM major_heads WHERE TRIM(name) = '$majorName'") }
                    }

                    db.execSQL("INSERT OR IGNORE INTO accounts (name, type, openingBalance, isEnabled, icon, minorHeadId) SELECT 'Savings Acct 1', 'asset', 0.0, 1, '🏦', id FROM minor_heads WHERE TRIM(name) = 'Savings account'")
                    db.execSQL("INSERT OR IGNORE INTO accounts (name, type, openingBalance, isEnabled, icon, minorHeadId) SELECT 'Cash', 'asset', 0.0, 1, '💵', id FROM minor_heads WHERE TRIM(name) = 'Cash'")
                    db.execSQL("INSERT OR IGNORE INTO accounts (name, type, openingBalance, isEnabled, icon, minorHeadId) SELECT 'Others', 'asset', 0.0, 1, '📁', id FROM minor_heads WHERE TRIM(name) = 'Default'")

                    val defaultTags = listOf("Personal", "Work", "Urgent", "Subscription")
                    defaultTags.forEach { db.execSQL("INSERT OR IGNORE INTO tags (name, isEnabled) VALUES ('$it', 1)") }
                    
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }
}
