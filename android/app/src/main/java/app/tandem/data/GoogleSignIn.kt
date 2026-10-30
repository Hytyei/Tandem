package app.tandem.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.tandem.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Asks Android for a Google ID token (the "Sign in with Google" sheet). The token is only ever sent to the Tandem server,
 * which checks Google's signature. Needs [BuildConfig.GOOGLE_WEB_CLIENT_ID]; without it [available] is false and the buttons stay hidden.
 */
object GoogleSignIn {
    val available: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** The ID token, or null when the person closed the sheet. Throws [AppException] with a readable message for real problems. [activityContext] must be an Activity. */
    suspend fun idToken(activityContext: Context): String? {
        if (!available) throw AppException("Google sign-in isn't set up in this build of the app.")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        try {
            val cred = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return GoogleIdTokenCredential.createFrom(cred.data).idToken
            }
            throw AppException("Google didn't return an account. Try again.")
        } catch (e: GetCredentialCancellationException) {
            return null
        } catch (e: NoCredentialException) {
            throw AppException("No Google account found on this phone. Add one in your phone's settings, then try again.")
        } catch (e: GoogleIdTokenParsingException) {
            throw AppException("Google sent something unexpected. Try again.")
        } catch (e: GetCredentialException) {
            throw AppException("Google sign-in didn't work (${e.type.substringAfterLast('.')}). Check that this build is registered in your Google Cloud project.")
        }
    }
}
