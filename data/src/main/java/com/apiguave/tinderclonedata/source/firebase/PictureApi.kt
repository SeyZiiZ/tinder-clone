package com.apiguave.tinderclonedata.source.firebase

import android.net.Uri
import android.util.Log
import com.apiguave.tinderclonedata.source.firebase.extension.getTaskResult
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID

object PictureApi {
    private const val USERS = "users"
    private const val TAG = "PictureApi"

    suspend fun getPictureUrl(userId: String, fileName: String): Uri {
        Log.d(TAG, "Tentative de récupération de l'URL pour l'image: $fileName")
        return FirebaseStorage.getInstance().reference.child(USERS)
            .child(userId).child(fileName).downloadUrl.getTaskResult()
    }

    suspend fun addPictures(pictures: List<Uri>): List<String>{
        return coroutineScope {
            pictures.map { async { addPicture(it) } }.awaitAll()
        }
    }

    suspend fun addPicture(picture: Uri): String {
        try {
            Log.d(TAG, "Tentative d'upload d'une nouvelle image")
            val filename = UUID.randomUUID().toString()+".jpg"
            val userId = AuthApi.userId ?: throw IllegalStateException("User ID is null")
            
            Log.d(TAG, "Chemin de stockage: users/$userId/$filename")
            val pictureRef = FirebaseStorage.getInstance().reference
                .child(USERS)
                .child(userId)
                .child(filename)

            Log.d(TAG, "Début de l'upload")
            pictureRef.putFile(picture).getTaskResult()
            Log.d(TAG, "Upload réussi")

            return filename
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'upload de l'image", e)
            throw e
        }
    }

    suspend fun deletePictures(pictures: List<String>){
        return coroutineScope {
            pictures.map { async { deletePicture(it) } }.awaitAll()
        }
    }

    private suspend fun deletePicture(picture: String){
        try {
            Log.d(TAG, "Tentative de suppression de l'image: $picture")
            val userId = AuthApi.userId ?: throw IllegalStateException("User ID is null")
            FirebaseStorage.getInstance().reference
                .child(USERS)
                .child(userId)
                .child(picture)
                .delete()
                .getTaskResult()
            Log.d(TAG, "Suppression réussie")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la suppression de l'image", e)
            throw e
        }
    }
}