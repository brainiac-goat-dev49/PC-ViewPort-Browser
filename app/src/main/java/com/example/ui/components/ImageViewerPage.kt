package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.ImageViewerState

@Composable
fun ImageViewerPage(
    state: ImageViewerState,
    onBack: () -> Unit,
    onDownloadImage: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var scale by remember(state) { mutableFloatStateOf(1f) }
    var offset by remember(state) { mutableStateOf(Offset.Zero) }
    var rotation by remember(state) { mutableFloatStateOf(0f) }

    Surface(
        color = Color(0xFF060911),
        contentColor = Color.White,
        modifier = modifier
            .fillMaxSize()
            .testTag("image_viewer_page")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = Color(0xFF0F172A)) {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("image_viewer_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_nav_back), tint = Color.White)
                            }
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = Color(0xFF38BDF8))
                            Text(
                                text = state.title.ifBlank { stringResource(R.string.image_viewer_title) },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row {
                            IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                                Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate 90 degrees", tint = Color.White)
                            }
                            IconButton(onClick = {
                                scale = 1f
                                offset = Offset.Zero
                                rotation = 0f
                            }) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset zoom", tint = Color.White)
                            }
                            if (onDownloadImage != null) {
                                IconButton(onClick = onDownloadImage) {
                                    Icon(Icons.Rounded.Download, contentDescription = "Download image", tint = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF1E293B))
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF060911))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(0.75f, 6f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + pan else Offset.Zero
                        }
                    }
            ) {
                val transformModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                        rotationZ = rotation
                    )

                when {
                    state.bitmap != null -> {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = state.title,
                            contentScale = ContentScale.Fit,
                            modifier = transformModifier
                        )
                    }
                    !state.imageUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = state.imageUrl,
                            contentDescription = state.title,
                            contentScale = ContentScale.Fit,
                            modifier = transformModifier
                        )
                    }
                    else -> {
                        Text("No image loaded", color = Color.White)
                    }
                }
            }
        }
    }
}
