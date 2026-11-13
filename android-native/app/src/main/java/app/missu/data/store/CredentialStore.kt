package app.missu.data.store

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
 * 「记住账号」的加密存储。
 *
 * 设计取舍：站点会话靠 Cookie 维持，但会话可能被服务端作废；能自动重登的前提是本地持有凭据。
 * NOTE: 凭据必须用 Android Keystore 里的密钥做 AES-GCM 加密再落盘，明文 SharedPreferences 不行。
 * NOTE: 密钥不可导出，随应用卸载销毁；`AndroidManifest` 里 `allowBackup=false`，避免密文随云备份离开设备。
 * TODO: 还没有「不记住密码」的开关；加设置页时补一个，默认记住。
 */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(email: String, password: String) {
        val encrypted = encrypt("$email\n$password") ?: return
        prefs.edit().putString(KEY_CREDENTIALS, encrypted).apply()
    }

    fun load(): Pair<String, String>? {
        val stored = prefs.getString(KEY_CREDENTIALS, null) ?: return null
        val plain = decrypt(stored) ?: return null
        val parts = plain.split('\n', limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        // NOTE: 凭据只在内存里短暂存在，不写日志。
        return parts[0] to parts[1]
    }

    fun clear() {
        prefs.edit().remove(KEY_CREDENTIALS).apply()
    }

    private fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_SIZE)
        val encrypted = bytes.copyOfRange(IV_SIZE, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "missu:credentials"
        const val KEY_CREDENTIALS = "credentials"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "missu.credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
