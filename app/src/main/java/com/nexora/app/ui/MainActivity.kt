package com.nexora.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainVM = viewModel()
            NexoraTheme(vm.theme) {
                CompositionLocalProvider(
                    LocalLang provides vm.lang,
                    LocalLayoutDirection provides if (vm.lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    NexoraRoot(vm)
                }
            }
        }
    }
}
