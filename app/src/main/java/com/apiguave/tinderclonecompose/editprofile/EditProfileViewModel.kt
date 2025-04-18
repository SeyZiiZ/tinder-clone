package com.apiguave.tinderclonecompose.editprofile

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apiguave.tinderclonecompose.extension.filterIndex
import com.apiguave.tinderclonecompose.extension.getTaskResult
import com.apiguave.tinderclonecompose.extension.toGender
import com.apiguave.tinderclonecompose.extension.toLongString
import com.apiguave.tinderclonecompose.extension.toOrientation
import com.apiguave.tinderclonecompose.model.PictureState
import com.apiguave.tinderclonedomain.picture.LocalPicture
import com.apiguave.tinderclonedomain.picture.RemotePicture
import com.apiguave.tinderclonedomain.profile.UserProfile
import com.apiguave.tinderclonedomain.usecase.GetPictureUseCase
import com.apiguave.tinderclonedomain.usecase.GetProfileUseCase
import com.apiguave.tinderclonedomain.usecase.SignOutUseCase
import com.apiguave.tinderclonedomain.usecase.UpdatePicturesUseCase
import com.apiguave.tinderclonedomain.usecase.UpdateProfileUseCase
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EditProfileViewModel(
    private val signOutUseCase: SignOutUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val getPictureUseCase: GetPictureUseCase,
    private val updatePicturesUseCase: UpdatePicturesUseCase
): ViewModel() {
    private val _uiState = MutableStateFlow(
        EditProfileViewState()
    )
    val uiState = _uiState.asStateFlow()

    private val _action = MutableSharedFlow<EditProfileAction>()
    val action = _action.asSharedFlow()

    fun setBio(bio: TextFieldValue) {
        _uiState.update { it.copy(bio = bio) }
    }

    fun setGenderIndex(genderIndex: Int) {
        _uiState.update { it.copy(genderIndex = genderIndex) }
    }

    fun setOrientationIndex(orientationIndex: Int) {
        _uiState.update { it.copy(orientationIndex = orientationIndex) }
    }

    fun closeDialog() {
        _uiState.update { it.copy(dialogState = EditProfileDialogState.NoDialog) }
    }

    fun showConfirmDeletionDialog(index: Int) {
        _uiState.update { it.copy(dialogState = EditProfileDialogState.DeleteConfirmationDialog(index)) }
    }

    fun showSelectPictureDialog() {
        _uiState.update { it.copy(dialogState = EditProfileDialogState.SelectPictureDialog) }
    }

    fun loadUserProfile() = viewModelScope.launch {
        getProfileUseCase().onSuccess { currentProfile ->
            _uiState.update {
                it.copy(
                    currentProfile = currentProfile,
                    name = currentProfile.name,
                    bio = TextFieldValue(currentProfile.bio),
                    birthDate = currentProfile.birthDate.toLongString(),
                    genderIndex = currentProfile.gender.ordinal,
                    orientationIndex = currentProfile.orientation.ordinal,
                    pictures = currentProfile.pictureNames.map { pictureName -> PictureState.Loading(pictureName) }
                )
            }
            loadProfilePictures(currentProfile.id, currentProfile.pictureNames)
        }
    }

    private suspend fun loadProfilePictures(userId: String, pictureNames: List<String>) {
        try {
            pictureNames.forEach { pictureName ->
                Log.i("EditProfileViewModel", "loadProfilePictures($userId, $pictureName)")
                getPictureUseCase(userId, pictureName)
                    .onSuccess { pictureUrl ->
                        updatePicturesState(pictureName, Uri.parse(pictureUrl))
                    }
                    .onFailure { e ->
                        Log.e("EditProfileViewModel", "Erreur lors du chargement de l'image $pictureName", e)
                        // Mettre à jour l'état pour indiquer l'erreur
                        _uiState.update {
                            it.copy(
                                pictures = it.pictures.map { pictureState ->
                                    if(pictureState is PictureState.Loading && pictureState.name == pictureName)
                                        PictureState.Error(pictureName)
                                    else pictureState
                                }
                            )
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("EditProfileViewModel", "Erreur lors du chargement des images", e)
            _uiState.update { it.copy(dialogState = EditProfileDialogState.ErrorDialog("Erreur lors du chargement des images")) }
        }
    }

    private fun updatePicturesState(pictureName: String, pictureUrl: Uri) {
        _uiState.update {
            it.copy(
                pictures = it.pictures.map { pictureState ->
                    if(pictureState is PictureState.Loading && pictureState.name == pictureName)
                        PictureState.Remote(pictureName, pictureUrl)
                    else pictureState
                }
            )
        }
    }

    fun updateProfile() {
        _uiState.update { it.copy(dialogState = EditProfileDialogState.Loading) }

        viewModelScope.launch {
            try {

                // Vérifiez d'abord si des images sont en erreur ou en chargement
                val currentPictures = _uiState.value.pictures
                if(currentPictures.any { it is PictureState.Loading }) {
                    _uiState.update { it.copy(dialogState = EditProfileDialogState.ErrorDialog("Veuillez attendre que toutes les images soient chargées")) }
                    return@launch
                }
                if(currentPictures.any { it is PictureState.Error }) {
                    _uiState.update { it.copy(dialogState = EditProfileDialogState.ErrorDialog("Certaines images n'ont pas pu être chargées. Veuillez réessayer.")) }
                    return@launch
                }

                // Mise à jour du profil texte
                val currentBio = _uiState.value.bio.text
                val currentGender = _uiState.value.genderIndex.toGender()
                val currentOrientation = _uiState.value.orientationIndex.toOrientation()

                updateProfileUseCase(currentBio, currentGender, currentOrientation).onFailure { e ->
                    _uiState.update { it.copy(dialogState = EditProfileDialogState.ErrorDialog(e.message ?: "Erreur lors de la mise à jour du profil")) }
                    return@launch
                }

                // Mise à jour des images seulement si la première étape réussit
                updatePicturesUseCase(currentPictures.mapNotNull { when(it) {
                    is PictureState.Loading, is PictureState.Error -> null
                    is PictureState.Local -> LocalPicture(it.uri.toString())
                    is PictureState.Remote -> RemotePicture(it.uri.toString(), it.name)
                } }).onSuccess {
                    _action.emit(EditProfileAction.ON_PROFILE_EDITED)
                }.onFailure { e ->
                    _uiState.update { it.copy(dialogState = EditProfileDialogState.ErrorDialog(e.message ?: "Erreur lors de la sauvegarde des images")) }
                }
            } catch (e: Exception) {
                Log.e("EditProfileViewModel", "Erreur lors de la mise à jour du profil", e)
                _uiState.update { it.copy(dialogState = EditProfileDialogState.ErrorDialog(e.message ?: "Une erreur inattendue s'est produite")) }
            }
        }
    }

    fun addPicture(picture: Uri){
        _uiState.update { it.copy(pictures = it.pictures + PictureState.Local(picture)) }
    }

    fun removePictureAt(index: Int){
        _uiState.update { it.copy(pictures = it.pictures.filterIndex(index)) }
    }

    fun signOut(signInClient: GoogleSignInClient) = viewModelScope.launch {
        signOutUseCase().onSuccess {
            signInClient.signOut().getTaskResult()
            _action.emit(EditProfileAction.ON_SIGNED_OUT)
        }
    }

}

@Immutable
sealed class EditProfileDialogState {
    object NoDialog: EditProfileDialogState()
    data class DeleteConfirmationDialog(val index: Int): EditProfileDialogState()
    data class ErrorDialog(val message: String): EditProfileDialogState()
    object SelectPictureDialog: EditProfileDialogState()
    object Loading: EditProfileDialogState()
}

@Immutable
data class EditProfileViewState(
    val currentProfile: UserProfile? = null,
    val name: String = "",
    val birthDate: String = "",
    val bio: TextFieldValue = TextFieldValue(),
    val genderIndex: Int = -1,
    val orientationIndex: Int = -1,
    val pictures: List<PictureState> = emptyList(),
    val dialogState: EditProfileDialogState = EditProfileDialogState.NoDialog
)

enum class EditProfileAction{ON_SIGNED_OUT, ON_PROFILE_EDITED}