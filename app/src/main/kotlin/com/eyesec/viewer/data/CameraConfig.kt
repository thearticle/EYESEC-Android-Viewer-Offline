package com.eyesec.viewer.data

import android.content.Context
import android.content.SharedPreferences
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class CameraConfig(
    val ip: String = "",
    val port: Int = 554,
    val username: String = "admin",
    val password: String = "",
    val quality: StreamQuality = StreamQuality.MAJOR,
    val audioEnabled: Boolean = false,
    val volume: Float = 0.6f
) {
    fun buildRtspUrl(): String {
        if (ip.isBlank()) return ""
        val path = if (quality == StreamQuality.MAJOR) "h264major" else "h264minor"
        val host = if (ip.contains(":")) "[$ip]" else ip
        val encodedUser = URLEncoder.encode(username.ifBlank { "admin" }, StandardCharsets.UTF_8.name())
        val encodedPass = URLEncoder.encode(password, StandardCharsets.UTF_8.name())
        
        return if (password.isNotEmpty()) {
            "rtsp://$encodedUser:$encodedPass@$host:$port/1/$path"
        } else {
            "rtsp://$host:$port/1/$path"
        }
    }
}

enum class StreamQuality(val label: String, val pathName: String) {
    MAJOR("HIGH", "h264major"),
    MINOR("LOW", "h264minor")
}

class CameraSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("eyesec_prefs", Context.MODE_PRIVATE)

    fun loadConfig(): CameraConfig {
        return CameraConfig(
            ip = prefs.getString(KEY_IP, "") ?: "",
            port = prefs.getInt(KEY_PORT, 554),
            username = prefs.getString(KEY_USER, "admin") ?: "admin",
            password = prefs.getString(KEY_PASS, "") ?: "",
            quality = if (prefs.getString(KEY_QUALITY, "MAJOR") == "MINOR") StreamQuality.MINOR else StreamQuality.MAJOR,
            audioEnabled = prefs.getBoolean(KEY_AUDIO, false),
            volume = prefs.getFloat(KEY_VOLUME, 0.6f)
        )
    }

    fun saveConfig(config: CameraConfig) {
        prefs.edit()
            .putString(KEY_IP, config.ip)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_USER, config.username)
            .putString(KEY_PASS, config.password)
            .putString(KEY_QUALITY, config.quality.name)
            .putBoolean(KEY_AUDIO, config.audioEnabled)
            .putFloat(KEY_VOLUME, config.volume)
            .apply()
    }

    companion object {
        private const val KEY_IP = "camera_ip"
        private const val KEY_PORT = "camera_port"
        private const val KEY_USER = "camera_username"
        private const val KEY_PASS = "camera_password"
        private const val KEY_QUALITY = "stream_quality"
        private const val KEY_AUDIO = "audio_enabled"
        private const val KEY_VOLUME = "audio_volume"
    }
}
