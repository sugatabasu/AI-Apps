package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.RestaurantDatabase
import com.example.data.model.Restaurant
import com.example.data.repository.RestaurantRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(val restaurants: List<Restaurant>) : UiState
    data class Error(val message: String, val fallbackActive: Boolean = false) : UiState
}

data class FilterState(
    val isVegOnly: Boolean = false,
    val isNonVegOnly: Boolean = false,
    val ratingMin: Double = 0.0,
    val selectedPriceLevels: Set<Int> = emptySet()
)

class RestaurantViewModel(private val repository: RestaurantRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("New York")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _isFavoritesTabActive = MutableStateFlow(false)
    val isFavoritesTabActive: StateFlow<Boolean> = _isFavoritesTabActive.asStateFlow()

    val favorites: StateFlow<List<Restaurant>> = repository.allFavorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedRestaurant = MutableStateFlow<Restaurant?>(null)
    val selectedRestaurant: StateFlow<Restaurant?> = _selectedRestaurant.asStateFlow()

    // Real-time calculated distance center if available
    private val _userCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val userCoordinates: StateFlow<Pair<Double, Double>?> = _userCoordinates.asStateFlow()

    // Holds all retrieved restaurants from the latest search
    private val _searchResults = MutableStateFlow<List<Restaurant>>(emptyList())

    // Combines search results/favorites, coordinates, and filters to display the final filtered list
    val filteredRestaurants: StateFlow<List<Restaurant>> = combine(
        _searchResults,
        favorites,
        _isFavoritesTabActive,
        _filterState
    ) { searchList, favoritesList, isFavoritesActive, filters ->
        val baseList = if (isFavoritesActive) favoritesList else searchList
        baseList.filter { restaurant ->
            // Veg filter
            val matchesVeg = if (filters.isVegOnly) restaurant.isVegOnly else true
            // Non-veg filter
            val matchesNonVeg = if (filters.isNonVegOnly) restaurant.servesNonVeg else true
            // Rating filter
            val matchesRating = restaurant.rating >= filters.ratingMin
            // Price level filter
            val matchesPrice = if (filters.selectedPriceLevels.isNotEmpty()) {
                filters.selectedPriceLevels.contains(restaurant.priceLevel)
            } else {
                true
            }

            matchesVeg && matchesNonVeg && matchesRating && matchesPrice
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Run initial search
        performSearch()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectRestaurant(restaurant: Restaurant?) {
        _selectedRestaurant.value = restaurant
    }

    fun setUserCoordinates(lat: Double, lon: Double) {
        _userCoordinates.value = Pair(lat, lon)
    }

    fun toggleFavoritesTab(active: Boolean) {
        _isFavoritesTabActive.value = active
    }

    fun toggleVegOnlyFilter() {
        _filterState.update { it.copy(isVegOnly = !it.isVegOnly) }
    }

    fun toggleNonVegOnlyFilter() {
        _filterState.update { it.copy(isNonVegOnly = !it.isNonVegOnly) }
    }

    fun setRatingFilter(minRating: Double) {
        _filterState.update { it.copy(ratingMin = minRating) }
    }

    fun togglePriceLevelFilter(level: Int) {
        _filterState.update { state ->
            val updatedLevels = if (state.selectedPriceLevels.contains(level)) {
                state.selectedPriceLevels - level
            } else {
                state.selectedPriceLevels + level
            }
            state.copy(selectedPriceLevels = updatedLevels)
        }
    }

    fun clearAllFilters() {
        _filterState.value = FilterState()
    }

    fun toggleFavorite(restaurant: Restaurant) {
        viewModelScope.launch {
            val isFav = favorites.value.any { it.id == restaurant.id }
            if (isFav) {
                repository.removeFavoriteById(restaurant.id)
            } else {
                repository.insertFavorite(restaurant)
            }
        }
    }

    fun performSearch() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return

        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val apiKey = BuildConfig.GEMINI_API_KEY
            // If API key is empty or is placeholder
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
                Log.w("RestaurantViewModel", "Using Mock Restaurant Data (No Gemini API key supplied)")
                val mockData = MockRestaurantGenerator.generateMockRestaurants(query)
                _searchResults.value = mockData
                _uiState.value = UiState.Success(mockData)
                return@launch
            }

            try {
                val coords = _userCoordinates.value
                val results = repository.searchRestaurants(
                    locationQuery = query,
                    apiKey = apiKey,
                    userLatitude = coords?.first,
                    userLongitude = coords?.second
                )
                _searchResults.value = results
                _uiState.value = UiState.Success(results)
            } catch (e: Exception) {
                Log.e("RestaurantViewModel", "Gemini API error: ${e.message}", e)
                // Fall back to mock generator so app doesn't crash or go blank
                val mockData = MockRestaurantGenerator.generateMockRestaurants(query)
                _searchResults.value = mockData
                _uiState.value = UiState.Error(
                    message = "Could not fetch online recommendations. Loaded offline recommendations.",
                    fallbackActive = true
                )
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = RestaurantDatabase.getDatabase(context)
            val repository = RestaurantRepository(database.favoriteRestaurantDao())
            @Suppress("UNCHECKED_CAST")
            return RestaurantViewModel(repository) as T
        }
    }
}

object MockRestaurantGenerator {
    fun generateMockRestaurants(location: String): List<Restaurant> {
        val city = location.trim().lowercase()
        return when {
            city.contains("mumbai") || city.contains("bombay") -> listOf(
                Restaurant(
                    id = "mumbai-1",
                    name = "The Spice Route",
                    cuisine = "North Indian Fine Dine",
                    address = "Colaba Causeway, Mumbai",
                    rating = 4.7,
                    priceLevel = 3,
                    isVegOnly = false,
                    servesNonVeg = true,
                    latitude = 18.922,
                    longitude = 72.834,
                    description = "An exquisite fine-dining culinary journey with aromatic spices and royal Mughlai recipes.",
                    popularDishes = listOf("Butter Chicken", "Dal Makhani", "Garlic Naan"),
                    phoneNumber = "+91 22 1234 5678",
                    openingHours = "12:00 PM - 11:30 PM",
                    imageUrl = ""
                ),
                Restaurant(
                    id = "mumbai-2",
                    name = "Sukh Sagar Vegetarian",
                    cuisine = "South Indian Street Food",
                    address = "Chowpatty Beach, Mumbai",
                    rating = 4.5,
                    priceLevel = 1,
                    isVegOnly = true,
                    servesNonVeg = false,
                    latitude = 18.954,
                    longitude = 72.812,
                    description = "Iconic pure-veg eatery famous for crisp Dosas, buttery Pav Bhaji, and fresh juices by the sea.",
                    popularDishes = listOf("Special Pav Bhaji", "Cheese Masala Dosa", "Mango Milkshake"),
                    phoneNumber = "+91 22 8765 4321",
                    openingHours = "9:00 AM - 12:00 AM",
                    imageUrl = ""
                ),
                Restaurant(
                    id = "mumbai-3",
                    name = "Britannia & Co.",
                    cuisine = "Parsi / Iranian Cafe",
                    address = "Ballard Estate, Fort, Mumbai",
                    rating = 4.6,
                    priceLevel = 2,
                    isVegOnly = false,
                    servesNonVeg = true,
                    latitude = 18.932,
                    longitude = 72.839,
                    description = "A historic Parsi café operating since 1923, famous for its berry pulav and colonial charm.",
                    popularDishes = listOf("Mutton Berry Pulav", "Sali Boti", "Caramel Custard"),
                    phoneNumber = "+91 22 2261 5264",
                    openingHours = "11:30 AM - 4:00 PM",
                    imageUrl = ""
                ),
                Restaurant(
                    id = "mumbai-4",
                    name = "Gokul Veg Restaurant",
                    cuisine = "Multi-cuisine Vegetarian",
                    address = "Near Gateway of India, Colaba, Mumbai",
                    rating = 4.2,
                    priceLevel = 2,
                    isVegOnly = true,
                    servesNonVeg = false,
                    latitude = 18.923,
                    longitude = 72.832,
                    description = "Family-friendly pure veg dining spot serving hot tandoori starters and delicious Punjabi mains.",
                    popularDishes = listOf("Paneer Tikka Masala", "Veg Biryani", "Sizzling Brownie"),
                    phoneNumber = "+91 22 2282 1212",
                    openingHours = "11:00 AM - 11:00 PM",
                    imageUrl = ""
                ),
                Restaurant(
                    id = "mumbai-5",
                    name = "Mahesh Lunch Home",
                    cuisine = "Mangalorean Seafood",
                    address = "Cawasji Patel St, Fort, Mumbai",
                    rating = 4.4,
                    priceLevel = 3,
                    isVegOnly = false,
                    servesNonVeg = true,
                    latitude = 18.931,
                    longitude = 72.833,
                    description = "A seafood institution serving rich coastal crab, butter garlic prawns, and authentic Surmai fry.",
                    popularDishes = listOf("Butter Garlic Crab", "Surmai Fry", "Neer Dosa with Fish Curry"),
                    phoneNumber = "+91 22 2287 0938",
                    openingHours = "11:30 AM - 11:30 PM",
                    imageUrl = ""
                )
            )
            city.contains("san francisco") || city.contains("sf") -> listOf(
                Restaurant(
                    id = "sf-1",
                    name = "The Golden Gate Bistro",
                    cuisine = "American Contemporary",
                    address = "Fisherman's Wharf, San Francisco, CA",
                    rating = 4.6,
                    priceLevel = 3,
                    isVegOnly = false,
                    servesNonVeg = true,
                    latitude = 37.808,
                    longitude = -122.417,
                    description = "Breathtaking views of the bay with legendary sourdough clam chowder and fresh Pacific Dungeness crab.",
                    popularDishes = listOf("Clam Chowder in Sourdough Bowl", "Dungeness Crab Cake", "California Pinot Noir"),
                    phoneNumber = "+1 415-555-0145",
                    openingHours = "11:00 AM - 10:00 PM",
                    imageUrl = ""
                ),
                Restaurant(
                    id = "sf-2",
                    name = "Golden Era Vegan",
                    cuisine = "Vietnamese Asian Vegan",
                    address = "395 Golden Gate Ave, San Francisco, CA",
                    rating = 4.7,
                    priceLevel = 2,
                    isVegOnly = true,
                    servesNonVeg = false,
                    latitude = 37.781,
                    longitude = -122.417,
                    description = "Award-winning organic 100% plant-based restaurant serving flavorful vegan pho, rolls, and curries.",
                    popularDishes = listOf("Golden Era Pho", "Spicy Gourmet Lemongrass", "Vegan Spring Rolls"),
                    phoneNumber = "+1 415-487-8687",
                    openingHours = "11:30 AM - 8:30 PM",
                    imageUrl = ""
                ),
                Restaurant(
                    id = "sf-3",
                    name = "Tony's Pizza Napoletana",
                    cuisine = "Italian Pizzeria",
                    address = "1570 Stockton St, San Francisco, CA",
                    rating = 4.8,
                    priceLevel = 3,
                    isVegOnly = false,
                    servesNonVeg = true,
                    latitude = 37.801,
                    longitude = -122.408,
                    description = "Slices of pizza heaven made by a 13-time World Pizza Champion in the heart of North Beach.",
                    popularDishes = listOf("Margherita Napoletana", "Coal Fired New Yorker", "Calzone"),
                    phoneNumber = "+1 415-835-9888",
                    openingHours = "12:00 PM - 10:00 PM",
                    imageUrl = ""
                ),
                Restaurant(
                    id = "sf-4",
                    name = "Greens Restaurant",
                    cuisine = "Gourmet Vegetarian",
                    address = "Marina Blvd, Fort Mason Center, San Francisco, CA",
                    rating = 4.5,
                    priceLevel = 3,
                    isVegOnly = true,
                    servesNonVeg = false,
                    latitude = 37.806,
                    longitude = -122.431,
                    description = "A visual and culinary masterpiece serving organic, seasonal, vegetarian farm-to-table cuisine with marina views.",
                    popularDishes = listOf("Wild Mushroom Tart", "Spring Garden Risotto", "Greens Mezze Plate"),
                    phoneNumber = "+1 415-771-6222",
                    openingHours = "12:00 PM - 9:00 PM",
                    imageUrl = ""
                ),
                Restaurant(
                    id = "sf-5",
                    name = "House of Prime Rib",
                    cuisine = "Steakhouse / American",
                    address = "1906 Van Ness Ave, San Francisco, CA",
                    rating = 4.8,
                    priceLevel = 4,
                    isVegOnly = false,
                    servesNonVeg = true,
                    latitude = 37.793,
                    longitude = -122.422,
                    description = "SF landmark famous for prime rib of beef carved tableside from shiny silver carts.",
                    popularDishes = listOf("House of Prime Rib Cut", "Yorkshire Pudding", "Spinach Salad"),
                    phoneNumber = "+1 415-885-4605",
                    openingHours = "4:30 PM - 10:00 PM",
                    imageUrl = ""
                )
            )
            else -> {
                val capitalizedLocation = location.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                listOf(
                    Restaurant(
                        id = "${city.replace(" ", "-")}-1",
                        name = "La Terrazza Bistro",
                        cuisine = "Italian Kitchen",
                        address = "12 Main Street, $capitalizedLocation",
                        rating = 4.5,
                        priceLevel = 2,
                        isVegOnly = false,
                        servesNonVeg = true,
                        latitude = 40.7128 + 0.005,
                        longitude = -74.0060 + 0.005,
                        description = "Cozy local bistro specializing in fresh wood-fired pizzas, handmade pasta, and beautiful local wines.",
                        popularDishes = listOf("Penne Alla Vodka", "Truffle Pizza", "Tiramisu"),
                        phoneNumber = "+1 234-567-8901",
                        openingHours = "11:00 AM - 10:00 PM",
                        imageUrl = ""
                    ),
                    Restaurant(
                        id = "${city.replace(" ", "-")}-2",
                        name = "The Green Garden Cafe",
                        cuisine = "Healthy Vegetarian",
                        address = "45 Wellness Lane, $capitalizedLocation",
                        rating = 4.6,
                        priceLevel = 2,
                        isVegOnly = true,
                        servesNonVeg = false,
                        latitude = 40.7128 + 0.008,
                        longitude = -74.0060 - 0.002,
                        description = "Bright, modern oasis serving 100% organic, locally sourced salads, smoothie bowls, and gourmet coffees.",
                        popularDishes = listOf("Avocado Sourdough Toast", "Açai Berry Superbowl", "Vegan Matcha Latte"),
                        phoneNumber = "+1 234-567-8902",
                        openingHours = "8:00 AM - 6:00 PM",
                        imageUrl = ""
                    ),
                    Restaurant(
                        id = "${city.replace(" ", "-")}-3",
                        name = "Sizzling Steaks",
                        cuisine = "Premium Steakhouse",
                        address = "78 Prime Blvd, $capitalizedLocation",
                        rating = 4.8,
                        priceLevel = 4,
                        isVegOnly = false,
                        servesNonVeg = true,
                        latitude = 40.7128 - 0.004,
                        longitude = -74.0060 + 0.010,
                        description = "Upscale contemporary steakhouse offering prime dry-aged cuts, premium seafood, and standard martinis.",
                        popularDishes = listOf("Dry Aged Ribeye", "Garlic Butter Lobster Tail", "Creamed Spinach"),
                        phoneNumber = "+1 234-567-8903",
                        openingHours = "5:00 PM - 11:00 PM",
                        imageUrl = ""
                    ),
                    Restaurant(
                        id = "${city.replace(" ", "-")}-4",
                        name = "The Curry House",
                        cuisine = "Authentic Indian",
                        address = "90 Saffron Road, $capitalizedLocation",
                        rating = 4.4,
                        priceLevel = 2,
                        isVegOnly = false,
                        servesNonVeg = true,
                        latitude = 40.7128 + 0.012,
                        longitude = -74.0060 + 0.002,
                        description = "Colorful, welcoming family restaurant serving robust tandoori specialties and savory regional curries.",
                        popularDishes = listOf("Paneer Tikka Masala", "Chicken Tikka Masala", "Garlic Butter Naan"),
                        phoneNumber = "+1 234-567-8904",
                        openingHours = "11:30 AM - 10:30 PM",
                        imageUrl = ""
                    ),
                    Restaurant(
                        id = "${city.replace(" ", "-")}-5",
                        name = "Sakura Sushi",
                        cuisine = "Japanese Sushi",
                        address = "128 Blossom Ave, $capitalizedLocation",
                        rating = 4.7,
                        priceLevel = 3,
                        isVegOnly = false,
                        servesNonVeg = true,
                        latitude = 40.7128 + 0.002,
                        longitude = -74.0060 - 0.008,
                        description = "Elegant Japanese dining room featuring expert sushi chefs, fresh sashimi platters, and premium sakes.",
                        popularDishes = listOf("Dragon Roll", "Chef's Sashimi Selection", "Chicken Teriyaki"),
                        phoneNumber = "+1 234-567-8905",
                        openingHours = "12:00 PM - 10:00 PM",
                        imageUrl = ""
                    ),
                    Restaurant(
                        id = "${city.replace(" ", "-")}-6",
                        name = "Radha Krishna Pure Veg",
                        cuisine = "South & North Indian Veg",
                        address = "23 Temple Road, $capitalizedLocation",
                        rating = 4.3,
                        priceLevel = 1,
                        isVegOnly = true,
                        servesNonVeg = false,
                        latitude = 40.7128 - 0.006,
                        longitude = -74.0060 + 0.004,
                        description = "Unpretentious traditional pure vegetarian restaurant known for piping-hot Dosas, Uttapams, and Thalis.",
                        popularDishes = listOf("Paper Masala Dosa", "Royal Indian Veg Thali", "Filter Coffee"),
                        phoneNumber = "+1 234-567-8906",
                        openingHours = "7:00 AM - 10:00 PM",
                        imageUrl = ""
                    )
                )
            }
        }
    }
}
