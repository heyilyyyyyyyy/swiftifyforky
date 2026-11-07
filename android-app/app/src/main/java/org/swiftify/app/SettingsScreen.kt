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
    onConnectToggle: (Boolean) -> Unit,
    onReauthorize: () -> Unit = {},
    onCrossfadeChanged: ((Boolean, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("swiftify_prefs", Context.MODE_PRIVATE) }

    val deviceName = remember {
        val m = android.os.Build.MODEL
        val mf = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        if (m.startsWith(mf, ignoreCase = true)) m else "$mf $m"
    }

    var selectedQuality by remember {
        mutableIntStateOf(prefs.getInt("audio_quality_kbps", 320))
    }
    var normalizationEnabled by remember {
        mutableStateOf(prefs.getBoolean("volume_norm", true))
    }
    var crossfadeEnabled by remember {
        mutableStateOf(prefs.getBoolean("crossfade_enabled", false))
    }
    var crossfadeSeconds by remember {
        mutableIntStateOf(prefs.getInt("crossfade_seconds", 5))
    }
    var cacheSizeMb by remember {
        mutableStateOf(calculateCacheSize(context))
    }

    var equalizerEnabled by remember {
        mutableStateOf(prefs.getBoolean("equalizer_enabled", false))
    }
    val defaultGains = remember { floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f) }
    val initialGains = remember {
        val str = prefs.getString("equalizer_gains", null)
        val arr = str?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
        if (arr != null && arr.size == 6) arr else defaultGains
    }
    val bandGains = remember { mutableStateListOf(*initialGains.toTypedArray()) }
    var selectedPreset by remember {
        mutableStateOf(prefs.getString("equalizer_preset", "Flat") ?: "Flat")
    }

    val eqPresets = remember {
        mapOf(
            "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f),
            "Bass Boost" to floatArrayOf(5.5f, 4.0f, 2.0f, 0f, 0f, 0f),
            "Electronic" to floatArrayOf(4.0f, 3.0f, 0f, 2.0f, 3.5f, 4.5f),
            "Rock" to floatArrayOf(4.5f, 2.5f, -1.0f, 1.5f, 3.0f, 4.0f),
            "Vocal" to floatArrayOf(-2.0f, 1.0f, 4.0f, 4.0f, 2.0f, -1.0f),
            "Treble" to floatArrayOf(0f, 0f, 0.5f, 2.0f, 4.0f, 5.5f)
        )
    }
    val bandLabels = remember { listOf("60 Hz", "150 Hz", "400 Hz", "1.0 kHz", "2.4 kHz", "15 kHz") }

    fun updateEq(gains: FloatArray, preset: String? = null) {
        if (preset != null) {
            selectedPreset = preset
            prefs.edit().putString("equalizer_preset", preset).apply()
        }
        val str = gains.joinToString(",") { "%.1f".format(java.util.Locale.US, it) }
        prefs.edit().putString("equalizer_gains", str).apply()
        if (equalizerEnabled) {
            SpotifyBridge.setEqualizer(gains)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF161413))
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

                // Crossfade
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crossfade Tracks", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (crossfadeEnabled) "Seamless overlap: ${crossfadeSeconds}s" else "Gapless native transition", color = Color(0xFF8E837D), fontSize = 12.sp)
                    }
                    Switch(
                        checked = crossfadeEnabled,
                        onCheckedChange = {
                            crossfadeEnabled = it
                            prefs.edit().putBoolean("crossfade_enabled", it).apply()
                            onCrossfadeChanged?.invoke(it, crossfadeSeconds)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF13100E),
                            checkedTrackColor = Color(0xFFF2A385)
                        )
                    )
                }

                if (crossfadeEnabled) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Overlap duration", color = Color(0xFF8E837D), fontSize = 12.sp)
                            Text("${crossfadeSeconds}s", color = Color(0xFFF2A385), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = crossfadeSeconds.toFloat(),
                            onValueChange = {
                                val s = it.toInt()
                                crossfadeSeconds = s
                                prefs.edit().putInt("crossfade_seconds", s).apply()
                                onCrossfadeChanged?.invoke(crossfadeEnabled, s)
                            },
                            valueRange = 1f..12f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFF2A385),
                                activeTrackColor = Color(0xFFF2A385),
                                inactiveTrackColor = Color(0x33F2A385)
                            )
                        )
                    }
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
                        Text("Broadcast $deviceName as speaker", color = Color(0xFF8E837D), fontSize = 12.sp)
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

        // Section: 6-Band Equalizer & DSP
        Text(
            text = "EQUALIZER & SOUND (DSP)",
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
                // Master Equalizer Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Parametric Equalizer", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (equalizerEnabled) "Active: $selectedPreset" else "Bypassed (Flat output)", color = Color(0xFF8E837D), fontSize = 12.sp)
                    }
                    Switch(
                        checked = equalizerEnabled,
                        onCheckedChange = {
                            equalizerEnabled = it
                            prefs.edit().putBoolean("equalizer_enabled", it).apply()
                            if (it) {
                                SpotifyBridge.setEqualizer(bandGains.toFloatArray())
                            } else {
                                SpotifyBridge.setEqualizer(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f))
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF13100E),
                            checkedTrackColor = Color(0xFFF2A385)
                        )
                    )
                }

                if (equalizerEnabled) {
                    Divider(color = Color(0x22FFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    Text("Sound Presets", color = Color(0xFF8E837D), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Preset Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        eqPresets.keys.forEach { pName ->
                            val isSel = selectedPreset == pName
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Color(0xFFF2A385) else Color(0xFF2B221F))
                                    .clickable {
                                        val pGains = eqPresets[pName] ?: defaultGains
                                        for (i in 0 until 6) {
                                            bandGains[i] = pGains[i]
                                        }
                                        updateEq(pGains, pName)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = pName,
                                    color = if (isSel) Color(0xFF13100E) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6 Band Frequency Sliders
                    for (i in 0 until 6) {
                        val freqLabel = bandLabels[i]
                        val gain = bandGains[i]
                        val gainStr = if (gain > 0f) "+%.1f dB".format(gain) else "%.1f dB".format(gain)

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(freqLabel, color = Color(0xFFD6CBC5), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    gainStr,
                                    color = if (gain != 0f) Color(0xFFF2A385) else Color(0xFF8E837D),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = gain,
                                onValueChange = { newGain ->
                                    val rounded = (kotlin.math.round(newGain * 2f) / 2f).coerceIn(-12f, 12f)
                                    bandGains[i] = rounded
                                    updateEq(bandGains.toFloatArray(), "Custom")
                                },
                                valueRange = -12f..12f,
                                steps = 47,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFF2A385),
                                    activeTrackColor = Color(0xFFF2A385),
                                    inactiveTrackColor = Color(0x33F2A385)
                                )
                            )
                        }
                    }
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
                    Text("AAudio Low-Latency Stereo", color = Color(0xFF1DB954), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Device", color = Color(0xFF8E837D), fontSize = 13.sp)
                    Text("$deviceName (API ${android.os.Build.VERSION.SDK_INT})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Architecture", color = Color(0xFF8E837D), fontSize = 13.sp)
                    Text("${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Swiftify Core", color = Color(0xFF8E837D), fontSize = 13.sp)
                    Text("2.0-native", color = Color(0xFFF2A385), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Account & Session
        Text(
            text = "SPOTIFY ACCOUNT & PERMISSIONS",
            color = Color(0xFFF2A385),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF26201E))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Re-authenticate to grant library & like permissions to sync with PC.",
                    color = Color(0xFFA59B94),
                    fontSize = 13.sp
                )
                Button(
                    onClick = onReauthorize,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2A385)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Re-authorize Spotify Session", color = Color(0xFF251510), fontWeight = FontWeight.Bold)
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
