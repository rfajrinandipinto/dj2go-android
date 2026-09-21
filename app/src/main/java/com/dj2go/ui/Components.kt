package com.dj2go.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dj2go.audio.BeatGrid
import com.dj2go.audio.WaveformData
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------- waveforms

@Composable
fun MainWaveform(
    waveform: WaveformData?,
    beatGrid: BeatGrid?,
    positionFrames: Double,
    sampleRate: Int,
    secondsVisible: Float,
    cuePositions: LongArray,
    cuePosition: Long,
    loopIn: Long,
    loopOut: Long,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        drawRect(color = WaveBackground)
        val width = size.width
        val height = size.height
        val midY = height / 2f
        if (waveform == null || width <= 0f || waveform.bucketCount == 0) return@Canvas

        val bucketFrames = waveform.bucketFrames
        val bucketCount = waveform.bucketCount
        val framesPerPixel = secondsVisible * sampleRate / width
        val half = height * 0.46f

        var x = 0f
        while (x < width) {
            val frame = positionFrames + (x - width / 2f) * framesPerPixel
            val bucket = (frame / bucketFrames).toInt()
            if (bucket in 0 until bucketCount) {
                val low = waveform.low[bucket].toInt() and 0xFF
                val mid = waveform.mid[bucket].toInt() and 0xFF
                val high = waveform.high[bucket].toInt() and 0xFF
                val amplitude = maxOf(low, mid, high) / 255f
                if (amplitude > 0.005f) {
                    val halfAmp = amplitude * half
                    drawLine(
                        color = bandColor(low, mid, high),
                        start = Offset(x, midY - halfAmp),
                        end = Offset(x, midY + halfAmp),
                        strokeWidth = 1f
                    )
                }
            }
            x += 1f
        }

        if (beatGrid != null && beatGrid.valid) {
            val period = beatGrid.periodFrames
            val first = beatGrid.firstBeatFrames.toDouble()
            val startFrame = positionFrames - (width / 2f) * framesPerPixel
            val endFrame = positionFrames + (width / 2f) * framesPerPixel
            var beatIndex = Math.ceil((startFrame - first) / period)
            var beatFrame = first + beatIndex * period
            while (beatFrame <= endFrame) {
                val beatX = ((beatFrame - positionFrames) / framesPerPixel + width / 2f).toFloat()
                val downbeat = beatIndex % 4.0 == 0.0
                drawLine(
                    color = if (downbeat) Color.White.copy(alpha = 0.45f)
                    else Color.White.copy(alpha = 0.14f),
                    start = Offset(beatX, 0f),
                    end = Offset(beatX, height),
                    strokeWidth = if (downbeat) 2f else 1f
                )
                beatIndex += 1.0
                beatFrame += period
            }
        }

        if (loopIn >= 0 && loopOut > loopIn) {
            val x1 = ((loopIn - positionFrames) / framesPerPixel + width / 2f).toFloat()
            val x2 = ((loopOut - positionFrames) / framesPerPixel + width / 2f).toFloat()
            drawRect(
                color = accent.copy(alpha = 0.16f),
                topLeft = Offset(minOf(x1, x2), 0f),
                size = Size(kotlin.math.abs(x2 - x1), height)
            )
        }

        cuePositions.forEachIndexed { index, frame ->
            if (frame < 0) return@forEachIndexed
            val cueX = ((frame - positionFrames) / framesPerPixel + width / 2f).toFloat()
            if (cueX >= -4f && cueX <= width + 4f) {
                val color = cueColors[index % cueColors.size]
                drawLine(color, Offset(cueX, 0f), Offset(cueX, height), strokeWidth = 2f)
                drawCircle(color, radius = 4f, center = Offset(cueX, 5f))
            }
        }

        if (cuePosition >= 0) {
            val cueX = ((cuePosition - positionFrames) / framesPerPixel + width / 2f).toFloat()
            if (cueX >= -4f && cueX <= width + 4f) {
                drawLine(
                    Color(0xFFFFD400),
                    Offset(cueX, 0f),
                    Offset(cueX, height),
                    strokeWidth = 2f
                )
            }
        }

        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, midY), Offset(width, midY), strokeWidth = 1f)
        drawLine(Color.White.copy(alpha = 0.9f), Offset(width / 2f, 0f), Offset(width / 2f, height), strokeWidth = 2f)
    }
}

@Composable
fun OverviewWaveform(
    waveform: WaveformData?,
    positionFrames: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        drawRect(color = Color(0xFF0D0D16))
        val width = size.width
        val height = size.height
        val midY = height / 2f
        if (waveform == null || width <= 0f || waveform.bucketCount == 0) return@Canvas

        val buckets = waveform.bucketCount
        val totalFrames = buckets.toDouble() * waveform.bucketFrames
        val half = height * 0.44f

        var x = 0f
        while (x < width) {
            val bucket = ((x / width) * buckets).toInt().coerceIn(0, buckets - 1)
            val low = waveform.low[bucket].toInt() and 0xFF
            val mid = waveform.mid[bucket].toInt() and 0xFF
            val high = waveform.high[bucket].toInt() and 0xFF
            val halfAmp = (maxOf(low, mid, high) / 255f) * half
            if (halfAmp > 0.5f) {
                drawLine(
                    color = bandColor(low, mid, high),
                    start = Offset(x, midY - halfAmp),
                    end = Offset(x, midY + halfAmp),
                    strokeWidth = 1f
                )
            }
            x += 1f
        }

        val playheadX = ((positionFrames / totalFrames) * width).toFloat().coerceIn(0f, width)
        drawLine(Color.White, Offset(playheadX, 0f), Offset(playheadX, height), strokeWidth = 2f)
    }
}

private fun bandColor(low: Int, mid: Int, high: Int): Color {
    val l = low / 255f
    val m = mid / 255f
    val h = high / 255f
    val sum = (l + m + h).coerceAtLeast(0.001f)
    return Color(
        red = (l / sum).coerceIn(0f, 1f),
        green = (m / sum * 0.95f).coerceIn(0f, 1f),
        blue = (h / sum).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private val cueColors = listOf(
    Color(0xFF4CAF50),
    Color(0xFFFF9800),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4)
)

/** Shows how far the two decks are out of phase (centre = locked). */
@Composable
fun PhaseMeter(phaseA: Float, phaseB: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFF14141C))
        val midY = size.height / 2f
        drawLine(
            Color(0x33FFFFFF),
            Offset(size.width / 2f, 0f),
            Offset(size.width / 2f, size.height),
            strokeWidth = 1f
        )
        var diff = phaseA - phaseB
        if (diff > 0.5f) diff -= 1f
        if (diff < -0.5f) diff += 1f
        val x = (size.width / 2f + diff * size.width).coerceIn(0f, size.width)
        val aligned = kotlin.math.abs(diff) < 0.03f
        drawCircle(
            color = if (aligned) Color(0xFF34C759) else Color(0xFFFFCC00),
            radius = size.height * 0.35f,
            center = Offset(x, midY)
        )
    }
}

// ---------------------------------------------------------------- jog / knobs

@Composable
fun JogWheel(
    positionFrames: Double,
    sampleRate: Int,
    accent: Color,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF15151F), radius = radius, center = center)
        drawCircle(Color(0xFF26263A), radius = radius * 0.82f, center = center)
        drawCircle(accent.copy(alpha = 0.35f), radius = radius, center = center, style = Stroke(width = 3f))
        drawCircle(Color(0xFF0B0B12), radius = radius * 0.5f, center = center)
        if (playing) drawCircle(accent.copy(alpha = 0.22f), radius = radius * 0.5f, center = center)

        val seconds = positionFrames / sampleRate
        val angle = (seconds / 1.8).mod(1.0) * 2.0 * Math.PI
        val markerRadius = radius * 0.72f
        val mx = center.x + (markerRadius * cos(angle)).toFloat()
        val my = center.y + (markerRadius * sin(angle)).toFloat()
        drawLine(accent, center, Offset(mx, my), strokeWidth = 3f)
        drawCircle(accent, radius = radius * 0.09f, center = Offset(mx, my))
    }
}

@Composable
fun Knob(
    value: Int,
    accent: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Canvas(
        modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val color = if (enabled) accent else Color(0xFF3A3A48)
        val fraction = (value / 127f).coerceIn(0f, 1f)

        drawCircle(Color(0xFF1A1A26), radius = radius, center = center)
        drawCircle(Color(0xFF2A2A3A), radius = radius * 0.8f, center = center)
        drawArc(
            color = color,
            startAngle = 135f,
            sweepAngle = 270f * fraction,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f),
            size = Size(radius * 1.8f, radius * 1.8f),
            style = Stroke(width = 3f)
        )
        val angle = Math.toRadians((135f + 270f * fraction).toDouble())
        val pointerRadius = radius * 0.62f
        val px = center.x + (pointerRadius * cos(angle)).toFloat()
        val py = center.y + (pointerRadius * sin(angle)).toFloat()
        drawLine(color, center, Offset(px, py), strokeWidth = 3f)
    }
}

@Composable
fun VuMeter(level: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Color(0xFF0C0C14))
        val filled = level.coerceIn(0f, 1f) * size.height
        val color = when {
            level > 0.9f -> Color(0xFFFF3B30)
            level > 0.7f -> Color(0xFFFFCC00)
            else -> Color(0xFF34C759)
        }
        drawRect(color, topLeft = Offset(0f, size.height - filled), size = Size(size.width, filled))
        for (i in 1..4) {
            val y = size.height * i / 5f
            drawLine(Color(0x33FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
    }
}

// ---------------------------------------------------------------- faders

@Composable
fun HorizontalFader(
    value: Int,
    accent: Color,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableStateOf(1) }
    fun emit(x: Float) {
        if (width > 0) onChange(((x / width) * 127f).roundToInt().coerceIn(0, 127))
    }

    Canvas(
        modifier
            .onSizeChanged { width = it.width }
            .pointerInput(Unit) { detectDragGestures { change, _ -> emit(change.position.x) } }
            .pointerInput(Unit) { detectTapGestures { offset -> emit(offset.x) } }
    ) {
        val height = size.height
        drawRoundRect(Color(0xFF1A1A26), cornerRadius = CornerRadius(6f, 6f))
        drawLine(
            Color(0xFF2E2E40),
            Offset(8f, height / 2f),
            Offset(size.width - 8f, height / 2f),
            strokeWidth = 4f
        )
        val x = 8f + (size.width - 16f) * (value / 127f)
        drawRoundRect(
            color = accent,
            topLeft = Offset(x - 10f, 2f),
            size = Size(20f, height - 4f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

@Composable
fun VerticalFader(
    value: Int,
    accent: Color,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var height by remember { mutableStateOf(1) }
    fun emit(y: Float) {
        if (height > 0) onChange(((1f - y / height) * 127f).roundToInt().coerceIn(0, 127))
    }

    Canvas(
        modifier
            .onSizeChanged { height = it.height }
            .pointerInput(Unit) { detectDragGestures { change, _ -> emit(change.position.y) } }
            .pointerInput(Unit) { detectTapGestures { offset -> emit(offset.y) } }
    ) {
        val width = size.width
        drawRoundRect(Color(0xFF1A1A26), cornerRadius = CornerRadius(6f, 6f))
        drawLine(
            Color(0xFF2E2E40),
            Offset(width / 2f, 8f),
            Offset(width / 2f, size.height - 8f),
            strokeWidth = 4f
        )
        val y = 8f + (size.height - 16f) * (1f - value / 127f)
        drawRoundRect(
            color = accent,
            topLeft = Offset(2f, y - 10f),
            size = Size(width - 4f, 20f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

// ---------------------------------------------------------------- buttons

@Composable
fun Pad(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) accent else Color(0xFF1B1B26))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.Black else MutedText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TransportButton(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) accent else Color(0xFF23232F))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color.Black else Color(0xFFD0D0E0),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PlayPauseButton(
    playing: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (playing) accent else Color(0xFF23232F))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(16.dp)) {
            val color = if (playing) Color.Black else Color(0xFFD0D0E0)
            if (playing) {
                val barWidth = size.width * 0.28f
                val gap = size.width * 0.18f
                drawRect(
                    color = color,
                    topLeft = Offset(size.width / 2f - gap / 2f - barWidth, 0f),
                    size = Size(barWidth, size.height)
                )
                drawRect(
                    color = color,
                    topLeft = Offset(size.width / 2f + gap / 2f, 0f),
                    size = Size(barWidth, size.height)
                )
            } else {
                val path = Path().apply {
                    moveTo(size.width * 0.22f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.22f, size.height)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SamplerPad(
    label: String,
    active: Boolean,
    loaded: Boolean,
    accent: Color,
    onTrigger: () -> Unit,
    onAssign: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    active -> accent
                    loaded -> Color(0xFF242438)
                    else -> Color(0xFF15151F)
                }
            )
            .combinedClickable(onClick = onTrigger, onLongClick = onAssign),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                active -> Color.Black
                loaded -> PrimaryText
                else -> MutedText
            },
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun <T> Dropdown(
    placeholder: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = selected?.let(optionLabel) ?: placeholder,
                color = PrimaryText,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), fontSize = 12.sp) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
