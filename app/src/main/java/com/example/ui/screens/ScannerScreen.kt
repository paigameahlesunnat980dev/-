package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Vrpano
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.LocationCategory
import com.example.model.MediaType
import com.example.model.VillageLocation
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SkyBlueAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ScannerUiState

@Composable
fun ScannerScreen(
    scannerState: ScannerUiState,
    existingLocations: List<VillageLocation>,
    maxTextureSize: Int,
    onMediaPicked: (List<android.net.Uri>, MediaType, Int) -> Unit,
    onCameraPhotoCaptured: (Bitmap) -> Unit,
    onAnalyzeWithAi: (String) -> Unit,
    onSaveLocation: (
        name: String,
        category: LocationCategory,
        notes: String,
        markedAsHouse: Boolean,
        favorite: Boolean,
        panoramaOnly: Boolean,
        connectToId: String?
    ) -> Unit,
    onClearScanner: () -> Unit,
    onExploreSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var locationName by remember { mutableStateOf("") }
    var locationNotes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(LocationCategory.ROAD) }
    var markedAsHouse by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(false) }
    var panoramaOnly by remember { mutableStateOf(true) }
    var connectToId by remember { mutableStateOf<String?>(existingLocations.lastOrNull()?.id) }
    var showCameraCapture by remember { mutableStateOf(false) }
    var cameraPermissionError by remember { mutableStateOf<String?>(null) }

    val singlePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            panoramaOnly = true
            onMediaPicked(listOf(uri), MediaType.PANORAMA_360, maxTextureSize.coerceAtMost(2048))
        }
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            panoramaOnly = uris.size <= 1
            onMediaPicked(
                uris,
                if (uris.size > 1) MediaType.MULTI_PHOTO else MediaType.PANORAMA_360,
                maxTextureSize.coerceAtMost(2048)
            )
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            panoramaOnly = false
            onMediaPicked(listOf(uri), MediaType.PANORAMA_VIDEO, maxTextureSize.coerceAtMost(2048))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionError = null
            showCameraCapture = true
        } else {
            cameraPermissionError = "Camera permission is required to capture live photos. You can still upload 360° photos or videos below."
        }
    }

    if (showCameraCapture) {
        CameraCaptureOverlay(
            onBitmapCaptured = { bmp ->
                onCameraPhotoCaptured(bmp)
                showCameraCapture = false
            },
            onClose = { showCameraCapture = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("scanner_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "MURGI TOLA SCANNER",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AmberGold
                )
                Text(
                    text = "Capture or upload 360° photos, multi-angle shots & videos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (scannerState.savedSuccessName != null) {
                item {
                    Surface(
                        color = EmeraldVibrant.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldVibrant, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldVibrant)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Saved '${scannerState.savedSuccessName}' permanently!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onExploreSaved,
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = ObsidianBg)
                                ) {
                                    Text("Explore in 3D / 360°")
                                }
                                OutlinedButton(onClick = {
                                    locationName = ""
                                    locationNotes = ""
                                    onClearScanner()
                                }) {
                                    Text("Scan Another")
                                }
                            }
                        }
                    }
                }
            }

            // Capture & Upload Action Grid
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "1. CAPTURE OR IMPORT VILLAGE MEDIA",
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberGold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ScannerSourceButton(
                                label = "Live Camera",
                                icon = Icons.Filled.CameraAlt,
                                accent = CrimsonHeart,
                                testTag = "btn_capture_camera",
                                onClick = {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        showCameraCapture = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ScannerSourceButton(
                                label = "360° Photo",
                                icon = Icons.Filled.Vrpano,
                                accent = AmberGold,
                                testTag = "btn_pick_360_photo",
                                onClick = {
                                    singlePhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ScannerSourceButton(
                                label = "Multi-Angle (3D)",
                                icon = Icons.Filled.Collections,
                                accent = EmeraldVibrant,
                                testTag = "btn_pick_multi_photos",
                                onClick = {
                                    multiPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ScannerSourceButton(
                                label = "Panoramic Video",
                                icon = Icons.Filled.VideoLibrary,
                                accent = SkyBlueAccent,
                                testTag = "btn_pick_pano_video",
                                onClick = {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!cameraPermissionError.isNullOrBlank()) {
                            Text(
                                text = cameraPermissionError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                    }
                }
            }

            // Selected Media Preview + AI Photogrammetry
            if (scannerState.selectedBitmaps.isNotEmpty()) {
                item {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CAPTURED FRAMES (${scannerState.selectedBitmaps.size}) • ${scannerState.mediaType.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldVibrant
                                )
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSecondary,
                                    modifier = Modifier.clickable { onClearScanner() }
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(scannerState.selectedBitmaps) { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Scanned village preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 160.dp, height = 104.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                                    )
                                }
                            }

                            Button(
                                onClick = { onAnalyzeWithAi(locationName) },
                                enabled = !scannerState.isAnalyzing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SkyBlueAccent,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_ai_reconstruct")
                            ) {
                                if (scannerState.isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = ObsidianBg,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("RUNNING AI DEPTH & SCENE ANALYSIS...")
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("AI-ASSISTED 3D DEPTH & SCENE ANALYSIS")
                                }
                            }

                            scannerState.aiResult?.let { ai ->
                                Surface(
                                    color = ObsidianBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, SkyBlueAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "AI RECONSTRUCTION REPORT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SkyBlueAccent
                                        )
                                        Text(
                                            text = ai.summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Buildings: ${ai.buildingsCount} • Trees: ${ai.treesCount} • Poles: ${ai.polesCount} • Surface: ${ai.roadSurface}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberGold
                                        )
                                        Text(
                                            text = if (ai.recommendedPanoramaOnly) {
                                                "Mode: Single 360° Panoramic Sphere (accurate viewpoint)"
                                            } else {
                                                "Mode: Walkable 3D Node + 360° Portal"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldVibrant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Location Metadata & Save Form
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "2. LOCATION DETAILS & VILLAGE LINK",
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberGold
                        )

                        OutlinedTextField(
                            value = locationName,
                            onValueChange = { locationName = it },
                            label = { Text("Location Name (e.g. Murgi Tola Main Chowk)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scanner_name_input")
                        )

                        OutlinedTextField(
                            value = locationNotes,
                            onValueChange = { locationNotes = it },
                            label = { Text("Notes / Landmark Description (optional)") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Category", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LocationCategory.entries) { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberGold,
                                        selectedLabelColor = ObsidianBg
                                    )
                                )
                            }
                        }

                        if (existingLocations.isNotEmpty()) {
                            Text("Connect Road / Portal To:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(existingLocations, key = { it.id }) { loc ->
                                    FilterChip(
                                        selected = connectToId == loc.id,
                                        onClick = { connectToId = if (connectToId == loc.id) null else loc.id },
                                        label = { Text(loc.name) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mark as My House ❤️", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Switch(checked = markedAsHouse, onCheckedChange = { markedAsHouse = it })
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Save to Favorites", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Switch(checked = favorite, onCheckedChange = { favorite = it })
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Open Directly in 360° Sphere Mode", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Switch(checked = panoramaOnly, onCheckedChange = { panoramaOnly = it })
                        }

                        if (!scannerState.errorMessage.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = scannerState.errorMessage!!,
                                    color = Color(0xFFFCA5A5),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onSaveLocation(
                                    locationName,
                                    scannerState.aiResult?.suggestedCategory ?: selectedCategory,
                                    locationNotes,
                                    markedAsHouse,
                                    favorite,
                                    panoramaOnly,
                                    connectToId
                                )
                            },
                            enabled = !scannerState.isSaving && !scannerState.isAnalyzing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberGold,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("btn_save_scanned_location")
                        ) {
                            if (scannerState.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ObsidianBg, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("SAVING TO MURGI TOLA...")
                            } else {
                                Icon(Icons.Filled.Save, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("SAVE SCANNED LOCATION PERMANENTLY", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ScannerSourceButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianBg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
    }
}

@Composable
private fun CameraCaptureOverlay(
    onBitmapCaptured: (Bitmap) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                    } catch (e: Exception) {
                        captureError = e.localizedMessage ?: "Camera unavailable on this device."
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(ObsidianBg.copy(alpha = 0.7f))
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close camera", tint = TextPrimary)
        }

        captureError?.let { err ->
            Surface(
                color = ObsidianSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Text(
                    text = err,
                    color = Color(0xFFFCA5A5),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Button(
            onClick = {
                val cap = imageCapture ?: return@Button
                cap.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = imageProxyToBitmap(image)
                            image.close()
                            if (bmp != null) {
                                onBitmapCaptured(bmp)
                            } else {
                                captureError = "Failed to decode captured frame."
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            captureError = exception.localizedMessage ?: "Capture failed."
                        }
                    }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = ObsidianBg),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(72.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "Take photo", modifier = Modifier.size(30.dp))
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val bitmap = image.toBitmap()
        val rot = image.imageInfo.rotationDegrees
        if (rot == 0) bitmap
        else {
            val matrix = Matrix().apply { postRotate(rot.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    } catch (_: Exception) {
        null
    }
}
