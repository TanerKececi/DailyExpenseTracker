package com.example.dailyexpensetracker.ui.common.util

import androidx.annotation.DrawableRes
import com.example.dailyexpensetracker.R

/** Maps a Category's logical iconName (stored in Room, kept resource-free) to a drawable. */
object CategoryIconMapper {
    @DrawableRes
    fun iconFor(iconName: String): Int = when (iconName) {
        "grocery" -> R.drawable.ic_cat_grocery_24
        "food" -> R.drawable.ic_cat_food_24
        "clothes" -> R.drawable.ic_cat_clothes_24
        "hotel" -> R.drawable.ic_cat_hotel_24
        "medicine" -> R.drawable.ic_cat_medicine_24
        "fuel" -> R.drawable.ic_cat_fuel_24
        "gifts" -> R.drawable.ic_cat_gifts_24
        "travel" -> R.drawable.ic_cat_travel_24
        "kids" -> R.drawable.ic_cat_kids_24
        "salary" -> R.drawable.ic_arrow_up_24
        else -> R.drawable.ic_grid_24
    }
}
