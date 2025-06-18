package com.example.eventix

import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface NominatimAPI {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("countrycodes") countryCodes: String = "pt",
        @Query("bounded") bounded: Int = 1,
        @Query("viewbox") viewbox: String = "-9.7,42.2,-6.0,36.8"
    ): Response<List<NominatimResponse>>

    @GET("reverse")
    suspend fun reverse(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1
    ): Response<NominatimResponse>
}

interface OverpassAPI {
    @GET("interpreter")
    suspend fun searchPOIs(
        @Query("data") query: String
    ): Response<OverpassResponse>
}

object LocationAPIClient {
    private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"
    private const val OVERPASS_BASE_URL = "https://overpass-api.de/api/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "EventixApp/1.0 (Android)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()

    val nominatim: NominatimAPI by lazy {
        Retrofit.Builder()
            .baseUrl(NOMINATIM_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimAPI::class.java)
    }

    val overpass: OverpassAPI by lazy {
        Retrofit.Builder()
            .baseUrl(OVERPASS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OverpassAPI::class.java)
    }

    fun buildOverpassQuery(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        categoria: CategoriaLocal
    ): String {
        val radiusMeters = (radiusKm * 1000).toInt()

        return when (categoria) {
            CategoriaLocal.TODOS -> {
                """
                [out:json][timeout:25];
                (
                  node["amenity"~"^(restaurant|cafe|bar)$"](around:$radiusMeters,$latitude,$longitude);
                  node["tourism"~"^(hotel|attraction)$"](around:$radiusMeters,$latitude,$longitude);
                  node["leisure"~"^(park|garden)$"](around:$radiusMeters,$latitude,$longitude);
                  way["amenity"~"^(restaurant|cafe|bar)$"](around:$radiusMeters,$latitude,$longitude);
                  way["tourism"~"^(hotel|attraction)$"](around:$radiusMeters,$latitude,$longitude);
                  way["leisure"~"^(park|garden)$"](around:$radiusMeters,$latitude,$longitude);
                );
                out center;
                """.trimIndent()
            }
            CategoriaLocal.RESTAURANTE -> {
                """
                [out:json][timeout:25];
                (
                  node["amenity"="restaurant"](around:$radiusMeters,$latitude,$longitude);
                  way["amenity"="restaurant"](around:$radiusMeters,$latitude,$longitude);
                );
                out center;
                """.trimIndent()
            }
            CategoriaLocal.HOTEL -> {
                """
                [out:json][timeout:25];
                (
                  node["tourism"="hotel"](around:$radiusMeters,$latitude,$longitude);
                  way["tourism"="hotel"](around:$radiusMeters,$latitude,$longitude);
                );
                out center;
                """.trimIndent()
            }
            CategoriaLocal.CAFE -> {
                """
                [out:json][timeout:25];
                (
                  node["amenity"="cafe"](around:$radiusMeters,$latitude,$longitude);
                  way["amenity"="cafe"](around:$radiusMeters,$latitude,$longitude);
                );
                out center;
                """.trimIndent()
            }
            CategoriaLocal.BAR -> {
                """
                [out:json][timeout:25];
                (
                  node["amenity"="bar"](around:$radiusMeters,$latitude,$longitude);
                  way["amenity"="bar"](around:$radiusMeters,$latitude,$longitude);
                );
                out center;
                """.trimIndent()
            }
            CategoriaLocal.PARQUE -> {
                """
                [out:json][timeout:25];
                (
                  node["leisure"="park"](around:$radiusMeters,$latitude,$longitude);
                  way["leisure"="park"](around:$radiusMeters,$latitude,$longitude);
                  node["leisure"="garden"](around:$radiusMeters,$latitude,$longitude);
                  way["leisure"="garden"](around:$radiusMeters,$latitude,$longitude);
                );
                out center;
                """.trimIndent()
            }
        }
    }
}

object LocationUtils {
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }
}