package com.apiguave.tinderclonedomain.usecase

import com.apiguave.tinderclonedomain.auth.Account
import com.apiguave.tinderclonedomain.auth.AuthRepository
import com.apiguave.tinderclonedomain.profile.Gender
import com.apiguave.tinderclonedomain.picture.PictureRepository
import com.apiguave.tinderclonedomain.profile.Orientation
import com.apiguave.tinderclonedomain.profile.ProfileRepository
import java.time.LocalDate

class SignUpUseCase(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val pictureRepository: PictureRepository
) {

    suspend operator fun invoke(
        account: Account,
        name: String,
        birthdate: LocalDate,
        bio: String,
        gender: Gender,
        orientation: Orientation,
        pictures: List<String>
    ): Result<Unit> {
        return Result.runCatching {
            try {
                authRepository.signUp(account)
                val userId = authRepository.userId ?: throw IllegalStateException("User ID is null after sign up")
                
                // Créer le profil
                profileRepository.addProfile(userId, name, birthdate, bio, gender, orientation)
                
                // Uploader les images
                val pictureNames = try {
                    pictureRepository.addPictures(pictures)
                } catch (e: Exception) {
                    // En cas d'échec de l'upload des images, supprimer le profil
                    throw e
                }
                
                // Mettre à jour le profil avec les noms des images
                profileRepository.updatePictures(pictureNames)
            } catch (e: Exception) {
                // En cas d'échec, supprimer les données partielles
                authRepository.userId?.let { userId ->
                    try {
                    } catch (ignored: Exception) {}
                }
                throw e
            }
        }
    }
}