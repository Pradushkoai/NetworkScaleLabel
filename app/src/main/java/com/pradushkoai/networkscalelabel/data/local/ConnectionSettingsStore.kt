package com.pradushkoai.networkscalelabel.data.local

import android.content.Context
import com.pradushkoai.networkscalelabel.domain.model.ScaleConnectionConfig
import com.pradushkoai.networkscalelabel.domain.repository.ConnectionSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранение параметров подключения в SharedPreferences.
 */
@Singleton
class ConnectionSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectionSettingsRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): ScaleConnectionConfig = ScaleConnectionConfig(
        host = prefs.getString(KEY_HOST, null) ?: ScaleConnectionConfig.DEFAULT_HOST,
        udpPort = prefs.getInt(KEY_PORT, ScaleConnectionConfig.DEFAULT_UDP_PORT),
        password = prefs.getString(KEY_PASSWORD, null) ?: ScaleConnectionConfig.DEFAULT_PASSWORD,
    )

    override fun save(config: ScaleConnectionConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_PORT, config.udpPort)
            .putString(KEY_PASSWORD, config.password.trim())
            .apply()
    }

    private companion object {
        const val PREFS_NAME: String = "network_scale_label_prefs"
        const val KEY_HOST: String = "host"
        const val KEY_PORT: String = "udp_port"
        const val KEY_PASSWORD: String = "password"
    }
}
