package com.nexora.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HomePanel = Color(0xFF1D1428)
private val HomePanelLight = Color(0xFF30213F)
private val HomePurple = Color(0xFF8B5CF6)
private val HomePink = Color(0xFFE879A8)
private val HomeText = Color(0xFFF4EFFA)
private val HomeMuted = Color(0xFFB7AFC2)

@Composable
internal fun NexoraHomeScreen(
    games: List<GameEntry>,
    featuredGame: GameEntry?,
    onImportGame: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayGame: (GameEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("BEM-VINDO DE VOLTA", color = HomePink, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
            Text("Continue sua aventura", color = HomeText, fontSize = 25.sp,
                fontWeight = FontWeight.Bold)
            Text("Seu universo de jogos está esperando.", color = HomeMuted, fontSize = 13.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CONTINUE DE ONDE PAROU", color = HomePink, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(HomePanel).padding(16.dp)
            ) {
                val wide = maxWidth >= 520.dp
                val coverWidth = if (wide) 158.dp else 112.dp
                val coverHeight = if (wide) 204.dp else 148.dp
                val floatTransition = rememberInfiniteTransition(label = "featured_cover_float")
                val floatY by floatTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "featured_cover_offset"
                )

                if (featuredGame != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(width = coverWidth, height = coverHeight)
                                .graphicsLayer { translationY = floatY }
                                .clip(RoundedCornerShape(16.dp)).background(HomePanelLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(featuredGame.name.firstOrNull()?.uppercase() ?: "N",
                                    color = HomePurple, fontSize = if (wide) 72.sp else 54.sp,
                                    fontWeight = FontWeight.Black)
                                Text("GAME BOY ADVANCE", color = HomeMuted, fontSize = 7.sp,
                                    letterSpacing = 1.sp)
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text("ÚLTIMO JOGO", color = HomePink, fontSize = 9.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text(featuredGame.name.substringBeforeLast('.'), color = HomeText,
                                fontSize = if (wide) 23.sp else 18.sp, fontWeight = FontWeight.Bold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("GAME BOY ADVANCE", color = HomeMuted, fontSize = 10.sp)
                            Button(onClick = { onPlayGame(featuredGame) },
                                modifier = Modifier.fillMaxWidth()) {
                                Text("JOGAR", fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(width = coverWidth, height = coverHeight)
                                .clip(RoundedCornerShape(16.dp)).background(HomePanelLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("N", color = HomePurple, fontSize = 64.sp,
                                fontWeight = FontWeight.Black)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("SUA PRÓXIMA AVENTURA", color = HomePink, fontSize = 9.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                            Text("Sua coleção começa aqui", color = HomeText, fontSize = 18.sp,
                                fontWeight = FontWeight.Bold)
                            Text("Adicione um jogo para começar a jogar.",
                                color = HomeMuted, fontSize = 11.sp)
                            Button(onClick = onImportGame, modifier = Modifier.fillMaxWidth()) {
                                Text("ADICIONAR JOGOS")
                            }
                        }
                    }
                }
            }
        }

        if (games.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("CONTINUE EXPLORANDO", color = HomeText, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f))
                    Text("VER BIBLIOTECA", color = HomePink, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onOpenLibrary))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(games.filterNot { it.uri == featuredGame?.uri }, key = { it.uri }) { game ->
                        Column(
                            modifier = Modifier.width(124.dp).clip(RoundedCornerShape(16.dp))
                                .background(HomePanel).clickable { onPlayGame(game) }.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(112.dp)
                                    .clip(RoundedCornerShape(11.dp)).background(HomePanelLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(game.name.firstOrNull()?.uppercase() ?: "N", color = HomePurple,
                                    fontSize = 46.sp, fontWeight = FontWeight.Black)
                            }
                            Text(game.name.substringBeforeLast('.'), color = HomeText, fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Text("GBA", color = HomeMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) {
                Text("BIBLIOTECA")
            }
            Button(onClick = onImportGame, modifier = Modifier.weight(1f)) {
                Text("＋ ADICIONAR")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
