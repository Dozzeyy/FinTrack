package com.openapps.fintrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Database(entities = [Account::class, Category::class, Tag::class, Budget::class, Transaction::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    fun checkpoint() {
        try {
            this.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL)")
                try {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tags` TEXT")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `amount` REAL NOT NULL, `duration` TEXT NOT NULL, `note` TEXT)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `transactionNumber` TEXT")
                } catch (e: Exception) {}
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expenses_database"
                )
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) {
                            populateDatabase(db)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun populateDatabase(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO accounts (name, type, openingBalance, isEnabled) VALUES ('Savings Bank', 'asset', 0.0, 1)")
            db.execSQL("INSERT INTO accounts (name, type, openingBalance, isEnabled) VALUES ('Cash', 'asset', 0.0, 1)")
            db.execSQL("INSERT INTO accounts (name, type, openingBalance, isEnabled) VALUES ('Investments', 'asset', 0.0, 1)")
            db.execSQL("INSERT INTO accounts (name, type, openingBalance, isEnabled) VALUES ('Credit Cards', 'liability', 0.0, 1)")

            val expenseCategories = listOf("Apps", "Clothing", "Education", "Electronics", "Entertainment", "Food 3 time", "Food online", "Food Snacks", "Food Healthy", "Groceries", "Home Telephone", "Social", "Telephone", "Travel", "Travel Distance", "Misc")
            val incomeCategories = listOf("Interest Income", "Investment Income", "Prof Fees", "Lucky Reward", "Salary")
            
            expenseCategories.forEach { 
                db.execSQL("INSERT INTO categories (name, type, isEnabled) VALUES ('$it', 'expense', 1)") 
            }
            incomeCategories.forEach { 
                db.execSQL("INSERT INTO categories (name, type, isEnabled) VALUES ('$it', 'income', 1)")
            }
            
            val defaultTags = listOf("Personal", "Work", "Urgent", "Subscription")
            defaultTags.forEach {
                db.execSQL("INSERT INTO tags (name, isEnabled) VALUES ('$it', 1)")
            }
        }
    }
}
