package com.apiguave.tinderclonecompose.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apiguave.tinderclonecompose.model.MatchState
import com.apiguave.tinderclonecompose.model.ProfilePictureState
import com.apiguave.tinderclonedomain.usecase.GetMessagesUseCase
import com.apiguave.tinderclonedomain.usecase.GetPictureUseCase
import com.apiguave.tinderclonedomain.usecase.SendMessageUseCase
import com.apiguave.tinderclonedomain.message.Message
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getPictureUseCase: GetPictureUseCase
): ViewModel() {
    private val _viewState = MutableStateFlow<MatchState?>(null)
    val viewState = _viewState.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    fun startCollectingMessages(matchId: String) {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Starting to collect messages for match: $matchId")
                getMessagesUseCase(matchId)
                    .catch { e -> 
                        Log.e("ChatViewModel", "Error collecting messages", e)
                    }
                    .collect { messagesList ->
                        Log.d("ChatViewModel", "Received ${messagesList.size} messages")
                        _messages.value = messagesList
                    }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in startCollectingMessages", e)
            }
        }
    }

    fun sendMessage(text: String) {
        val matchId = _viewState.value?.match?.id ?: return
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Sending message: $text to match: $matchId")
                sendMessageUseCase(matchId, text)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending message", e)
            }
        }
    }

    fun setMatchState(matchState: MatchState) {
        _viewState.value = matchState
        if(matchState.pictureState is ProfilePictureState.Loading) {
            viewModelScope.launch {
                getPictureUseCase(matchState.match.profile.id, matchState.match.profile.pictureNames.first()).onSuccess { pictureUrl ->
                    _viewState.update { it?.copy(pictureState = ProfilePictureState.Remote(Uri.parse(pictureUrl))) }
                }
            }
        }
    }
}

