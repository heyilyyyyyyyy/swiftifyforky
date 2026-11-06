package org.swiftify.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun SettingsScreen(
    connectEnabled: Boolean,
    onConnectToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("swiftify_prefs", Context.MODE_PRIVATE) }

    var selectedQuality by remember {
        mutableIntStateOf(prefs.getInt("audio_quality_kbps", 160))
    }
    var normalizationEnabled by remember {
        mutableStateOf(prefs.getBoolean("volume_norm", true))
    }
    var cacheSizeMb by remember {
        mutableStateOf(calculateCacheSize(context))
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF13100E))
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Audio Quality
        Text(
            text = "AUDIO QUALITY",
            color = Color(0xFFF2A385),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1816))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val qualities = listOf(
                    Triple(96, "Normal (96 kbps)", "Data saver, fast loading"),
                    Triple(160, "High (160 kbps)", "Balanced high fidelity"),
                    Triple(320, "Very High (320 kbps)", "Original Vorbis studio master")
                )

                qualities.forEachIndexed { index, (kbps, title, desc) ->
                    val isSelected = selectedQuality == kbps
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0x22F2A385) else Color.Transparent)
                            .clickable {
                                selectedQuality = kbps
                                prefs.edit().putInt("audio_quality_kbps", kbps).apply()
                                SpotifyBridge.setStreamQuality(kbps)
                                Toast.makeText(context, "Audio quality set to $kbps kbps", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = title,
                                color = if (isSelected) Color(0xFFF2A385) else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(text = desc, color = Color(0xFF8E837D), fontSize = 12.sp)
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedQuality = kbps
                                prefs.edit().putInt("audio_quality_kbps", kbps).apply()
                                SpotifyBridge.setStreamQuality(kbps)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFF2A385),
                                unselectedColor = Color(0xFF6B5F59)
                            )
                        )
                    }
                    if (index < qualities.size - 1) {
                        Divider(color = Color(0x22FFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Playback Options
        Text(
            text = "PLAYBACK & NETWORKING",
            color = Color(0xFFF2A385),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1816))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Volume Normalization
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Volume Normalization", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Equalize volume across tracks", color = Color(0xFF8E837D), fontSize = 12.sp)
                    }
                    Switch(
                        checked = normalizationEnabled,
                        onCheckedChange = {
                            normalizationEnabled = it
                            prefs.edit().putBoolean("volume_norm", it).apply()
                            SpotifyBridge.setVolumeNormalization(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF13100E),
                            checkedTrackColor = Color(0xFFF2A385)
                        )
                    )
                }

                Divider(color = Color(0x22FFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // Spotify Connect
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spotify Connect", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Broadcast Nothing Phone (1) as speaker", color = Color(0xFF8E837D), fontSize = 12.sp)
                    }
                    Switch(
                        checked = connectEnabled,
                        onCheckedChange = onConnectToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF13100E),
                            checkedTrackColor = Color(0xFFF2A385)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Storage & Cache
        Text(
            text = "STORAGE & CACHE",
            color = Color(0xFFF2A385),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1816))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Audio & Image Cache", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Current usage: $cacheSizeMb MB", color = Color(0xFF8E837D), fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            clearAppCache(context)
                            cacheSizeMb = calculateCacheSize(context)
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2C2320),
                            contentColor = Color(0xFFF2A385)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Engine & System Info
        Text(
            text = "SYSTEM & ENGINE",
            color = Color(0xFFF2A385),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1816))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Audio Engine", color = Color(0xFF8E837D), fontSize = 13.sp)
                    Text("Librespot Vorbis + AAudio", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Output Backend", color = Color(0xFF8E837D), fontSize = 13.sp)
                    Text("AAudio Direct Stereo (snd_device 2)", color = Color(0xFF1DB954), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Device", color = Color(0xFF8E837D), fontSize = 13.sp)
                    Text("Nothing Phone (1) (A063)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Architecture", color = Color(0xFF8E837D), fontSize = 13.sp)
                    Text("arm64-v8a (API 34)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Swiftify Core", color = Color(0xFF8E837D), fontSize = 13.sp)
                    Text("2.0-native", color = Color(0xFFF2A385), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(110.dp))
    }
}

private fun calculateCacheSize(context: Context): Long {
    var sizeBytes = 0L
    try {
        context.cacheDir?.walkTopDown()?.forEach { sizeBytes += it.length() }
        context.externalCacheDir?.walkTopDown()?.forEach { sizeBytes += it.length() }
    } catch (_: Exception) {}
    return (sizeBytes / (1024 * 1024)).coerceAtLeast(1L)
}

private fun clearAppCache(context: Context) {
    try {
        context.cacheDir?.deleteRecursively()
        context.externalCacheDir?.deleteRecursively()
    } catch (_: Exception) {}
}
