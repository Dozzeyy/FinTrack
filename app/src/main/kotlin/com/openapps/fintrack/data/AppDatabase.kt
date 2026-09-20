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
import java.time.LocalDate

@Database(entities = [Account::class, Category::class, Tag::class, Budget::class, TransactionLegacy::class, TemplateLegacy::class, Party::class, MajorHead::class, MinorHead::class, SubscriptionStatus::class, Note::class, ExchangeRate::class, Loan::class, LoanRepayment::class, Subscription::class, Notebook::class, Rule::class, InvoiceClearance::class, TransactionHeader::class, TransactionLine::class, TransactionTag::class, BudgetCategory::class, BudgetAccount::class, TemplateHeader::class, TemplateLine::class, TemplateTag::class, SmsLog::class, SmsTransactionDraft::class, Goal::class, GoalAccountAllocation::class, GoalRule::class, GoalTransaction::class, GoalAllocationHistory::class, FdClearance::class], version = 58, exportSchema = false)
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

    fun prepareForBackup() {
        try {
            val db = this.openHelper.writableDatabase
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            db.query("PRAGMA journal_mode=DELETE").close()
            Log.d("AppDatabase", "Prepare For Backup Success: Journal mode set to DELETE")
        } catch (e: Exception) {
            Log.e("AppDatabase", "Prepare for backup failed", e)
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
                db.execSQL("INSERT INTO budgets_new (id, categoryIds, amount, duration, note) SELECT id, categoryIds, amount, duration, note FROM budgets")
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
                    9 to listOf("Bank Loan"),
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

        val MIGRATION_14_15_FTD = object : Migration(14, 15) {
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

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `notes` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `notes` ADD COLUMN `color` INTEGER") } catch (e: Exception) {}
            }
        }

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // accounts
                db.execSQL("ALTER TABLE accounts ADD COLUMN openingBalanceMinorUnits INTEGER")
                db.execSQL("ALTER TABLE accounts ADD COLUMN creditLimitMinorUnits INTEGER")
                db.execSQL("UPDATE accounts SET openingBalanceMinorUnits = CAST(ROUND(openingBalance * 100) AS INTEGER)")
                db.execSQL("UPDATE accounts SET creditLimitMinorUnits = CAST(ROUND(creditLimit * 100) AS INTEGER) WHERE creditLimit IS NOT NULL")

                // tags
                db.execSQL("ALTER TABLE tags ADD COLUMN targetNumberMinorUnits INTEGER")
                db.execSQL("UPDATE tags SET targetNumberMinorUnits = CAST(ROUND(targetNumber * 100) AS INTEGER) WHERE targetNumber IS NOT NULL")

                // parties
                db.execSQL("ALTER TABLE parties ADD COLUMN openingBalanceMinorUnits INTEGER")
                db.execSQL("UPDATE parties SET openingBalanceMinorUnits = CAST(ROUND(openingBalance * 100) AS INTEGER)")

                // budgets
                db.execSQL("ALTER TABLE budgets ADD COLUMN amountMinorUnits INTEGER")
                db.execSQL("UPDATE budgets SET amountMinorUnits = CAST(ROUND(amount * 100) AS INTEGER)")

                // templates
                db.execSQL("ALTER TABLE templates ADD COLUMN amountMinorUnits INTEGER")
                db.execSQL("UPDATE templates SET amountMinorUnits = CAST(ROUND(amount * 100) AS INTEGER) WHERE amount IS NOT NULL")

                // transactions
                db.execSQL("ALTER TABLE transactions ADD COLUMN amountMinorUnits INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN amountOriginalMinorUnits INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN amountBaseMinorUnits INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN negotiationAmountOriginalMinorUnits INTEGER")
                db.execSQL("UPDATE transactions SET amountMinorUnits = CAST(ROUND(amount * 100) AS INTEGER)")
                db.execSQL("UPDATE transactions SET amountOriginalMinorUnits = CAST(ROUND(amountOriginal * 100) AS INTEGER) WHERE amountOriginal IS NOT NULL")
                db.execSQL("UPDATE transactions SET amountBaseMinorUnits = CAST(ROUND(amountBase * 100) AS INTEGER) WHERE amountBase IS NOT NULL")
                db.execSQL("UPDATE transactions SET negotiationAmountOriginalMinorUnits = CAST(ROUND(negotiationAmountOriginal * 100) AS INTEGER) WHERE negotiationAmountOriginal IS NOT NULL")

                // invoice_clearances
                db.execSQL("ALTER TABLE invoice_clearances ADD COLUMN amountClearedMinorUnits INTEGER")
                db.execSQL("UPDATE invoice_clearances SET amountClearedMinorUnits = CAST(ROUND(amountCleared * 100) AS INTEGER)")

                // loans
                db.execSQL("ALTER TABLE loans ADD COLUMN principalAmountMinorUnits INTEGER")
                db.execSQL("ALTER TABLE loans ADD COLUMN installmentAmountMinorUnits INTEGER")
                db.execSQL("ALTER TABLE loans ADD COLUMN totalInterestPaidMinorUnits INTEGER")
                db.execSQL("ALTER TABLE loans ADD COLUMN totalPrincipalRepaidMinorUnits INTEGER")
                db.execSQL("ALTER TABLE loans ADD COLUMN outstandingBalanceMinorUnits INTEGER")
                db.execSQL("ALTER TABLE loans ADD COLUMN gapInterestMinorUnits INTEGER")
                db.execSQL("ALTER TABLE loans ADD COLUMN actualRepaymentAmountMinorUnits INTEGER")
                db.execSQL("UPDATE loans SET principalAmountMinorUnits = CAST(ROUND(principalAmount * 100) AS INTEGER)")
                db.execSQL("UPDATE loans SET installmentAmountMinorUnits = CAST(ROUND(installmentAmount * 100) AS INTEGER)")
                db.execSQL("UPDATE loans SET totalInterestPaidMinorUnits = CAST(ROUND(totalInterestPaid * 100) AS INTEGER)")
                db.execSQL("UPDATE loans SET totalPrincipalRepaidMinorUnits = CAST(ROUND(totalPrincipalRepaid * 100) AS INTEGER)")
                db.execSQL("UPDATE loans SET outstandingBalanceMinorUnits = CAST(ROUND(outstandingBalance * 100) AS INTEGER)")
                db.execSQL("UPDATE loans SET gapInterestMinorUnits = CAST(ROUND(gapInterest * 100) AS INTEGER)")
                db.execSQL("UPDATE loans SET actualRepaymentAmountMinorUnits = CAST(ROUND(actualRepaymentAmount * 100) AS INTEGER)")

                // loan_repayments
                db.execSQL("ALTER TABLE loan_repayments ADD COLUMN amountPaidMinorUnits INTEGER")
                db.execSQL("ALTER TABLE loan_repayments ADD COLUMN principalPortionMinorUnits INTEGER")
                db.execSQL("ALTER TABLE loan_repayments ADD COLUMN interestPortionMinorUnits INTEGER")
                db.execSQL("UPDATE loan_repayments SET amountPaidMinorUnits = CAST(ROUND(amountPaid * 100) AS INTEGER)")
                db.execSQL("UPDATE loan_repayments SET principalPortionMinorUnits = CAST(ROUND(principalPortion * 100) AS INTEGER)")
                db.execSQL("UPDATE loan_repayments SET interestPortionMinorUnits = CAST(ROUND(interestPortion * 100) AS INTEGER)")
            }
        }

        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_headers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionNumber` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `note` TEXT, `partyId` INTEGER, `toPartyId` INTEGER, `subName` TEXT, `subFrequency` INTEGER, `merchantName` TEXT, `invoiceNumber` TEXT, `dueDays` INTEGER, `editedAt` INTEGER)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_headers_transactionNumber` ON `transaction_headers` (`transactionNumber`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_lines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `headerId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `toAccountId` INTEGER, `categoryId` INTEGER, `amount` REAL NOT NULL, `amountMinorUnits` INTEGER, `amountOriginal` REAL, `amountOriginalMinorUnits` INTEGER, `currencyCode` TEXT, `amountBase` REAL, `amountBaseMinorUnits` INTEGER, `isNegotiated` INTEGER NOT NULL, `negotiationAmountOriginal` REAL, `negotiationAmountOriginalMinorUnits` INTEGER, `isDiscretionary` INTEGER NOT NULL, `note` TEXT, FOREIGN KEY(`headerId`) REFERENCES `transaction_headers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")

                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_tags` (`headerId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`headerId`, `tagId`), FOREIGN KEY(`headerId`) REFERENCES `transaction_headers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                db.execSQL("CREATE TABLE IF NOT EXISTS `budget_categories` (`budgetId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`budgetId`, `categoryId`), FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                db.execSQL("CREATE TABLE IF NOT EXISTS `budget_accounts` (`budgetId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, PRIMARY KEY(`budgetId`, `accountId`), FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                db.execSQL("CREATE TABLE IF NOT EXISTS `template_headers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `note` TEXT, `subName` TEXT, `subFrequency` INTEGER)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `template_lines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `headerId` INTEGER NOT NULL, `accountId` INTEGER, `toAccountId` INTEGER, `categoryId` INTEGER, `amount` REAL, `amountMinorUnits` INTEGER, `note` TEXT, FOREIGN KEY(`headerId`) REFERENCES `template_headers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                db.execSQL("CREATE TABLE IF NOT EXISTS `template_tags` (`headerId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`headerId`, `tagId`), FOREIGN KEY(`headerId`) REFERENCES `template_headers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")


                val txnCursor = db.query("SELECT * FROM transactions")
                val txnGroups = mutableMapOf<String, MutableList<Map<String, Any?>>>()
                
                if (txnCursor.moveToFirst()) {
                    val columnNames = txnCursor.columnNames
                    do {
                        val row = mutableMapOf<String, Any?>()
                        for (i in columnNames.indices) {
                            row[columnNames[i]] = when (txnCursor.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_INTEGER -> txnCursor.getLong(i)
                                android.database.Cursor.FIELD_TYPE_FLOAT -> txnCursor.getDouble(i)
                                android.database.Cursor.FIELD_TYPE_STRING -> txnCursor.getString(i)
                                else -> null
                            }
                        }
                        val txnNum = row["transactionNumber"] as? String ?: "LEGACY_${row["id"]}"
                        txnGroups.getOrPut(txnNum) { mutableListOf() }.add(row)
                    } while (txnCursor.moveToNext())
                }
                txnCursor.close()

                txnGroups.forEach { (txnNum, rows) ->
                    val first = rows[0]
                    db.execSQL("INSERT INTO transaction_headers (transactionNumber, date, time, note, partyId, toPartyId, subName, subFrequency, merchantName, invoiceNumber, dueDays, editedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(txnNum, first["date"], first["time"], first["note"], first["partyId"], first["toPartyId"], first["subName"], first["subFrequency"], first["merchantName"], first["invoiceNumber"], first["dueDays"], first["editedAt"]))
                    
                    val headerIdCursor = db.query("SELECT last_insert_rowid()")
                    headerIdCursor.moveToFirst()
                    val headerId = headerIdCursor.getLong(0)
                    headerIdCursor.close()

                    rows.forEach { row ->
                        db.execSQL("INSERT INTO transaction_lines (headerId, accountId, toAccountId, categoryId, amount, amountMinorUnits, amountOriginal, amountOriginalMinorUnits, currencyCode, amountBase, amountBaseMinorUnits, isNegotiated, negotiationAmountOriginal, negotiationAmountOriginalMinorUnits, isDiscretionary, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(headerId, row["accountId"], row["toAccountId"], row["categoryId"], row["amount"], row["amountMinorUnits"], row["amountOriginal"], row["amountOriginalMinorUnits"], row["currencyCode"], row["amountBase"], row["amountBaseMinorUnits"], row["isNegotiated"], row["negotiationAmountOriginal"], row["negotiationAmountOriginalMinorUnits"], row["isDiscretionary"], row["note"]))
                    }

                    val tagsStr = first["tags"] as? String
                    if (!tagsStr.isNullOrBlank()) {
                        tagsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { tagId ->
                            db.execSQL("INSERT OR IGNORE INTO transaction_tags (headerId, tagId) VALUES (?, ?)", arrayOf<Any?>(headerId, tagId))
                        }
                    }
                }

                val budgetCursor = db.query("SELECT * FROM budgets")
                if (budgetCursor.moveToFirst()) {
                    do {
                        val budgetId = budgetCursor.getLong(budgetCursor.getColumnIndexOrThrow("id"))
                        val catIdsStr = budgetCursor.getString(budgetCursor.getColumnIndexOrThrow("categoryIds"))
                        val accIdsStr = budgetCursor.getString(budgetCursor.getColumnIndexOrThrow("accountIds"))

                        if (!catIdsStr.isNullOrBlank()) {
                            catIdsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { catId ->
                                db.execSQL("INSERT OR IGNORE INTO budget_categories (budgetId, categoryId) VALUES (?, ?)", arrayOf<Any?>(budgetId, catId))
                            }
                        }
                        if (!accIdsStr.isNullOrBlank()) {
                            accIdsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { accId ->
                                db.execSQL("INSERT OR IGNORE INTO budget_accounts (budgetId, accountId) VALUES (?, ?)", arrayOf<Any?>(budgetId, accId))
                            }
                        }
                    } while (budgetCursor.moveToNext())
                }
                budgetCursor.close()

                val templateCursor = db.query("SELECT * FROM templates")
                if (templateCursor.moveToFirst()) {
                    val columnNames = templateCursor.columnNames
                    do {
                        val row = mutableMapOf<String, Any?>()
                        for (i in columnNames.indices) {
                            row[columnNames[i]] = when (templateCursor.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_INTEGER -> templateCursor.getLong(i)
                                android.database.Cursor.FIELD_TYPE_FLOAT -> templateCursor.getDouble(i)
                                android.database.Cursor.FIELD_TYPE_STRING -> templateCursor.getString(i)
                                else -> null
                            }
                        }

                        db.execSQL("INSERT INTO template_headers (name, type, note, subName, subFrequency) VALUES (?, ?, ?, ?, ?)",
                            arrayOf<Any?>(row["name"], row["type"], row["note"], row["subName"], row["subFrequency"]))
                        
                        val tHeaderIdCursor = db.query("SELECT last_insert_rowid()")
                        tHeaderIdCursor.moveToFirst()
                        val tHeaderId = tHeaderIdCursor.getLong(0)
                        tHeaderIdCursor.close()

                        val multiEntries = row["multiEntries"] as? String
                        if (!multiEntries.isNullOrBlank()) {
                            multiEntries.split("|").forEach { entry ->
                                val parts = entry.split(":")
                                if (parts.size >= 2) {
                                    val catId = parts[0].toIntOrNull()
                                    val amt = parts[1].toDoubleOrNull() ?: 0.0
                                    val note = if (parts.size > 2) parts[2] else null
                                    db.execSQL("INSERT INTO template_lines (headerId, categoryId, amount, amountMinorUnits, note) VALUES (?, ?, ?, ?, ?)",
                                        arrayOf<Any?>(tHeaderId, catId, amt, (amt * 100).toLong(), note))
                                }
                            }
                        } else {
                            db.execSQL("INSERT INTO template_lines (headerId, accountId, toAccountId, categoryId, amount, amountMinorUnits, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
                                arrayOf<Any?>(tHeaderId, row["accountId"], row["toAccountId"], row["categoryId"], row["amount"], row["amountMinorUnits"], row["note"]))
                        }

                        val tagsStr = row["tags"] as? String
                        if (!tagsStr.isNullOrBlank()) {
                            tagsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { tagId ->
                                db.execSQL("INSERT OR IGNORE INTO template_tags (headerId, tagId) VALUES (?, ?)", arrayOf<Any?>(tHeaderId, tagId))
                            }
                        }
                    } while (templateCursor.moveToNext())
                }
                templateCursor.close()
            }
        }

        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sms_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sender` TEXT NOT NULL, `bodyHash` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_logs_bodyHash` ON `sms_logs` (`bodyHash`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sms_drafts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `amount` REAL NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `sender` TEXT NOT NULL, `body` TEXT NOT NULL, `merchantName` TEXT, `accountLastFour` TEXT, `type` TEXT NOT NULL)")
            }
        }

        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transaction_lines` ADD COLUMN `isReconciled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isReconciled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `budgets` ADD COLUMN `rolloverEnabled` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                
                db.execSQL("CREATE TABLE IF NOT EXISTS `budgets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `amount` REAL NOT NULL, `amountMinorUnits` INTEGER, `duration` TEXT NOT NULL, `note` TEXT, `higherIsBetter` INTEGER NOT NULL, `rolloverEnabled` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `budgets_new` (id, name, amount, amountMinorUnits, duration, note, higherIsBetter, rolloverEnabled, editedAt, isDeleted) SELECT id, name, amount, amountMinorUnits, duration, note, higherIsBetter, rolloverEnabled, $now, 0 FROM budgets")
                db.execSQL("DROP TABLE budgets")
                db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")

                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_headers_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionNumber` TEXT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, `note` TEXT, `partyId` INTEGER, `toPartyId` INTEGER, `subName` TEXT, `subFrequency` INTEGER, `merchantName` TEXT, `invoiceNumber` TEXT, `dueDays` INTEGER, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_headers_transactionNumber` ON `transaction_headers_new` (`transactionNumber`)")
                db.execSQL("INSERT INTO `transaction_headers_new` (id, transactionNumber, date, time, note, partyId, toPartyId, subName, subFrequency, merchantName, invoiceNumber, dueDays, editedAt, isDeleted) SELECT id, transactionNumber, date, time, note, partyId, toPartyId, subName, subFrequency, merchantName, invoiceNumber, dueDays, COALESCE(editedAt, $now), 0 FROM transaction_headers")
                db.execSQL("DROP TABLE transaction_headers")
                db.execSQL("ALTER TABLE transaction_headers_new RENAME TO transaction_headers")

                db.execSQL("CREATE TABLE IF NOT EXISTS `accounts_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `openingBalance` REAL NOT NULL, `openingBalanceMinorUnits` INTEGER, `description` TEXT, `isEnabled` INTEGER NOT NULL, `minorHeadId` INTEGER, `creditLimit` REAL, `creditLimitMinorUnits` INTEGER, `billingCycleStart` TEXT, `billingCycleEnd` TEXT, `paymentDueDate` TEXT, `icon` TEXT, `isEmergencyFund` INTEGER NOT NULL, `defaultDueDays` INTEGER, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name_minorHeadId` ON `accounts_new` (`name`, `minorHeadId`)")
                db.execSQL("INSERT INTO `accounts_new` (id, name, type, openingBalance, openingBalanceMinorUnits, description, isEnabled, minorHeadId, creditLimit, creditLimitMinorUnits, billingCycleStart, billingCycleEnd, paymentDueDate, icon, isEmergencyFund, defaultDueDays, editedAt, isDeleted) SELECT id, name, type, openingBalance, openingBalanceMinorUnits, description, isEnabled, minorHeadId, creditLimit, creditLimitMinorUnits, billingCycleStart, billingCycleEnd, paymentDueDate, icon, isEmergencyFund, defaultDueDays, $now, 0 FROM accounts")
                db.execSQL("DROP TABLE accounts")
                db.execSQL("ALTER TABLE accounts_new RENAME TO accounts")

                db.execSQL("CREATE TABLE IF NOT EXISTS `categories_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `description` TEXT, `isEnabled` INTEGER NOT NULL, `icon` TEXT, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name_type` ON `categories_new` (`name`, `type`)")
                db.execSQL("INSERT INTO `categories_new` (id, name, type, description, isEnabled, icon, editedAt, isDeleted) SELECT id, name, type, description, isEnabled, icon, $now, 0 FROM categories")
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")

                db.execSQL("CREATE TABLE IF NOT EXISTS `tags_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `trackingType` TEXT NOT NULL, `targetNumber` REAL, `targetNumberMinorUnits` INTEGER, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags_new` (`name`)")
                db.execSQL("INSERT INTO `tags_new` (id, name, isEnabled, trackingType, targetNumber, targetNumberMinorUnits, editedAt, isDeleted) SELECT id, name, isEnabled, trackingType, targetNumber, targetNumberMinorUnits, $now, 0 FROM tags")
                db.execSQL("DROP TABLE tags")
                db.execSQL("ALTER TABLE tags_new RENAME TO tags")

                db.execSQL("CREATE TABLE IF NOT EXISTS `major_heads_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_major_heads_name` ON `major_heads_new` (`name`)")
                db.execSQL("INSERT INTO `major_heads_new` (id, name, isEnabled, editedAt, isDeleted) SELECT id, name, isEnabled, $now, 0 FROM major_heads")
                db.execSQL("DROP TABLE major_heads")
                db.execSQL("ALTER TABLE major_heads_new RENAME TO major_heads")

                db.execSQL("CREATE TABLE IF NOT EXISTS `minor_heads_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `majorHeadId` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, FOREIGN KEY(`majorHeadId`) REFERENCES `major_heads`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_minor_heads_name_majorHeadId` ON `minor_heads_new` (`name`, `majorHeadId`)")
                db.execSQL("INSERT INTO `minor_heads_new` (id, name, majorHeadId, isEnabled, editedAt, isDeleted) SELECT id, name, majorHeadId, isEnabled, $now, 0 FROM minor_heads")
                db.execSQL("DROP TABLE minor_heads")
                db.execSQL("ALTER TABLE minor_heads_new RENAME TO minor_heads")

                db.execSQL("CREATE TABLE IF NOT EXISTS `parties_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `openingBalance` REAL NOT NULL, `openingBalanceMinorUnits` INTEGER, `isEnabled` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `parties_new` (id, name, openingBalance, openingBalanceMinorUnits, isEnabled, editedAt, isDeleted) SELECT id, name, openingBalance, openingBalanceMinorUnits, isEnabled, $now, 0 FROM parties")
                db.execSQL("DROP TABLE parties")
                db.execSQL("ALTER TABLE parties_new RENAME TO parties")

                db.execSQL("CREATE TABLE IF NOT EXISTS `template_headers_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `note` TEXT, `subName` TEXT, `subFrequency` INTEGER, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `template_headers_new` (id, name, type, note, subName, subFrequency, editedAt, isDeleted) SELECT id, name, type, note, subName, subFrequency, $now, 0 FROM template_headers")
                db.execSQL("DROP TABLE template_headers")
                db.execSQL("ALTER TABLE template_headers_new RENAME TO template_headers")

                db.execSQL("CREATE TABLE IF NOT EXISTS `subscriptions_master_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `frequency` INTEGER NOT NULL, `note` TEXT, `isTransfer` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `subscriptions_master_new` (id, name, frequency, note, isTransfer, isEnabled, editedAt, isDeleted) SELECT id, name, frequency, note, isTransfer, isEnabled, $now, 0 FROM subscriptions_master")
                db.execSQL("DROP TABLE subscriptions_master")
                db.execSQL("ALTER TABLE subscriptions_master_new RENAME TO subscriptions_master")

                db.execSQL("CREATE TABLE IF NOT EXISTS `rules_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `msgFrom` TEXT, `textContaining` TEXT NOT NULL, `type` TEXT NOT NULL, `categoryId` INTEGER, `accountId` INTEGER, `toAccountId` INTEGER, `partyId` INTEGER, `toPartyId` INTEGER, `note` TEXT, `tags` TEXT, `isEnabled` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `rules_new` (id, name, msgFrom, textContaining, type, categoryId, accountId, toAccountId, partyId, toPartyId, note, tags, isEnabled, editedAt, isDeleted) SELECT id, name, msgFrom, textContaining, type, categoryId, accountId, toAccountId, partyId, toPartyId, note, tags, isEnabled, $now, 0 FROM rules")
                db.execSQL("DROP TABLE rules")
                db.execSQL("ALTER TABLE rules_new RENAME TO rules")

                db.execSQL("CREATE TABLE IF NOT EXISTS `notes_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `type` TEXT NOT NULL, `notebookId` INTEGER, `createdAt` INTEGER NOT NULL, `tags` TEXT, `editedAt` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `color` INTEGER, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `notes_new` (id, title, content, type, notebookId, createdAt, tags, editedAt, isPinned, color, isDeleted) SELECT id, title, content, type, notebookId, createdAt, tags, COALESCE(editedAt, $now), isPinned, color, 0 FROM notes")
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")

                val tablesToAlter = listOf("subscription_status", "notebooks", "notes", "loans", "loan_repayments", "subscriptions_master", "rules")
                tablesToAlter.forEach { table ->
                    try {
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN `editedAt` INTEGER NOT NULL DEFAULT $now")
                        db.execSQL("ALTER TABLE `$table` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                    } catch (e: Exception) {}
                }
            }
        }

        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                
                fun tableHasColumn(table: String, column: String): Boolean {
                    val cursor = db.query("SELECT * FROM `$table` LIMIT 0")
                    val has = cursor.columnNames.contains(column)
                    cursor.close()
                    return has
                }

                val hasSyncTxn = tableHasColumn("transactions", "isDeleted")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL, 
                        `accountId` INTEGER NOT NULL, `toAccountId` INTEGER, `categoryId` INTEGER, 
                        `amount` REAL NOT NULL, `amountMinorUnits` INTEGER, `note` TEXT, `tags` TEXT, 
                        `transactionNumber` TEXT, `partyId` INTEGER, `toPartyId` INTEGER, `subName` TEXT, 
                        `subFrequency` INTEGER, `amountOriginal` REAL, `amountOriginalMinorUnits` INTEGER, 
                        `currencyCode` TEXT, `amountBase` REAL, `amountBaseMinorUnits` INTEGER, 
                        `editedAt` INTEGER NOT NULL, `isNegotiated` INTEGER NOT NULL, 
                        `negotiationAmountOriginal` REAL, `negotiationAmountOriginalMinorUnits` INTEGER, 
                        `merchantName` TEXT, `isDiscretionary` INTEGER NOT NULL, 
                        `invoiceNumber` TEXT, `dueDays` INTEGER, `isReconciled` INTEGER NOT NULL, 
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , 
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL 
                    )
                """.trimIndent())
                
                val txnEditedAt = if (hasSyncTxn) "COALESCE(editedAt, $now)" else "$now"
                db.execSQL("""
                    INSERT INTO `transactions_new` (id, date, time, accountId, toAccountId, categoryId, amount, amountMinorUnits, note, tags, transactionNumber, partyId, toPartyId, subName, subFrequency, amountOriginal, amountOriginalMinorUnits, currencyCode, amountBase, amountBaseMinorUnits, editedAt, isNegotiated, negotiationAmountOriginal, negotiationAmountOriginalMinorUnits, merchantName, isDiscretionary, invoiceNumber, dueDays, isReconciled)
                    SELECT id, date, time, accountId, toAccountId, categoryId, amount, amountMinorUnits, note, tags, transactionNumber, partyId, toPartyId, subName, subFrequency, amountOriginal, amountOriginalMinorUnits, currencyCode, amountBase, amountBaseMinorUnits, $txnEditedAt, isNegotiated, negotiationAmountOriginal, negotiationAmountOriginalMinorUnits, merchantName, isDiscretionary, invoiceNumber, dueDays, isReconciled FROM transactions
                """.trimIndent())
                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")

                val hasSyncNb = tableHasColumn("notebooks", "isDeleted")
                db.execSQL("CREATE TABLE IF NOT EXISTS `notebooks_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                val nbEditedAt = if (hasSyncNb) "COALESCE(editedAt, $now)" else "$now"
                val nbIsDeleted = if (hasSyncNb) "isDeleted" else "0"
                db.execSQL("INSERT INTO `notebooks_new` (id, name, createdAt, editedAt, isDeleted) SELECT id, name, createdAt, $nbEditedAt, $nbIsDeleted FROM notebooks")
                db.execSQL("DROP TABLE notebooks")
                db.execSQL("ALTER TABLE notebooks_new RENAME TO notebooks")

                val hasSyncSs = tableHasColumn("subscription_status", "isDeleted")
                db.execSQL("CREATE TABLE IF NOT EXISTS `subscription_status_new` (`subName` TEXT NOT NULL, `isStopped` INTEGER NOT NULL, `isAutoRecordEnabled` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, PRIMARY KEY(`subName`))")
                val ssEditedAt = if (hasSyncSs) "COALESCE(editedAt, $now)" else "$now"
                val ssIsDeleted = if (hasSyncSs) "isDeleted" else "0"
                db.execSQL("INSERT INTO `subscription_status_new` (subName, isStopped, isAutoRecordEnabled, editedAt, isDeleted) SELECT subName, isStopped, isAutoRecordEnabled, $ssEditedAt, $ssIsDeleted FROM subscription_status")
                db.execSQL("DROP TABLE subscription_status")
                db.execSQL("ALTER TABLE subscription_status_new RENAME TO subscription_status")
                
                val hasSyncLoans = tableHasColumn("loans", "isDeleted")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `loans_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `loanType` TEXT NOT NULL, 
                        `principalAmount` REAL NOT NULL, `principalAmountMinorUnits` INTEGER, `interestRateAnnual` REAL NOT NULL, 
                        `frequency` TEXT NOT NULL, `installmentAmount` REAL NOT NULL, `installmentAmountMinorUnits` INTEGER, 
                        `disbursementDate` INTEGER NOT NULL, `firstRepaymentDate` INTEGER NOT NULL, `totalInterestPaid` REAL NOT NULL, 
                        `totalInterestPaidMinorUnits` INTEGER, `totalPrincipalRepaid` REAL NOT NULL, `totalPrincipalRepaidMinorUnits` INTEGER, 
                        `outstandingBalance` REAL NOT NULL, `outstandingBalanceMinorUnits` INTEGER, `nextDueDate` INTEGER NOT NULL, 
                        `periodsTotal` INTEGER NOT NULL, `periodsPassed` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, 
                        `partyId` INTEGER NOT NULL, `gapMethod` TEXT NOT NULL, `gapInterest` REAL NOT NULL, 
                        `gapInterestMinorUnits` INTEGER, `isActualEmiDifferent` INTEGER NOT NULL, `actualRepaymentAmount` REAL NOT NULL, 
                        `actualRepaymentAmountMinorUnits` INTEGER, `isAutoRecordEnabled` INTEGER NOT NULL, `sourceAccountId` INTEGER, 
                        `tags` TEXT, `notes` TEXT, `isClosed` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL
                    )
                """.trimIndent())
                val loanEditedAt = if (hasSyncLoans) "COALESCE(editedAt, $now)" else "$now"
                val loanIsDeleted = if (hasSyncLoans) "isDeleted" else "0"
                db.execSQL("""
                    INSERT INTO `loans_new` (id, name, loanType, principalAmount, principalAmountMinorUnits, interestRateAnnual, frequency, installmentAmount, installmentAmountMinorUnits, disbursementDate, firstRepaymentDate, totalInterestPaid, totalInterestPaidMinorUnits, totalPrincipalRepaid, totalPrincipalRepaidMinorUnits, outstandingBalance, outstandingBalanceMinorUnits, nextDueDate, periodsTotal, periodsPassed, accountId, partyId, gapMethod, gapInterest, gapInterestMinorUnits, isActualEmiDifferent, actualRepaymentAmount, actualRepaymentAmountMinorUnits, isAutoRecordEnabled, sourceAccountId, tags, notes, isClosed, editedAt, isDeleted)
                    SELECT id, name, loanType, principalAmount, principalAmountMinorUnits, interestRateAnnual, frequency, installmentAmount, installmentAmountMinorUnits, disbursementDate, firstRepaymentDate, totalInterestPaid, totalInterestPaidMinorUnits, totalPrincipalRepaid, totalPrincipalRepaidMinorUnits, outstandingBalance, outstandingBalanceMinorUnits, nextDueDate, periodsTotal, periodsPassed, accountId, partyId, gapMethod, gapInterest, gapInterestMinorUnits, isActualEmiDifferent, actualRepaymentAmount, actualRepaymentAmountMinorUnits, isAutoRecordEnabled, sourceAccountId, tags, notes, isClosed, $loanEditedAt, $loanIsDeleted FROM loans
                """.trimIndent())
                db.execSQL("DROP TABLE loans")
                db.execSQL("ALTER TABLE loans_new RENAME TO loans")

                val hasSyncLr = tableHasColumn("loan_repayments", "isDeleted")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `loan_repayments_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `loanId` INTEGER NOT NULL, `amountPaid` REAL NOT NULL, 
                        `amountPaidMinorUnits` INTEGER, `principalPortion` REAL NOT NULL, `principalPortionMinorUnits` INTEGER, 
                        `interestPortion` REAL NOT NULL, `interestPortionMinorUnits` INTEGER, `paymentDate` INTEGER NOT NULL, 
                        `transactionId` INTEGER, `isScheduled` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL
                    )
                """.trimIndent())
                val lrEditedAt = if (hasSyncLr) "COALESCE(editedAt, $now)" else "$now"
                val lrIsDeleted = if (hasSyncLr) "isDeleted" else "0"
                db.execSQL("""
                    INSERT INTO `loan_repayments_new` (id, loanId, amountPaid, amountPaidMinorUnits, principalPortion, principalPortionMinorUnits, interestPortion, interestPortionMinorUnits, paymentDate, transactionId, isScheduled, editedAt, isDeleted)
                    SELECT id, loanId, amountPaid, amountPaidMinorUnits, principalPortion, principalPortionMinorUnits, interestPortion, interestPortionMinorUnits, paymentDate, transactionId, isScheduled, $lrEditedAt, $lrIsDeleted FROM loan_repayments
                """.trimIndent())
                db.execSQL("DROP TABLE loan_repayments")
                db.execSQL("ALTER TABLE loan_repayments_new RENAME TO loan_repayments")

                val hasSyncNotes = tableHasColumn("notes", "isDeleted")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notes_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `notebookId` INTEGER, 
                        `createdAt` INTEGER NOT NULL, 
                        `tags` TEXT, 
                        `editedAt` INTEGER NOT NULL, 
                        `isPinned` INTEGER NOT NULL, 
                        `color` INTEGER, 
                        `isDeleted` INTEGER NOT NULL
                    )
                """.trimIndent())
                val notesEditedAt = if (hasSyncNotes) "COALESCE(editedAt, $now)" else if (tableHasColumn("notes", "editedAt")) "COALESCE(editedAt, $now)" else "$now"
                val notesIsDeleted = if (hasSyncNotes) "isDeleted" else "0"
                db.execSQL("""
                    INSERT INTO `notes_new` (id, title, content, type, notebookId, createdAt, tags, editedAt, isPinned, color, isDeleted)
                    SELECT id, title, content, type, notebookId, createdAt, tags, $notesEditedAt, isPinned, color, $notesIsDeleted FROM notes
                """.trimIndent())
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")

                val hasSyncRules = tableHasColumn("rules", "isDeleted")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rules_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `msgFrom` TEXT, 
                        `textContaining` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `categoryId` INTEGER, 
                        `accountId` INTEGER, 
                        `toAccountId` INTEGER, 
                        `partyId` INTEGER, 
                        `toPartyId` INTEGER, 
                        `note` TEXT, 
                        `tags` TEXT, 
                        `isEnabled` INTEGER NOT NULL, 
                        `editedAt` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL
                    )
                """.trimIndent())
                val rulesEditedAt = if (hasSyncRules) "COALESCE(editedAt, $now)" else "$now"
                val rulesIsDeleted = if (hasSyncRules) "isDeleted" else "0"
                db.execSQL("""
                    INSERT INTO `rules_new` (id, name, msgFrom, textContaining, type, categoryId, accountId, toAccountId, partyId, toPartyId, note, tags, isEnabled, editedAt, isDeleted)
                    SELECT id, name, msgFrom, textContaining, type, categoryId, accountId, toAccountId, partyId, toPartyId, note, tags, isEnabled, $rulesEditedAt, $rulesIsDeleted FROM rules
                """.trimIndent())
                db.execSQL("DROP TABLE rules")
                db.execSQL("ALTER TABLE rules_new RENAME TO rules")

                val hasSyncSm = tableHasColumn("subscriptions_master", "isDeleted")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `subscriptions_master_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `frequency` INTEGER NOT NULL, 
                        `note` TEXT, 
                        `isTransfer` INTEGER NOT NULL, 
                        `isEnabled` INTEGER NOT NULL, 
                        `editedAt` INTEGER NOT NULL, 
                        `isDeleted` INTEGER NOT NULL
                    )
                """.trimIndent())
                val smEditedAt = if (hasSyncSm) "COALESCE(editedAt, $now)" else "$now"
                val smIsDeleted = if (hasSyncSm) "isDeleted" else "0"
                db.execSQL("""
                    INSERT INTO `subscriptions_master_new` (id, name, frequency, note, isTransfer, isEnabled, editedAt, isDeleted)
                    SELECT id, name, frequency, note, isTransfer, isEnabled, $smEditedAt, $smIsDeleted FROM subscriptions_master
                """.trimIndent())
                db.execSQL("DROP TABLE subscriptions_master")
                db.execSQL("ALTER TABLE subscriptions_master_new RENAME TO subscriptions_master")
            }
        }

        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) {

                val now = System.currentTimeMillis()
                                
                db.execSQL("UPDATE accounts SET openingBalanceMinorUnits = 0 WHERE openingBalanceMinorUnits IS NULL")
                db.execSQL("UPDATE accounts SET isEmergencyFund = 0 WHERE isEmergencyFund IS NULL")
                db.execSQL("UPDATE accounts SET editedAt = $now WHERE editedAt IS NULL")
                db.execSQL("UPDATE accounts SET isDeleted = 0 WHERE isDeleted IS NULL")

                db.execSQL("INSERT OR IGNORE INTO accounts (name, type, openingBalance, openingBalanceMinorUnits, isEnabled, editedAt, isDeleted, isEmergencyFund) VALUES ('On Account', 'asset', 0.0, 0, 1, $now, 0, 0)")
            }
        }

        val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) {
    
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `invoice_clearances_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `transferTransactionId` INTEGER NOT NULL, 
                        `invoiceTransactionId` INTEGER NOT NULL, 
                        `amountCleared` REAL NOT NULL, 
                        `amountClearedMinorUnits` INTEGER,
                        FOREIGN KEY(`transferTransactionId`) REFERENCES `transaction_headers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`invoiceTransactionId`) REFERENCES `transaction_headers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `invoice_clearances_new` (transferTransactionId, invoiceTransactionId, amountCleared, amountClearedMinorUnits)
                    SELECT 
                        (SELECT h.id FROM transaction_headers h JOIN transactions t ON h.transactionNumber = COALESCE(t.transactionNumber, 'LEGACY_' || t.id) WHERE t.id = ic.transferTransactionId LIMIT 1),
                        (SELECT h.id FROM transaction_headers h JOIN transactions t ON h.transactionNumber = COALESCE(t.transactionNumber, 'LEGACY_' || t.id) WHERE t.id = ic.invoiceTransactionId LIMIT 1),
                        ic.amountCleared, ic.amountClearedMinorUnits
                    FROM invoice_clearances ic
                """.trimIndent())

                db.execSQL("DELETE FROM `invoice_clearances_new` WHERE transferTransactionId IS NULL OR invoiceTransactionId IS NULL")
                db.execSQL("DROP TABLE invoice_clearances")
                db.execSQL("ALTER TABLE invoice_clearances_new RENAME TO invoice_clearances")
            }
        }

        val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `loans` ADD COLUMN `isUpdateBank` INTEGER NOT NULL DEFAULT 1")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name_minorHeadId` ON `accounts` (`name`, `minorHeadId`)") } catch(e: Exception) {}
                try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name_type` ON `categories` (`name`, `type`)") } catch(e: Exception) {}
                try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)") } catch(e: Exception) {}
                try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_major_heads_name` ON `major_heads` (`name`)") } catch(e: Exception) {}
                try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_minor_heads_name_majorHeadId` ON `minor_heads` (`name`, `majorHeadId`)") } catch(e: Exception) {}
                try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_headers_transactionNumber` ON `transaction_headers` (`transactionNumber`)") } catch(e: Exception) {}
            }
        }

        val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("DELETE FROM accounts WHERE id NOT IN (SELECT MAX(id) FROM accounts GROUP BY name, minorHeadId)")
                    db.execSQL("DELETE FROM categories WHERE id NOT IN (SELECT MAX(id) FROM categories GROUP BY name, type)")
                    db.execSQL("DELETE FROM tags WHERE id NOT IN (SELECT MAX(id) FROM tags GROUP BY name)")
                    db.execSQL("DELETE FROM major_heads WHERE id NOT IN (SELECT MAX(id) FROM major_heads GROUP BY name)")
                    db.execSQL("DELETE FROM minor_heads WHERE id NOT IN (SELECT MAX(id) FROM minor_heads GROUP BY name, majorHeadId)")

                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_name_minorHeadId` ON `accounts` (`name`, `minorHeadId`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name_type` ON `categories` (`name`, `type`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_major_heads_name` ON `major_heads` (`name`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_minor_heads_name_majorHeadId` ON `minor_heads` (`name`, `majorHeadId`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_headers_transactionNumber` ON `transaction_headers` (`transactionNumber`)")
                } catch(e: Exception) {
                    Log.e("AppDatabase", "Migration 51 failed", e)
                }
            }
        }

        val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `transaction_lines` ADD COLUMN `reconciliationStatus` TEXT NOT NULL DEFAULT 'PENDING'") } catch(e: Exception) {}
                try { db.execSQL("ALTER TABLE `transactions` ADD COLUMN `reconciliationStatus` TEXT NOT NULL DEFAULT 'PENDING'") } catch(e: Exception) {}
                try { db.execSQL("UPDATE `transaction_lines` SET `reconciliationStatus` = 'VERIFIED' WHERE `isReconciled` = 1") } catch(e: Exception) {}
                try { db.execSQL("UPDATE `transactions` SET `reconciliationStatus` = 'VERIFIED' WHERE `isReconciled` = 1") } catch(e: Exception) {}
            }
        }

        val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `transaction_lines` ADD COLUMN `tags` TEXT") } catch(e: Exception) {}
            }
        }

        val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `targetAmount` REAL NOT NULL, `targetAmountMinorUnits` INTEGER NOT NULL, `targetDate` INTEGER, `icon` TEXT NOT NULL, `color` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `editedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_account_allocations` (`goalId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `allocatedAmount` REAL NOT NULL, `allocatedAmountMinorUnits` INTEGER NOT NULL, PRIMARY KEY(`goalId`, `accountId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `amount` REAL NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `triggerDay` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `lastExecutedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_transactions` (`goalId` INTEGER NOT NULL, `transactionId` INTEGER NOT NULL, PRIMARY KEY(`goalId`, `transactionId`))")
            }
        }

        val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_transactions` (`goalId` INTEGER NOT NULL, `transactionId` INTEGER NOT NULL, PRIMARY KEY(`goalId`, `transactionId`))")
            }
        }

        val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `goal_allocation_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `amount` REAL NOT NULL, `amountMinorUnits` INTEGER NOT NULL, `source` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `last4Digits` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `ifscCode` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `branchName` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `websiteUrl` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `contactPerson` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `minimumBalance` REAL") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `minimumBalanceMinorUnits` INTEGER") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `maturityDate` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `accounts` ADD COLUMN `bankName` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `sms_drafts` ADD COLUMN `accountId` INTEGER") } catch (e: Exception) {}
            }
        }

        val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try { db.execSQL("ALTER TABLE `transaction_lines` ADD COLUMN `fdLast4` TEXT") } catch (e: Exception) {}
                try { db.execSQL("ALTER TABLE `transaction_lines` ADD COLUMN `fdMaturityDate` TEXT") } catch (e: Exception) {}
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `fd_clearances` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `redemptionHeaderId` INTEGER NOT NULL,
                            `creationHeaderId` INTEGER NOT NULL,
                            `amountCleared` REAL NOT NULL
                        )
                    """.trimIndent())
                } catch (e: Exception) {}
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            val dbFile = context.getDatabasePath(DB_NAME)
            
            val legacyFiles = listOf("expensesrepository", "expenses_database.ftd", "expensesrepository.ftd")
            for (name in legacyFiles) {
                val legacyFile = context.getDatabasePath(name)
                if (legacyFile.exists() && (!dbFile.exists() || dbFile.length() < 1024)) {
                    Log.w("AppDatabase", "Found legacy data file '$name'. Migrating to '$DB_NAME'...")
                    try {
                        if (dbFile.exists()) dbFile.delete()
                        legacyFile.renameTo(dbFile)
                        File(legacyFile.path + "-shm").renameTo(File(dbFile.path + "-shm"))
                        File(legacyFile.path + "-wal").renameTo(File(dbFile.path + "-wal"))
                        File(legacyFile.path + ".xpt").renameTo(File(dbFile.path + ".xpt"))
                        break
                    } catch (e: Exception) {
                        Log.e("AppDatabase", "Failed to migrate legacy file $name", e)
                    }
                }
            }

            val encryptedFile = File(dbFile.path + ".xpt")
            if (encryptedFile.exists() && (!dbFile.exists() || dbFile.length() < 1024)) {
                Log.w("AppDatabase", "Database is currently encrypted at rest (.xpt exists). Awaiting user decryption.")
            }
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: let {
                    val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_14_15_FTD, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43, MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47, MIGRATION_47_48, MIGRATION_48_49, MIGRATION_49_50, MIGRATION_50_51, MIGRATION_51_52, MIGRATION_52_53, MIGRATION_53_54, MIGRATION_54_55, MIGRATION_55_56, MIGRATION_56_57, MIGRATION_57_58)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                        }
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                if (db.isOpen) {
                                    repairLegacyData(db)
                                    populateDatabaseIfEmpty(db)
                                }
                            } catch (e: Exception) {
                                Log.e("AppDatabase", "Error in onOpen callback", e)
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

        private fun repairLegacyData(db: SupportSQLiteDatabase) {
            if (!db.isOpen) return
            try {
                db.execSQL("ALTER TABLE `transaction_lines` ADD COLUMN `fdLast4` TEXT")
            } catch (e: Exception) {}
            try {
                db.execSQL("ALTER TABLE `transaction_lines` ADD COLUMN `fdMaturityDate` TEXT")
            } catch (e: Exception) {}
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `fd_clearances` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `redemptionHeaderId` INTEGER NOT NULL,
                        `creationHeaderId` INTEGER NOT NULL,
                        `amountCleared` REAL NOT NULL
                    )
                """.trimIndent())
            } catch (e: Exception) {}
            try {
                val now = System.currentTimeMillis()
                
                val accountCountCursor = db.query("SELECT COUNT(*) FROM accounts")
                accountCountCursor.moveToFirst()
                val accountCount = accountCountCursor.getInt(0)
                accountCountCursor.close()

                if (accountCount == 0) {
                     val legacyAccCheck = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('accounts_old', 'Accounts')")
                     if (legacyAccCheck.count > 0) {
                         legacyAccCheck.moveToFirst()
                         val source = legacyAccCheck.getString(0)
                         Log.w("AppDatabase", "Recovering accounts from '$source'...")
                         db.execSQL("INSERT OR IGNORE INTO accounts (id, name, type, openingBalance, openingBalanceMinorUnits, isEnabled, editedAt, isDeleted, isEmergencyFund) SELECT id, name, type, openingBalance, CAST(ROUND(openingBalance * 100) AS INTEGER), 1, $now, 0, 0 FROM `$source`")
                     }
                     legacyAccCheck.close()
                }

                val headerCountCursor = db.query("SELECT COUNT(*) FROM transaction_headers")
                headerCountCursor.moveToFirst()
                val headerCount = headerCountCursor.getInt(0)
                headerCountCursor.close()

                if (headerCount > 0) return

                var sourceTable = "transactions"
                val tableCheck = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='transactions'")
                val transactionsExists = tableCheck.count > 0
                tableCheck.close()

                if (!transactionsExists) {
                    val expTableCheck = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('expenses', 'Expenses')")
                    val expensesExists = expTableCheck.count > 0
                    if (expensesExists) {
                        expTableCheck.moveToFirst()
                        sourceTable = expTableCheck.getString(0)
                    } else {
                        expTableCheck.close()
                        return
                    }
                    expTableCheck.close()
                }

                val txnRowCountCursor = db.query("SELECT COUNT(*) FROM `$sourceTable`")
                txnRowCountCursor.moveToFirst()
                val rowCount = txnRowCountCursor.getInt(0)
                txnRowCountCursor.close()

                if (rowCount == 0) return

                Log.w("AppDatabase", "Emergency Repair: Found $rowCount rows in legacy table '$sourceTable' but 'transaction_headers' is empty. Migrating...")

                val txnCursor = db.query("SELECT * FROM `$sourceTable`")
                val txnGroups = mutableMapOf<String, MutableList<Map<String, Any?>>>()
                
                if (txnCursor.moveToFirst()) {
                    val columnNames = txnCursor.columnNames
                    do {
                        val row = mutableMapOf<String, Any?>()
                        for (i in columnNames.indices) {
                            row[columnNames[i]] = when (txnCursor.getType(i)) {
                                android.database.Cursor.FIELD_TYPE_INTEGER -> txnCursor.getLong(i)
                                android.database.Cursor.FIELD_TYPE_FLOAT -> txnCursor.getDouble(i)
                                android.database.Cursor.FIELD_TYPE_STRING -> txnCursor.getString(i)
                                else -> null
                            }
                        }
                        
                        val idVal = row["id"] ?: row["_id"] ?: 0L
                        val txnNum = row["transactionNumber"] as? String ?: "LEGACY_$idVal"
                        txnGroups.getOrPut(txnNum) { mutableListOf() }.add(row)
                    } while (txnCursor.moveToNext())
                }
                txnCursor.close()

                db.beginTransaction()
                try {
                    txnGroups.forEach { (txnNum, rows) ->
                        val first = rows[0]
                        val dateVal = (first["date"] ?: first["entryDate"] ?: LocalDate.now().toString()).toString()
                        val timeVal = (first["time"] ?: "12:00").toString()
                        val noteVal = (first["note"] ?: first["description"])?.toString()
                        
                        db.execSQL("INSERT INTO transaction_headers (transactionNumber, date, time, note, partyId, toPartyId, subName, subFrequency, merchantName, invoiceNumber, dueDays, editedAt, isDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf<Any?>(txnNum, dateVal, timeVal, noteVal, first["partyId"], first["toPartyId"], first["subName"], first["subFrequency"], first["merchantName"], first["invoiceNumber"], first["dueDays"], first["editedAt"] ?: now, first["isDeleted"] ?: 0))
                        
                        val headerIdCursor = db.query("SELECT last_insert_rowid()")
                        headerIdCursor.moveToFirst()
                        val headerId = headerIdCursor.getLong(0)
                        headerIdCursor.close()

                        rows.forEach { row ->
                            val amt = (row["amount"] ?: row["value"] ?: 0.0).toString().toDoubleOrNull() ?: 0.0
                            val amtMinor = (row["amountMinorUnits"] ?: (amt * 100).toLong())
                            
                            db.execSQL("INSERT INTO transaction_lines (headerId, accountId, toAccountId, categoryId, amount, amountMinorUnits, amountOriginal, amountOriginalMinorUnits, currencyCode, amountBase, amountBaseMinorUnits, isNegotiated, negotiationAmountOriginal, negotiationAmountOriginalMinorUnits, isDiscretionary, note, isReconciled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                arrayOf<Any?>(headerId, row["accountId"] ?: 0, row["toAccountId"], row["categoryId"], amt, amtMinor, row["amountOriginal"] ?: amt, row["amountOriginalMinorUnits"] ?: amtMinor, row["currencyCode"] ?: "INR", row["amountBase"] ?: amt, row["amountBaseMinorUnits"] ?: amtMinor, row["isNegotiated"] ?: 0, row["negotiationAmountOriginal"], row["negotiationAmountOriginalMinorUnits"], row["isDiscretionary"] ?: 0, row["note"], row["isReconciled"] ?: 0))
                        }

                        val tagsStr = first["tags"] as? String
                        if (!tagsStr.isNullOrBlank()) {
                            tagsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { tagId ->
                                db.execSQL("INSERT OR IGNORE INTO transaction_tags (headerId, tagId) VALUES (?, ?)", arrayOf<Any?>(headerId, tagId))
                            }
                        }
                    }

                    
                    val budgetCheck = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='budget_categories'")
                    val budgetsEmpty = budgetCheck.count == 0 || run {
                         val c = db.query("SELECT COUNT(*) FROM budget_categories")
                         c.moveToFirst()
                         val count = c.getInt(0)
                         c.close()
                         count == 0
                    }
                    budgetCheck.close()

                    if (budgetsEmpty) {
                        val budgetCursor = db.query("SELECT * FROM budgets")
                        if (budgetCursor.moveToFirst()) {
                            do {
                                val budgetId = budgetCursor.getLong(budgetCursor.getColumnIndexOrThrow("id"))
                                val catIdsIdx = budgetCursor.getColumnIndex("categoryIds")
                                val accIdsIdx = budgetCursor.getColumnIndex("accountIds")
                                
                                val catIdsStr = if (catIdsIdx != -1) budgetCursor.getString(catIdsIdx) else null
                                val accIdsStr = if (accIdsIdx != -1) budgetCursor.getString(accIdsIdx) else null

                                if (!catIdsStr.isNullOrBlank()) {
                                    catIdsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { catId ->
                                        db.execSQL("INSERT OR IGNORE INTO budget_categories (budgetId, categoryId) VALUES (?, ?)", arrayOf<Any?>(budgetId, catId))
                                    }
                                }
                                if (!accIdsStr.isNullOrBlank()) {
                                    accIdsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { accId ->
                                        db.execSQL("INSERT OR IGNORE INTO budget_accounts (budgetId, accountId) VALUES (?, ?)", arrayOf<Any?>(budgetId, accId))
                                    }
                                }
                            } while (budgetCursor.moveToNext())
                        }
                        budgetCursor.close()
                    }

                    db.setTransactionSuccessful()
                    Log.i("AppDatabase", "Repair Migration Completed Successfully.")
                } finally {
                    db.endTransaction()
                }
            } catch (e: Exception) {
                Log.e("AppDatabase", "Emergency Repair Failed", e)
            }
        }

        private fun populateDatabaseIfEmpty(db: SupportSQLiteDatabase) {
            if (!db.isOpen) return
            try {
                val dbPath = db.path ?: return
                val encryptedFile = File("$dbPath.xpt")
                if (encryptedFile.exists()) {
                    Log.w("AppDatabase", "Encrypted file .xpt exists on disk. Skipping default population.")
                    return
                }

                val countCursor = db.query("SELECT COUNT(*) FROM major_heads")
                countCursor.moveToFirst()
                val count = countCursor.getInt(0)
                countCursor.close()
                
                if (count == 0) {
                    Log.d("AppDatabase", "Database is empty. Populating with fresh default data.")
                    val now = System.currentTimeMillis()
                    db.beginTransaction()
                    try {
                        val majorHeads = listOf("Investments", "Bank Accounts", "Digital Wallets", "Crypto", "Precious metals", "On Account (Loan)", "Cash", "credit cards", "Others")
                        majorHeads.forEach { db.execSQL("INSERT OR IGNORE INTO major_heads (name, isEnabled, editedAt, isDeleted) VALUES ('$it', 1, $now, 0)") }

                        val expenseCategories = listOf("Apps" to "📱", "Clothing" to "👔", "Education" to "🎓", "Electronics" to "💻", "Entertainment" to "🎬", "Food" to "🍔", "Groceries" to "🛒", "Social" to "🥂", "Telephone" to "📞", "Travel" to "🚗", "Travel Distance" to "⛽", "Misc" to "📦", "Rent" to "🏠", "Utilities" to "💡")
                        val incomeCategories = listOf("Interest Income" to "📈", "Investment Income" to "💹", "Prof Fees" to "👨‍💼", "Lucky Reward" to "🍀", "Salary" to "💰")
                        expenseCategories.forEach { (name, icon) -> db.execSQL("INSERT OR IGNORE INTO categories (name, type, isEnabled, icon, editedAt, isDeleted) VALUES ('$name', 'expense', 1, '$icon', $now, 0)") }
                        incomeCategories.forEach { (name, icon) -> db.execSQL("INSERT OR IGNORE INTO categories (name, type, isEnabled, icon, editedAt, isDeleted) VALUES ('$name', 'income', 1, '$icon', $now, 0)") }

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
                            minors.forEach { db.execSQL("INSERT OR IGNORE INTO minor_heads (name, majorHeadId, isEnabled, editedAt, isDeleted) SELECT '$it', id, 1, $now, 0 FROM major_heads WHERE TRIM(name) = '$majorName'") }
                        }

                        db.execSQL("INSERT OR IGNORE INTO accounts (name, type, openingBalance, openingBalanceMinorUnits, isEnabled, icon, minorHeadId, editedAt, isDeleted, isEmergencyFund) SELECT 'Savings Acct 1', 'asset', 0.0, 0, 1, '🏦', id, $now, 0, 0 FROM minor_heads WHERE TRIM(name) = 'Savings account'")
                        db.execSQL("INSERT OR IGNORE INTO accounts (name, type, openingBalance, openingBalanceMinorUnits, isEnabled, icon, minorHeadId, editedAt, isDeleted, isEmergencyFund) SELECT 'Cash', 'asset', 0.0, 0, 1, '💵', id, $now, 0, 0 FROM minor_heads WHERE TRIM(name) = 'Cash'")
                        db.execSQL("INSERT OR IGNORE INTO accounts (name, type, openingBalance, openingBalanceMinorUnits, isEnabled, icon, minorHeadId, editedAt, isDeleted, isEmergencyFund) SELECT 'Others', 'asset', 0.0, 0, 1, '📁', id, $now, 0, 0 FROM minor_heads WHERE TRIM(name) = 'Default'")

                        val defaultTags = listOf("Personal", "Work", "Urgent", "Subscription")
                        defaultTags.forEach { db.execSQL("INSERT OR IGNORE INTO tags (name, isEnabled, editedAt, isDeleted, trackingType) VALUES ('$it', 1, $now, 0, 'Both')") }
                        
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            } catch (e: Exception) {
                Log.e("AppDatabase", "Error populating database if empty", e)
            }
        }
    }
}
