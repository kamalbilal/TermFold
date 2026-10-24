package com.termfold.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termfold.app.R
import com.termfold.app.acp.AcpBlock
import com.termfold.app.acp.AcpImageBitmaps
import com.termfold.app.ui.theme.Motion
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SCALE = 6f

private sealed interface PreviewPicture {
    data object Loading : PreviewPicture
    data class Ready(val decoded: AcpImageBitmaps.Decoded) : PreviewPicture
    data object Failed : PreviewPicture
}

@Composable
internal fun AcpImagePreviewDialog(
    image: AcpBlock.Image,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var picture by remember(image) { mutableStateOf<PreviewPicture>(PreviewPicture.Loading) }
    val scale = remember(image) { Animatable(1f) }
    val offset = remember(image) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(image) {
        picture = withContext(Dispatchers.Default) {
            AcpImageBitmaps.decodePreview(image)?.let { PreviewPicture.Ready(it) } ?: PreviewPicture.Failed
        }
    }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = box.width * (s - 1f) / 2f
        val maxY = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE6000000)),
        ) {
            Text(
                text = image.mimeType,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextFaint,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { box = it }
                    .pointerInput(image) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                scope.launch {
                                    if (scale.value > 1.05f) {
                                        launch { scale.animateTo(1f, Motion.settle()) }
                                        offset.animateTo(Offset.Zero, Motion.settle())
                                    } else {
                                        val target = 2.5f
                                        val centre = Offset(box.width / 2f, box.height / 2f)
                                        launch { scale.animateTo(target, Motion.settle()) }
                                        offset.animateTo(clamp((centre - tap) * (target - 1f), target), Motion.settle())
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(image) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scope.launch {
                                val s = (scale.value * zoom).coerceIn(1f, MAX_SCALE)
                                scale.snapTo(s)
                                offset.snapTo(clamp(offset.value + pan, s))
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (val p = picture) {
                    PreviewPicture.Loading ->
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Palette.Accent)
                    PreviewPicture.Failed ->
                        Text(
                            stringResource(R.string.files_binary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextDim,
                        )
                    is PreviewPicture.Ready -> AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(Motion.standard()) + scaleIn(Motion.slow(), initialScale = 0.96f),
                    ) {
                        Image(
                            bitmap = p.decoded.bitmap,
                            contentDescription = stringResource(R.string.acp_attach_image),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                    translationX = offset.value.x
                                    translationY = offset.value.y
                                },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 20.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Palette.CardPressed)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TermFoldIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Palette.Text,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
