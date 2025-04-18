package com.apiguave.tinderclonedomain.usecase

import com.apiguave.tinderclonedomain.picture.LocalPicture
import com.apiguave.tinderclonedomain.picture.Picture
import com.apiguave.tinderclonedomain.picture.PictureRepository
import com.apiguave.tinderclonedomain.picture.RemotePicture
import com.apiguave.tinderclonedomain.profile.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class UpdatePicturesUseCase(private val profileRepository: ProfileRepository, private val pictureRepository: PictureRepository) {
    suspend operator fun invoke(pictures: List<Picture>): Result<List<String>> {
        return Result.runCatching {
            val currentProfile = profileRepository.getProfile()
            val remotePictureNames = pictures.mapNotNull { if (it is RemotePicture) it.filename else null }

            //This is a list of the pictures that were already uploaded but that have been removed from the profile.
            val picturesToDelete: List<String> = currentProfile.pictureNames.filter { !remotePictureNames.contains(it) }

            try {
                coroutineScope {
                    // Supprimer d'abord les images qui ne sont plus utilisées
                    if(picturesToDelete.isNotEmpty()) {
                        pictureRepository.deletePictures(picturesToDelete)
                    }

                    // Ensuite, traiter les nouvelles images
                    val pictureNames = pictures.map { picture ->
                        when (picture) {
                            is RemotePicture -> picture.filename
                            is LocalPicture -> {
                                try {
                                    pictureRepository.addPicture(picture.uri)
                                } catch (e: Exception) {
                                    throw Exception("Erreur lors de l'upload de l'image: ${e.message}")
                                }
                            }
                        }
                    }

                    // Mettre à jour le profil avec les nouveaux noms d'images
                    profileRepository.updatePictures(pictureNames)
                    pictureNames
                }
            } catch (e: Exception) {
                throw Exception("Erreur lors de la mise à jour des images: ${e.message}")
            }
        }
    }
}