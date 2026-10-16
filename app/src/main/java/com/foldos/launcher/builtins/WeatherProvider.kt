package com.foldos.launcher.builtins

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Sky condition shown by the weather widgets; [label] is the human text, [icon] the glyph. */
enum class WeatherCondition(val label: String, val icon: ImageVector) {
    CLEAR("Sunny", Icons.Outlined.WbSunny),
    PARTLY_CLOUDY("Partly cloudy", Icons.Outlined.WbCloudy),
    CLOUDY("Cloudy", Icons.Outlined.Cloud),
    RAIN("Rain", Icons.Outlined.Opacity),
    STORM("Storms", Icons.Outlined.FlashOn),
    SNOW("Snow", Icons.Outlined.AcUnit),
}

/** One hour of the forecast strip (mock-up `hourly`). [label] is e.g. "10A", "12P". */
data class HourlyForecast(val label: String, val condition: WeatherCondition, val tempF: Int)

/** One day of the forecast list (mock-up `DAILY`). */
data class DailyForecast(val day: String, val condition: WeatherCondition, val highF: Int, val lowF: Int)

/** Everything the Clock, Glance and Weather widgets display. Temperatures are Fahrenheit. */
data class WeatherSnapshot(
    val location: String,
    val tempF: Int,
    val condition: WeatherCondition,
    val highF: Int,
    val lowF: Int,
    val rainChancePercent: Int,
    val windMph: Int,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
) {
    val tempText: String get() = "$tempF°"
}

/**
 * Source of weather data for the built-in widgets. The launcher ships only [FakeWeatherProvider]
 * (no network); a real provider (Open-Meteo, Samsung Weather content provider, ...) implements
 * this and is passed to `FoldOsBuiltInWidgets`.
 */
interface WeatherProvider {
    /** Latest snapshot; null while nothing is known yet. */
    val weather: Flow<WeatherSnapshot?>
}

/** Static Austin forecast matching the mock-up (64°, partly cloudy, H 71° L 55°, rain 10%). */
class FakeWeatherProvider : WeatherProvider {

    private val state = MutableStateFlow<WeatherSnapshot?>(AUSTIN)

    override val weather: StateFlow<WeatherSnapshot?> = state

    companion object {
        val AUSTIN: WeatherSnapshot = WeatherSnapshot(
            location = "Austin",
            tempF = 64,
            condition = WeatherCondition.PARTLY_CLOUDY,
            highF = 71,
            lowF = 55,
            rainChancePercent = 10,
            windMph = 8,
            hourly = listOf(
                HourlyForecast("10A", WeatherCondition.PARTLY_CLOUDY, 66),
                HourlyForecast("11A", WeatherCondition.CLEAR, 68),
                HourlyForecast("12P", WeatherCondition.CLEAR, 70),
                HourlyForecast("1P", WeatherCondition.CLEAR, 71),
                HourlyForecast("2P", WeatherCondition.PARTLY_CLOUDY, 71),
                HourlyForecast("3P", WeatherCondition.CLOUDY, 70),
                HourlyForecast("4P", WeatherCondition.CLOUDY, 68),
                HourlyForecast("5P", WeatherCondition.RAIN, 65),
            ),
            daily = listOf(
                DailyForecast("Sat", WeatherCondition.CLEAR, 70, 55),
                DailyForecast("Sun", WeatherCondition.RAIN, 66, 52),
                DailyForecast("Mon", WeatherCondition.PARTLY_CLOUDY, 68, 54),
                DailyForecast("Tue", WeatherCondition.CLEAR, 72, 56),
                DailyForecast("Wed", WeatherCondition.CLOUDY, 69, 55),
                DailyForecast("Thu", WeatherCondition.CLEAR, 74, 58),
            ),
        )
    }
}
