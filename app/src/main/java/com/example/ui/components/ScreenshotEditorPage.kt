package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import kotlin.math.abs
import kotlin.math.min

private enum class DrawTool { PEN, HIGHLIGHT, RECT }

private data class EditorAnnotation(
    val tool: DrawTool,
    val color: Color,
    val strokeFraction: Float,
    val points: List<Offset>
)

@Composable
fun ScreenshotEditorPage(
    bitmap: Bitmap?,
    onBack: () -> Unit,
    onSaveBitmap: (Bitmap) -> Unit,
    onShareBitmap: (Bitmap) -> Unit,
    onInspectImage: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotations = remember(bitmap) { mutableStateListOf<EditorAnnotation>() }
    var activeTool by remember { mutableStateOf(DrawTool.PEN) }
    var activeColor by remember { mutableStateOf(Color(0xFF38BDF8)) }
    var activeStrokeFraction by remember { mutableFloatStateOf(0.006f) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val palette = listOf(
        Color(0xFF38BDF8),
        Color(0xFFF43F5E),
        Color(0xFFFBBF24),
        Color(0xFF34D399),
        Color(0xFFFFFFFF)
    )

    fun renderFinalBitmap(): Bitmap? {
        val base = bitmap ?: return null
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()

        annotations.forEach { item ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = item.color.copy(
                    alpha = if (item.tool == DrawTool.HIGHLIGHT) 0.42f else 1f
                ).toArgb()
                strokeWidth = (w * item.strokeFraction) * if (item.tool == DrawTool.HIGHLIGHT) 3f else 1f
            }
            if (item.tool == DrawTool.RECT && item.points.size >= 2) {
                val p1 = item.points.first()
                val p2 = item.points.last()
                val left = min(p1.x, p2.x) * w
                val top = min(p1.y, p2.y) * h
                val right = kotlin.math.max(p1.x, p2.x) * w
                val bottom = kotlin.math.max(p1.y, p2.y) * h
                canvas.drawRect(left, top, right, bottom, paint)
            } else if (item.points.size >= 2) {
                val path = android.graphics.Path()
                path.moveTo(item.points.first().x * w, item.points.first().y * h)
                for (i in 1 until item.points.size) {
                    path.lineTo(item.points[i].x * w, item.points[i].y * h)
                }
                canvas.drawPath(path, paint)
            }
        }
        return result
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("screenshot_editor_page")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("screenshot_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_nav_back))
                            }
                            Icon(Icons.Rounded.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.screenshot_title), style = MaterialTheme.typography.titleLarge)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { renderFinalBitmap()?.let(onInspectImage) },
                                enabled = bitmap != null
                            ) {
                                Icon(Icons.Rounded.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("View")
                            }
                            FilledTonalButton(
                                onClick = { renderFinalBitmap()?.let(onShareBitmap) },
                                enabled = bitmap != null
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.screenshot_share))
                            }
                            Button(
                                onClick = { renderFinalBitmap()?.let(onSaveBitmap) },
                                enabled = bitmap != null,
                                modifier = Modifier.testTag("screenshot_save_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.screenshot_save))
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        FilterChip(
                            selected = activeTool == DrawTool.PEN,
                            onClick = { activeTool = DrawTool.PEN },
                            leadingIcon = { Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Pen") }
                        )
                        FilterChip(
                            selected = activeTool == DrawTool.HIGHLIGHT,
                            onClick = { activeTool = DrawTool.HIGHLIGHT },
                            leadingIcon = { Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Highlight") }
                        )
                        FilterChip(
                            selected = activeTool == DrawTool.RECT,
                            onClick = { activeTool = DrawTool.RECT },
                            leadingIcon = { Icon(Icons.Rounded.CropSquare, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Box") }
                        )

                        Spacer(Modifier.width(8.dp))

                        palette.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { activeColor = color }
                                    .padding(2.dp)
                            ) {
                                if (activeColor == color) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(2.dp, Color.Black),
                                        modifier = Modifier.fillMaxSize()
                                    ) {}
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        IconButton(
                            onClick = { if (annotations.isNotEmpty()) annotations.removeAt(annotations.lastIndex) },
                            enabled = annotations.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo stroke")
                        }
                        IconButton(
                            onClick = { annotations.clear() },
                            enabled = annotations.isNotEmpty()
                        ) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear annotations")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090E1A))
                    .padding(16.dp)
            ) {
                if (bitmap == null) {
                    Text("No screenshot captured.", color = Color.White)
                } else {
                    val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                    val containerAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                    val canvasModifier = if (imageAspect > containerAspect) {
                        Modifier.fillMaxWidth().aspectRatio(imageAspect)
                    } else {
                        Modifier.fillMaxHeight().aspectRatio(imageAspect)
                    }

                    Box(
                        modifier = canvasModifier
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Captured WebView screenshot",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeTool, activeColor, activeStrokeFraction) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            val nx = (start.x / size.width).coerceIn(0f, 1f)
                                            val ny = (start.y / size.height).coerceIn(0f, 1f)
                                            currentPoints = listOf(Offset(nx, ny))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                            val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                            currentPoints = if (activeTool == DrawTool.RECT && currentPoints.isNotEmpty()) {
                                                listOf(currentPoints.first(), Offset(nx, ny))
                                            } else {
                                                currentPoints + Offset(nx, ny)
                                            }
                                        },
                                        onDragEnd = {
                                            if (currentPoints.size >= 2) {
                                                annotations.add(
                                                    EditorAnnotation(
                                                        tool = activeTool,
                                                        color = activeColor,
                                                        strokeFraction = activeStrokeFraction,
                                                        points = currentPoints
                                                    )
                                                )
                                            }
                                            currentPoints = emptyList()
                                        }
                                    )
                                }
                        ) {
                            val allItems = if (currentPoints.size >= 2) {
                                annotations + EditorAnnotation(activeTool, activeColor, activeStrokeFraction, currentPoints)
                            } else {
                                annotations.toList()
                            }
                            allItems.forEach { item ->
                                val strokeColor = item.color.copy(
                                    alpha = if (item.tool == DrawTool.HIGHLIGHT) 0.42f else 1f
                                )
                                val strokePx = (size.width * item.strokeFraction) *
                                    if (item.tool == DrawTool.HIGHLIGHT) 3f else 1f
                                if (item.tool == DrawTool.RECT && item.points.size >= 2) {
                                    val p1 = item.points.first()
                                    val p2 = item.points.last()
                                    drawRect(
                                        color = strokeColor,
                                        topLeft = Offset(min(p1.x, p2.x) * size.width, min(p1.y, p2.y) * size.height),
                                        size = Size(abs(p2.x - p1.x) * size.width, abs(p2.y - p1.y) * size.height),
                                        style = Stroke(width = strokePx)
                                    )
                                } else if (item.points.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(item.points.first().x * size.width, item.points.first().y * size.height)
                                        for (i in 1 until item.points.size) {
                                            lineTo(item.points[i].x * size.width, item.points[i].y * size.height)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = strokeColor,
                                        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
