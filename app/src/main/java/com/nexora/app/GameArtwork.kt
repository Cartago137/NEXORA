package com.nexora.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Lightweight, disk-cached GBA box-art lookup.
 *
 * Only known games receive automatic artwork. Unknown titles keep the NEXORA
 * placeholder, and failed downloads never affect ROM loading or save data.
 */
internal object GameArtworkCatalog {
    private val coverUrls = listOf(
        Triple(
            listOf("pokemon - firered", "pokemon firered", "pokemon fire red"),
            "https://i5.walmartimages.com/seo/Restored-Pre-Owned-Pokemon-FireRed-Nintendo-Gameboy-Advance-GBA-Refurbished-Good-Refurbished_81e03620-61d4-4932-887f-506557f47d0d_1.0983c65920c95181f7327e61812c5528.jpeg",
            "Pokémon FireRed Version"
        ),
        Triple(
            listOf("mario & luigi - superstar saga", "mario and luigi superstar saga", "mario luigi superstar saga"),
            "https://mybrainongames.wordpress.com/wp-content/uploads/2017/10/mario-luigi-superstar-saga-game-boy-advance-north-american-cover.jpg",
            "Mario & Luigi: Superstar Saga"
        ),
        Triple(
            listOf("mega man battle network 2", "megaman battle network 2"),
            "https://gamesfirst.com/articles/2002/mega-man-battle-network-2-review/cover.jpg",
            "Mega Man Battle Network 2"
        ),
        Triple(
            listOf("metroid - zero mission", "metroid zero mission"),
            "https://cdn.mobygames.com/covers/4508344-metroid-zero-mission-game-boy-advance-front-cover.jpg",
            "Metroid: Zero Mission"
        )
    )

    fun match(gameName: String): Pair<String, String>? {
        val normalized = gameName.substringBeforeLast('.')
            .lowercase()
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val match = coverUrls.firstOrNull { entry ->
            entry.first.any { normalized.contains(it) || it.contains(normalized) }
        } ?: return null
        return match.second to match.third
    }

    fun cacheFile(context: android.content.Context, url: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "artwork_cache"), "$digest.jpg")
    }

    suspend fun load(context: android.content.Context, url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(context, url)
            if (cache.isFile && cache.length() > 0L) {
                BitmapFactory.decodeFile(cache.absolutePath)?.let { return@withContext it }
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "NEXORA-Android/0.3")
                    instanceFollowRedirects = true
                }
                connection.connect()
                if (connection.responseCode !in 200..299) return@withContext null
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return@withContext null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext null
                cache.parentFile?.mkdirs()
                cache.outputStream().use { it.write(bytes) }
                bitmap
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
}

@Composable
internal fun GameCoverArtwork(
    gameName: String,
    modifier: Modifier = Modifier,
    fallbackLabel: String = "GBA"
) {
    val context = LocalContext.current
    val artwork = GameArtworkCatalog.match(gameName)
    val bitmapState = produceState<Bitmap?>(initialValue = null, gameName, artwork?.first) {
        value = if (artwork == null) null else GameArtworkCatalog.load(context, artwork.first)
    }

    Box(modifier = modifier.background(Color(0xFF21172E)), contentAlignment = Alignment.Center) {
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = artwork?.second ?: gameName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                gameName.firstOrNull()?.uppercase() ?: fallbackLabel,
                color = Color(0xFF9B63FF),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                fallbackLabel,
                modifier = Modifier.align(Alignment.BottomEnd),
                color = Color(0xFFB7AFC2),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
