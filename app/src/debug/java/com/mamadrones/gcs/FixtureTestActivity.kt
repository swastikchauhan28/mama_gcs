package com.mamadrones.gcs

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Empty Hilt host for synthetic Compose fixtures; never starts MainActivity's transports.
 * Debug only. Fixed orientation avoids recreating test-owned setContent during sensor changes;
 * real MainActivity portrait/landscape/recreation coverage lives in separate acceptance tests.
 */
@AndroidEntryPoint
class FixtureTestActivity : ComponentActivity()
