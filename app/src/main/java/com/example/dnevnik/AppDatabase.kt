package com.example.dnevnik

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Указываем обе сущности: заметки и людей.
// Если ты будешь дальше менять поля в PersonEntity или NoteEntity, просто увеличивай version на 1 (например, на 3, 4 и т.д.)
@Database(
    entities = [NoteEntity::class, PersonEntity::class],
    version = 3, // <-- ТУТ УВЕЛИЧИВАЕМ ЦИФРУ
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun personDao(): PersonDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dnevnik_database"
                )
                    // Эта строка автоматически очищает и пересоздает базу данных,
                    // если структура таблиц изменилась, предотвращая вылеты на старте!
                    .fallbackToDestructiveMigration()

                    // Разрешаем запросы в главном потоке, чтобы не городить асинхронность на этапе отладки шторки
                    .allowMainThreadQueries()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}