package com.dj2go.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dj2go.audio.BeatGrid
import com.dj2go.audio.WaveformData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DETENT_CENTER = 64
private const val DETENT_THRESHOLD = 3

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
    colorMode: WaveColorMode,
    amplitudeScale: Float,
    showBeatGrid: Boolean,
    showCueMarkers: Boolean,
    showBarBeat: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val barPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    val barTextSize = with(density) { 10.sp.toPx() }
    Canvas(modifier) {
        drawRect(color = WaveBackground)
        val width = size.width
        val height = size.height
        val midY = height / 2f
        if (waveform == null || width <= 0f || waveform.bucketCount == 0) return@Canvas

        val bucketFrames = waveform.bucketFrames
        val bucketCount = waveform.bucketCount
        val framesPerPixel = secondsVisible * sampleRate / width
        val half = height * 0.46f * amplitudeScale

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
                        color = waveColor(colorMode, accent, low, mid, high),
                        start = Offset(x, midY - halfAmp),
                        end = Offset(x, midY + halfAmp),
                        strokeWidth = 1f
                    )
                }
            }
            x += 1f
        }

        if (showBeatGrid && beatGrid != null && beatGrid.valid) {
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

        if (showBarBeat && beatGrid != null && beatGrid.valid) {
            val period = beatGrid.periodFrames
            val first = beatGrid.firstBeatFrames.toDouble()
            val startFrame = positionFrames - (width / 2f) * framesPerPixel
            val endFrame = positionFrames + (width / 2f) * framesPerPixel
            barPaint.textSize = barTextSize
            barPaint.color = android.graphics.Color.argb(230, 235, 235, 245)
            var barIndex = Math.ceil(Math.ceil((startFrame - first) / period) / 4.0) * 4.0
            var barFrame = first + barIndex * period
            while (barFrame <= endFrame) {
                val barX = ((barFrame - positionFrames) / framesPerPixel + width / 2f).toFloat()
                val barNumber = (barIndex / 4.0).toLong() + 1
                if (barNumber >= 1L && barX >= -20f && barX <= width + 20f) {
                    drawContext.canvas.nativeCanvas.drawText(
                        barNumber.toString(),
                        barX + 3f,
                        barTextSize + 2f,
                        barPaint
                    )
                }
                barIndex += 4.0
                barFrame += 4.0 * period
            }
        }

        if (showCueMarkers && loopIn >= 0 && loopOut > loopIn) {
            val x1 = ((loopIn - positionFrames) / framesPerPixel + width / 2f).toFloat()
            val x2 = ((loopOut - positionFrames) / framesPerPixel + width / 2f).toFloat()
            drawRect(
                color = accent.copy(alpha = 0.16f),
                topLeft = Offset(minOf(x1, x2), 0f),
                size = Size(kotlin.math.abs(x2 - x1), height)
            )
        }

        if (showCueMarkers) {
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
        }

        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, midY), Offset(width, midY), strokeWidth = 1f)
        drawLine(Color.White.copy(alpha = 0.9f), Offset(width / 2f, 0f), Offset(width / 2f, height), strokeWidth = 2f)
    }
}

@Composable
fun OverviewWaveform(
    waveform: WaveformData?,
    positionFrames: Double,
    accent: Color,
    colorMode: WaveColorMode,
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
                    color = waveColor(colorMode, accent, low, mid, high),
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

private fun waveColor(mode: WaveColorMode, accent: Color, low: Int, mid: Int, high: Int): Color =
    when (mode) {
        WaveColorMode.SPECTRUM -> bandColor(low, mid, high)
        WaveColorMode.MONO -> Color(0xFFC8D0DC)
        WaveColorMode.DECK -> accent
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
    onClick: (() -> Unit)? = null,
    onChange: ((Int) -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onChange)
    var snapped by remember { mutableStateOf(false) }

    val dragModifier = if (onChange != null) {
        Modifier.pointerInput(Unit) {
            var base = 0
            var accumulated = 0f
            detectVerticalDragGestures(
                onDragStart = {
                    base = latestValue
                    accumulated = 0f
                    snapped = false
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    accumulated -= dragAmount
                    var next = (base + accumulated / 3f).roundToInt().coerceIn(0, 127)
                    if (abs(next - DETENT_CENTER) <= DETENT_THRESHOLD) {
                        next = DETENT_CENTER
                        if (!snapped) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            snapped = true
                        }
                    } else {
                        snapped = false
                    }
                    latestOnChange?.invoke(next)
                }
            )
        }
    } else {
        Modifier
    }

    Canvas(
        modifier
            .then(dragModifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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
        // Centre detent mark at 12 o'clock (value 64).
        drawLine(
            Color(0xFFE6E6F0).copy(alpha = 0.75f),
            Offset(center.x, center.y - radius),
            Offset(center.x, center.y - radius * 0.72f),
            strokeWidth = 2f
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
    var snapped by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    fun emit(x: Float) {
        if (width <= 0) return
        var next = ((x / width) * 127f).roundToInt().coerceIn(0, 127)
        if (abs(next - DETENT_CENTER) <= DETENT_THRESHOLD) {
            next = DETENT_CENTER
            if (!snapped) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                snapped = true
            }
        } else {
            snapped = false
        }
        onChange(next)
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
        drawLine(
            Color(0xFFE6E6F0).copy(alpha = 0.55f),
            Offset(size.width / 2f, 2f),
            Offset(size.width / 2f, height - 2f),
            strokeWidth = 2f
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
    var snapped by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    fun emit(y: Float) {
        if (height <= 0) return
        var next = ((1f - y / height) * 127f).roundToInt().coerceIn(0, 127)
        if (abs(next - DETENT_CENTER) <= DETENT_THRESHOLD) {
            next = DETENT_CENTER
            if (!snapped) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                snapped = true
            }
        } else {
            snapped = false
        }
        onChange(next)
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
        drawLine(
            Color(0xFFE6E6F0).copy(alpha = 0.55f),
            Offset(2f, size.height / 2f),
            Offset(width - 2f, size.height / 2f),
            strokeWidth = 2f
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

enum class TransportIcon { CUE, SYNC, HEADPHONE }

/** Small gear button used on the waveform corner to open settings. */
@Composable
fun SettingsIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(15.dp)) {
            val color = Color(0xFFD0D0E0)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.30f
            val stroke = size.minDimension * 0.12f
            for (i in 0 until 8) {
                val angle = i * Math.PI / 4.0
                drawLine(
                    color,
                    Offset(
                        center.x + (cos(angle) * radius * 1.1).toFloat(),
                        center.y + (sin(angle) * radius * 1.1).toFloat()
                    ),
                    Offset(
                        center.x + (cos(angle) * radius * 1.6).toFloat(),
                        center.y + (sin(angle) * radius * 1.6).toFloat()
                    ),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color, radius = radius, center = center, style = Stroke(width = stroke))
        }
    }
}

@Composable
fun TransportIconButton(
    icon: TransportIcon,
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
        Canvas(Modifier.size(18.dp)) {
            val color = if (active) Color.Black else Color(0xFFD0D0E0)
            val w = size.width
            val h = size.height
            when (icon) {
                TransportIcon.CUE -> {
                    drawRect(
                        color = color,
                        topLeft = Offset(w * 0.08f, h * 0.12f),
                        size = Size(w * 0.16f, h * 0.76f)
                    )
                    val path = Path().apply {
                        moveTo(w * 0.38f, h * 0.12f)
                        lineTo(w * 0.92f, h * 0.5f)
                        lineTo(w * 0.38f, h * 0.88f)
                        close()
                    }
                    drawPath(path, color)
                }

                TransportIcon.SYNC -> {
                    val stroke = h * 0.13f
                    drawLine(
                        color, Offset(w * 0.14f, h * 0.33f), Offset(w * 0.76f, h * 0.33f),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                    drawLine(
                        color, Offset(w * 0.58f, h * 0.14f), Offset(w * 0.8f, h * 0.33f),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                    drawLine(
                        color, Offset(w * 0.58f, h * 0.52f), Offset(w * 0.8f, h * 0.33f),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                    drawLine(
                        color, Offset(w * 0.86f, h * 0.67f), Offset(w * 0.24f, h * 0.67f),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                    drawLine(
                        color, Offset(w * 0.42f, h * 0.48f), Offset(w * 0.2f, h * 0.67f),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                    drawLine(
                        color, Offset(w * 0.42f, h * 0.86f), Offset(w * 0.2f, h * 0.67f),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                }

                TransportIcon.HEADPHONE -> {
                    val stroke = h * 0.13f
                    drawArc(
                        color = color,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.16f, h * 0.24f),
                        size = Size(w * 0.68f, h * 0.68f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.08f, h * 0.54f),
                        size = Size(w * 0.2f, h * 0.34f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.72f, h * 0.54f),
                        size = Size(w * 0.2f, h * 0.34f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                    )
                }
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
    loading: Boolean,
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
        if (loading) {
            CircularProgressIndicator(
                color = if (active) Color.Black else accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
        } else {
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
}

/** Dialog styled to match the app's dark panels, tinted with an accent colour. */
@Composable
fun DjDialog(
    title: String,
    accent: Color,
    onDismiss: () -> Unit,
    confirmLabel: String? = "Done",
    onConfirm: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0D0D16))
                .padding(16.dp)
        ) {
            Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                extraActions()
                if (confirmLabel != null) {
                    TextButton(onClick = { onConfirm?.invoke() ?: onDismiss() }) {
                        Text(confirmLabel, color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Flat button matching the transport buttons, for use inside dialogs. */
@Composable
fun DialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF23232F))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFFD0D0E0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Outlined button: accent border with accent text. */
@Composable
fun OutlineButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.5.dp, accent, RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Large tactile knob you rotate with a circular drag; calls back with a value delta. */
@Composable
fun BigKnob(
    value: Int,
    accent: Color,
    onDelta: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnDelta by rememberUpdatedState(onDelta)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val twoPi = (2 * PI).toFloat()
                    var lastAngle = 0f
                    detectDragGestures(
                        onDragStart = { position ->
                            lastAngle = atan2(position.y - center.y, position.x - center.x)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val angle = atan2(
                                change.position.y - center.y,
                                change.position.x - center.x
                            )
                            var delta = angle - lastAngle
                            if (delta > PI.toFloat()) delta -= twoPi
                            if (delta < -PI.toFloat()) delta += twoPi
                            lastAngle = angle
                            val step = (delta / twoPi * 127f).roundToInt()
                            if (step != 0) latestOnDelta(step)
                        }
                    )
                }
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val fraction = (value / 127f).coerceIn(0f, 1f)

            drawCircle(Color(0xFF15151F), radius = radius, center = center)
            drawCircle(Color(0xFF242434), radius = radius * 0.9f, center = center)
            drawArc(
                color = accent,
                startAngle = 135f,
                sweepAngle = 270f * fraction,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.8f, center.y - radius * 0.8f),
                size = Size(radius * 1.6f, radius * 1.6f),
                style = Stroke(width = 5f)
            )
            for (i in 0..20) {
                val t = i / 20f
                val angle = Math.toRadians((135f + 270f * t).toDouble())
                drawLine(
                    color = if (t <= fraction) accent else Color(0xFF3A3A4C),
                    start = Offset(
                        center.x + (cos(angle) * radius * 0.66f).toFloat(),
                        center.y + (sin(angle) * radius * 0.66f).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(angle) * radius * 0.76f).toFloat(),
                        center.y + (sin(angle) * radius * 0.76f).toFloat()
                    ),
                    strokeWidth = 2f
                )
            }
            drawLine(
                Color(0xFFE6E6F0).copy(alpha = 0.75f),
                Offset(center.x, center.y - radius * 0.9f),
                Offset(center.x, center.y - radius * 0.66f),
                strokeWidth = 2f
            )
            val pointerAngle = Math.toRadians((135f + 270f * fraction).toDouble())
            val inner = radius * 0.24f
            val outer = radius * 0.55f
            drawLine(
                accent,
                Offset(
                    center.x + (cos(pointerAngle) * inner).toFloat(),
                    center.y + (sin(pointerAngle) * inner).toFloat()
                ),
                Offset(
                    center.x + (cos(pointerAngle) * outer).toFloat(),
                    center.y + (sin(pointerAngle) * outer).toFloat()
                ),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
        Text(
            text = "${(value * 100f / 127f).roundToInt()}%",
            color = PrimaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Endless-encoder style knob: rotates by [angleDegrees] with a marker and ticks. */
@Composable
fun RotaryKnob(angleDegrees: Float, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF1A1A26), radius = radius, center = center)
        drawCircle(Color(0xFF2A2A3A), radius = radius * 0.82f, center = center)
        drawCircle(
            accent.copy(alpha = 0.45f),
            radius = radius * 0.82f,
            center = center,
            style = Stroke(width = 1.5f)
        )
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            drawLine(
                Color(0xFF3A3A4C),
                Offset(
                    center.x + (cos(angle) * radius * 0.9f).toFloat(),
                    center.y + (sin(angle) * radius * 0.9f).toFloat()
                ),
                Offset(
                    center.x + (cos(angle) * radius * 0.99f).toFloat(),
                    center.y + (sin(angle) * radius * 0.99f).toFloat()
                ),
                strokeWidth = 1.5f
            )
        }
        val marker = Math.toRadians((angleDegrees - 90f).toDouble())
        val markerRadius = radius * 0.64f
        val mx = center.x + (cos(marker) * markerRadius).toFloat()
        val my = center.y + (sin(marker) * markerRadius).toFloat()
        drawLine(accent, center, Offset(mx, my), strokeWidth = 3f, cap = StrokeCap.Round)
        drawCircle(accent, radius = radius * 0.1f, center = Offset(mx, my))
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
