// PortedFrom: MC1/ContentView.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: Edge-to-edge Activity and retained screen navigation; no process/radio graph assembly.
package com.meshcoreone.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.meshcoreone.android.app.navigation.NativeNavigationShell
import com.meshcoreone.android.app.navigation.NavigationCoordinator
import com.meshcoreone.android.app.navigation.NavigationFailure
import com.meshcoreone.android.app.navigation.NavigationSavedState
import com.meshcoreone.android.app.navigation.NavigationState
import com.meshcoreone.android.core.designsystem.MeshCoreTheme

class NavigationHostViewModel : ViewModel() {
    val navigation = NavigationCoordinator()
    var initialized = false
}

open class MainActivity : ComponentActivity() {
    private lateinit var host: NavigationHostViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        host = ViewModelProvider(this)[NavigationHostViewModel::class.java]
        if (!host.initialized) {
            if (savedInstanceState?.containsKey(NavigationSavedState.KEY) == true) {
                val saved = savedInstanceState.getStringArrayList(NavigationSavedState.KEY)
                host.navigation.restoreHostState(
                    if (saved != null) NavigationSavedState.restore(saved)
                    else NavigationState(failure = NavigationFailure.InvalidSavedState),
                )
            }
            host.initialized = true
        }
        setContent {
            MeshCoreTheme {
                NativeNavigationShell(host.navigation)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(NavigationSavedState.KEY, NavigationSavedState.encode(host.navigation.state.value))
        super.onSaveInstanceState(outState)
    }
}
