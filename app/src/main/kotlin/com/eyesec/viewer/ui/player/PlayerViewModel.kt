package com.eyesec.viewer.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alexvas.rtsp.RtspClient
import com.alexvas.rtsp.widget.RtspStatusListener
import com.alexvas.rtsp.widget.RtspSurfaceView
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
import java.net.InetSocketAddress
import java.net.Socket

enum class PlayerState {
    IDLE,
    CONNECTING,
    PLAYING,
    ERROR
}

enum class ActiveModal {
    NONE,
    SETTINGS,
    CREDITS,
    HELP
}

data class PlayerUiState(
    val config: CameraConfig = CameraConfig(),
    val playerState: PlayerState = PlayerState.IDLE,
    val errorMessage: String? = null,
    val pingMs: Long? = null,
    val isControlsVisible: Boolean = true,
    val isFullscreen: Boolean = false,
    val activeModal: ActiveModal = ActiveModal.NONE,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
)

class PlayerViewModel(
    private val context: Context,
    private val repository: CameraSettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var surfaceViewRef: RtspSurfaceView? = null
    private var pingJob: Job? = null
    private var restartStreamJob: Job? = null

    init {
        val loaded = repository.loadConfig()
        _uiState.value = _uiState.value.copy(
            config = loaded,
            activeModal = if (loaded.ip.isBlank()) ActiveModal.SETTINGS else ActiveModal.NONE
        )
    }

    fun attachSurfaceView(surfaceView: RtspSurfaceView) {
        surfaceViewRef = surfaceView
        surfaceView.setStatusListener(object : RtspStatusListener {
            override fun onRtspStatusConnecting() {
                // Keep playing if already playing so screen doesn't flicker/buffering modal doesn't flash
                if (_uiState.value.playerState != PlayerState.PLAYING) {
                    _uiState.value = _uiState.value.copy(
                        playerState = PlayerState.CONNECTING,
                        errorMessage = null
                    )
                }
            }

            override fun onRtspStatusConnected() {
                // No popup during stream handshake
            }

            override fun onRtspStatusDisconnected() {
                if (_uiState.value.playerState != PlayerState.ERROR) {
                    _uiState.value = _uiState.value.copy(playerState = PlayerState.IDLE)
                }
            }

            override fun onRtspStatusFailed(message: String?) {
                _uiState.value = _uiState.value.copy(
                    playerState = PlayerState.ERROR,
                    errorMessage = message ?: "Failed to connect to camera"
                )
            }

            override fun onRtspFirstFrameRendered() {
                _uiState.value = _uiState.value.copy(
                    playerState = PlayerState.PLAYING,
                    errorMessage = null
                )
            }

            override fun onRtspFrameSizeChanged(width: Int, height: Int) {
                _uiState.value = _uiState.value.copy(
                    videoWidth = width,
                    videoHeight = height
                )
            }
        })

        if (_uiState.value.config.ip.isNotBlank()) {
            connect()
        }
    }

    fun detachSurfaceView() {
        restartStreamJob?.cancel()
        surfaceViewRef?.stop()
        surfaceViewRef = null
    }

    fun updateConfig(newConfig: CameraConfig) {
        _uiState.value = _uiState.value.copy(config = newConfig)
        repository.saveConfig(newConfig)
        connect()
    }

    fun setQuality(quality: StreamQuality) {
        if (_uiState.value.config.quality == quality) return
        val updated = _uiState.value.config.copy(quality = quality)
        _uiState.value = _uiState.value.copy(config = updated)
        repository.saveConfig(updated)
        playStream()
    }

    fun toggleStretchMode() {
        val newStretch = !_uiState.value.config.stretchToFit
        val updated = _uiState.value.config.copy(stretchToFit = newStretch)
        _uiState.value = _uiState.value.copy(config = updated)
        repository.saveConfig(updated)
    }

    fun toggleControlsVisibility() {
        _uiState.value = _uiState.value.copy(isControlsVisible = !_uiState.value.isControlsVisible)
    }

    fun openModal(modal: ActiveModal) {
        _uiState.value = _uiState.value.copy(
            activeModal = if (_uiState.value.activeModal == modal) ActiveModal.NONE else modal
        )
    }

    fun closeModal() {
        _uiState.value = _uiState.value.copy(activeModal = ActiveModal.NONE)
    }

    fun connect() {
        val config = _uiState.value.config
        if (config.ip.isBlank()) {
            _uiState.value = _uiState.value.copy(
                activeModal = ActiveModal.SETTINGS,
                errorMessage = "Please enter Camera IP address"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            playerState = PlayerState.CONNECTING,
            errorMessage = null,
            activeModal = ActiveModal.NONE
        )

        startPingProbe(config.ip, config.port)
        playStream()
    }

    private fun playStream() {
        val view = surfaceViewRef ?: return
        val config = _uiState.value.config
        if (config.ip.isBlank()) return

        restartStreamJob?.cancel()
        restartStreamJob = viewModelScope.launch(Dispatchers.Main) {
            // Stop old RTSP stream cleanly first
            try {
                if (view.isStarted()) {
                    view.stop()
                }
            } catch (ignored: Exception) {}

            // Grace period: allow hardware MediaCodec and sockets to unbind cleanly
            delay(250)

            val path = if (config.quality == StreamQuality.MAJOR) "1/h264major" else "1/h264minor"
            val host = if (config.ip.contains(":")) "[${config.ip}]" else config.ip
            val uri = Uri.parse("rtsp://$host:${config.port}/$path")

            view.init(
                uri = uri,
                username = config.username.ifBlank { "admin" },
                password = config.password,
                socketTimeout = 3000,
                transport = RtspClient.Transport.TCP
            )

            // Start video only (requestAudio = false)
            view.start(
                requestVideo = true,
                requestAudio = false
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
        restartStreamJob?.cancel()
        try {
            surfaceViewRef?.stop()
        } catch (ignored: Exception) {}
        surfaceViewRef = null
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
