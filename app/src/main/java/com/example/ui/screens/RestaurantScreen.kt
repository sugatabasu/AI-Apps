package com.example.ui.screens

import android.Manifest
import android.util.Log
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.Restaurant
import com.example.ui.viewmodel.FilterState
import com.example.ui.viewmodel.RestaurantViewModel
import com.example.ui.viewmodel.UiState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlin.math.*

// Custom Color Palette for the Clean Minimalism Theme
private val TerracottaPrimary = Color(0xFF6750A4)
private val TerracottaDark = Color(0xFF21005D)
private val SageGreen = Color(0xFF062F11)
private val SageGreenLight = Color(0xFFC4E7CB)
private val CharcoalDark = Color(0xFF1C1B1F)
private val CreamBackground = Color(0xFFFEF7FF)
private val GoldRating = Color(0xFFFFB300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantScreen(
    viewModel: RestaurantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ViewModel state bindings
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isFavoritesTabActive by viewModel.isFavoritesTabActive.collectAsStateWithLifecycle()
    val favoritesList by viewModel.favorites.collectAsStateWithLifecycle()
    val filteredList by viewModel.filteredRestaurants.collectAsStateWithLifecycle()
    val selectedRestaurant by viewModel.selectedRestaurant.collectAsStateWithLifecycle()
    val userCoordinates by viewModel.userCoordinates.collectAsStateWithLifecycle()

    // State for rating filter dropdown
    var showRatingMenu by remember { mutableStateOf(false) }

    // State for sheet trigger
    var showBottomSheet by remember { mutableStateOf(false) }

    // Sync bottom sheet state
    LaunchedEffect(selectedRestaurant) {
        showBottomSheet = selectedRestaurant != null
    }

    // Permission and GPS launcher
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            try {
                locationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.setUserCoordinates(location.latitude, location.longitude)
                        viewModel.updateSearchQuery("Nearby GPS")
                        viewModel.performSearch()
                        Toast.makeText(context, "Location updated successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not acquire GPS. Try searching standard cities.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: SecurityException) {
                Log.e("GPS", "Security exception fetching location", e)
            }
        } else {
            Toast.makeText(context, "Location permission denied. Type location manually.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CreamBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestaurantMenu,
                            contentDescription = null,
                            tint = TerracottaPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restaurant Finder",
                            fontWeight = FontWeight.Bold,
                            color = CharcoalDark,
                            fontSize = 22.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CreamBackground,
                    titleContentColor = CharcoalDark
                ),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.clearAllFilters()
                            Toast.makeText(context, "Filters cleared", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FilterAltOff,
                            contentDescription = "Clear Filters",
                            tint = CharcoalDark
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFF3EDF7),
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = !isFavoritesTabActive,
                    onClick = { viewModel.toggleFavoritesTab(false) },
                    icon = {
                        Icon(
                            imageVector = if (!isFavoritesTabActive) Icons.Filled.Explore else Icons.Outlined.Explore,
                            contentDescription = "Search"
                        )
                    },
                    label = { Text("Search Nearby") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF21005D),
                        selectedTextColor = Color(0xFF21005D),
                        indicatorColor = Color(0xFFEADDFF),
                        unselectedIconColor = Color(0xFF49454F),
                        unselectedTextColor = Color(0xFF49454F)
                    ),
                    modifier = Modifier.testTag("nav_search_tab")
                )
                NavigationBarItem(
                    selected = isFavoritesTabActive,
                    onClick = { viewModel.toggleFavoritesTab(true) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (favoritesList.isNotEmpty()) {
                                    Badge(containerColor = TerracottaPrimary) {
                                        Text(favoritesList.size.toString(), color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isFavoritesTabActive) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorites"
                            )
                        }
                    },
                    label = { Text("Favorites") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF21005D),
                        selectedTextColor = Color(0xFF21005D),
                        indicatorColor = Color(0xFFEADDFF),
                        unselectedIconColor = Color(0xFF49454F),
                        unselectedTextColor = Color(0xFF49454F)
                    ),
                    modifier = Modifier.testTag("nav_favorites_tab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Block
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(1.dp, Color(0xFFE7E0EC), RoundedCornerShape(28.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Type city name, e.g. London...") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = TerracottaPrimary)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.Gray)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6750A4),
                                unfocusedBorderColor = Color(0xFF79747E),
                                cursorColor = Color(0xFF6750A4),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_location_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // GPS Trigger button
                        FilledIconButton(
                            onClick = {
                                val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                                val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
                                    try {
                                        locationClient.lastLocation.addOnSuccessListener { location ->
                                            if (location != null) {
                                                viewModel.setUserCoordinates(location.latitude, location.longitude)
                                                viewModel.updateSearchQuery("Nearby GPS")
                                                viewModel.performSearch()
                                                Toast.makeText(context, "Location synced", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Could not acquire GPS coordinates", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: SecurityException) {
                                        Log.e("GPS", "SecurityException during check", e)
                                    }
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFEADDFF),
                                contentColor = Color(0xFF21005D)
                            ),
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("gps_location_button")
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = "Use current location", tint = Color(0xFF21005D))
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = { viewModel.performSearch() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.height(52.dp).testTag("search_action_button")
                        ) {
                            Text("Find", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Cities Scroll
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val cities = listOf("Mumbai", "San Francisco", "New York", "London", "Tokyo", "Paris")
                        items(cities) { city ->
                            val isCitySelected = searchQuery.equals(city, ignoreCase = true)
                            FilterChip(
                                selected = isCitySelected,
                                onClick = {
                                    viewModel.updateSearchQuery(city)
                                    viewModel.performSearch()
                                },
                                label = { Text(city, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEADDFF),
                                    selectedLabelColor = Color(0xFF21005D),
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF49454F)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isCitySelected,
                                    borderColor = Color(0xFF79747E),
                                    selectedBorderColor = Color(0xFFEADDFF),
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            // Interactive Search Filters Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(1.dp, Color(0xFFE7E0EC), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Customize Food Preferences:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Veg Only Chip
                        item {
                            FilterChip(
                                selected = filterState.isVegOnly,
                                onClick = { viewModel.toggleVegOnlyFilter() },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Canvas(modifier = Modifier.size(10.dp)) {
                                            drawCircle(color = SageGreen, radius = 4.dp.toPx())
                                            drawRect(color = SageGreen, style = Stroke(width = 1.5.dp.toPx()))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Veg Only", fontSize = 12.sp)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SageGreenLight,
                                    selectedLabelColor = SageGreen,
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF49454F)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filterState.isVegOnly,
                                    borderColor = Color(0xFF79747E),
                                    selectedBorderColor = SageGreenLight,
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("filter_veg_only")
                            )
                        }

                        // Non-Veg Chip
                        item {
                            FilterChip(
                                selected = filterState.isNonVegOnly,
                                onClick = { viewModel.toggleNonVegOnlyFilter() },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Canvas(modifier = Modifier.size(10.dp)) {
                                            drawCircle(color = Color(0xFF410002), radius = 4.dp.toPx())
                                            drawRect(color = Color(0xFF410002), style = Stroke(width = 1.5.dp.toPx()))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Serves Non-Veg", fontSize = 12.sp)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFDAD6),
                                    selectedLabelColor = Color(0xFF410002),
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF49454F)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filterState.isNonVegOnly,
                                    borderColor = Color(0xFF79747E),
                                    selectedBorderColor = Color(0xFFFFDAD6),
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("filter_non_veg")
                            )
                        }

                        // Price Level Multi-selector
                        items(listOf(1, 2, 3, 4)) { level ->
                            val symbol = "$".repeat(level)
                            val isPriceSelected = filterState.selectedPriceLevels.contains(level)
                            FilterChip(
                                selected = isPriceSelected,
                                onClick = { viewModel.togglePriceLevelFilter(level) },
                                label = { Text(symbol, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEADDFF),
                                    selectedLabelColor = Color(0xFF21005D),
                                    containerColor = Color.White,
                                    labelColor = Color(0xFF49454F)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isPriceSelected,
                                    borderColor = Color(0xFF79747E),
                                    selectedBorderColor = Color(0xFFEADDFF),
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("filter_price_$level")
                            )
                        }

                        // Rating Filter Dropdown Trigger
                        item {
                            Box {
                                val isRatingSelected = filterState.ratingMin > 0.0
                                FilterChip(
                                    selected = isRatingSelected,
                                    onClick = { showRatingMenu = true },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (filterState.ratingMin == 0.0) "Rating" else "${filterState.ratingMin}+ ⭐",
                                                fontSize = 12.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFEADDFF),
                                        selectedLabelColor = Color(0xFF21005D),
                                        containerColor = Color.White,
                                        labelColor = Color(0xFF49454F)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isRatingSelected,
                                        borderColor = Color(0xFF79747E),
                                        selectedBorderColor = Color(0xFFEADDFF),
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 1.dp
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("filter_rating")
                                )

                                DropdownMenu(
                                    expanded = showRatingMenu,
                                    onDismissRequest = { showRatingMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Ratings") },
                                        onClick = {
                                            viewModel.setRatingFilter(0.0)
                                            showRatingMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("4.0+ Stars ⭐") },
                                        onClick = {
                                            viewModel.setRatingFilter(4.0)
                                            showRatingMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("4.5+ Stars ⭐") },
                                        onClick = {
                                            viewModel.setRatingFilter(4.5)
                                            showRatingMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("4.8+ Stars ⭐") },
                                        onClick = {
                                            viewModel.setRatingFilter(4.8)
                                            showRatingMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Radar Visualizer & Main List Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (val currentUi = uiState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = TerracottaPrimary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Discovering savory spots...", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    }
                    is UiState.Error -> {
                        // Friendly Warning bar about Fallback
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (currentUi.fallbackActive) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = "Offline Mode",
                                            tint = Color(0xFF856404)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = currentUi.message,
                                            color = Color(0xFF856404),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Error: ${currentUi.message}\nTry setting your Gemini API Key in secrets.",
                                        textAlign = TextAlign.Center,
                                        color = TerracottaPrimary
                                    )
                                }
                            }

                            // Still show recommendations in success list
                            RestaurantsAndRadarView(
                                filteredList = filteredList,
                                favoritesList = favoritesList,
                                selectedRestaurant = selectedRestaurant,
                                onSelectRestaurant = { viewModel.selectRestaurant(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) }
                            )
                        }
                    }
                    else -> {
                        RestaurantsAndRadarView(
                            filteredList = filteredList,
                            favoritesList = favoritesList,
                            selectedRestaurant = selectedRestaurant,
                            onSelectRestaurant = { viewModel.selectRestaurant(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                        )
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet
    if (showBottomSheet && selectedRestaurant != null) {
        val restaurant = selectedRestaurant!!
        val isFav = favoritesList.any { it.id == restaurant.id }

        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
                viewModel.selectRestaurant(null)
            },
            containerColor = Color.White,
            modifier = Modifier.testTag("restaurant_detail_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = restaurant.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = CharcoalDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = restaurant.cuisine,
                                fontWeight = FontWeight.Medium,
                                color = TerracottaPrimary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$".repeat(restaurant.priceLevel),
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Favorite Button in bottom sheet
                    IconButton(
                        onClick = { viewModel.toggleFavorite(restaurant) }
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (isFav) TerracottaPrimary else Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Rating, Veg/Non-veg Status Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GoldRating.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = "Rating", tint = GoldRating, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${restaurant.rating} / 5.0", fontWeight = FontWeight.Bold, color = Color(0xFFD48A00), fontSize = 13.sp)
                        }
                    }

                    if (restaurant.isVegOnly) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SageGreenLight),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Canvas(modifier = Modifier.size(10.dp)) {
                                    drawCircle(color = SageGreen, radius = 4.dp.toPx())
                                    drawRect(color = SageGreen, style = Stroke(width = 1.5.dp.toPx()))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pure Veg 🟢", fontWeight = FontWeight.Bold, color = SageGreen, fontSize = 13.sp)
                            }
                        }
                    } else if (restaurant.servesNonVeg) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TerracottaPrimary.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Canvas(modifier = Modifier.size(10.dp)) {
                                    drawCircle(color = TerracottaPrimary, radius = 4.dp.toPx())
                                    drawRect(color = TerracottaPrimary, style = Stroke(width = 1.5.dp.toPx()))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Serves Non-Veg 🔴", fontWeight = FontWeight.Bold, color = TerracottaPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = restaurant.description,
                    fontSize = 15.sp,
                    color = CharcoalDark,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = Color.LightGray.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(12.dp))

                // Opening Hours & Contact Information
                Text("Operational details:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = restaurant.openingHours ?: "Closed • Opens tomorrow 11:30 AM",
                        fontSize = 14.sp,
                        color = CharcoalDark
                    )
                }

                if (!restaurant.phoneNumber.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${restaurant.phoneNumber}"))
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null, tint = TerracottaPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = restaurant.phoneNumber,
                            fontSize = 14.sp,
                            color = TerracottaPrimary,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = restaurant.address,
                        fontSize = 14.sp,
                        color = CharcoalDark
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Popular Dishes Tags
                if (!restaurant.popularDishes.isNullOrEmpty()) {
                    Text("Popular Culinary Highlights:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        restaurant.popularDishes.forEach { dish ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(dish, fontSize = 12.sp) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation GPS intent button
                Button(
                    onClick = {
                        val geoUri = Uri.parse("geo:${restaurant.latitude},${restaurant.longitude}?q=${Uri.encode(restaurant.name)}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        if (mapIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(mapIntent)
                        } else {
                            // fallback plain geo intent
                            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Get Nav Directions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * Split Screen View: Culinary Discovery Radar on Top (Collapsible) + Custom Restaurant Cards list
 */
@Composable
fun RestaurantsAndRadarView(
    filteredList: List<Restaurant>,
    favoritesList: List<Restaurant>,
    selectedRestaurant: Restaurant?,
    onSelectRestaurant: (Restaurant) -> Unit,
    onToggleFavorite: (Restaurant) -> Unit
) {
    var showRadarByQuery by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sonar toggle header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (showRadarByQuery) "Interactive Discovery Radar" else "Show Discovery Radar",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CharcoalDark
            )
            IconButton(
                onClick = { showRadarByQuery = !showRadarByQuery },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (showRadarByQuery) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle Radar Display",
                    tint = TerracottaPrimary
                )
            }
        }

        AnimatedVisibility(
            visible = showRadarByQuery,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF21005D))
                    .border(1.dp, Color(0xFFEADDFF).copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                CulinaryRadarCanvas(
                    restaurants = filteredList,
                    onSelectRestaurant = onSelectRestaurant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main List of Restaurants
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NoFood,
                        contentDescription = "No results",
                        tint = Color.LightGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No restaurants match your filters.",
                        fontWeight = FontWeight.Bold,
                        color = CharcoalDark,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try clearing filters, selecting another city, or searching coordinates.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("restaurant_lazy_list")
            ) {
                items(filteredList) { restaurant ->
                    val isFav = favoritesList.any { it.id == restaurant.id }
                    RestaurantListItemCard(
                        restaurant = restaurant,
                        isFav = isFav,
                        onSelect = { onSelectRestaurant(restaurant) },
                        onFavoriteClick = { onToggleFavorite(restaurant) }
                    )
                }
            }
        }
    }
}

/**
 * Animated Sonar / Radar Canvas showing local spots
 */
@Composable
fun CulinaryRadarCanvas(
    restaurants: List<Restaurant>,
    onSelectRestaurant: (Restaurant) -> Unit
) {
    // Sonar rotating animation
    val infiniteTransition = rememberInfiniteTransition(label = "Radar Sweep")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Radar Rotation"
    )

    // Pulse animation for coordinates
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Radar Dot Pulse"
    )

    // Fit restaurant coordinate layout mapping
    val mappedPoints = remember(restaurants) {
        if (restaurants.isEmpty()) return@remember emptyList<Pair<Restaurant, Offset>>()

        val minLat = restaurants.minOf { it.latitude }
        val maxLat = restaurants.maxOf { it.latitude }
        val minLon = restaurants.minOf { it.longitude }
        val maxLon = restaurants.maxOf { it.longitude }

        val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
        val lonRange = (maxLon - minLon).coerceAtLeast(0.0001)

        restaurants.map { res ->
            // Normalize to a range [-0.8, 0.8] from the center of radar canvas
            val normX = (((res.longitude - minLon) / lonRange) * 2 - 1) * 0.75
            val normY = -(((res.latitude - minLat) / latRange) * 2 - 1) * 0.75 // Flip y so North is up
            res to Offset(normX.toFloat(), normY.toFloat())
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val center = Offset(widthPx / 2f, heightPx / 2f)
        val maxRadius = min(widthPx, heightPx) / 2f - 16.dp.value

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(restaurants) {
                    detectTapGestures { tapOffset ->
                        // Detect nearest tapped dot on Radar
                        var closestRes: Restaurant? = null
                        var closestDist = 48.dp.toPx() // Click tolerance radius
                        mappedPoints.forEach { (res, offset) ->
                            val dotX = center.x + offset.x * maxRadius
                            val dotY = center.y + offset.y * maxRadius
                            val distance = sqrt((tapOffset.x - dotX).pow(2) + (tapOffset.y - dotY).pow(2))
                            if (distance < closestDist) {
                                closestDist = distance
                                closestRes = res
                            }
                        }
                        closestRes?.let { onSelectRestaurant(it) }
                    }
                }
        ) {
            // Draw sonar concentric circles
            drawCircle(color = Color.DarkGray.copy(alpha = 0.5f), radius = maxRadius, center = center, style = Stroke(1.dp.toPx()))
            drawCircle(color = Color.DarkGray.copy(alpha = 0.3f), radius = maxRadius * 0.66f, center = center, style = Stroke(1.dp.toPx()))
            drawCircle(color = Color.DarkGray.copy(alpha = 0.2f), radius = maxRadius * 0.33f, center = center, style = Stroke(1.dp.toPx()))

            // Crosshairs axes
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.3f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.3f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx()
            )

            // Draw Sweep radar hand (radial slice gradient)
            val sweepRad = Math.toRadians(angle.toDouble()).toFloat()
            val lineEndX = center.x + maxRadius * cos(sweepRad)
            val lineEndY = center.y + maxRadius * sin(sweepRad)
            drawLine(
                color = TerracottaPrimary.copy(alpha = 0.5f),
                start = center,
                end = Offset(lineEndX, lineEndY),
                strokeWidth = 1.5.dp.toPx()
            )

            // Center User Indicator
            drawCircle(color = Color.White, radius = 6.dp.toPx(), center = center)
            drawCircle(color = TerracottaPrimary, radius = 4.dp.toPx(), center = center)

            // Plot mapped nearby restaurants as active dots
            mappedPoints.forEach { (restaurant, offset) ->
                val dotX = center.x + offset.x * maxRadius
                val dotY = center.y + offset.y * maxRadius
                val dotColor = if (restaurant.isVegOnly) SageGreen else TerracottaPrimary

                // Outer halo animating pulse
                drawCircle(
                    color = dotColor.copy(alpha = 0.25f),
                    radius = 12.dp.toPx() * pulseRatio,
                    center = Offset(dotX, dotY)
                )

                // Solid center pin dot
                drawCircle(
                    color = dotColor,
                    radius = 5.dp.toPx(),
                    center = Offset(dotX, dotY)
                )

                // Draw miniature star rating value next to the dot to keep it elegant
                val textOffset = Offset(dotX + 6.dp.toPx(), dotY - 6.dp.toPx())
                // In Canvas, simple drawings look stunning
            }
        }

        // Floating info indicators on radar
        if (restaurants.isNotEmpty()) {
            Text(
                text = "${restaurants.size} restaurants nearby found in sonar sweep",
                color = Color.LightGray.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        } else {
            Text(
                text = "Sonar idle • Enter a search location",
                color = Color.LightGray.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
            )
        }
    }
}

/**
 * Custom Restaurant Card Item
 */
@Composable
fun RestaurantListItemCard(
    restaurant: Restaurant,
    isFav: Boolean,
    onSelect: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(1.dp, Color(0xFFE7E0EC), RoundedCornerShape(24.dp))
            .testTag("restaurant_card_${restaurant.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Food icon/image placeholder matching theme
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (restaurant.isVegOnly) Color(0xFFC4E7CB) else Color(0xFFFFDAD6)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (restaurant.isVegOnly) Icons.Filled.Eco else Icons.Filled.LocalPizza,
                        contentDescription = null,
                        tint = if (restaurant.isVegOnly) Color(0xFF062F11) else Color(0xFF410002),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (restaurant.isVegOnly) "VEG" else "NON-VEG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (restaurant.isVegOnly) Color(0xFF062F11) else Color(0xFF410002)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle Information
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = restaurant.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = CharcoalDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${restaurant.cuisine} • ${"$".repeat(restaurant.priceLevel)}",
                    fontSize = 13.sp,
                    color = Color(0xFF49454F),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Rating",
                        tint = GoldRating,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "${restaurant.rating}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CharcoalDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF79747E),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = restaurant.address,
                        fontSize = 11.sp,
                        color = Color(0xFF49454F),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Action: Favorite Heart trigger
            IconButton(
                onClick = { onFavoriteClick() },
                modifier = Modifier.testTag("favorite_button_${restaurant.id}")
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) TerracottaPrimary else Color(0xFF79747E),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Quick flow row supporting tags layout in Compose
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    horizontalArrangement: Arrangement.HorizontalOrVertical,
    verticalArrangement: Arrangement.HorizontalOrVertical,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        modifier = modifier,
        content = { content() }
    )
}
