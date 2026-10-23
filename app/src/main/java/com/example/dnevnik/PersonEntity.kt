package com.example.dnevnik

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val height: String,
    val birthday: String,
    val hobbies: String,
    val favoriteMusic: String, // Именно favoriteMusic
    val favoriteMovies: String, // Именно favoriteMovies
    val wishlistNotes: String,  // Именно wishlistNotes
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false // Добавили строго isDeleted
)