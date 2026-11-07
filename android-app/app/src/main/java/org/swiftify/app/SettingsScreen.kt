package org.swiftify.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun SettingsScreen(
    connectEnabled: Boolean,
    onConnectToggle: (Boolean) -> Unit,
    onReauthorize: () -> Unit = {},
    onCrossfadeChanged: ((Boolean, Int) -> Unit)? = null,
    onLogout: (() -> Unit)? = null
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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.settings_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Account & Session
        Text(
            text = stringResource(R.string.settings_spotify_account),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_connected_to_spotify), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_premium_active), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onReauthorize,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_reauthorize), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    var showLogoutConfirm by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showLogoutConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_logout), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    if (showLogoutConfirm) {
                        AlertDialog(
                            onDismissRequest = { showLogoutConfirm = false },
                            title = { Text(stringResource(R.string.settings_logout_confirm_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                            text = { Text(stringResource(R.string.settings_logout_confirm_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showLogoutConfirm = false
                                        onLogout?.invoke()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.settings_logout), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLogoutConfirm = false }) {
                                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Audio Quality
        Text(
            text = "AUDIO QUALITY",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
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
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(text = desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedQuality = kbps
                                prefs.edit().putInt("audio_quality_kbps", kbps).apply()
                                SpotifyBridge.setStreamQuality(kbps)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                    if (index < qualities.size - 1) {
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Playback Options
        Text(
            text = stringResource(R.string.settings_playback_networking),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Volume Normalization
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_volume_norm), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_volume_norm_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(
                        checked = normalizationEnabled,
                        onCheckedChange = {
                            normalizationEnabled = it
                            prefs.edit().putBoolean("volume_norm", it).apply()
                            SpotifyBridge.setVolumeNormalization(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // Crossfade
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_crossfade), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (crossfadeEnabled) stringResource(R.string.settings_crossfade_overlap, crossfadeSeconds) else stringResource(R.string.settings_crossfade_gapless), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(
                        checked = crossfadeEnabled,
                        onCheckedChange = {
                            crossfadeEnabled = it
                            prefs.edit().putBoolean("crossfade_enabled", it).apply()
                            onCrossfadeChanged?.invoke(it, crossfadeSeconds)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (crossfadeEnabled) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.settings_overlap_duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Text("${crossfadeSeconds}s", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))

                // Spotify Connect
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_spotify_connect), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_spotify_connect_desc, deviceName), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Switch(
                        checked = connectEnabled,
                        onCheckedChange = onConnectToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: 6-Band Equalizer & DSP
        Text(
            text = stringResource(R.string.settings_dsp),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Master Equalizer Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_parametric_eq), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (equalizerEnabled) stringResource(R.string.settings_eq_active, selectedPreset) else stringResource(R.string.settings_eq_bypassed), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (equalizerEnabled) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                    Text(stringResource(R.string.settings_sound_presets), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                                    .clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable {
                                        val pGains = eqPresets[pName] ?: defaultGains
                                        for (i in 0 until 6) {
                                            bandGains[i] = pGains[i]
                                        }
                                        updateEq(pGains, pName)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                Text(
                                    text = pName,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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
                                Text(freqLabel, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    gainStr,
                                    color = if (gain != 0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
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
            text = stringResource(R.string.settings_storage_cache),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.settings_audio_cache), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_cache_usage, cacheSizeMb), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            clearAppCache(context)
                            cacheSizeMb = calculateCacheSize(context)
                            Toast.makeText(context, context.getString(R.string.settings_cache_cleared), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.settings_cache_clear), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section: Engine & System Info
        Text(
            text = stringResource(R.string.settings_system_engine),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_audio_engine), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("Librespot Vorbis + AAudio", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_output_backend), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("AAudio Low-Latency Stereo", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_target_device), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("$deviceName (API ${android.os.Build.VERSION.SDK_INT})", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_architecture), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"}", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_swiftify_core), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text("2.0-native", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
