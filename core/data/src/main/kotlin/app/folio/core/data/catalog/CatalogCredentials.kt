package app.folio.core.data.catalog

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional sign-in per catalog id, in the `catalog_auth` prefs: the login in plain text and the password
 * AES-GCM-encrypted with a non-exportable Android Keystore key. A password the key can't decrypt (key lost)
 * reads as absent, so the user signs in again.
 */
@Singleton
class CatalogCredentials @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("catalog_auth", Context.MODE_PRIVATE)

    private val _logins = MutableStateFlow(prefs.all.mapNotNull { (id, value) -> (value as? String)?.substringBefore('\n')?.let { id to it } }.toMap())
    /** Catalog id → login of the catalogs with a saved sign-in. */
    val logins: StateFlow<Map<String, String>> = _logins.asStateFlow()

    fun password(id: String): String? {
        val stored = prefs.getString(id, null)?.substringAfter('\n', "")?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Saved catalog password can't be read", e)
            null
        }
    }

    /** Saves [login]; a null [password] keeps the saved one. A blank login signs out. */
    fun save(id: String, login: String, password: String?) {
        val name = login.trim()
        if (name.isEmpty()) return remove(id)
        require('\n' !in name) { "Логин в одну строку" }
        val sealed = if (password == null) prefs.getString(id, null)?.substringAfter('\n', "").orEmpty() else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        }
        prefs.edit().putString(id, "$name\n$sealed").apply()
        _logins.value += id to name
    }

    fun remove(id: String) {
        if (!prefs.contains(id)) return
        prefs.edit().remove(id).apply()
        _logins.value -= id
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val TAG = "CatalogCredentials"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "catalog_auth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
