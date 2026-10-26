/*
 * Mis Podcast - Gestor y reproductor libre de podcasts para Android
 * Copyright (C) 2026 ferlagod
 *
 * Este programa es software libre: puedes redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General de GNU tal como fue
 * publicada por la Free Software Foundation, ya sea la versión 3 de la
 * Licencia, o (a tu elección) cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil,
 * pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de
 * COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR.
 * Consulta la Licencia Pública General de GNU para más detalles.
 *
 * Deberías haber recibido una copia de la Licencia Pública General de GNU
 * junto con este programa. Si no es así, consulta <https://www.gnu.org/licenses/>.
 */

package com.ferlagod.mispodcast.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Utilidad de cifrado simétrico respaldada por el hardware seguro de Android (AndroidKeyStore).
 *
 * Cifra credenciales sensibles como contraseñas de aplicación de Nextcloud con AES-256 en modo GCM.
 * La clave criptográfica reside en el almacenamiento protegido del sistema (TEE / StrongBox)
 * y no puede ser exportada ni copiada en las copias de seguridad de Google en la nube.
 */
object KeyStoreCryptoHelper {

    private const val TAG = "KeyStoreCryptoHelper"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "mispodcast_nextcloud_credential_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc_v1:"
    private const val IV_SIZE = 12
    private const val TAG_BIT_LENGTH = 128

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore no disponible en este entorno: ${e.message}")
            null
        }
    }

    /**
     * Cifra un texto plano utilizando AES-GCM con el KeyStore de Android.
     * Si el KeyStore no está disponible (p. ej. en pruebas locales de JVM), se devuelve el texto.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        val secretKey = getOrCreateSecretKey() ?: return plainText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error cifrando credencial con KeyStore", e)
            plainText
        }
    }

    /**
     * Descifra un texto previamente cifrado.
     * Si no tiene el prefijo de cifrado, se asume texto plano existente (migración transparente).
     */
    fun decrypt(cipherTextOrPlain: String): String {
        if (cipherTextOrPlain.isBlank()) return ""
        if (!cipherTextOrPlain.startsWith(PREFIX)) {
            return cipherTextOrPlain // Texto plano previo sin cifrar
        }
        val secretKey = getOrCreateSecretKey() ?: return ""
        return try {
            val encodedData = cipherTextOrPlain.removePrefix(PREFIX)
            val combined = Base64.decode(encodedData, Base64.NO_WRAP)
            if (combined.size < IV_SIZE) return ""

            val iv = combined.copyOfRange(0, IV_SIZE)
            val encryptedBytes = combined.copyOfRange(IV_SIZE, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_BIT_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error descifrando credencial con KeyStore", e)
            ""
        }
    }
}
