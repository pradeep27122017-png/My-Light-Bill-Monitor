package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.model.WeatherSnapshot
import com.example.data.model.WeatherStatus
import com.example.data.model.WeatherUnavailableReason
import kotlinx.coroutines.CancellationException

class WeatherRepository(
    private val context: Context,
    private val locationService: LocationService = LocationService(context),
    private val forecastApi: OpenMeteoForecastApi = OpenMeteoClient.forecastApi,
    private val geocodingApi: OpenMeteoGeocodingApi = OpenMeteoClient.geocodingApi
) {

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun fetchWeather(
        manualLatitude: Double? = null,
        manualLongitude: Double? = null,
        manualLocationName: String? = null,
        preferGps: Boolean = true
    ): WeatherStatus {
        if (!isOnline()) {
            return WeatherStatus.Unavailable(
                WeatherUnavailableReason.NO_INTERNET,
                "Offline — readings still save locally and sync automatically."
            )
        }

        var lat = manualLatitude
        var lon = manualLongitude
        var label = manualLocationName

        if (preferGps) {
            when (val locResult = locationService.getCurrentCoordinates()) {
                is DeviceLocationResult.Success -> {
                    lat = locResult.latitude
                    lon = locResult.longitude
                    label = locResult.locationLabel
                }
                DeviceLocationResult.PermissionDenied -> {
                    if (lat == null || lon == null) {
                        return WeatherStatus.Unavailable(
                            WeatherUnavailableReason.PERMISSION_DENIED,
                            "Location permission denied. Grant permission or set a weather city in Settings."
                        )
                    }
                }
                DeviceLocationResult.GpsDisabled -> {
                    if (lat == null || lon == null) {
                        return WeatherStatus.Unavailable(
                            WeatherUnavailableReason.GPS_DISABLED,
                            "Device GPS is disabled. Enable location or set a weather city in Settings."
                        )
                    }
                }
                DeviceLocationResult.Unavailable -> {
                    if (lat == null || lon == null) {
                        return WeatherStatus.Unavailable(
                            WeatherUnavailableReason.GPS_DISABLED,
                            "Unable to obtain GPS fix. Set a fallback weather city in Settings."
                        )
                    }
                }
            }
        }

        if (lat == null || lon == null) {
            return WeatherStatus.Unavailable(
                WeatherUnavailableReason.PERMISSION_DENIED,
                "No weather location configured. Enable GPS or pick a city in Settings."
            )
        }

        return try {
            val response = forecastApi.getCurrentWeather(latitude = lat, longitude = lon)
            val current = response.current
                ?: return WeatherStatus.Unavailable(
                    WeatherUnavailableReason.API_FAILURE,
                    "Weather data unavailable from Open-Meteo."
                )

            val temp = current.temperature2m
                ?: return WeatherStatus.Unavailable(
                    WeatherUnavailableReason.API_FAILURE,
                    "Missing temperature in weather response."
                )

            val code = current.weatherCode ?: 0
            val snapshot = WeatherSnapshot(
                temperatureC = temp,
                condition = decodeWmoWeatherCode(code),
                humidityPercent = current.relativeHumidity2m ?: 0,
                windSpeedKmh = current.windSpeed10m ?: 0.0,
                rainMm = current.rain,
                weatherCode = code,
                locationName = label ?: "${"%.2f".format(lat)}, ${"%.2f".format(lon)}",
                latitude = lat,
                longitude = lon
            )
            WeatherStatus.Ready(snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WeatherStatus.Unavailable(
                WeatherUnavailableReason.API_FAILURE,
                "Weather service error: ${e.localizedMessage ?: "Unable to reach Open-Meteo"}"
            )
        }
    }

    suspend fun searchLocations(query: String): Result<List<GeocodingResultDto>> {
        if (query.trim().length < 2) return Result.success(emptyList())
        if (!isOnline()) return Result.failure(IllegalStateException("Offline — cannot search cities"))
        return try {
            val res = geocodingApi.searchCity(query.trim())
            Result.success(res.results.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        fun decodeWmoWeatherCode(code: Int): String = when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45, 48 -> "Foggy"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61, 63 -> "Rain"
            65 -> "Heavy Rain"
            66, 67 -> "Freezing Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95 -> "Thunderstorm"
            96, 99 -> "Thunderstorm & Hail"
            else -> "Variable Conditions"
        }
    }
}
