package com.example.myapplication

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.net.URL
import coil.size.Size
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val permissionRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request legacy permissions for older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    permissionRequestCode
                )
            }
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    @Deprecated("Use ActivityResult API instead")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Permission", "Write permission granted")
            } else {
                Log.d("Permission", "Write permission denied")
            }
        }
    }
}

@Composable
fun MainContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImageCarousel()
    }
}

@Composable
fun ImageCarousel() {
    val scrollState = rememberLazyListState()
    val itemCount = remember { mutableStateOf(1) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex == itemCount.value - 1) {
                    itemCount.value += 10
                }
            }
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(itemCount.value) { index ->
            ImageCard(
                imageUrl = "https://picsum.photos/seed/${index + 1}/3600/2500",
                index = index + 1
            )
        }
    }
}
@Composable
fun ImageCard(imageUrl: String, index: Int) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var showDownloadMessage by remember { mutableStateOf(false) }

    // Animation for the download message
    val alpha by animateFloatAsState(
        targetValue = if (showDownloadMessage) 1f else 0f,
        animationSpec = tween(
            durationMillis = 800,
            easing = LinearEasing
        ),
        finishedListener = {
            if (showDownloadMessage) {
                // After fade in, start fade out
                coroutineScope.launch {
                    delay(1000) // Show message for 1 second
                    showDownloadMessage = false
                }
            }
        }
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .size(Size.ORIGINAL)
                    .build(),
                contentDescription = "Image $index",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentScale = ContentScale.Fit
            )

            // Download message overlay
            if (showDownloadMessage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f * alpha))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Downloaded to Gallery",
                        color = Color.White.copy(alpha = alpha),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = {
                    isDownloading = true
                    coroutineScope.launch(Dispatchers.IO) {
                        downloadImageAndSaveToGallery(imageUrl, context) {
                            isDownloading = false
                            // Show download message after successful download
                            showDownloadMessage = true
                        }
                    }
                },
                enabled = !isDownloading,
                modifier = Modifier.padding(8.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Icon(Icons.Filled.Download, contentDescription = "Download Image")
                }
            }
        }
    }
}

fun downloadImageAndSaveToGallery(imageUrl: String, context: Context, onFinished: () -> Unit) {
    var bitmap: Bitmap? = null
    try {
        val url = URL(imageUrl)
        val inputStream: InputStream = url.openStream()
        bitmap = BitmapFactory.decodeStream(inputStream)
    } catch (e: IOException) {
        Log.e("ImageDownload", "Failed to download image", e)
    }

    bitmap?.let {
        val imageName = "Image_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        try {
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        throw IOException("Failed to save bitmap.")
                    }
                }

                contentValues.clear()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, contentValues, null, null)
                Log.d("ImageDownload", "Image saved to gallery")
            }
        } catch (e: IOException) {
            uri?.let { context.contentResolver.delete(it, null, null) }
            Log.e("ImageDownload", "Failed to save image to gallery", e)
        } finally {
            onFinished()
        }
    } ?: run {
        Log.e("ImageDownload", "Downloaded bitmap is null")
        onFinished()
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    MyApplicationTheme {
        MainContent()
    }
}