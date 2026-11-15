package com.eyesec.viewer.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eyesec.viewer.data.CameraConfig
import com.eyesec.viewer.data.CameraSettingsRepository
import com.eyesec.viewer.data.StreamQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.net.InetSocketAddress
import java.net.Socket

enum class PlayerState {
    IDLE,
    CONNECTING,
    PLAYING,
    BUFFERING,
    ERROR
}

data class PlayerUiState(
    val config: CameraConfig = CameraConfig(),
    val playerState: PlayerState = PlayerState.IDLE,
    val errorMessage: String? = null,
    val pingMs: Long? = null,
    val isControlsVisible: Boolean = true,
    val isFullscreen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false
)

class PlayerViewModel(
    private val context: Context,
    private val repository: CameraSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayoutRef: VLCVideoLayout? = null
    private var pingJob: Job? = null

    init {
        val loaded = repository.loadConfig()
        _uiState.value = _uiState.value.copy(
            config = loaded,
            isSettingsSheetOpen = loaded.ip.isBlank()
        )
    }

    private fun initLibVLC(): LibVLC {
        if (libVLC == null) {
            val options = arrayListOf(
                "--network-caching=300",
                "--live-caching=300",
                "--rtsp-tcp",                // TCP RTSP interleaved
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--avcodec-hw=any",
                "-vvv"
            )
            libVLC = LibVLC(context, options)
        }
        return libVLC!!
    }

    fun attachVideoLayout(layout: VLCVideoLayout) {
        videoLayoutRef = layout
        if (mediaPlayer == null) {
            val vlc = initLibVLC()
            mediaPlayer = MediaPlayer(vlc).apply {
                attachViews(layout, null, false, false)
                setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Buffering -> {
                            if (_uiState.value.playerState != PlayerState.PLAYING) {
                                _uiState.value = _uiState.value.copy(
                                    playerState = PlayerState.BUFFERING,
                                    errorMessage = null
                                )
                            }
                        }
                        MediaPlayer.Event.Playing -> {
                            _uiState.value = _uiState.value.copy(
                                playerState = PlayerState.PLAYING,
                                errorMessage = null
                            )
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            _uiState.value = _uiState.value.copy(
                                playerState = PlayerState.ERROR,
                                errorMessage = "Stream error: check camera credentials or RTSP path"
                            )
                        }
                        MediaPlayer.Event.Stopped -> {
                            if (_uiState.value.playerState != PlayerState.ERROR) {
                                _uiState.value = _uiState.value.copy(playerState = PlayerState.IDLE)
                            }
                        }
                    }
                }
            }
        } else {
            mediaPlayer?.detachViews()
            mediaPlayer?.attachViews(layout, null, false, false)
        }

        applyAudioVolume()
        if (_uiState.value.config.ip.isNotBlank() && _uiState.value.playerState == PlayerState.IDLE) {
            connect()
        }
    }

    fun detachVideoLayout() {
        mediaPlayer?.detachViews()
        videoLayoutRef = null
    }

    fun updateConfig(newConfig: CameraConfig) {
        _uiState.value = _uiState.value.copy(config = newConfig)
        repository.saveConfig(newConfig)
        connect()
    }

    fun setQuality(quality: StreamQuality) {
        val updated = _uiState.value.config.copy(quality = quality)
        _uiState.value = _uiState.value.copy(config = updated)
        repository.saveConfig(updated)
        playStream()
    }

    fun toggleAudio() {
        val newAudioState = !_uiState.value.config.audioEnabled
        val updated = _uiState.value.config.copy(audioEnabled = newAudioState)
        _uiState.value = _uiState.value.copy(config = updated)
        repository.saveConfig(updated)
        applyAudioVolume()
    }

    fun setVolume(volume: Float) {
        val updated = _uiState.value.config.copy(volume = volume)
        _uiState.value = _uiState.value.copy(config = updated)
        repository.saveConfig(updated)
        applyAudioVolume()
    }

    private fun applyAudioVolume() {
        val cfg = _uiState.value.config
        val volInt = if (cfg.audioEnabled) (cfg.volume * 100).toInt().coerceIn(0, 100) else 0
        mediaPlayer?.volume = volInt
    }

    fun toggleControlsVisibility() {
        _uiState.value = _uiState.value.copy(isControlsVisible = !_uiState.value.isControlsVisible)
    }

    fun setSettingsSheetOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsSheetOpen = open)
    }

    fun connect() {
        val config = _uiState.value.config
        if (config.ip.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isSettingsSheetOpen = true,
                errorMessage = "Please enter Camera IP address"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            playerState = PlayerState.CONNECTING,
            errorMessage = null,
            isSettingsSheetOpen = false
        )

        startPingProbe(config.ip, config.port)
        playStream()
    }

    private fun playStream() {
        val url = _uiState.value.config.buildRtspUrl()
        if (url.isBlank()) return

        val player = mediaPlayer ?: return
        val vlc = initLibVLC()

        try {
            player.stop()
            val media = Media(vlc, Uri.parse(url)).apply {
                setHWDecoderEnabled(true, false)
                addOption(":network-caching=300")
                addOption(":rtsp-tcp")
                addOption(":no-drop-late-frames")
            }
            player.media = media
            media.release()
            applyAudioVolume()
            player.play()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                playerState = PlayerState.ERROR,
                errorMessage = e.localizedMessage ?: "Failed to start RTSP stream"
            )
        }
    }

    private fun startPingProbe(ip: String, port: Int) {
        pingJob?.cancel()
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val ping = try {
                    val t0 = System.currentTimeMillis()
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, port), 1200)
                    }
                    System.currentTimeMillis() - t0
                } catch (e: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(pingMs = ping)
                }
                delay(3000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pingJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.detachViews()
            mediaPlayer?.release()
            libVLC?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        libVLC = null
    }

    class Factory(
        private val context: Context,
        private val repository: CameraSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(context, repository) as T
        }
    }
}
