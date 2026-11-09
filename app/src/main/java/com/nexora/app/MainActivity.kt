package com.nexora.app

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import java.util.Locale

private val NexoraBackground = Color(0xFF100B18)
private val NexoraPanel = Color(0xFF1D1428)
private val NexoraPanelLight = Color(0xFF291D38)
private val NexoraPurple = Color(0xFF8B5CF6)
private val NexoraPink = Color(0xFFE879A8)
private val NexoraText = Color(0xFFF4EFFA)
private val NexoraMuted = Color(0xFFB7AFC2)

private enum class NexoraTab { HOME, LIBRARY, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the Android status bar (clock, notifications, battery and signal).
        // The system navigation gesture/bar remains available.
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContent {
            var games by remember { mutableStateOf(GameLibraryStore.load(this@MainActivity)) }
            var libraryMessage by remember { mutableStateOf<String?>(null) }

            val romPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    val accepted = runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }.isSuccess

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
                        if (accepted) {
                            runCatching {
                                contentResolver.releasePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                        }
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

            MaterialTheme {
                val nativeStatus = remember {
                    runCatching { NativeBridge.nativeStatus() }
                        .getOrElse { getString(R.string.native_bridge_unavailable) }
                }

                NexoraApp(
                    nativeStatus = nativeStatus,
                    games = games,
                    libraryMessage = libraryMessage,
                    onImportGame = { romPicker.launch(arrayOf("*/*")) },
                    onClearLibraryMessage = { libraryMessage = null },
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
                            runCatching {
                                contentResolver.releasePersistableUriPermission(
                                    android.net.Uri.parse(game.uri),
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
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
    nativeStatus: String,
    games: List<GameEntry>,
    libraryMessage: String?,
    onImportGame: () -> Unit,
    onClearLibraryMessage: () -> Unit,
    onRemoveGame: (GameEntry, Boolean) -> Boolean
) {
    var selectedTab by remember { mutableStateOf(NexoraTab.HOME) }
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
                when (selectedTab) {
                    NexoraTab.HOME -> HomeScreen(
                        onImportGame = onImportGame,
                        onOpenLibrary = { selectedTab = NexoraTab.LIBRARY }
                    )
                    NexoraTab.LIBRARY -> LibraryScreen(
                        games = games,
                        message = libraryMessage,
                        onImportGame = onImportGame,
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

            BottomNavigation(selectedTab = selectedTab, onSelect = { selectedTab = it })
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
    onImportGame: () -> Unit,
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
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(games, key = { it.uri }) { game ->
                    GameLibraryRow(game = game, onRemove = { onRemoveGame(game) })
                }
            }
        }
    }
}

@Composable
private fun GameLibraryRow(game: GameEntry, onRemove: () -> Unit) {
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
        TextButton(onClick = onRemove) {
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
private fun SettingsScreen(nativeStatus: String) {
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
        Text(nativeStatus, color = NexoraMuted, fontSize = 11.sp)
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
