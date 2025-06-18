package com.example.eventix

import com.google.gson.annotations.SerializedName

data class LocalSelecionado(
    val id: String = "",
    val nome: String = "",
    val categoria: String = "",
    val endereco: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val telefone: String = "",
    val website: String = "",
    val avaliacao: Float = 0f,
    val distancia: Double = 0.0
)

data class NominatimResponse(
    @SerializedName("place_id") val placeId: Long,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("lat") val latitude: String,
    @SerializedName("lon") val longitude: String,
    @SerializedName("address") val address: NominatimAddress?
)

data class NominatimAddress(
    @SerializedName("house_number") val houseNumber: String?,
    @SerializedName("road") val road: String?,
    @SerializedName("city") val city: String?,
    @SerializedName("postcode") val postcode: String?,
    @SerializedName("country") val country: String?
)

data class OverpassResponse(
    @SerializedName("elements") val elements: List<OverpassElement>
)

data class OverpassElement(
    @SerializedName("id") val id: Long,
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lon") val longitude: Double,
    @SerializedName("tags") val tags: Map<String, String>?,
    @SerializedName("center") val center: OverpassCenter?
)

data class OverpassCenter(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double
)

enum class CategoriaLocal(val valor: String, val nomeExibicao: String, val overpassQuery: String) {
    TODOS("todos", "Todos", ""),
    RESTAURANTE("restaurante", "Restaurantes", "amenity=restaurant"),
    HOTEL("hotel", "Hotéis", "tourism=hotel"),
    CAFE("cafe", "Cafés", "amenity=cafe"),
    BAR("bar", "Bares", "amenity=bar"),
    PARQUE("parque", "Parques", "leisure=park")
}