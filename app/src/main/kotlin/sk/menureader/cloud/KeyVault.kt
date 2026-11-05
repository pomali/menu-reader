package sk.menureader.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's API keys, encrypted with an AES key that never leaves the Android Keystore.
 * Keys stay on this phone and are only sent to their own provider.
 */
class KeyVault(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("vault", Context.MODE_PRIVATE)

    fun get(kind: KeyKind): String? = prefs.getString(kind.name, null)?.let { stored ->
        runCatching {
            val (iv, data) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
            String(c.doFinal(data), Charsets.UTF_8)
        }.getOrNull()
    }

    fun has(kind: KeyKind) = prefs.contains(kind.name)

    fun set(kind: KeyKind, value: String?) {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) { prefs.edit().remove(kind.name).apply(); return }
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, secret())
        val enc = Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(v.toByteArray()), Base64.NO_WRAP)
        prefs.edit().putString(kind.name, enc).apply()
    }

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "menu-reader-api-keys"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
