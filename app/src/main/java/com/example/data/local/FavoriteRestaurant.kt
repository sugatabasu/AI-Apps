package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_restaurants")
data class FavoriteRestaurant(
    @PrimaryKey val id: String,
    val name: String,
    val cuisine: String,
    val address: String,
    val rating: Double,
    val priceLevel: Int,
    val isVegOnly: Boolean,
    val servesNonVeg: Boolean,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val popularDishes: String?, // comma-separated strings
    val phoneNumber: String?,
    val openingHours: String?,
    val imageUrl: String?,
    val timestamp: Long = System.currentTimeMillis()
)
