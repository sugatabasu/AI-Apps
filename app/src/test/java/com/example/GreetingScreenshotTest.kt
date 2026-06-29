package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.Restaurant
import com.example.ui.screens.RestaurantListItemCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val dummy = Restaurant(
      id = "1",
      name = "The Saffron Garden",
      cuisine = "Indian Spice",
      address = "12 Main St, New York",
      rating = 4.8,
      priceLevel = 2,
      isVegOnly = true,
      servesNonVeg = false,
      latitude = 0.0,
      longitude = 0.0,
      description = "A warm pure-veg restaurant serving premium Indian delicacies.",
      popularDishes = listOf("Paneer Tikka", "Samosas"),
      phoneNumber = "+1 555-0100",
      openingHours = "11:00 AM - 10:00 PM"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        RestaurantListItemCard(
          restaurant = dummy,
          isFav = true,
          onSelect = {},
          onFavoriteClick = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
