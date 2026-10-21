package com.example.data.security

import java.security.MessageDigest

object SecurityUtils {
    private const val SALT = "GCE_SALEM_SECURE_SALT_2026"

    /**
     * Computes SHA-256 hash of password combined with salt.
     */
    fun hashPassword(password: String): String {
        val salted = password + SALT
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(salted.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies password against stored hash.
     */
    fun verifyPassword(password: String, storedHash: String): Boolean {
        return hashPassword(password) == storedHash
    }
}
