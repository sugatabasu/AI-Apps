package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteRestaurantDao {
    @Query("SELECT * FROM favorite_restaurants ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteRestaurant>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(restaurant: FavoriteRestaurant)

    @Query("DELETE FROM favorite_restaurants WHERE id = :id")
    suspend fun deleteFavoriteById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_restaurants WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean
}
