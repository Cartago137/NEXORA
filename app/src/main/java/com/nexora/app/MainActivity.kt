package com.nexora.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.nexora.app.link.LinkProtocol
import com.nexora.app.link.WifiLinkTransport
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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

private fun gameStateKey(uri: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(uri.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }


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
                                extension !in setOf("gba", "agb", "bin", "gb", "gbc") -> {
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
    var activeGameSaveKey by remember { mutableStateOf<String?>(null) }
    var gamePendingRemoval by remember { mutableStateOf<GameEntry?>(null) }
    var removalFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var touchControlSettings by remember { mutableStateOf(TouchControlSettingsStore.load(context)) }
    var showTouchEditor by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = showTouchEditor) { showTouchEditor = false }
    fun updateTouchControlSettings(updated: TouchControlSettings) {
        touchControlSettings = updated
        TouchControlSettingsStore.save(context, updated)
    }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkTransport by remember { mutableStateOf<WifiLinkTransport?>(null) }
    var linkMessage by remember { mutableStateOf<String?>(null) }
    var linkBusy by remember { mutableStateOf(false) }
    var roomCode by remember { mutableStateOf<String?>(null) }
    var hostIp by remember { mutableStateOf(WifiLinkTransport.localIpv4Address()) }
    var joinIp by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    val linkScope = rememberCoroutineScope()
    var linkConnectJob by remember { mutableStateOf<Job?>(null) }
    val roomCodeRandom = remember { SecureRandom() }
    val lastPongNonce = remember { AtomicLong(0L) }

    fun disconnectLink(finalMessage: String? = null) {
        if (linkBusy && linkTransport == null && linkConnectJob == null) return
        val pendingConnect = linkConnectJob
        pendingConnect?.cancel()
        linkConnectJob = null
        val active = linkTransport
        NativeBridge.nativeLinkSetConnected(false)
        linkTransport = null
        roomCode = null
        joinCode = ""
        linkBusy = true
        linkScope.launch(Dispatchers.IO) {
            pendingConnect?.join()
            if (active != null) runCatching { active.close() }
            runCatching { NativeBridge.nativeLinkDetach() }
            linkScope.launch {
                linkBusy = false
                linkMessage = finalMessage ?: context.getString(R.string.link_disconnected)
            }
        }
    }

    fun installLinkTransport(transport: WifiLinkTransport) {
        NativeBridge.nativeLinkSetConnected(true)
        if (!NativeBridge.nativeLinkAttach(transport.session.localPlayerId)) {
            NativeBridge.nativeLinkSetConnected(false)
            transport.close()
            linkBusy = false
            linkMessage = context.getString(R.string.link_native_attach_failed)
            return
        }

        lastPongNonce.set(0L)
        linkTransport = transport
        linkBusy = false
        linkMessage = context.getString(R.string.link_connected_instruction)
        transport.startReceiving(
            onMessage = { message ->
                when (message) {
                    is LinkProtocol.SerialTransfer -> {
                        val accepted = message.mode == LinkProtocol.SerialMode.MULTIPLAYER &&
                            message.bitWidth == 16 &&
                            NativeBridge.nativeLinkReceiveSerialTransfer(
                                message.transferId,
                                message.cycle,
                                message.baudCode,
                                message.dataOut and 0xFFFF,
                                message.sioControl,
                                message.rcnt
                            )
                        if (!accepted) {
                            NativeBridge.nativeLinkSetConnected(false)
                            linkScope.launch {
                                linkMessage = context.getString(R.string.link_transfer_rejected)
                            }
                        }
                    }
                    is LinkProtocol.SerialResult -> {
                        val localWord = message.receivedWords.getOrNull(1)
                            ?: message.receivedWords.firstOrNull()
                            ?: 0xFFFF
                        val accepted = NativeBridge.nativeLinkReceiveSerialResult(
                            message.transferId,
                            message.cycle,
                            localWord,
                            message.sioControl,
                            0
                        )
                        if (!accepted) {
                            NativeBridge.nativeLinkSetConnected(false)
                            linkScope.launch {
                                linkMessage = context.getString(R.string.link_transfer_rejected)
                            }
                        }
                    }
                    is LinkProtocol.Ping -> runCatching {
                        transport.send(LinkProtocol.Pong(message.nonce))
                    }
                    is LinkProtocol.Pong -> lastPongNonce.set(message.nonce)
                    is LinkProtocol.Goodbye -> NativeBridge.nativeLinkSetConnected(false)
                    is LinkProtocol.ProtocolError -> {
                        NativeBridge.nativeLinkSetConnected(false)
                        linkScope.launch {
                            linkMessage = context.getString(R.string.link_remote_ended_reason, message.message)
                        }
                    }
                    else -> Unit
                }
            },
            onDisconnected = { error ->
                NativeBridge.nativeLinkSetConnected(false)
                linkScope.launch {
                    if (linkTransport === transport) {
                        linkMessage = error?.message?.let {
                            context.getString(R.string.link_connection_lost, it)
                        } ?: context.getString(R.string.link_remote_ended)
                    }
                }
            }
        )
    }

    LaunchedEffect(linkTransport) {
        val active = linkTransport ?: return@LaunchedEffect
        var nextPingNonce = 1L
        var awaitingPongNonce: Long? = null
        var pingSentAtNanos = 0L
        var nextPingAtNanos = System.nanoTime() + 3_000_000_000L

        while (isActive && linkTransport === active) {
            if (NativeBridge.nativeLinkStatus() != 1) {
                disconnectLink(context.getString(R.string.link_session_interrupted))
                break
            }

            val nowNanos = System.nanoTime()
            val waitingForPong = awaitingPongNonce
            if (waitingForPong != null) {
                if (lastPongNonce.get() == waitingForPong) {
                    awaitingPongNonce = null
                    nextPingAtNanos = nowNanos + 3_000_000_000L
                } else if (nowNanos - pingSentAtNanos > 10_000_000_000L) {
                    disconnectLink(context.getString(R.string.link_heartbeat_timeout))
                    break
                }
            }

            val packet = NativeBridge.nativeLinkTakeOutgoing()
            if (packet == null) {
                if (awaitingPongNonce == null && nowNanos >= nextPingAtNanos) {
                    val nonce = nextPingNonce++
                    try {
                        withContext(Dispatchers.IO) {
                            active.send(LinkProtocol.Ping(nonce))
                        }
                        awaitingPongNonce = nonce
                        pingSentAtNanos = nowNanos
                    } catch (error: Throwable) {
                        disconnectLink(
                            context.getString(
                                R.string.link_send_failed,
                                error.message ?: context.getString(R.string.link_network_error)
                            )
                        )
                        break
                    }
                }
                delay(2)
                continue
            }
            if (packet.size != 7) {
                disconnectLink(context.getString(R.string.link_invalid_event))
                break
            }
            val message: LinkProtocol.Message? = when (packet[0].toInt()) {
                1 -> LinkProtocol.SerialTransfer(
                    transferId = packet[1],
                    cycle = packet[2],
                    mode = LinkProtocol.SerialMode.MULTIPLAYER,
                    baudCode = packet[3].toInt(),
                    bitWidth = 16,
                    dataOut = packet[4].toInt() and 0xFFFF,
                    sioControl = packet[5].toInt() and 0xFFFF,
                    rcnt = packet[6].toInt() and 0xFFFF
                )
                2 -> LinkProtocol.SerialResult(
                    transferId = packet[1],
                    cycle = packet[2],
                    receivedWords = listOf(0xFFFF, packet[4].toInt() and 0xFFFF),
                    sioControl = packet[5].toInt() and 0xFFFF
                )
                else -> null
            }
            if (message == null) {
                disconnectLink(context.getString(R.string.link_unknown_event))
                break
            }
            try {
                withContext(Dispatchers.IO) { active.send(message) }
            } catch (error: Throwable) {
                disconnectLink(
                    context.getString(
                        R.string.link_send_failed,
                        error.message ?: context.getString(R.string.link_network_error)
                    )
                )
                break
            }
        }
    }

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
                activeGameSaveKey = gameStateKey(game.uri)
                lastPlayedUri = game.uri
                context.getSharedPreferences("nexora_home", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_played_uri", game.uri)
                    .apply()
            }
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(modifier = Modifier.fillMaxSize(), color = NexoraBackground) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.nexora_cat_mascot),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .padding(start = 9.dp)
                            .size(42.dp)
                    )
                    Column(modifier = Modifier.padding(start = 10.dp)) {
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
                        saveStateKey = activeGameSaveKey ?: gameStateKey(activeGameName!!),
                        controlSettings = touchControlSettings,
                        isAppInForeground = isAppInForeground,
                        isLinkConnected = linkTransport != null || linkBusy,
                        onOpenLink = {
                            hostIp = WifiLinkTransport.localIpv4Address()
                            showLinkDialog = true
                        },
                        onExit = {
                            disconnectLink()
                            onExitGame()
                            activeGameName = null
                            activeGameSaveKey = null
                        }
                    )
                    if (showLinkDialog) {
                        val gameCode = NativeBridge.nativeGetGameCode()
                        val romCrc32 = NativeBridge.nativeGetRomCrc32()
                        AlertDialog(
                            onDismissRequest = { if (!linkBusy) showLinkDialog = false },
                            title = { Text(stringResource(R.string.link_dialog_title), color = NexoraText) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (linkTransport != null) {
                                        Text(stringResource(R.string.link_room_connected), color = NexoraPurple, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.link_player_connected, linkTransport!!.session.localPlayerId + 1), color = NexoraText)
                                        Text(stringResource(R.string.link_cable_club_instruction), color = NexoraMuted, fontSize = 12.sp)
                                        Button(onClick = { disconnectLink() }) { Text(stringResource(R.string.link_disconnect)) }
                                    } else {
                                        Text(stringResource(R.string.link_wifi_same_rom), color = NexoraMuted, fontSize = 12.sp)
                                        Button(
                                            onClick = {
                                                val code = (1000 + roomCodeRandom.nextInt(9000)).toString()
                                                roomCode = code
                                                hostIp = WifiLinkTransport.localIpv4Address()
                                                linkBusy = true
                                                linkMessage = context.getString(R.string.link_room_waiting)
                                                linkConnectJob = linkScope.launch {
                                                    val connectionJob = currentCoroutineContext()[Job]
                                                    try {
                                                        val session = withContext(Dispatchers.IO) {
                                                            WifiLinkTransport.host(
                                                                gameCode = gameCode,
                                                                romCrc32 = romCrc32,
                                                                sessionId = WifiLinkTransport.roomSessionId(code),
                                                                onListening = { address ->
                                                                    linkScope.launch {
                                                                        hostIp = address
                                                                        linkMessage = context.getString(R.string.link_room_waiting_short)
                                                                    }
                                                                },
                                                                shouldCancel = { connectionJob?.isActive != true }
                                                            )
                                                        }
                                                        linkConnectJob = null
                                                        installLinkTransport(session)
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (error: Throwable) {
                                                        linkConnectJob = null
                                                        linkBusy = false
                                                        roomCode = null
                                                        linkMessage = context.getString(R.string.link_create_room_failed, error.message ?: context.getString(R.string.link_timeout))
                                                    }
                                                }
                                            },
                                            enabled = !linkBusy && gameCode.length == 4 && romCrc32 != 0L
                                        ) { Text(stringResource(R.string.link_create_room)) }
                                        if (roomCode != null) {
                                            Text(stringResource(R.string.link_room_code, roomCode ?: ""), color = NexoraPink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                stringResource(R.string.link_host_ip),
                                                color = NexoraMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "$hostIp:${WifiLinkTransport.DEFAULT_PORT}",
                                                color = NexoraText,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(NexoraPanelLight, RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 11.dp)
                                            )
                                            Text(stringResource(R.string.link_share_ip_code), color = NexoraMuted, fontSize = 11.sp)
                                        }
                                        TextField(
                                            value = joinIp,
                                            onValueChange = { joinIp = it },
                                            label = { Text(stringResource(R.string.link_host_ip_label)) },
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedTextColor = NexoraText,
                                                unfocusedTextColor = NexoraText,
                                                focusedContainerColor = NexoraPanelLight,
                                                unfocusedContainerColor = NexoraPanelLight,
                                                focusedLabelColor = NexoraPurple,
                                                unfocusedLabelColor = NexoraMuted,
                                                cursorColor = NexoraPurple,
                                                focusedIndicatorColor = NexoraPurple,
                                                unfocusedIndicatorColor = NexoraMuted
                                            )
                                        )
                                        TextField(
                                            value = joinCode,
                                            onValueChange = { joinCode = it.filter(Char::isDigit).take(4) },
                                            label = { Text(stringResource(R.string.link_room_code_label)) },
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedTextColor = NexoraText,
                                                unfocusedTextColor = NexoraText,
                                                focusedContainerColor = NexoraPanelLight,
                                                unfocusedContainerColor = NexoraPanelLight,
                                                focusedLabelColor = NexoraPurple,
                                                unfocusedLabelColor = NexoraMuted,
                                                cursorColor = NexoraPurple,
                                                focusedIndicatorColor = NexoraPurple,
                                                unfocusedIndicatorColor = NexoraMuted
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                linkBusy = true
                                                linkMessage = context.getString(R.string.link_joining)
                                                val code = joinCode
                                                val address = joinIp.trim()
                                                linkConnectJob = linkScope.launch {
                                                    val connectionJob = currentCoroutineContext()[Job]
                                                    try {
                                                        val session = withContext(Dispatchers.IO) {
                                                            WifiLinkTransport.join(
                                                                hostAddress = address,
                                                                expectedSessionId = WifiLinkTransport.roomSessionId(code),
                                                                gameCode = gameCode,
                                                                romCrc32 = romCrc32,
                                                                shouldCancel = { connectionJob?.isActive != true }
                                                            )
                                                        }
                                                        linkConnectJob = null
                                                        installLinkTransport(session)
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (error: Throwable) {
                                                        linkConnectJob = null
                                                        linkBusy = false
                                                        linkMessage = context.getString(R.string.link_join_room_failed, error.message ?: context.getString(R.string.link_network_error))
                                                    }
                                                }
                                            },
                                            enabled = !linkBusy && joinIp.isNotBlank() &&
                                                joinCode.length == 4 && gameCode.length == 4 &&
                                                romCrc32 != 0L
                                        ) { Text(stringResource(R.string.link_join_room)) }
                                        if (linkBusy) Text(stringResource(R.string.link_waiting_connection), color = NexoraPurple, fontSize = 12.sp)
                                    }
                                    linkMessage?.let {
                                        Text(it, color = if (linkTransport != null) NexoraPurple else NexoraMuted, fontSize = 12.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showLinkDialog = false }, enabled = !linkBusy) {
                                    Text(stringResource(R.string.close), color = NexoraText)
                                }
                            },
                            containerColor = NexoraPanel,
                            titleContentColor = NexoraText,
                            textContentColor = NexoraText
                        )
                    }
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
                        onRemoveSelectedGames = { selected -> selected.forEach { onRemoveGame(it, false) } },
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
                    NexoraTab.SETTINGS -> if (showTouchEditor) {
                        TouchLayoutEditorScreen(
                            settings = touchControlSettings,
                            onSettingsChange = ::updateTouchControlSettings,
                            onBack = { showTouchEditor = false }
                        )
                    } else {
                        SettingsScreen(
                            nativeStatus = nativeStatus,
                            touchSettings = touchControlSettings,
                            onTouchSettingsChange = ::updateTouchControlSettings,
                            onOpenTouchEditor = { showTouchEditor = true }
                        )
                    }
                }
            }

            if (activeGameName == null && !isLandscape) {
                BottomNavigation(selectedTab = selectedTab, onSelect = { selectedTab = it })
            }
            }

            if (activeGameName == null && isLandscape) {
                SideNavigation(selectedTab = selectedTab, onSelect = { selectedTab = it })
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
private fun PlayerScreen(
    gameName: String,
    saveStateKey: String,
    controlSettings: TouchControlSettings,
    isAppInForeground: Boolean,
    isLinkConnected: Boolean,
    onOpenLink: () -> Unit,
    onExit: () -> Unit
) {
    val videoWidth = remember(saveStateKey) {
        NativeBridge.nativeGetVideoWidth().takeIf { it > 0 } ?: 240
    }
    val videoHeight = remember(saveStateKey) {
        NativeBridge.nativeGetVideoHeight().takeIf { it > 0 } ?: 160
    }
    val isGbFamily = remember(saveStateKey) { NativeBridge.nativeGetPlatform() == 1 }
    val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
    val pixelBuffer = remember(saveStateKey) { IntArray(videoWidth * videoHeight) }
    val frameBitmap = remember(saveStateKey) {
        Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
    }
    val frameImage = remember(saveStateKey, frameBitmap) { frameBitmap.asImageBitmap() }
    var frameReady by remember { mutableStateOf(false) }
    var frameError by remember { mutableStateOf(false) }
    var frameVersion by remember { mutableIntStateOf(0) }
    var emulationSpeed by remember { mutableFloatStateOf(1.0f) }
    var showFunctionsMenu by remember { mutableStateOf(false) }
    var functionsBusy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val functionsScope = rememberCoroutineScope()
    val stateFile = remember(saveStateKey) {
        File(File(context.filesDir, "save_states"), "$saveStateKey.state")
    }

    LaunchedEffect(gameName, saveStateKey, isAppInForeground, showFunctionsMenu) {
        if (!isAppInForeground || showFunctionsMenu) {
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
        var appliedAudioSpeed = 1.0f

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
                // Keep Android audio consumption in step with emulation speed.
                // Matching playback rate prevents an ever-growing PCM queue.
                if (appliedAudioSpeed != emulationSpeed) {
                    val requestedRate = (outputSampleRate * emulationSpeed).toInt()
                    createdAudioTrack?.setPlaybackRate(requestedRate)
                    appliedAudioSpeed = emulationSpeed
                }

                val frameStartNanos = System.nanoTime()
                val rendered = withContext(Dispatchers.Default) {
                    runCatching {
                        val success = NativeBridge.nativeRunFrame(pixelBuffer)
                        if (success) {
                            // Keep pixel conversion/copying off the UI thread.
                            frameBitmap.setPixels(pixelBuffer, 0, videoWidth, 0, 0, videoWidth, videoHeight)
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

                val frameIntervalNanos = (targetFrameNanos / emulationSpeed).toLong()
                val remainingNanos = frameIntervalNanos - (System.nanoTime() - frameStartNanos)
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
        if (showFunctionsMenu) {
            if (!functionsBusy) showFunctionsMenu = false
        } else {
            NativeBridge.nativeSetKeys(0)
            onExit()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(NexoraBackground)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // Landscape: keep the complete GBA image in the center, with
            // the approved control arrangement on both sides.
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NexoraControlShell(Modifier.width(224.dp).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize().padding(8.dp)) {
                        if (!isGbFamily) {
                            Box(Modifier.align(Alignment.TopStart)) {
                                GbaControl("L", 1 shl 9, shoulderSize = controlSettings.lSizeDp, systemButtonSize = controlSettings.systemButtonSizeDp, opacity = controlSettings.opacityPercent / 100f, offsetXDp = controlSettings.lOffsetXDp, offsetYDp = controlSettings.lOffsetYDp)
                            }
                        }
                        Box(Modifier.align(Alignment.CenterStart)) {
                            GbaDPad(sizeDp = controlSettings.dpadSizeDp, opacity = controlSettings.opacityPercent / 100f, offsetXDp = controlSettings.dpadOffsetXDp, offsetYDp = controlSettings.dpadOffsetYDp)
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        GameFrame(frameError, frameReady, frameVersion, frameImage, videoAspectRatio)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GbaMenuControl(onClick = { showFunctionsMenu = true }, sizeDp = controlSettings.menuSizeDp, opacity = controlSettings.opacityPercent / 100f, offsetXDp = controlSettings.menuOffsetXDp, offsetYDp = controlSettings.menuOffsetYDp)
                        Spacer(Modifier.width(8.dp))
                        GbaControl("SELECT", 1 shl 2, systemButtonSize = controlSettings.selectSizeDp, opacity = controlSettings.opacityPercent / 100f, offsetXDp = controlSettings.selectOffsetXDp, offsetYDp = controlSettings.selectOffsetYDp)
                        Spacer(Modifier.width(8.dp))
                        GbaControl("START", 1 shl 3, systemButtonSize = controlSettings.startSizeDp, opacity = controlSettings.opacityPercent / 100f, offsetXDp = controlSettings.startOffsetXDp, offsetYDp = controlSettings.startOffsetYDp)
                    }
                }

                NexoraControlShell(Modifier.width(224.dp).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize().padding(8.dp)) {
                        if (!isGbFamily) {
                            Box(Modifier.align(Alignment.TopEnd)) {
                                GbaControl("R", 1 shl 8, shoulderSize = controlSettings.rSizeDp, systemButtonSize = controlSettings.systemButtonSizeDp, opacity = controlSettings.opacityPercent / 100f, offsetXDp = controlSettings.rOffsetXDp, offsetYDp = controlSettings.rOffsetYDp)
                            }
                        }
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GbaControl("B", 1 shl 1, controlSize = controlSettings.bSizeDp, systemButtonSize = controlSettings.systemButtonSizeDp, opacity = controlSettings.opacityPercent / 100f, offsetXDp = controlSettings.bOffsetXDp, offsetYDp = controlSettings.bOffsetYDp)
                            GbaControl("A", 1 shl 0, controlSize = controlSettings.aSizeDp, systemButtonSize = controlSettings.systemButtonSizeDp, opacity = controlSettings.opacityPercent / 100f, offsetXDp = controlSettings.aOffsetXDp, offsetYDp = controlSettings.aOffsetYDp)
                        }
                    }
                }
            }
        } else {
            // Portrait: preserve the game-above-controls hierarchy.
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GameFrame(frameError, frameReady, frameVersion, frameImage, videoAspectRatio)
                }

                GbaPortraitControlDeck(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    showShoulderButtons = !isGbFamily,
                    settings = controlSettings,
                    onMenu = { showFunctionsMenu = true }
                )
            }
        }

        if (showFunctionsMenu) {
            AlertDialog(
                onDismissRequest = { if (!functionsBusy) showFunctionsMenu = false },
                title = {
                    Text(stringResource(R.string.functions_menu_title), color = NexoraText, fontSize = 17.sp)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxHeight * 0.72f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        TextButton(
                            onClick = {
                                NativeBridge.nativeSetKeys(0)
                                showFunctionsMenu = false
                                onExit()
                            },
                            enabled = !functionsBusy
                        ) { Text(stringResource(R.string.exit_game), color = NexoraPink) }

                        TextButton(
                            onClick = {
                                functionsBusy = true
                                functionsScope.launch {
                                    val saved = withContext(Dispatchers.IO) {
                                        runCatching {
                                            stateFile.parentFile?.mkdirs()
                                            NativeBridge.nativeSaveState(stateFile.absolutePath)
                                        }.getOrDefault(false)
                                    }
                                    functionsBusy = false
                                    showFunctionsMenu = false
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            if (saved) R.string.save_state_success else R.string.save_state_failure
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !functionsBusy && !isLinkConnected
                        ) { Text(stringResource(R.string.save_state), color = NexoraText) }

                        TextButton(
                            onClick = {
                                functionsBusy = true
                                functionsScope.launch {
                                    val loaded = withContext(Dispatchers.IO) {
                                        runCatching {
                                            NativeBridge.nativeLoadState(stateFile.absolutePath)
                                        }.getOrDefault(false)
                                    }
                                    if (loaded) withContext(Dispatchers.IO) {
                                        runCatching { NativeBridge.nativeClearAudio() }
                                    }
                                    functionsBusy = false
                                    showFunctionsMenu = false
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            if (loaded) R.string.load_state_success else R.string.load_state_failure
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = !functionsBusy && !isLinkConnected && stateFile.exists()
                        ) { Text(stringResource(R.string.load_state), color = NexoraText) }

                        Text(stringResource(R.string.emulation_speed), color = NexoraMuted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(1.0f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                                TextButton(
                                    onClick = {
                                        emulationSpeed = speed
                                        showFunctionsMenu = false
                                    },
                                    enabled = !functionsBusy
                                ) {
                                    Text(
                                        "${speed}x",
                                        color = if (emulationSpeed == speed) NexoraPink else NexoraMuted,
                                        fontSize = 12.sp,
                                        fontWeight = if (emulationSpeed == speed) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                showFunctionsMenu = false
                                onOpenLink()
                            },
                            enabled = !functionsBusy && !isGbFamily
                        ) { Text(stringResource(R.string.link_cable_trade), color = NexoraText) }
                        if (isGbFamily) {
                            Text(
                                stringResource(R.string.link_cable_gba_only),
                                color = NexoraMuted,
                                fontSize = 10.sp
                            )
                        }

                        if (isLinkConnected) {
                            Text(
                                context.getString(R.string.save_states_disabled_link),
                                color = NexoraMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showFunctionsMenu = false },
                        enabled = !functionsBusy
                    ) { Text("Fechar", color = NexoraText) }
                },
                containerColor = NexoraPanel,
                titleContentColor = NexoraText,
                textContentColor = NexoraText
            )
        }
    }
}

@Composable
private fun GameFrame(
    frameError: Boolean,
    frameReady: Boolean,
    frameVersion: Int,
    frameImage: androidx.compose.ui.graphics.ImageBitmap,
    aspectRatio: Float
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
            // Preserve the active console's native aspect ratio (GBA 3:2, GB/GBC 10:9).
            val fittedWidth = minOf(maxWidth, maxHeight * aspectRatio)
            Canvas(
                modifier = Modifier.width(fittedWidth).aspectRatio(aspectRatio)
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
private fun NexoraAtlasSprite(cell: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Decode the raster resource directly. Compose's imageResource path can throw
    // when Android returns a non-BitmapDrawable for this packaged asset.
    val atlas = remember(context) {
        val decoded = runCatching {
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.nexora_controls_atlas,
                BitmapFactory.Options().apply {
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        }.onFailure {
            Log.e("NEXORA", "Failed to decode the official controls atlas.", it)
        }.getOrNull()

        if (decoded == null) {
            Log.e("NEXORA", "Official controls atlas is unavailable; vector fallback is active.")
        } else {
            Log.i("NEXORA", "Official controls atlas decoded: ${decoded.width}x${decoded.height}.")
        }
        decoded?.asImageBitmap()
    }

    Canvas(modifier) {
        val cellSize = 96
        if (atlas != null &&
            atlas.width >= 5 * cellSize &&
            atlas.height >= 3 * cellSize &&
            cell in 0..14
        ) {
            drawImage(
                image = atlas,
                srcOffset = IntOffset((cell % 5) * cellSize, (cell / 5) * cellSize),
                srcSize = IntSize(cellSize, cellSize),
                dstSize = IntSize(
                    size.width.toInt().coerceAtLeast(1),
                    size.height.toInt().coerceAtLeast(1)
                )
            )
        } else {
            // Keep the game usable if the optional atlas cannot be decoded.
            val center = Offset(size.width / 2f, size.height / 2f)
            val unit = minOf(size.width, size.height).coerceAtLeast(1f)
            val fill = Color(0xFF442A66)
            val edge = Color(0xFFB99AFF)
            if (cell in 0..4) {
                drawLine(fill, Offset(center.x - unit * 0.31f, center.y), Offset(center.x + unit * 0.31f, center.y), unit * 0.23f)
                drawLine(fill, Offset(center.x, center.y - unit * 0.31f), Offset(center.x, center.y + unit * 0.31f), unit * 0.23f)
                drawCircle(edge, unit * 0.12f, center)
            } else {
                drawCircle(fill, unit * 0.42f, center)
                drawCircle(edge, unit * 0.42f, center, style = Stroke(width = unit * 0.045f))
            }

            val glyph = when (cell) {
                0 -> "✣"
                1 -> "↑"
                2 -> "↓"
                3 -> "←"
                4 -> "→"
                5, 6 -> "M"
                7, 8 -> "A"
                9, 10 -> "B"
                11, 12 -> "L"
                else -> "R"
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = unit * 0.34f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            drawContext.canvas.nativeCanvas.drawText(
                glyph,
                center.x,
                center.y - (paint.ascent() + paint.descent()) / 2f,
                paint
            )
        }
    }
}

@Composable
private fun NexoraControlShell(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF160D23))
            .border(1.dp, Color(0xFF51366F), RoundedCornerShape(22.dp))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val points = listOf(
                0.10f to 0.15f, 0.24f to 0.78f, 0.43f to 0.20f,
                0.61f to 0.84f, 0.78f to 0.18f, 0.91f to 0.62f
            )
            points.forEachIndexed { index, point ->
                val center = Offset(size.width * point.first, size.height * point.second)
                val radius = if (index % 2 == 0) 2.2f else 1.4f
                drawCircle(Color(0xFFB99AFF).copy(alpha = 0.72f), radius, center)
                if (index % 2 == 0) {
                    drawLine(
                        Color(0xFFB99AFF).copy(alpha = 0.55f),
                        Offset(center.x - 4f, center.y),
                        Offset(center.x + 4f, center.y),
                        strokeWidth = 1f
                    )
                    drawLine(
                        Color(0xFFB99AFF).copy(alpha = 0.55f),
                        Offset(center.x, center.y - 4f),
                        Offset(center.x, center.y + 4f),
                        strokeWidth = 1f
                    )
                }
            }
        }
        content()
    }
}

@Composable
private fun NexoraTouchButton(
    normalCell: Int,
    pressedCell: Int,
    modifier: Modifier,
    keyMask: Int? = null,
    onClick: (() -> Unit)? = null
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.pointerInput(keyMask, onClick) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    if (keyMask != null) NativeBridge.nativeAddKeys(keyMask)
                    try {
                        tryAwaitRelease()
                    } finally {
                        if (keyMask != null) NativeBridge.nativeClearKeys(keyMask)
                        pressed = false
                    }
                },
                onTap = {
                    if (keyMask == null) onClick?.invoke()
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        NexoraAtlasSprite(
            cell = if (pressed) pressedCell else normalCell,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun GbaDPad(sizeDp: Int = 184, opacity: Float = 1f, offsetXDp: Int = 0, offsetYDp: Int = 0) {
    var visualCell by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .offset(x = offsetXDp.dp, y = offsetYDp.dp)
            .alpha(opacity.coerceIn(0.35f, 1f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var activeDirectionMask = 0

                    fun directionAt(position: Offset): Int {
                        val halfWidth = size.width / 2f
                        val halfHeight = size.height / 2f
                        if (halfWidth <= 0f || halfHeight <= 0f) return 0
                        val normalizedX = (position.x - halfWidth) / halfWidth
                        val normalizedY = (position.y - halfHeight) / halfHeight
                        val horizontal = if (abs(normalizedX) >= 0.28f) {
                            if (normalizedX < 0f) (1 shl 5) else (1 shl 4)
                        } else 0
                        val vertical = if (abs(normalizedY) >= 0.28f) {
                            if (normalizedY < 0f) (1 shl 6) else (1 shl 7)
                        } else 0
                        return horizontal or vertical
                    }

                    fun updateDirection(nextMask: Int) {
                        if (nextMask == activeDirectionMask) return
                        if (activeDirectionMask != 0) NativeBridge.nativeClearKeys(activeDirectionMask)
                        if (nextMask != 0) NativeBridge.nativeAddKeys(nextMask)
                        activeDirectionMask = nextMask
                        visualCell = when {
                            nextMask and (1 shl 6) != 0 -> 1
                            nextMask and (1 shl 7) != 0 -> 2
                            nextMask and (1 shl 5) != 0 -> 3
                            nextMask and (1 shl 4) != 0 -> 4
                            else -> 0
                        }
                    }

                    try {
                        updateDirection(directionAt(down.position))
                        var pointer = down
                        while (pointer.pressed) {
                            val event = awaitPointerEvent()
                            pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            updateDirection(directionAt(pointer.position))
                            pointer.consume()
                        }
                    } finally {
                        if (activeDirectionMask != 0) NativeBridge.nativeClearKeys(activeDirectionMask)
                        visualCell = 0
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        NexoraAtlasSprite(cell = visualCell, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun GbaControl(
    label: String,
    keyMask: Int,
    compact: Boolean = false,
    controlSize: Int = 52,
    shoulderSize: Int = 68,
    systemButtonSize: Int = 48,
    opacity: Float = 1f,
    offsetXDp: Int = 0,
    offsetYDp: Int = 0
) {
    val normalCell = when (label) {
        "A" -> 7
        "B" -> 9
        "L" -> 11
        "R" -> 13
        else -> 5
    }
    val pressedCell = normalCell + 1
    val isRoundLabel = label == "SELECT" || label == "START"
    val visualSize = when (label) {
        "L", "R" -> shoulderSize.dp
        "A", "B" -> controlSize.dp
        else -> systemButtonSize.dp
    }

    Column(
        modifier = Modifier.offset(x = offsetXDp.dp, y = offsetYDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        NexoraTouchButton(
            normalCell = normalCell,
            pressedCell = pressedCell,
            modifier = Modifier.size(visualSize).alpha(opacity.coerceIn(0.35f, 1f)),
            keyMask = keyMask
        )
        if (isRoundLabel) {
            Text(
                label,
                color = Color(0xFFE9DDF8),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GbaMenuControl(onClick: () -> Unit, sizeDp: Int = 48, opacity: Float = 1f, offsetXDp: Int = 0, offsetYDp: Int = 0) {
    Column(
        modifier = Modifier.offset(x = offsetXDp.dp, y = offsetYDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        NexoraTouchButton(
            normalCell = 5,
            pressedCell = 6,
            modifier = Modifier.size(sizeDp.dp).alpha(opacity.coerceIn(0.35f, 1f)),
            onClick = onClick
        )
        Text("MENU", color = Color(0xFFE9DDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GbaPortraitControlDeck(
    modifier: Modifier = Modifier,
    showShoulderButtons: Boolean = true,
    settings: TouchControlSettings = TouchControlSettings(),
    onMenu: () -> Unit
) {
    NexoraControlShell(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showShoulderButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GbaControl("L", 1 shl 9, shoulderSize = settings.lSizeDp, systemButtonSize = settings.systemButtonSizeDp, opacity = settings.opacityPercent / 100f, offsetXDp = settings.lOffsetXDp, offsetYDp = settings.lOffsetYDp)
                    GbaControl("R", 1 shl 8, shoulderSize = settings.rSizeDp, systemButtonSize = settings.systemButtonSizeDp, opacity = settings.opacityPercent / 100f, offsetXDp = settings.rOffsetXDp, offsetYDp = settings.rOffsetYDp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GbaDPad(sizeDp = settings.dpadSizeDp, opacity = settings.opacityPercent / 100f, offsetXDp = settings.dpadOffsetXDp, offsetYDp = settings.dpadOffsetYDp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GbaControl("B", 1 shl 1, controlSize = settings.bSizeDp, systemButtonSize = settings.systemButtonSizeDp, opacity = settings.opacityPercent / 100f, offsetXDp = settings.bOffsetXDp, offsetYDp = settings.bOffsetYDp)
                    GbaControl("A", 1 shl 0, controlSize = settings.aSizeDp, systemButtonSize = settings.systemButtonSizeDp, opacity = settings.opacityPercent / 100f, offsetXDp = settings.aOffsetXDp, offsetYDp = settings.aOffsetYDp)
                }
            }

            Row(
                modifier = Modifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GbaMenuControl(onClick = onMenu, sizeDp = settings.menuSizeDp, opacity = settings.opacityPercent / 100f, offsetXDp = settings.menuOffsetXDp, offsetYDp = settings.menuOffsetYDp)
                GbaControl("SELECT", 1 shl 2, systemButtonSize = settings.selectSizeDp, opacity = settings.opacityPercent / 100f, offsetXDp = settings.selectOffsetXDp, offsetYDp = settings.selectOffsetYDp)
                GbaControl("START", 1 shl 3, systemButtonSize = settings.startSizeDp, opacity = settings.opacityPercent / 100f, offsetXDp = settings.startOffsetXDp, offsetYDp = settings.startOffsetYDp)
            }
        }
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

private const val LIBRARY_UNFILED_FILTER = "__nexora_unfiled__"

@Composable
private fun LibraryScreen(
    games: List<GameEntry>,
    folders: List<GameFolder>,
    message: String?,
    isLoadingGame: Boolean,
    onImportGame: () -> Unit,
    onPlayGame: (GameEntry) -> Unit,
    onClearMessage: () -> Unit,
    onRemoveGame: (GameEntry) -> Unit,
    onSaveFolders: (List<GameFolder>) -> Unit,
    onUpdateGame: (GameEntry) -> Unit,
    onDeleteFolder: (GameFolder) -> Unit,
    onRemoveSelectedGames: (List<GameEntry>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedConsoleFilter by remember { mutableStateOf("ALL") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedGameUris by remember { mutableStateOf(setOf<String>()) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showRemoveSelectedDialog by remember { mutableStateOf(false) }
    var folderPendingDelete by remember { mutableStateOf<GameFolder?>(null) }

    val configuration = LocalConfiguration.current
    val sortedFolders = folders.sortedByDescending { it.isPinned }
    val visibleGames = games
        .filter { game ->
            val matchesFolder = when (selectedFolderId) {
                null -> true
                LIBRARY_UNFILED_FILTER -> game.folderId == null
                else -> game.folderId == selectedFolderId
            }
            val extension = game.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val matchesConsole = when (selectedConsoleFilter) {
                "GBA" -> extension in setOf("gba", "agb", "bin")
                "GB" -> extension == "gb"
                "GBC" -> extension == "gbc"
                else -> true
            }
            matchesFolder && matchesConsole &&
                game.name.contains(searchQuery.trim(), ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<GameEntry> { it.isPinned }
                .thenByDescending { it.isFavorite }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.library_label), color = NexoraText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.library_game_count, games.size), color = NexoraMuted, fontSize = 12.sp)
            }
            TextButton(onClick = { showCreateFolder = true }) {
                Text(stringResource(R.string.create_folder), color = NexoraPurple)
            }
            Button(onClick = onImportGame) {
                Text(stringResource(R.string.add_games))
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search_games)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.console_filter),
                color = NexoraMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(listOf("ALL", "GBA", "GB", "GBC")) { console ->
                    TextButton(onClick = { selectedConsoleFilter = console }) {
                        val label = when (console) {
                            "ALL" -> stringResource(R.string.console_all)
                            "GBA" -> stringResource(R.string.console_gba)
                            "GB" -> stringResource(R.string.console_gb)
                            else -> stringResource(R.string.console_gbc)
                        }
                        Text(
                            label,
                            color = if (selectedConsoleFilter == console) NexoraPink else NexoraMuted
                        )
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                TextButton(onClick = { selectedFolderId = null }) {
                    Text(
                        stringResource(R.string.all_games),
                        color = if (selectedFolderId == null) NexoraPink else NexoraMuted
                    )
                }
            }
            item {
                TextButton(onClick = { selectedFolderId = LIBRARY_UNFILED_FILTER }) {
                    Text(
                        stringResource(R.string.unfiled_games),
                        color = if (selectedFolderId == LIBRARY_UNFILED_FILTER) NexoraPink else NexoraMuted
                    )
                }
            }
            items(sortedFolders, key = { it.id }) { folder ->
                Column(
                    modifier = Modifier
                        .background(
                            if (selectedFolderId == folder.id) NexoraPanelLight else NexoraPanel,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(onClick = { selectedFolderId = folder.id }) {
                        Text(
                            text = (if (folder.isPinned) "📌 " else "▰ ") + folder.name,
                            color = if (selectedFolderId == folder.id) NexoraPink else NexoraText,
                            maxLines = 1
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            onSaveFolders(
                                folders.map {
                                    if (it.id == folder.id) it.copy(isPinned = !it.isPinned) else it
                                }
                            )
                        }) {
                            Text(
                                if (folder.isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin),
                                color = NexoraPurple,
                                fontSize = 10.sp
                            )
                        }
                        TextButton(onClick = { folderPendingDelete = folder }) {
                            Text(stringResource(R.string.delete_folder), color = NexoraPink, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.selected_games_count, selectedGameUris.size),
                    color = NexoraText,
                    fontSize = 12.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    item {
                        TextButton(
                            onClick = { showMoveDialog = true },
                            enabled = selectedGameUris.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.move_to_folder), color = NexoraPurple)
                        }
                    }
                    item {
                        TextButton(
                            onClick = {
                                val selected = games.filter { it.uri in selectedGameUris }
                                val markFavorite = !selected.all { it.isFavorite }
                                selected.forEach { onUpdateGame(it.copy(isFavorite = markFavorite)) }
                            },
                            enabled = selectedGameUris.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.favorite_selected), color = NexoraPink)
                        }
                    }
                    item {
                        TextButton(
                            onClick = {
                                val selected = games.filter { it.uri in selectedGameUris }
                                val markPinned = !selected.all { it.isPinned }
                                selected.forEach { onUpdateGame(it.copy(isPinned = markPinned)) }
                            },
                            enabled = selectedGameUris.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.pin_selected), color = NexoraPurple)
                        }
                    }
                    item {
                        TextButton(
                            onClick = { showRemoveSelectedDialog = true },
                            enabled = selectedGameUris.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.remove_selected), color = NexoraPink)
                        }
                    }
                    item {
                        TextButton(onClick = {
                            selectionMode = false
                            selectedGameUris = emptySet()
                        }) {
                            Text(stringResource(R.string.cancel), color = NexoraMuted)
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { selectionMode = true }) {
                    Text(stringResource(R.string.select_games), color = NexoraPurple)
                }
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
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp), contentAlignment = Alignment.Center) {
                EmptyScreen(
                    eyebrow = stringResource(R.string.library_label),
                    title = stringResource(R.string.no_games_yet),
                    description = stringResource(R.string.library_empty_description),
                    symbol = "▦"
                )
            }
        } else if (visibleGames.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.no_games_in_view), color = NexoraMuted)
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 460.dp)) {
                val columns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems(visibleGames, key = { it.uri }) { game ->
                        GameLibraryCard(
                            game = game,
                            isLoading = isLoadingGame,
                            selectionMode = selectionMode,
                            isSelected = game.uri in selectedGameUris,
                            onToggleSelect = {
                                selectedGameUris = if (game.uri in selectedGameUris) {
                                    selectedGameUris - game.uri
                                } else {
                                    selectedGameUris + game.uri
                                }
                            },
                            onPlay = { onPlayGame(game) },
                            onRemove = { onRemoveGame(game) },
                            onToggleFavorite = {
                                onUpdateGame(game.copy(isFavorite = !game.isFavorite))
                            },
                            onTogglePinned = {
                                onUpdateGame(game.copy(isPinned = !game.isPinned))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false; newFolderName = "" },
            containerColor = NexoraPanel,
            title = { Text(stringResource(R.string.create_folder), color = NexoraText) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.folder_name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleanName = newFolderName.trim()
                    if (cleanName.isNotEmpty() && folders.none { it.name.equals(cleanName, true) }) {
                        val createdFolder = GameFolder(
                            id = java.util.UUID.randomUUID().toString(),
                            name = cleanName
                        )
                        onSaveFolders(folders + createdFolder)
                        selectedFolderId = createdFolder.id
                        showCreateFolder = false
                        newFolderName = ""
                    }
                }, enabled = newFolderName.isNotBlank() && folders.none { it.name.equals(newFolderName.trim(), true) }) {
                    Text(stringResource(R.string.create), color = NexoraPurple)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false; newFolderName = "" }) {
                    Text(stringResource(R.string.cancel), color = NexoraMuted)
                }
            }
        )
    }

    if (showRemoveSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveSelectedDialog = false },
            containerColor = NexoraPanel,
            title = { Text(stringResource(R.string.remove_selected), color = NexoraText) },
            text = { Text(stringResource(R.string.remove_selected_question, selectedGameUris.size), color = NexoraMuted) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveSelectedGames(games.filter { it.uri in selectedGameUris })
                    selectedGameUris = emptySet()
                    selectionMode = false
                    showRemoveSelectedDialog = false
                }) {
                    Text(stringResource(R.string.remove_only), color = NexoraPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveSelectedDialog = false }) {
                    Text(stringResource(R.string.cancel), color = NexoraMuted)
                }
            }
        )
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            containerColor = NexoraPanel,
            title = { Text(stringResource(R.string.move_to_folder), color = NexoraText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        games.filter { it.uri in selectedGameUris }.forEach {
                            onUpdateGame(it.copy(folderId = null))
                        }
                        selectedGameUris = emptySet()
                        selectionMode = false
                        showMoveDialog = false
                    }) {
                        Text(stringResource(R.string.unfiled_games), color = NexoraText)
                    }
                    folders.forEach { folder ->
                        TextButton(onClick = {
                            games.filter { it.uri in selectedGameUris }.forEach {
                                onUpdateGame(it.copy(folderId = folder.id))
                            }
                            selectedGameUris = emptySet()
                            selectionMode = false
                            showMoveDialog = false
                        }) {
                            Text(folder.name, color = NexoraText)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text(stringResource(R.string.cancel), color = NexoraMuted)
                }
            }
        )
    }

    folderPendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderPendingDelete = null },
            containerColor = NexoraPanel,
            title = { Text(stringResource(R.string.delete_folder), color = NexoraText) },
            text = { Text(stringResource(R.string.delete_folder_question, folder.name), color = NexoraMuted) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(folder)
                    if (selectedFolderId == folder.id) selectedFolderId = null
                    folderPendingDelete = null
                }) {
                    Text(stringResource(R.string.delete_folder), color = NexoraPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingDelete = null }) {
                    Text(stringResource(R.string.cancel), color = NexoraMuted)
                }
            }
        )
    }
}

@Composable
private fun GameLibraryCard(
    game: GameEntry,
    isLoading: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexoraPanel, RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
            } else {
                TextButton(onClick = onToggleFavorite) {
                    Text(if (game.isFavorite) "★" else "☆", color = NexoraPink, fontSize = 17.sp)
                }
                TextButton(onClick = onTogglePinned) {
                    Text(if (game.isPinned) "📌" else "⌖", color = NexoraPurple, fontSize = 15.sp)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !selectionMode && !isLoading, onClick = onPlay)
        ) {
            GameCoverArtwork(
                gameName = game.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        val consoleLabel = when (game.name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "gb" -> "GB"
            "gbc" -> "GBC"
            else -> "GBA"
        }
        Text(
            consoleLabel,
            color = NexoraPink,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp
        )
        Text(
            game.name.substringBeforeLast('.'),
            color = NexoraText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        Text(formatFileSize(game.sizeBytes), color = NexoraMuted, fontSize = 9.sp, maxLines = 1)
        if (selectionMode) {
            TextButton(onClick = onToggleSelect, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (isSelected) stringResource(R.string.selected) else stringResource(R.string.select),
                    color = if (isSelected) NexoraPink else NexoraPurple,
                    fontSize = 10.sp
                )
            }
        } else {
            TextButton(onClick = onPlay, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.load_game), color = NexoraPurple, fontSize = 10.sp)
            }
            TextButton(onClick = onRemove, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.remove), color = NexoraPink, fontSize = 10.sp)
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val megabytes = bytes / (1024.0 * 1024.0)
    return String.format(Locale.getDefault(), "%.1f MB", megabytes)
}

@Composable
private fun TouchLayoutEditorScreen(settings: TouchControlSettings, onSettingsChange: (TouchControlSettings) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf("D-pad") }
    val opacity = settings.opacityPercent / 100f
    val density = LocalDensity.current.density
    val transform: (String, Float, Float, Float) -> Unit = { name, dx, dy, scale ->
        val x=dx.roundToInt(); val y=dy.roundToInt()
        val next=when(name) {
            "D-pad" -> settings.copy(dpadOffsetXDp=(settings.dpadOffsetXDp+x).coerceIn(-180,180),dpadOffsetYDp=(settings.dpadOffsetYDp+y).coerceIn(-180,180),dpadSizeDp=(settings.dpadSizeDp*scale).roundToInt().coerceIn(100,240))
            "A" -> settings.copy(aOffsetXDp=(settings.aOffsetXDp+x).coerceIn(-180,180),aOffsetYDp=(settings.aOffsetYDp+y).coerceIn(-180,180),aSizeDp=(settings.aSizeDp*scale).roundToInt().coerceIn(36,120))
            "B" -> settings.copy(bOffsetXDp=(settings.bOffsetXDp+x).coerceIn(-180,180),bOffsetYDp=(settings.bOffsetYDp+y).coerceIn(-180,180),bSizeDp=(settings.bSizeDp*scale).roundToInt().coerceIn(36,120))
            "L" -> settings.copy(lOffsetXDp=(settings.lOffsetXDp+x).coerceIn(-180,180),lOffsetYDp=(settings.lOffsetYDp+y).coerceIn(-180,180),lSizeDp=(settings.lSizeDp*scale).roundToInt().coerceIn(36,120))
            "R" -> settings.copy(rOffsetXDp=(settings.rOffsetXDp+x).coerceIn(-180,180),rOffsetYDp=(settings.rOffsetYDp+y).coerceIn(-180,180),rSizeDp=(settings.rSizeDp*scale).roundToInt().coerceIn(36,120))
            "MENU" -> settings.copy(menuOffsetXDp=(settings.menuOffsetXDp+x).coerceIn(-180,180),menuOffsetYDp=(settings.menuOffsetYDp+y).coerceIn(-180,180),menuSizeDp=(settings.menuSizeDp*scale).roundToInt().coerceIn(28,96))
            "SELECT" -> settings.copy(selectOffsetXDp=(settings.selectOffsetXDp+x).coerceIn(-180,180),selectOffsetYDp=(settings.selectOffsetYDp+y).coerceIn(-180,180),selectSizeDp=(settings.selectSizeDp*scale).roundToInt().coerceIn(28,96))
            else -> settings.copy(startOffsetXDp=(settings.startOffsetXDp+x).coerceIn(-180,180),startOffsetYDp=(settings.startOffsetYDp+y).coerceIn(-180,180),startSizeDp=(settings.startSizeDp*scale).roundToInt().coerceIn(28,96))
        }
        onSettingsChange(next)
    }
    Column(Modifier.fillMaxSize().background(NexoraBackground).padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("‹ Voltar",color=NexoraPurple) }
            Column(Modifier.weight(1f)) {
                Text("Personalizar botões",color=NexoraText,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Text("Arraste para mover • pinça para redimensionar",color=NexoraMuted,fontSize=10.sp)
            }
            TextButton(onClick={onSettingsChange(TouchControlSettings(opacityPercent=settings.opacityPercent))}) { Text("Redefinir",color=NexoraPink,fontSize=11.sp) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF160D23)).border(1.dp,Color(0xFF51366F),RoundedCornerShape(20.dp))) {
            if(maxWidth>maxHeight) {
                Row(Modifier.fillMaxSize().padding(6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    NexoraControlShell(Modifier.width(164.dp).fillMaxHeight()) {
                        Box(Modifier.fillMaxSize().padding(6.dp)) {
                            EditorControl("L",11,settings.lSizeDp,settings.lOffsetXDp,settings.lOffsetYDp,Modifier.align(Alignment.TopStart).offset(4.dp,4.dp),selected=="L",density,opacity,{selected="L"}) {dx,dy,z->transform("L",dx,dy,z)}
                            EditorControl("D-pad",0,settings.dpadSizeDp,settings.dpadOffsetXDp,settings.dpadOffsetYDp,Modifier.align(Alignment.CenterStart),selected=="D-pad",density,opacity,{selected="D-pad"}) {dx,dy,z->transform("D-pad",dx,dy,z)}
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                            Box(Modifier.fillMaxWidth(.82f).aspectRatio(1.5f).clip(RoundedCornerShape(9.dp)).background(Color(0xFF08070B)).border(1.dp,NexoraPurple.copy(alpha=.55f),RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
                                Text("NEXORA",color=NexoraPurple.copy(alpha=.45f),fontSize=12.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(bottom=4.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                            EditorControl("MENU",5,settings.menuSizeDp,settings.menuOffsetXDp,settings.menuOffsetYDp,Modifier,selected=="MENU",density,opacity,{selected="MENU"}) {dx,dy,z->transform("MENU",dx,dy,z)}
                            Spacer(Modifier.width(8.dp))
                            EditorControl("SELECT",5,settings.selectSizeDp,settings.selectOffsetXDp,settings.selectOffsetYDp,Modifier,selected=="SELECT",density,opacity,{selected="SELECT"}) {dx,dy,z->transform("SELECT",dx,dy,z)}
                            Spacer(Modifier.width(8.dp))
                            EditorControl("START",5,settings.startSizeDp,settings.startOffsetXDp,settings.startOffsetYDp,Modifier,selected=="START",density,opacity,{selected="START"}) {dx,dy,z->transform("START",dx,dy,z)}
                        }
                    }
                    NexoraControlShell(Modifier.width(164.dp).fillMaxHeight()) {
                        Box(Modifier.fillMaxSize().padding(6.dp)) {
                            EditorControl("R",13,settings.rSizeDp,settings.rOffsetXDp,settings.rOffsetYDp,Modifier.align(Alignment.TopEnd).offset((-4).dp,4.dp),selected=="R",density,opacity,{selected="R"}) {dx,dy,z->transform("R",dx,dy,z)}
                            Row(Modifier.align(Alignment.CenterEnd),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
                                EditorControl("B",9,settings.bSizeDp,settings.bOffsetXDp,settings.bOffsetYDp,Modifier,selected=="B",density,opacity,{selected="B"}) {dx,dy,z->transform("B",dx,dy,z)}
                                EditorControl("A",7,settings.aSizeDp,settings.aOffsetXDp,settings.aOffsetYDp,Modifier,selected=="A",density,opacity,{selected="A"}) {dx,dy,z->transform("A",dx,dy,z)}
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(6.dp),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                        Box(Modifier.fillMaxWidth(.62f).aspectRatio(1.5f).clip(RoundedCornerShape(9.dp)).background(Color(0xFF08070B)).border(1.dp,NexoraPurple.copy(alpha=.55f),RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center) {
                            Text("NEXORA",color=NexoraPurple.copy(alpha=.45f),fontSize=12.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
                        }
                    }
                    NexoraControlShell(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                EditorControl("L",11,settings.lSizeDp,settings.lOffsetXDp,settings.lOffsetYDp,Modifier,selected=="L",density,opacity,{selected="L"}) {dx,dy,z->transform("L",dx,dy,z)}
                                EditorControl("R",13,settings.rSizeDp,settings.rOffsetXDp,settings.rOffsetYDp,Modifier,selected=="R",density,opacity,{selected="R"}) {dx,dy,z->transform("R",dx,dy,z)}
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                                EditorControl("D-pad",0,settings.dpadSizeDp,settings.dpadOffsetXDp,settings.dpadOffsetYDp,Modifier,selected=="D-pad",density,opacity,{selected="D-pad"}) {dx,dy,z->transform("D-pad",dx,dy,z)}
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    EditorControl("B",9,settings.bSizeDp,settings.bOffsetXDp,settings.bOffsetYDp,Modifier,selected=="B",density,opacity,{selected="B"}) {dx,dy,z->transform("B",dx,dy,z)}
                                    EditorControl("A",7,settings.aSizeDp,settings.aOffsetXDp,settings.aOffsetYDp,Modifier,selected=="A",density,opacity,{selected="A"}) {dx,dy,z->transform("A",dx,dy,z)}
                                }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                EditorControl("MENU",5,settings.menuSizeDp,settings.menuOffsetXDp,settings.menuOffsetYDp,Modifier,selected=="MENU",density,opacity,{selected="MENU"}) {dx,dy,z->transform("MENU",dx,dy,z)}
                                EditorControl("SELECT",5,settings.selectSizeDp,settings.selectOffsetXDp,settings.selectOffsetYDp,Modifier,selected=="SELECT",density,opacity,{selected="SELECT"}) {dx,dy,z->transform("SELECT",dx,dy,z)}
                                EditorControl("START",5,settings.startSizeDp,settings.startOffsetXDp,settings.startOffsetYDp,Modifier,selected=="START",density,opacity,{selected="START"}) {dx,dy,z->transform("START",dx,dy,z)}
                            }
                        }
                    }
                }
            }
        }
        Text("Selecionado: $selected",color=NexoraPurple,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        Text("As alterações são aplicadas e salvas automaticamente.",color=NexoraMuted,fontSize=10.sp)
    }
}

@Composable
private fun EditorControl(label:String,cell:Int,sizeDp:Int,offsetXDp:Int,offsetYDp:Int,modifier:Modifier,selected:Boolean,density:Float,opacity:Float,onSelect:()->Unit,onTransform:(Float,Float,Float)->Unit) {
    val latestTransform by rememberUpdatedState(onTransform)
    val latestSelect by rememberUpdatedState(onSelect)
    val gesture=Modifier.offset(x=offsetXDp.dp,y=offsetYDp.dp)
        .pointerInput(label) { detectTransformGestures { _,pan,zoom,_ -> latestSelect(); latestTransform(pan.x/density,pan.y/density,zoom) } }
        .clickable { latestSelect() }
        .background(if(selected) NexoraPurple.copy(alpha=.20f) else Color.Transparent,RoundedCornerShape(12.dp))
        .border(if(selected) 1.dp else 0.dp,if(selected) NexoraPink else Color.Transparent,RoundedCornerShape(12.dp)).padding(3.dp)
    if(label=="MENU"||label=="SELECT"||label=="START") {
        Column(modifier.then(gesture),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(0.dp)) {
            NexoraAtlasSprite(cell=cell,modifier=Modifier.size(sizeDp.dp).alpha(opacity.coerceIn(.20f,1f)))
            Text(label,color=if(selected) NexoraPink else NexoraMuted,fontSize=8.sp,fontWeight=FontWeight.Bold)
        }
    } else {
        Box(modifier.then(gesture).size(sizeDp.dp),contentAlignment=Alignment.Center) {
            NexoraAtlasSprite(cell=cell,modifier=Modifier.fillMaxSize().alpha(opacity.coerceIn(.20f,1f)))
        }
    }
}

@Composable
private fun HistoryScreen() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp), contentAlignment = Alignment.Center) {
            EmptyScreen(
                eyebrow = stringResource(R.string.history_label),
                title = stringResource(R.string.no_history_yet),
                description = stringResource(R.string.history_empty_description),
                symbol = "◷"
            )
        }
        Spacer(Modifier.height(24.dp))
    }
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
private fun SettingsScreen(nativeStatus: Int, touchSettings: TouchControlSettings, onTouchSettingsChange: (TouchControlSettings) -> Unit, onOpenTouchEditor: () -> Unit) {
    var selectedCategory by remember { mutableStateOf("main") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selectedCategory != "main") TextButton(onClick = { selectedCategory = "main" }) { Text("‹", color = NexoraPurple, fontSize = 22.sp) }
            Column {
                Text(stringResource(R.string.settings_label), color = NexoraText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(if (selectedCategory == "main") stringResource(R.string.settings_intro) else "Configurações organizadas por categoria", color = NexoraMuted, fontSize = 12.sp)
            }
        }
        when (selectedCategory) {
            "main" -> {
                SettingsCategoryCard("🎮", "Controles", "Editor visual, posição e tamanho", NexoraPurple) { selectedCategory = "controls" }
                SettingsCategoryCard("✦", "Aparência", "Temas e elementos visuais", NexoraPink) { selectedCategory = "appearance" }
                SettingsCategoryCard("⚡", "Emulação", "Velocidade e desempenho", NexoraPurple) { selectedCategory = "emulation" }
                SettingsCategoryCard("♫", "Áudio", "Volume e configurações sonoras", NexoraPink) { selectedCategory = "audio" }
                SettingsCategoryCard("⚙", "Sistema", "Idioma, armazenamento e informações", NexoraPurple) { selectedCategory = "system" }
                Text(stringResource(R.string.technical_status), color = NexoraPink, fontSize = 10.sp, letterSpacing = 1.5.sp)
                val statusText = when (nativeStatus) {
                    2 -> stringResource(R.string.native_core_ready)
                    1 -> stringResource(R.string.native_core_init_failed)
                    0 -> stringResource(R.string.native_core_create_failed)
                    else -> stringResource(R.string.native_bridge_unavailable)
                }
                Text(statusText, color = NexoraMuted, fontSize = 11.sp)
            }
            "controls" -> {
                SettingsCategoryCard("🎮", "Botões e personalizar", "Abra a tela de jogo para mover e redimensionar cada botão", NexoraPurple, onOpenTouchEditor)
                Column(Modifier.fillMaxWidth().background(NexoraPanel, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Opacidade dos controles", color = NexoraText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(Modifier.weight(1f), value = touchSettings.opacityPercent.toFloat(), onValueChange = { onTouchSettingsChange(touchSettings.copy(opacityPercent = it.roundToInt().coerceIn(20, 100))) }, valueRange = 20f..100f)
                        Text(touchSettings.opacityPercent.toString() + "%", color = NexoraMuted, fontSize = 11.sp)
                    }
                    Text("A opacidade é independente do tamanho e da posição dos botões.", color = NexoraMuted, fontSize = 11.sp)
                }
                Text("As alterações são salvas automaticamente e não modificam ROMs ou saves.", color = NexoraMuted, fontSize = 12.sp)
            }
            "appearance" -> SettingsRow(stringResource(R.string.appearance_setting), stringResource(R.string.appearance_description))
            "emulation" -> SettingsRow("Velocidade", "As opções de velocidade ficam disponíveis durante o jogo.")
            "audio" -> SettingsRow("Áudio", "As opções sonoras serão organizadas aqui.")
            else -> {
                SettingsRow(stringResource(R.string.language_setting), stringResource(R.string.language_description))
                SettingsRow(stringResource(R.string.about_setting), stringResource(R.string.about_description))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsCategoryCard(symbol: String, title: String, description: String, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(NexoraPanel).border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(symbol, color = accent, fontSize = 24.sp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = NexoraText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = NexoraMuted, fontSize = 11.sp)
        }
        Text("›", color = accent, fontSize = 24.sp)
    }
}

@Composable
private fun ControlSettingSlider(label: String, value: Int, range: IntRange, unit: String, onValueChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = NexoraMuted, fontSize = 11.sp)
            Text("$value$unit", color = NexoraText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(value = value.toFloat(), onValueChange = { onValueChange(it.roundToInt().coerceIn(range)) }, valueRange = range.first.toFloat()..range.last.toFloat())
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
private fun SideNavigation(selectedTab: NexoraTab, onSelect: (NexoraTab) -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .fillMaxHeight()
            .background(NexoraPanel.copy(alpha = 0.82f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
