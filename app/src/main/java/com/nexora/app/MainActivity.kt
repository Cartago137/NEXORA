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
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

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

    private fun loadGame(game: GameEntry, onResult: (Int) -> Unit) {
        Thread {
            val extension = game.name.substringAfterLast('.', "gba").lowercase(Locale.ROOT)
            val cachedRom = File(cacheDir, "nexora-${System.nanoTime()}.$extension")
            val copied = runCatching {
                val source = contentResolver.openInputStream(Uri.parse(game.uri))
                    ?: error("The selected document could not be opened.")
                source.use { input ->
                    cachedRom.outputStream().use { output -> input.copyTo(output) }
                }
            }.isSuccess

            val status = if (copied) {
                runCatching { NativeBridge.nativeLoadRom(cachedRom.absolutePath) }.getOrDefault(-1)
            } else {
                -2
            }

            runOnUiThread {
                // nativeLoadRom replaces any previous session before loading the new ROM.
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

        // Hide the Android status bar (clock, notifications, battery and signal).
        // The system navigation gesture/bar remains available.
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContent {
            var games by remember { mutableStateOf(GameLibraryStore.load(this@MainActivity)) }
            var libraryMessage by remember { mutableStateOf<String?>(null) }
            var isLoadingGame by remember { mutableStateOf(false) }

            val romPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val readWriteFlags =
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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
                        libraryMessage = getString(R.string.file_access_permission_failed)
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

                        val extension = displayName.first.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (extension !in setOf("gba", "agb", "bin")) {
                            libraryMessage = getString(R.string.unsupported_rom_format)
                            releaseDocumentAccess(uri)
                        } else if (games.any { it.uri == uri.toString() }) {
                            libraryMessage = getString(R.string.game_already_added)
                        } else {
                            val entry = GameEntry(displayName.first, uri.toString(), displayName.second)
                            games = games + entry
                            GameLibraryStore.save(this@MainActivity, games)
                            libraryMessage = getString(R.string.game_added_success)
                        }
                    }
                }
            }

            MaterialTheme {
                val nativeStatus = remember {
                    runCatching { NativeBridge.nativeStatus() }.getOrDefault(-1)
                }

                NexoraApp(
                    nativeStatus = nativeStatus,
                    games = games,
                    libraryMessage = libraryMessage,
                    isLoadingGame = isLoadingGame,
                    onImportGame = { romPicker.launch(arrayOf("*/*")) },
                    onPlayGame = { game, onLoaded ->
                        isLoadingGame = true
                        loadGame(game) { status ->
                            isLoadingGame = false
                            if (status != 4) {
                                libraryMessage = when (status) {
                                    -2 -> getString(R.string.game_file_copy_failed)
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
    games: List<GameEntry>,
    libraryMessage: String?,
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

    Surface(modifier = Modifier.fillMaxSize(), color = NexoraBackground) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (activeGameName != null) {
                    PlayerScreen(
                        gameName = activeGameName!!,
                        onExit = {
                            onExitGame()
                            activeGameName = null
                        }
                    )
                } else when (selectedTab) {
                    NexoraTab.HOME -> HomeScreen(
                        onImportGame = {
                            selectedTab = NexoraTab.LIBRARY
                            onImportGame()
                        },
                        onOpenLibrary = { selectedTab = NexoraTab.LIBRARY }
                    )
                    NexoraTab.LIBRARY -> LibraryScreen(
                        games = games,
                        message = libraryMessage,
                        isLoadingGame = isLoadingGame,
                        onImportGame = onImportGame,
                        onPlayGame = { game ->
                            onPlayGame(game) { status ->
                                if (status == 4) activeGameName = game.name
                            }
                        },
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
private fun PlayerScreen(gameName: String, onExit: () -> Unit) {
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val audioTrack = remember {
        val minBuffer = AudioTrack.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(44100)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    DisposableEffect(audioTrack) {
        audioTrack.play()
        onDispose {
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
            audioTrack.release()
        }
    }

    LaunchedEffect(audioTrack) {
        val samples = ShortArray(2048)
        while (isActive) {
            val frames = withContext(Dispatchers.Default) {
                runCatching { NativeBridge.nativeReadAudio(samples) }.getOrDefault(0)
            }
            if (frames > 0) {
                withContext(Dispatchers.IO) {
                    audioTrack.write(
                        samples, 0, frames * 2, AudioTrack.WRITE_BLOCKING
                    )
                }
            } else {
                delay(4)
            }
        }
    }

    LaunchedEffect(gameName) {
        while (isActive) {
            val pixels = withContext(Dispatchers.Default) {
                runCatching { NativeBridge.nativeRunFrame() }.getOrNull()
            }
            if (pixels != null && pixels.size == 240 * 160) {
                frame = Bitmap.createBitmap(
                    pixels, 240, 160, Bitmap.Config.ARGB_8888
                ).asImageBitmap()
            }
            delay(16)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(gameName, color = NexoraText, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                NativeBridge.nativeSetKeys(0)
                onExit()
            }) {
                Text("SAIR", color = NexoraPink)
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (frame != null) {
                Image(
                    bitmap = frame!!,
                    contentDescription = "Tela do jogo GBA",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("Iniciando jogo…", color = NexoraMuted)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GbaControl("↑", 1 shl 6)
                Row {
                    GbaControl("←", 1 shl 5)
                    GbaControl("↓", 1 shl 7)
                    GbaControl("→", 1 shl 4)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GbaControl("B", 1 shl 1)
                GbaControl("A", 1 shl 0)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            GbaControl("SELECT", 1 shl 2, compact = true)
            GbaControl("START", 1 shl 3, compact = true)
            GbaControl("L", 1 shl 9, compact = true)
            GbaControl("R", 1 shl 8, compact = true)
        }
    }
}

@Composable
private fun GbaControl(label: String, keyMask: Int, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .padding(3.dp)
            .size(width = if (compact) 58.dp else 52.dp, height = if (compact) 38.dp else 52.dp)
            .background(NexoraPanelLight, RoundedCornerShape(14.dp))
            .pointerInput(keyMask) {
                detectTapGestures(onPress = {
                    NativeBridge.nativeSetKeys(keyMask)
                    tryAwaitRelease()
                    NativeBridge.nativeSetKeys(0)
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = NexoraText, fontSize = if (compact) 9.sp else 16.sp, fontWeight = FontWeight.Bold)
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
