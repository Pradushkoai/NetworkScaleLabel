package com.pradushkoai.networkscalelabel.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pradushkoai.networkscalelabel.presentation.scales.ScalesScreen
import com.pradushkoai.networkscalelabel.presentation.theme.NetworkScaleLabelTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Единственная активити приложения: хостит экран весов.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkScaleLabelTheme {
                ScalesScreen()
            }
        }
    }
}
