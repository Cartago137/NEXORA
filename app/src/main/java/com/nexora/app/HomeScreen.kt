package com.nexora.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

private val HomeBackground = Color(0xFF100B18)
private val HomePanel = Color(0xFF1B1226)
private val HomePanelLight = Color(0xFF30203F)
private val HomePurple = Color(0xFF9B63FF)
private val HomeViolet = Color(0xFF6738A8)
private val HomePink = Color(0xFFE879B8)
private val HomeText = Color(0xFFF7F1FC)
private val HomeMuted = Color(0xFFB7AFC2)
private val HomeLine = Color(0xFF49335C)

@Composable
internal fun NexoraHomeScreen(
    games: List<GameEntry>,
    featuredGame: GameEntry?,
    isLastPlayed: Boolean,
    onImportGame: () -> Unit,
    onOpenLibrary: () -> Unit,
    onPlayGame: (GameEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 2.dp)
                        .background(HomePink, RoundedCornerShape(2.dp))
                )
                Text(
                    stringResource(R.string.home_welcome),
                    modifier = Modifier.padding(start = 8.dp),
                    color = HomePink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.2.sp
                )
            }
            Text(
                stringResource(R.string.home_continue),
                color = HomeText,
                fontSize = 27.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                stringResource(R.string.home_subtitle_new),
                color = HomeMuted,
                fontSize = 13.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_featured_eyebrow),
                    color = HomePink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(HomeLine.copy(alpha = 0.7f))
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(28.dp), clip = false)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF29183A),
                                Color(0xFF1A1225),
                                Color(0xFF17101F)
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.linearGradient(
                                listOf(HomePurple.copy(alpha = 0.55f), HomePink.copy(alpha = 0.18f))
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(16.dp)
            ) {
                val wide = maxWidth >= 520.dp
                val coverWidth = if (wide) 166.dp else 116.dp
                val coverHeight = if (wide) 214.dp else 154.dp
                val floatTransition = rememberInfiniteTransition(label = "featured_cover_float")
                val floatY by floatTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2100, easing = FastOutSlowInEasing),
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
                        FeaturedGameArtwork(
                            gameName = featuredGame.name,
                            width = coverWidth,
                            translationY = floatY
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text(
                                stringResource(
                                    if (isLastPlayed) R.string.home_last_game
                                    else R.string.home_featured_game
                                ),
                                color = HomePink,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                featuredGame.name.substringBeforeLast('.'),
                                color = HomeText,
                                fontSize = if (wide) 23.sp else 18.sp,
                                lineHeight = if (wide) 28.sp else 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(HomePurple, CircleShape)
                                )
                                Text(
                                    stringResource(R.string.home_gba_label),
                                    color = HomeMuted,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.2.sp
                                )
                            }
                            Button(
                                onClick = { onPlayGame(featuredGame) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 3.dp)
                                    .shadow(10.dp, RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HomePurple,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    stringResource(R.string.home_play),
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.3.sp
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EmptyFeaturedArtwork(coverWidth, coverHeight)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text(
                                stringResource(R.string.home_next_adventure),
                                color = HomePink,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.3.sp
                            )
                            Text(
                                stringResource(R.string.home_collection_start),
                                color = HomeText,
                                fontSize = 18.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                stringResource(R.string.home_add_game_hint),
                                color = HomeMuted,
                                fontSize = 11.sp
                            )
                            Button(
                                onClick = onImportGame,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HomePurple,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    stringResource(R.string.home_add_games_cta),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (games.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_continue_exploring),
                            color = HomeText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            stringResource(R.string.home_gba_label),
                            color = HomeMuted,
                            fontSize = 9.sp,
                            letterSpacing = 1.4.sp
                        )
                    }
                    Text(
                        stringResource(R.string.home_view_library),
                        color = HomePink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenLibrary)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(
                        games.filterNot { it.uri == featuredGame?.uri },
                        key = { it.uri }
                    ) { game ->
                        Column(
                            modifier = Modifier
                                .width(132.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(HomePanel)
                                .border(1.dp, HomeLine.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                                .clickable { onPlayGame(game) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            GameCoverArtwork(
                                gameName = game.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(118.dp)
                                    .clip(RoundedCornerShape(13.dp))
                            )
                            Text(
                                game.name.substringBeforeLast('.'),
                                color = HomeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOpenLibrary,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomePanelLight,
                    contentColor = HomeText
                )
            ) {
                Text(stringResource(R.string.home_library_cta), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onImportGame,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HomePurple,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.home_add_cta), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FeaturedGameArtwork(
    gameName: String,
    width: androidx.compose.ui.unit.Dp,
    translationY: Float
) {
    // FireRed's supplied front-cover image is square; keep its frame square too,
    // so the border hugs the artwork instead of surrounding an oversized portrait box.
    Box(
        modifier = Modifier
            .size(width = width, height = width)
            .graphicsLayer { this.translationY = translationY }
            .shadow(16.dp, RoundedCornerShape(17.dp))
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF59347A), Color(0xFF2A1B3A), Color(0xFF17111F))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(HomePink.copy(alpha = 0.65f), HomePurple.copy(alpha = 0.25f))),
                RoundedCornerShape(17.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(44.dp)
                .background(HomePink.copy(alpha = 0.10f), CircleShape)
        )
        GameCoverArtwork(
            gameName = gameName,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun EmptyFeaturedArtwork(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF59347A), Color(0xFF2A1B3A), Color(0xFF17111F))
                )
            )
            .border(1.dp, HomePurple.copy(alpha = 0.45f), RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "N",
            color = HomePurple,
            fontSize = 72.sp,
            fontWeight = FontWeight.Black
        )
    }
}
