package com.example.data.remote

import android.util.Log
import com.example.data.model.Restaurant
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = null,
    @Json(name = "temperature") val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * Cleans up markdown code fences if Gemini returns them.
     */
    fun cleanJsonResponse(rawResponse: String): String {
        var cleaned = rawResponse.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json").substringBeforeLast("```")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```").substringBeforeLast("```")
        }
        return cleaned.trim()
    }

    suspend fun fetchNearbyRestaurants(
        locationQuery: String,
        apiKey: String,
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): List<Restaurant> {
        val systemPrompt = """
            You are an expert local restaurant discoverer.
            Your task is to return a raw JSON array representing highly realistic or actual nearby restaurants for the given location: "$locationQuery".
            ${if (userLatitude != null && userLongitude != null) "The user's coordinates are Latitude: $userLatitude, Longitude: $userLongitude. Please center the recommendations around these coordinates, placing restaurants within a 5km radius." else ""}
            
            IMPORTANT:
            1. You MUST respond with a valid JSON array of restaurant objects. 
            2. Do NOT wrap your response in markdown code blocks or formatting like ```json ... ```. Just return the raw JSON array string.
            3. Each restaurant in the list must exactly follow this schema:
               {
                 "id": "unique-string-slug-id",
                 "name": "Restaurant Name",
                 "cuisine": "Cuisine type (e.g., Italian, Indian, Japanese, Mexican)",
                 "address": "Street address, City",
                 "rating": Double (between 1.0 and 5.0, e.g. 4.6),
                 "priceLevel": Int (1 to 4 representing price, where 1 is $ and 4 is $$$$),
                 "isVegOnly": Boolean (true if 100% vegetarian),
                 "servesNonVeg": Boolean (true if they serve non-veg),
                 "latitude": Double (latitude coordinate, close to the location query or user coordinates),
                 "longitude": Double (longitude coordinate, close to the location query or user coordinates),
                 "description": "Short, catchy 2-sentence description of the restaurant",
                 "popularDishes": ["Dish 1", "Dish 2", "Dish 3"],
                 "phoneNumber": "Phone number, e.g. +1 555-0199",
                 "openingHours": "e.g., 9:00 AM - 10:00 PM",
                 "imageUrl": "Empty string or a culinary-themed stock image url"
               }
            4. Ensure the list has a good mix of veg-only, serves both, and non-veg restaurants. Provide at least 5-8 restaurants.
        """.trimIndent()

        val prompt = "Find restaurants near location: $locationQuery"

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.3
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        val response = service.generateContent(apiKey, request)
        val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response received from Gemini API")

        val cleanedJson = cleanJsonResponse(rawText)
        Log.d("GeminiApi", "Raw JSON output: $cleanedJson")

        val type = Types.newParameterizedType(List::class.java, Restaurant::class.java)
        val adapter = moshi.adapter<List<Restaurant>>(type)
        return adapter.fromJson(cleanedJson) ?: emptyList()
    }
}
