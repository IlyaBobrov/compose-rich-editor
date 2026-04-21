package com.mohamedrejeb.richeditor.platform

internal actual val currentPlatform: Platform = Platform.Android

internal actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()