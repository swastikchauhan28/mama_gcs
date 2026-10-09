package com.mamadrones.gcs.data.local.datastore

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.mamadrones.gcs.domain.repository.AccessRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Android wiring for the same account service exercised by the local regression tests. */
@Singleton
class LocalAccessRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AccessRepository by LocalAccountService(
    storage = EncryptedAccessStorage(context),
    clock = object : AccessClock {
        override fun elapsedMillis() = SystemClock.elapsedRealtime()
        override fun epochMillis() = System.currentTimeMillis()
        override fun bootCount() = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)
    },
    passwords = PbkdfPasswordHasher(),
    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
)
