package com.nexora.app

import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private val NexoraBackground = Color(0xFF100B18)
private val NexoraPanel = Color(0xFF1D1428)
private val NexoraPanelLight = Color(0xFF291D38)
private val NexoraPurple = Color(0xFF8B5CF6)
private val NexoraPink = Color(0xFFE879A8)
private val NexoraText = Color(0xFFF4EFFA)
private val NexoraMuted = Color(0xFFB7AFC2)

private enum class NexoraTab { HOME, LIBRARY, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {

    private var activeRomCacheFile: File? = null
    private var isAppInForeground by mutableStateOf(true)

    private fun hideSystemBars() {
        // Keep the previously used immersive flags: this implementation
        // avoids the newer WindowInsetsController path during startup.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        isAppInForeground = false
        super.onPause()
    }

    private fun loadGame(game: GameEntry, onResult: (Int) -> Unit) {
        Thread {
            val extension = game.name.substringAfterLast('.', "gba").lowercase(Locale.ROOT)
            val cachedRom = File(cacheDir, "nexora-${System.nanoTime()}.$extension")
            var copyFailureCode = -2
            val copied = runCatching {
                val source = contentResolver.openInputStream(Uri.parse(game.uri))
                    ?: error("The selected document could not be opened.")
                source.use { input ->
                    cachedRom.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var totalBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > 64L * 1024L * 1024L) {
                                copyFailureCode = -3
                                throw IllegalArgumentException("ROM exceeds the 64 MiB limit.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }.isSuccess

            val status = if (copied) {
                val loadStatus = runCatching {
                    NativeBridge.nativeLoadRom(cachedRom.absolutePath)
                }.getOrDefault(-1)

                if (loadStatus == 4) {
                    // Battery saves are kept privately by NEXORA and separated per imported ROM.
                    val saveDirectory = File(filesDir, "saves").apply { mkdirs() }
                    val safeGameName = game.name.substringBeforeLast('.')
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val saveFile = File(
                        saveDirectory,
                        "$safeGameName-${Integer.toHexString(game.uri.hashCode())}.sav"
                    )
                    // A missing/empty battery save must never prevent the game from starting.
                    runCatching {
                        NativeBridge.nativeLoadBatterySave(saveFile.absolutePath)
                    }
                }
                loadStatus
            } else {
                cachedRom.delete()
                copyFailureCode
            }

            runOnUiThread {
                activeRomCacheFile?.delete()
                activeRomCacheFile = if (status == 4) cachedRom else null
                if (status != 4) cachedRom.delete()
                onResult(status)
            }
        }.start()
    }

    private fun unloadGame() {
        runCatching { NativeBridge.nativeUnloadRom() }
        activeRomCacheFile?.delete()
        activeRomCacheFile = null
    }

    override fun onDestroy() {
        if (isFinishing) unloadGame()
        super.onDestroy()
    }

    private fun releaseDocumentAccess(uri: android.net.Uri) {
        val readWriteFlags =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            contentResolver.releasePersistableUriPermission(uri, readWriteFlags)
        }.onFailure {
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use immersive fullscreen so neither the status bar nor navigation bar
        // covers the game. Android can reveal them temporarily with a swipe.
        hideSystemBars()

        setContent {
            var games by remember { mutableStateOf(GameLibraryStore.load(this@MainActivity)) }
            var folders by remember { mutableStateOf(GameFolderStore.load(this@MainActivity)) }
            var libraryMessage by remember { mutableStateOf<String?>(null) }
            var isLoadingGame by remember { mutableStateOf(false) }

            val romPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments()
            ) { selectedUris ->
                if (selectedUris.isNotEmpty()) {
                    val readWriteFlags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    val updatedGames = games.toMutableList()
                    var addedCount = 0
                    var duplicateCount = 0
                    var invalidCount = 0
                    var permissionErrorCount = 0

                    selectedUris.forEach { uri ->
                        val accepted = runCatching {
                            contentResolver.takePersistableUriPermission(uri, readWriteFlags)
                            true
                        }.getOrElse {
                            runCatching {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                                true
                            }.getOrDefault(false)
                        }

                        if (!accepted) {
                            permissionErrorCount++
                        } else {
                            val displayName = contentResolver.query(
                                uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                                null,
                                null,
                                null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                                        cursor.getLong(sizeIndex)
                                    } else 0L
                                    (name ?: uri.lastPathSegment ?: "GBA game") to size
                                } else {
                                    (uri.lastPathSegment ?: "GBA game") to 0L
                                }
                            } ?: ((uri.lastPathSegment ?: "GBA game") to 0L)

                            val extension = displayName.first
                                .substringAfterLast('.', "")
                                .lowercase(Locale.ROOT)

                            when {
                                extension !in setOf("gba", "agb", "bin") -> {
                                    invalidCount++
                                    releaseDocumentAccess(uri)
                                }
                                updatedGames.any { it.uri == uri.toString() } -> {
                                    duplicateCount++
                                }
                                else -> {
                                    updatedGames.add(
                                        GameEntry(displayName.first, uri.toString(), displayName.second)
                                    )
                                    addedCount++
                                }
                            }
                        }
                    }

                    if (addedCount > 0) {
                        games = updatedGames
                        GameLibraryStore.save(this@MainActivity, games)
                    }

                    libraryMessage = getString(
                        R.string.import_summary,
                        addedCount,
                        duplicateCount,
                        invalidCount,
                        permissionErrorCount
                    )
                }
            }

            MaterialTheme {
                // Keep the launcher path independent from JNI/native core startup.
                // The native engine is initialized only when the user starts a game.
                NexoraApp(
                    nativeStatus = -1,
                    isAppInForeground = isAppInForeground,
                    games = games,
                    folders = folders,
                    libraryMessage = libraryMessage,
                    onSaveFolders = { updatedFolders ->
                        folders = updatedFolders
                        GameFolderStore.save(this@MainActivity, folders)
                    },
                    onUpdateGame = { updatedGame ->
                        games = games.map { if (it.uri == updatedGame.uri) updatedGame else it }
                        GameLibraryStore.save(this@MainActivity, games)
                    },
                    onDeleteFolder = { folder ->
                        folders = folders.filterNot { it.id == folder.id }
                        games = games.map { if (it.folderId == folder.id) it.copy(folderId = null) else it }
                        GameFolderStore.save(this@MainActivity, folders)
                        GameLibraryStore.save(this@MainActivity, games)
                    },
                    isLoadingGame = isLoadingGame,
                    onImportGame = { romPicker.launch(arrayOf("*/*")) },
                    onPlayGame = { game, onLoaded ->
                        isLoadingGame = true
                        loadGame(game) { status ->
                            isLoadingGame = false
                            if (status != 4) {
                                libraryMessage = when (status) {
                                    -2 -> getString(R.string.game_file_copy_failed)
                                    -3 -> getString(R.string.game_file_too_large)
                                    0, 1, 2, 3 -> getString(R.string.game_load_failed)
                                    else -> getString(R.string.native_bridge_unavailable)
                                }
                            } else {
                                libraryMessage = null
                            }
                            onLoaded(status)
                        }
                    },
                    onClearLibraryMessage = { libraryMessage = null },
                    onExitGame = { unloadGame() },
                    onRemoveGame = { game, deleteOriginal ->
                        val removedSuccessfully = if (deleteOriginal) {
                            runCatching {
                                DocumentsContract.deleteDocument(
                                    contentResolver,
                                    android.net.Uri.parse(game.uri)
                                )
                            }.getOrDefault(false)
                        } else {
                            true
                        }

                        if (removedSuccessfully) {
                            releaseDocumentAccess(android.net.Uri.parse(game.uri))
                            games = games.filterNot { it.uri == game.uri }
                            GameLibraryStore.save(this@MainActivity, games)
                            libraryMessage = null
                        }
                        removedSuccessfully
                    }
                )
            }
        }
    }
}

@Composable
private fun NexoraApp(
    nativeStatus: Int,
    isAppInForeground: Boolean,
    games: List<GameEntry>,
    folders: List<GameFolder>,
    libraryMessage: String?,
    onSaveFolders: (List<GameFolder>) -> Unit,
    onUpdateGame: (GameEntry) -> Unit,
    onDeleteFolder: (GameFolder) -> Unit,
    isLoadingGame: Boolean,
    onImportGame: () -> Unit,
    onPlayGame: (GameEntry, (Int) -> Unit) -> Unit,
    onClearLibraryMessage: () -> Unit,
    onExitGame: () -> Unit,
    onRemoveGame: (GameEntry, Boolean) -> Boolean
) {
    var selectedTab by remember { mutableStateOf(NexoraTab.HOME) }
    var activeGameName by remember { mutableStateOf<String?>(null) }
    var gamePendingRemoval by remember { mutableStateOf<GameEntry?>(null) }
    var removalFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var lastPlayedUri by remember {
        mutableStateOf(
            context.getSharedPreferences("nexora_home", android.content.Context.MODE_PRIVATE)
                .getString("last_played_uri", null)
        )
    }
    val startGame: (GameEntry) -> Unit = { game ->
        onPlayGame(game) { status ->
            if (status == 4) {
                activeGameName = game.name
                lastPlayedUri = game.uri
                context.getSharedPreferences("nexora_home", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_played_uri", game.uri)
                    .apply()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = NexoraBackground) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (activeGameName == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 38.dp)
                            .background(NexoraPurple, RoundedCornerShape(4.dp))
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = stringResource(R.string.brand_name),
                            color = NexoraText,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            color = NexoraMuted,
                            fontSize = 9.sp,
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text("●", color = NexoraPink, fontSize = 12.sp)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (activeGameName != null) {
                    PlayerScreen(
                        gameName = activeGameName!!,
                        isAppInForeground = isAppInForeground,
                        onExit = {
                            onExitGame()
                            activeGameName = null
                        }
                    )
                } else when (selectedTab) {
                    NexoraTab.HOME -> NexoraHomeScreen(
                        games = games,
                        featuredGame = games.firstOrNull { it.uri == lastPlayedUri } ?: games.firstOrNull(),
                        isLastPlayed = lastPlayedUri != null && games.any { it.uri == lastPlayedUri },
                        onImportGame = {
                            selectedTab = NexoraTab.LIBRARY
                            onImportGame()
                        },
                        onOpenLibrary = { selectedTab = NexoraTab.LIBRARY },
                        onPlayGame = startGame
                    )
                    NexoraTab.LIBRARY -> LibraryScreen(
                        games = games,
                        folders = folders,
                        onSaveFolders = onSaveFolders,
                        onUpdateGame = onUpdateGame,
                        onDeleteFolder = onDeleteFolder,
                        message = libraryMessage,
                        isLoadingGame = isLoadingGame,
                        onImportGame = onImportGame,
                        onPlayGame = { game -> startGame(game) },
                        onClearMessage = onClearLibraryMessage,
                        onRemoveGame = { game ->
                            removalFailed = false
                            gamePendingRemoval = game
                        }
                    )
                    NexoraTab.HISTORY -> HistoryScreen()
                    NexoraTab.SETTINGS -> SettingsScreen(nativeStatus)
                }
            }

            if (activeGameName == null) {
                BottomNavigation(selectedTab = selectedTab, onSelect = { selectedTab = it })
            }
        }
    }

    gamePendingRemoval?.let { game ->
        AlertDialog(
            onDismissRequest = {
                gamePendingRemoval = null
                removalFailed = false
            },
            containerColor = NexoraPanel,
            title = {
                Text(stringResource(R.string.remove_game_title), color = NexoraText)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.remove_game_question, game.name),
                        color = NexoraMuted
                    )
                    if (removalFailed) {
                        Text(
                            stringResource(R.string.delete_original_failed),
                            color = NexoraPink,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onRemoveGame(game, true)) {
                        gamePendingRemoval = null
                    } else {
                        removalFailed = true
                    }
                }) {
                    Text(stringResource(R.string.remove_and_delete), color = NexoraPink)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onRemoveGame(game, false)
                        gamePendingRemoval = null
                    }) {
                        Text(stringResource(R.string.remove_only), color = NexoraText)
                    }
                    TextButton(onClick = {
                        gamePendingRemoval = null
                        removalFailed = false
                    }) {
                        Text(stringResource(R.string.cancel), color = NexoraMuted)
                    }
                }
            }
        )
    }
}

@Composable
private fun PlayerScreen(gameName: String, isAppInForeground: Boolean, onExit: () -> Unit) {
    val pixelBuffer = remember { IntArray(240 * 160) }
    val frameBitmap = remember {
        Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
    }
    val frameImage = remember { frameBitmap.asImageBitmap() }
    var frameReady by remember { mutableStateOf(false) }
    var frameError by remember { mutableStateOf(false) }
    var frameVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(gameName, isAppInForeground) {
        if (!isAppInForeground) {
            NativeBridge.nativeSetKeys(0)
            return@LaunchedEffect
        }

        var audioTrack: AudioTrack? = null
        var audioJob: kotlinx.coroutines.Job? = null
        try {
        // Match Android's output stream to the PCM rate supplied by mGBA.
        val outputSampleRate = NativeBridge.nativeGetAudioSampleRate()
            .takeIf { it > 0 } ?: 32_768
        val createdAudioTrack = runCatching {
            withContext(Dispatchers.IO) {
                val minimumBufferBytes = AudioTrack.getMinBufferSize(
                    outputSampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferBytes = if (minimumBufferBytes > 0) {
                    maxOf(minimumBufferBytes * 4, 16_384)
                } else {
                    8_192
                }
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(outputSampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                    .also {
                        check(it.state == AudioTrack.STATE_INITIALIZED)
                        // Keep the device output well below clipping while the
                        // core mixer is also attenuated.
                        it.setVolume(0.60f)
                        it.play()
                    }
            }
        }.getOrNull()
        audioTrack = createdAudioTrack

        // mGBA produces audio while frames run; this worker drains it without
        // blocking the Compose/UI thread or slowing down video frame pacing.
        audioJob = if (createdAudioTrack != null) {
            launch(Dispatchers.IO) {
                val audioSamples = ShortArray(4_096)
                while (isActive) {
                    val sampleFrames = runCatching {
                        NativeBridge.nativeReadAudio(audioSamples)
                    }.getOrDefault(0)

                    if (sampleFrames > 0) {
                        val sampleCount = sampleFrames * 2
                        // Final digital headroom: avoid sending near-full-scale
                        // samples to Android's output mixer.
                        for (index in 0 until sampleCount) {
                            audioSamples[index] = (audioSamples[index] * 0.60f).toInt().toShort()
                        }
                        var offset = 0
                        while (offset < sampleCount && isActive) {
                            val written = createdAudioTrack.write(
                                audioSamples,
                                offset,
                                sampleCount - offset,
                                AudioTrack.WRITE_BLOCKING
                            )
                            if (written <= 0) break
                            offset += written
                        }
                    } else {
                        delay(4)
                    }
                }
            }
        } else {
            null
        }

            // The GBA runs at 16,777,216 / 280,896 Hz (about 59.7275 FPS).
            // Matching the hardware cadence prevents gradual audio/video drift.
            val targetFrameNanos = 16_742_706L

            while (isActive) {
                val frameStartNanos = System.nanoTime()
                val rendered = withContext(Dispatchers.Default) {
                    runCatching {
                        val success = NativeBridge.nativeRunFrame(pixelBuffer)
                        if (success) {
                            // Keep pixel conversion/copying off the UI thread.
                            frameBitmap.setPixels(pixelBuffer, 0, 240, 0, 0, 240, 160)
                        }
                        success
                    }.getOrDefault(false)
                }

                if (!rendered) {
                    frameError = true
                    break
                }

                frameReady = true
                // Canvas reads this state in its draw phase, forcing the existing
                // bitmap to be painted again even though its object identity is stable.
                frameVersion++

                val remainingNanos = targetFrameNanos - (System.nanoTime() - frameStartNanos)
                if (remainingNanos > 0L) {
                    delay((remainingNanos / 1_000_000L).coerceAtLeast(1L))
                } else {
                    yield()
                }
            }
        } finally {
            audioJob?.cancelAndJoin()
            withContext(Dispatchers.IO) {
                runCatching {
                    audioTrack?.pause()
                    audioTrack?.flush()
                    audioTrack?.release()
                }
            }
            NativeBridge.nativeSetKeys(0)
            NativeBridge.nativeClearAudio()
        }
    }

    androidx.activity.compose.BackHandler {
        NativeBridge.nativeSetKeys(0)
        onExit()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Landscape: D-pad on the left, A/B on the right,
            // shoulder buttons at the upper outer corners, and
            // SELECT/START centered beneath the game image.
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.width(160.dp).fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp)) {
                        GbaControl("L", 1 shl 9, compact = true)
                    }
                    Box(Modifier.align(Alignment.CenterStart)) {
                        GbaDPad()
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        GameFrame(frameError, frameReady, frameVersion, frameImage)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GbaControl("SELECT", 1 shl 2, compact = true)
                        Spacer(Modifier.width(18.dp))
                        GbaControl("START", 1 shl 3, compact = true)
                    }
                }

                Box(modifier = Modifier.width(160.dp).fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 2.dp)) {
                        GbaControl("R", 1 shl 8, compact = true)
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GbaControl("B", 1 shl 1, controlSize = 56)
                        GbaControl("A", 1 shl 0, controlSize = 56)
                    }
                }
            }
        } else {
            // Portrait: keep the game above a dedicated control deck.
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GameFrame(frameError, frameReady, frameVersion, frameImage)
                }

                GbaPortraitControlDeck(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GameFrame(
    frameError: Boolean,
    frameReady: Boolean,
    frameVersion: Int,
    frameImage: androidx.compose.ui.graphics.ImageBitmap
) {
    when {
        frameError -> Text(
            stringResource(R.string.emulation_frame_failed),
            color = NexoraPink,
            fontSize = 13.sp
        )
        frameReady -> BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Use the whole available area while preserving the GBA's native 3:2 ratio.
            val fittedWidth = minOf(maxWidth, maxHeight * 1.5f)
            Canvas(
                modifier = Modifier.width(fittedWidth).aspectRatio(1.5f)
            ) {
                frameVersion.let { /* Snapshot read invalidates this draw scope. */ }
                drawImage(
                    image = frameImage,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.None
                )
            }
        }
        else -> Text("Iniciando jogo…", color = NexoraMuted)
    }
}


@Composable
private fun GbaDPad() {
    Column(
        modifier = Modifier
            .background(NexoraPanel, RoundedCornerShape(22.dp))
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.size(46.dp))
            GbaControl("↑", 1 shl 6, controlSize = 46)
            Spacer(Modifier.size(46.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            GbaControl("←", 1 shl 5, controlSize = 46)
            Box(Modifier.size(46.dp).background(NexoraPanelLight, RoundedCornerShape(12.dp)))
            GbaControl("→", 1 shl 4, controlSize = 46)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.size(46.dp))
            GbaControl("↓", 1 shl 7, controlSize = 46)
            Spacer(Modifier.size(46.dp))
        }
    }
}

@Composable
private fun GbaPortraitControlDeck(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GbaControl("L", 1 shl 9, compact = true)
            GbaControl("R", 1 shl 8, compact = true)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GbaDPad()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GbaControl("B", 1 shl 1, controlSize = 58)
                GbaControl("A", 1 shl 0, controlSize = 58)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            GbaControl("SELECT", 1 shl 2, compact = true)
            GbaControl("START", 1 shl 3, compact = true)
        }
    }
}

@Composable
private fun GbaControl(
    label: String,
    keyMask: Int,
    compact: Boolean = false,
    controlSize: Int = 52
) {
    Box(
        modifier = Modifier
            .size(
                width = if (compact) 54.dp else controlSize.dp,
                height = if (compact) 36.dp else controlSize.dp
            )
            .background(NexoraPanelLight, RoundedCornerShape(if (compact) 11.dp else 16.dp))
            .pointerInput(keyMask) {
                detectTapGestures(onPress = {
                    NativeBridge.nativeAddKeys(keyMask)
                    try {
                        tryAwaitRelease()
                    } finally {
                        NativeBridge.nativeClearKeys(keyMask)
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = NexoraText,
            fontSize = if (compact) 9.sp else 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


@Composable
private fun HomeScreen(onImportGame: () -> Unit, onOpenLibrary: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                stringResource(R.string.home_headline),
                color = NexoraText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.home_subtitle), color = NexoraMuted, fontSize = 13.sp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(NexoraPanel, RoundedCornerShape(26.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.featured_label), color = NexoraPink, fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .size(width = 150.dp, height = 190.dp)
                    .background(NexoraPanelLight, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("N", color = NexoraPurple, fontSize = 76.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.no_featured_game), color = NexoraText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.featured_description),
                color = NexoraMuted,
                fontSize = 12.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAction(
                modifier = Modifier.weight(1f),
                symbol = "＋",
                title = stringResource(R.string.add_games),
                onClick = onImportGame
            )
            QuickAction(
                modifier = Modifier.weight(1f),
                symbol = "▤",
                title = stringResource(R.string.open_library),
                onClick = onOpenLibrary
            )
        }
    }
}

@Composable
private fun QuickAction(modifier: Modifier, symbol: String, title: String, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .background(NexoraPanel, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, color = NexoraPurple, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(title, color = NexoraText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LibraryScreen(
    games: List<GameEntry>,
    message: String?,
    isLoadingGame: Boolean,
    onImportGame: () -> Unit,
    onPlayGame: (GameEntry) -> Unit,
    onClearMessage: () -> Unit,
    onRemoveGame: (GameEntry) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.library_label), color = NexoraText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.library_game_count, games.size), color = NexoraMuted, fontSize = 12.sp)
                Text(stringResource(R.string.library_not_playable_yet), color = NexoraMuted, fontSize = 10.sp)
            }
            Button(onClick = onImportGame) {
                Text(stringResource(R.string.add_games))
            }
        }

        if (message != null) {
            Text(
                text = message,
                color = NexoraPink,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onClearMessage)
            )
        }

        if (games.isEmpty()) {
            EmptyScreen(
                eyebrow = stringResource(R.string.library_label),
                title = stringResource(R.string.no_games_yet),
                description = stringResource(R.string.library_empty_description),
                symbol = "▦"
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(games, key = { it.uri }) { game ->
                    GameLibraryRow(
                        game = game,
                        isLoading = isLoadingGame,
                        onPlay = { onPlayGame(game) },
                        onRemove = { onRemoveGame(game) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameLibraryRow(
    game: GameEntry,
    isLoading: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexoraPanel, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(NexoraPanelLight, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("GBA", color = NexoraPurple, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(game.name, color = NexoraText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(formatFileSize(game.sizeBytes), color = NexoraMuted, fontSize = 11.sp)
        }
        TextButton(onClick = onPlay, enabled = !isLoading) {
            Text(stringResource(R.string.load_game), color = NexoraPurple)
        }
        TextButton(onClick = onRemove, enabled = !isLoading) {
            Text(stringResource(R.string.remove), color = NexoraPink)
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val megabytes = bytes / (1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", megabytes)
}

@Composable
private fun HistoryScreen() {
    EmptyScreen(
        eyebrow = stringResource(R.string.history_label),
        title = stringResource(R.string.no_history_yet),
        description = stringResource(R.string.history_empty_description),
        symbol = "◷"
    )
}

@Composable
private fun EmptyScreen(eyebrow: String, title: String, description: String, symbol: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(eyebrow.uppercase(), color = NexoraPink, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(22.dp))
        Text(symbol, color = NexoraPurple, fontSize = 54.sp)
        Spacer(Modifier.height(14.dp))
        Text(title, color = NexoraText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(description, color = NexoraMuted, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsScreen(nativeStatus: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.settings_label), color = NexoraText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        SettingsRow(stringResource(R.string.appearance_setting), stringResource(R.string.appearance_description))
        SettingsRow(stringResource(R.string.language_setting), stringResource(R.string.language_description))
        SettingsRow(stringResource(R.string.controls_setting), stringResource(R.string.controls_description))
        SettingsRow(stringResource(R.string.about_setting), stringResource(R.string.about_description))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.technical_status), color = NexoraPink, fontSize = 10.sp, letterSpacing = 1.5.sp)
        val statusText = when (nativeStatus) {
            2 -> stringResource(R.string.native_core_ready)
            1 -> stringResource(R.string.native_core_init_failed)
            0 -> stringResource(R.string.native_core_create_failed)
            else -> stringResource(R.string.native_bridge_unavailable)
        }
        Text(statusText, color = NexoraMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsRow(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().background(NexoraPanel, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, color = NexoraText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = NexoraMuted, fontSize = 11.sp)
    }
}

@Composable
private fun BottomNavigation(selectedTab: NexoraTab, onSelect: (NexoraTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexoraPanel)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavigationItem("⌂", stringResource(R.string.nav_home), NexoraTab.HOME, selectedTab, onSelect)
        NavigationItem("▦", stringResource(R.string.nav_library), NexoraTab.LIBRARY, selectedTab, onSelect)
        NavigationItem("◷", stringResource(R.string.nav_history), NexoraTab.HISTORY, selectedTab, onSelect)
        NavigationItem("⚙", stringResource(R.string.nav_settings), NexoraTab.SETTINGS, selectedTab, onSelect)
    }
}

@Composable
private fun NavigationItem(
    symbol: String,
    label: String,
    tab: NexoraTab,
    selectedTab: NexoraTab,
    onSelect: (NexoraTab) -> Unit
) {
    val selected = selectedTab == tab
    Column(
        modifier = Modifier
            .clickable { onSelect(tab) }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(symbol, color = if (selected) NexoraPurple else NexoraMuted, fontSize = 21.sp)
        Text(
            label,
            color = if (selected) NexoraText else NexoraMuted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
