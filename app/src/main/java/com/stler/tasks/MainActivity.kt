package com.stler.tasks

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stler.tasks.ui.auth.AuthScreen
import com.stler.tasks.ui.auth.AuthUiState
import com.stler.tasks.ui.auth.AuthViewModel
import com.stler.tasks.ui.main.MainScreen
import com.stler.tasks.ui.theme.TasksTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Class-level Compose state so [onNewIntent] can update it while the
     * composable tree is already running. A plain `remember { mutableStateOf }` inside
     * setContent only captures the *launch* intent and never sees new intents.
     */
    private var pendingDeepLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink = intent?.data?.takeIf { it.scheme == "stlertasks" }

        setContent {
            TasksTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val authState by authViewModel.uiState.collectAsStateWithLifecycle()

                when (authState) {
                    is AuthUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is AuthUiState.SignedIn -> {
                        MainScreen(
                            onSignOut           = authViewModel::signOut,
                            initialDeepLinkUri  = pendingDeepLink?.toString(),
                            onDeepLinkConsumed  = { pendingDeepLink = null },
                        )
                    }

                    else -> {
                        AuthScreen(viewModel = authViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Update the Compose state — triggers recomposition and re-fires the LaunchedEffect
        pendingDeepLink = intent.data?.takeIf { it.scheme == "stlertasks" }
    }
}
