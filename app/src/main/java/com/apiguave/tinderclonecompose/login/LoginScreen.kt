package com.apiguave.tinderclonecompose.login

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import org.koin.androidx.compose.get
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onNavigateToSignUp: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val signInClient: GoogleSignInClient = get()
    val loginViewModel: LoginViewModel = koinViewModel()
    val uiState by loginViewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginViewState.SignedIn -> {
                onNavigateToHome()
            }
            is LoginViewState.Error -> {
                // Réinitialiser l'état pour permettre une nouvelle tentative
                loginViewModel.checkLoginState()
            }
            else -> {}
        }
    }

    val startForResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = loginViewModel::signIn
    )

    LoginView(
        uiState = uiState,
        onNavigateToSignUp = onNavigateToSignUp,
        onSignInClicked = {
            startForResult.launch(signInClient.signInIntent)
        }
    )
}