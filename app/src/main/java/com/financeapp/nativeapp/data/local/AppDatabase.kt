package com.financeapp.nativeapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters
import com.financeapp.nativeapp.data.local.converter.Converters
import com.financeapp.nativeapp.data.local.dao.TransactionDao
import com.financeapp.nativeapp.data.local.dao.FinancePlanDao
import com.financeapp.nativeapp.data.local.dao.PendingDeletionDao
import com.financeapp.nativeapp.data.local.entity.FinancePlanEntity
import com.financeapp.nativeapp.data.local.entity.FinancePlanPaymentEntity
import com.financeapp.nativeapp.data.local.entity.PendingTransactionDeletionEntity
import com.financeapp.nativeapp.data.local.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class, FinancePlanEntity::class, FinancePlanPaymentEntity::class, PendingTransactionDeletionEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun financePlanDao(): FinancePlanDao
    abstract fun pendingDeletionDao(): PendingDeletionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `finance_plans` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `totalAmount` REAL NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `startAt` INTEGER NOT NULL, `mode` TEXT NOT NULL, `installmentCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `finance_plan_payments` (`planId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `paidAt` INTEGER NOT NULL, `transactionId` TEXT NOT NULL, PRIMARY KEY(`planId`, `sequence`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_transaction_deletions` (`id` TEXT NOT NULL, `queuedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Não apaga nem recria nenhuma tabela. Os registros anteriores recebem
                // o proprietário no onOpen, conforme UID original salvo na V11.
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `ownerUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `finance_plans` ADD COLUMN `ownerUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `finance_plan_payments` ADD COLUMN `ownerUid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `pending_transaction_deletions` ADD COLUMN `ownerUid` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Campo opcional: registros antigos permanecem intactos e sem status de pagamento.
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `paidAt` INTEGER")
                // Os lançamentos de parcelas já quitadas herdam a data registrada na programação.
                db.execSQL("""UPDATE transactions SET paidAt = (
                    SELECT p.paidAt FROM finance_plan_payments p
                    WHERE p.transactionId = transactions.id AND p.ownerUid = transactions.ownerUid
                    LIMIT 1
                ) WHERE EXISTS (
                    SELECT 1 FROM finance_plan_payments p
                    WHERE p.transactionId = transactions.id AND p.ownerUid = transactions.ownerUid
                )""")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    val instance = Room.databaseBuilder(appContext, AppDatabase::class.java, "finance_app_database")
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                        .addCallback(object : RoomDatabase.Callback() {
                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                // V11 utiliza um único UID salvo para o banco antigo.
                                // Nunca atribuir dados legados ao novo usuário simplesmente
                                // porque ele foi o último a fazer login.
                                val originalUid = appContext.getSharedPreferences(
                                    "financeapp_account_owner", Context.MODE_PRIVATE
                                ).getString("uid", null)?.takeIf { it.isNotBlank() } ?: return
                                db.beginTransaction()
                                try {
                                    for (table in arrayOf(
                                        "transactions", "finance_plans", "finance_plan_payments",
                                        "pending_transaction_deletions"
                                    )) {
                                        db.execSQL("UPDATE `$table` SET `ownerUid` = ? WHERE `ownerUid` = ''", arrayOf(originalUid))
                                    }
                                    db.setTransactionSuccessful()
                                } finally {
                                    db.endTransaction()
                                }
                            }
                        })
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}
