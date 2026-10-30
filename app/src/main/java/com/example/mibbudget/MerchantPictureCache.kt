package com.example.mibbudget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Download a small icon only from the verified merchant website. */
object MerchantPictureCache {
    fun saveIfAbsent(context: Context, description: String, website: String): Boolean {
        val key = VendorProfiles.key(description)
        val prefs = context.getSharedPreferences("vendor_avatar_files", Context.MODE_PRIVATE)
        if (prefs.getString(key, null) != null) return false
        val site = URL(website)
        if (site.protocol != "https" || site.userInfo != null) return false
        val icon = URL(site.protocol, site.host, "/favicon.ico")
        val c = icon.openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.instanceFollowRedirects = false
        return try {
            if (c.responseCode != 200 || !c.contentType.orEmpty().startsWith("image/")) return false
            val bytes = c.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buf = ByteArray(4096)
                while (output.size() < 150000) {
                    val n = input.read(buf, 0, minOf(buf.size, 150000 - output.size()))
                    if (n < 0) break
                    output.write(buf, 0, n)
                }
                output.toByteArray()
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
            val scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
            val file = File(context.filesDir, "merchant_auto_$key.jpg")
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            if (prefs.getString(key, null) != null) { file.delete(); return false }
            prefs.edit().putString(key, file.absolutePath).apply()
            true
        } finally { c.disconnect() }
    }
}
