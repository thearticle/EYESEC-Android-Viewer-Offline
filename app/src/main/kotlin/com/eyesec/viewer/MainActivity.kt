package com.eyesec.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eyesec.viewer.data.CameraSettingsRepository
import com.eyesec.viewer.ui.player.PlayerScreen
import com.eyesec.viewer.ui.player.PlayerViewModel
import com.eyesec.viewer.ui.theme.EyesecViewerTheme

class MainActivity : ComponentActivity() {

  private val playerViewModel: PlayerViewModel by viewModels {
    PlayerViewModel.Factory(
      applicationContext,
      CameraSettingsRepository(applicationContext)
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      EyesecViewerTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = Color(0xFF05070B)
        ) {
          PlayerScreen(viewModel = playerViewModel)
        }
      }
    }
  }
}

