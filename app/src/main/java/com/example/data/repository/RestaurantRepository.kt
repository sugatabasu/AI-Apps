package com.example.data.repository

import com.example.data.local.FavoriteRestaurant
import com.example.data.local.FavoriteRestaurantDao
import com.example.data.model.Restaurant
import com.example.data.remote.GeminiRetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RestaurantRepository(private val favoriteRestaurantDao: FavoriteRestaurantDao) {

    val allFavorites: Flow<List<Restaurant>> = favoriteRestaurantDao.getAllFavorites()
        .map { list -> list.map { it.toRestaurant() } }

    suspend fun insertFavorite(restaurant: Restaurant) {
        favoriteRestaurantDao.insertFavorite(restaurant.toFavorite())
    }

    suspend fun removeFavoriteById(id: String) {
        favoriteRestaurantDao.deleteFavoriteById(id)
    }

    suspend fun isFavorite(id: String): Boolean {
        return favoriteRestaurantDao.isFavorite(id)
    }

    suspend fun searchRestaurants(
        locationQuery: String,
        apiKey: String,
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): List<Restaurant> {
        return GeminiRetrofitClient.fetchNearbyRestaurants(
            locationQuery = locationQuery,
            apiKey = apiKey,
            userLatitude = userLatitude,
            userLongitude = userLongitude
        )
    }

    private fun Restaurant.toFavorite(): FavoriteRestaurant {
        return FavoriteRestaurant(
            id = id,
            name = name,
            cuisine = cuisine,
            address = address,
            rating = rating,
            priceLevel = priceLevel,
            isVegOnly = isVegOnly,
            servesNonVeg = servesNonVeg,
            latitude = latitude,
            longitude = longitude,
            description = description,
            popularDishes = popularDishes?.joinToString(","),
            phoneNumber = phoneNumber,
            openingHours = openingHours,
            imageUrl = imageUrl
        )
    }

    private fun FavoriteRestaurant.toRestaurant(): Restaurant {
        return Restaurant(
            id = id,
            name = name,
            cuisine = cuisine,
            address = address,
            rating = rating,
            priceLevel = priceLevel,
            isVegOnly = isVegOnly,
            servesNonVeg = servesNonVeg,
            latitude = latitude,
            longitude = longitude,
            description = description,
            popularDishes = popularDishes?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
            phoneNumber = phoneNumber,
            openingHours = openingHours,
            imageUrl = imageUrl
        )
    }
}
