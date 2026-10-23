package com.example.dnevnik

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PersonDao {

    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllPeople(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPerson(person: PersonEntity)

    @Update
    fun updatePerson(person: PersonEntity)

    @Delete
    fun deletePerson(person: PersonEntity)
}