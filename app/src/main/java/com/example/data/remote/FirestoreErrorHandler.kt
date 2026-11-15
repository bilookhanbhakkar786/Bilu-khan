package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun friendlyFirestoreMessage(exception: Throwable, fallback: String = "Operation failed."): String {
    val root = generateSequence(exception) { it.cause }
        .firstOrNull { it is FirebaseFirestoreException } as? FirebaseFirestoreException
        ?: return exception.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback

    return when (root.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Permission denied. Make sure your account is verified for this school action."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "Daily Firestore quota exceeded. Quota resets tomorrow."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Network unavailable. Please check your internet connection and retry."
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "Requested record was not found."
        else -> root.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
    }
}
