package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Restaurant(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "cuisine") val cuisine: String,
    @Json(name = "address") val address: String,
    @Json(name = "rating") val rating: Double,
    @Json(name = "priceLevel") val priceLevel: Int, // 1 to 4 representing $, $$, $$$, $$$$
    @Json(name = "isVegOnly") val isVegOnly: Boolean, // true if purely vegetarian
    @Json(name = "servesNonVeg") val servesNonVeg: Boolean, // true if serves non-veg
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "description") val description: String,
    @Json(name = "popularDishes") val popularDishes: List<String>? = null,
    @Json(name = "phoneNumber") val phoneNumber: String? = null,
    @Json(name = "openingHours") val openingHours: String? = null,
    @Json(name = "imageUrl") val imageUrl: String? = null
)
