package my.feeder.app.ui.screens

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import my.feeder.app.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var hasFinished by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun finishOnce() {
        if (!hasFinished) {
            hasFinished = true
            onFinish()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {
                Log.e("CatchItSplash", "Error releasing MediaPlayer", e)
            }
        }
    }

    // Safety timeout: transition after 8s max if video completion fails
    LaunchedEffect(Unit) {
        delay(8_000L)
        Log.d("CatchItSplash", "Safety timeout reached")
        finishOnce()
    }

    // Referenced directly rather than looked up by name, so a missing or
    // renamed resource is a compile error instead of a splash that silently
    // never plays.
    val videoUri = remember {
        Uri.parse("android.resource://${context.packageName}/${R.raw.splash_loading}")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            if (videoUri == null) {
                                finishOnce()
                                return
                            }
                            val surface = Surface(st)
                            val mp = MediaPlayer()
                            mediaPlayer = mp
                            try {
                                mp.setSurface(surface)
                                mp.setDataSource(ctx, videoUri)
                                mp.setOnPreparedListener { player ->
                                    val videoWidth = player.videoWidth.toFloat()
                                    val videoHeight = player.videoHeight.toFloat()
                                    val viewWidth = width.toFloat()
                                    val viewHeight = height.toFloat()

                                    // Fit the video, do not fill it.
                                    //
                                    // The animation is 1080x1920 (9:16) but a
                                    // modern phone is 1080x2400 (20:9).
                                    // Cropping to fill cuts 12.5% off each side
                                    // and clips the ends off the wordmark.
                                    // The animation's background is pure black,
                                    // so fitting it leaves letterbox bars that
                                    // are invisible against the black splash.
                                    if (videoWidth > 0 && videoHeight > 0) {
                                        val scale = minOf(
                                            viewWidth / videoWidth,
                                            viewHeight / videoHeight
                                        )
                                        val scaledWidth = videoWidth * scale
                                        val scaledHeight = videoHeight * scale

                                        val matrix = Matrix()
                                        matrix.setScale(
                                            scaledWidth / viewWidth,
                                            scaledHeight / viewHeight,
                                            viewWidth / 2f,
                                            viewHeight / 2f
                                        )
                                        setTransform(matrix)
                                    }

                                    player.start()
                                    Log.d("CatchItSplash", "Video started playback smoothly")
                                }
                                mp.setOnCompletionListener {
                                    Log.d("CatchItSplash", "Video completed, transitioning")
                                    finishOnce()
                                }
                                mp.setOnErrorListener { _, what, extra ->
                                    Log.e("CatchItSplash", "Video error: $what, $extra")
                                    finishOnce()
                                    true
                                }
                                mp.prepareAsync()
                            } catch (e: Exception) {
                                Log.e("CatchItSplash", "MediaPlayer init failed", e)
                                finishOnce()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            }
        )
    }
}
