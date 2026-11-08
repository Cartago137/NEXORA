package com.nexora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NexoraBackground = Color(0xFF100B18)
private val NexoraPanel = Color(0xFF1D1428)
private val NexoraPurple = Color(0xFF8B5CF6)
private val NexoraPink = Color(0xFFE879A8)
private val NexoraText = Color(0xFFF4EFFA)
private val NexoraMuted = Color(0xFFB7AFC2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val nativeStatus = remember {
                    runCatching { NativeBridge.nativeStatus() }
                        .getOrElse { "Native bridge could not be loaded." }
                }
                NexoraHome(nativeStatus)
            }
        }
    }
}

@Composable
private fun NexoraHome(nativeStatus: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NexoraBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .background(NexoraPurple, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.brand_name),
                        color = NexoraText,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                    Text(
                        text = stringResource(R.string.game_library),
                        color = NexoraMuted,
                        fontSize = 10.sp,
                        letterSpacing = 2.5.sp
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.home_headline),
                    color = NexoraText,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    color = NexoraMuted,
                    fontSize = 14.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(NexoraPanel, RoundedCornerShape(24.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("YOUR LIBRARY", color = NexoraPink, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.no_games_yet),
                    color = NexoraText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.library_empty_description),
                    color = NexoraMuted,
                    fontSize = 13.sp
                )
            }

            Text(
                text = nativeStatus,
                color = NexoraMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
