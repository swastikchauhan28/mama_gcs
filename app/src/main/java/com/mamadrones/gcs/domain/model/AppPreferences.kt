package com.mamadrones.gcs.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }
data class AppPreferences(
    val theme: ThemeMode = ThemeMode.DARK,
    val udpEndpoint: UdpEndpoint? = null
)
