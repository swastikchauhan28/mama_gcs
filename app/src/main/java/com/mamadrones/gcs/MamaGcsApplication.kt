package com.mamadrones.gcs

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application-level Hilt entry point; feature bindings are introduced with their domains. */
@HiltAndroidApp
class MamaGcsApplication : Application()
