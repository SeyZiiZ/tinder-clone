package com.apiguave.tinderclonecompose.chat

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.apiguave.tinderclonecompose.R

@Composable
fun ChatScreen(
    onArrowBackPressed: () -> Unit,
    viewModel: ChatViewModel
) {
    val chatViewState by viewModel.viewState.collectAsState()
    
    chatViewState?.let { state ->
        LaunchedEffect(state.match.id) {
            Log.d("ChatScreen", "Collecting messages for match: ${state.match.id}")
            viewModel.startCollectingMessages(state.match.id)
        }
        
        val messages by viewModel.messages.collectAsState()
        Log.d("ChatScreen", "Current messages: ${messages.size}")
        
        ChatView(
            state = state,
            messages = messages,
            onArrowBackPressed = onArrowBackPressed,
            sendMessage = viewModel::sendMessage,
        )
    } ?: run {
        Text(
            modifier = Modifier.fillMaxSize(),
            textAlign = TextAlign.Center,
            text = stringResource(id = R.string.no_match_value_passed)
        )
    }
}