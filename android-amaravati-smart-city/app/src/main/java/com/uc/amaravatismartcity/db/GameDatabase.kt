package com.uc.amaravatismartcity.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.uc.amaravatismartcity.db.entities.GameStateEntity
import com.uc.amaravatismartcity.db.entities.PlacedItemEntity

@Database(entities = [PlacedItemEntity::class, GameStateEntity::class], version = 2, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE game_state ADD COLUMN trafficDensity INTEGER NOT NULL DEFAULT 35")
                db.execSQL("ALTER TABLE game_state ADD COLUMN totalBuildings INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE game_state ADD COLUMN activeGoal TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE game_state ADD COLUMN currentNews TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "amaravati_city_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
