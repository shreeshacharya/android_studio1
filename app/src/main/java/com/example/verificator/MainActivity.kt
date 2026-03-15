package com.example.verificator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                VerificatorApp()
            }
        }
    }
}

@Composable
fun VerificatorApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Ready") }
    var registerNumber by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("http://127.0.0.1:3000") }

    val apiService = remember(serverUrl) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            Retrofit.Builder()
                .baseUrl(if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        } catch (e: Exception) { null }
    }

    fun fetchRecord(regNo: String) {
        isLoading = true
        resultText = "Fetching..."
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) { apiService?.getRecord(regNo) }
                if (response?.isSuccessful == true) {
                    val r = response.body()
                    resultText = "✅ DATA RECEIVED:\nName: ${r?.name}\nCollege: ${r?.college}\nResult: ${r?.result}"
                } else if (response?.code() == 404) {
                    resultText = "❌ No record found!\nThis certificate is not legit."
                } else {
                    resultText = "❌ Error: ${response?.code()}\nCould not verify record."
                }
            } catch (e: Exception) {
                resultText = "⚠️ Connection Failed: ${e.localizedMessage}"
            } finally { isLoading = false }
        }
    }

    fun verifyImage(file: File) {
        isLoading = true
        resultText = "Verifying..."
        scope.launch {
            try {
                val part = MultipartBody.Part.createFormData("image", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                val response = withContext(Dispatchers.IO) { apiService?.verifyCertificate(part) }
                if (response?.isSuccessful == true) {
                    val res = response.body()
                    if (res?.status == "genuine") {
                        resultText = "✅ Genuine Certificate!\nMessage: ${res.message}"
                    } else {
                        resultText = "❌ Alert: ${res?.message ?: "Certificate not legit"}"
                    }
                } else {
                    resultText = "❌ No record found!\nThis certificate is not legit."
                }
            } catch (e: Exception) {
                resultText = "Error: ${e.message}"
            } finally { isLoading = false }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { 
        if (it != null) {
            bitmap = it
            imageUri = null
            resultText = "Captured. Ready to verify."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { 
        if (it != null) {
            imageUri = it
            bitmap = null
            resultText = "Selected. Ready to verify."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) cameraLauncher.launch(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Verificator AI", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Header image
        Image(
            painter = painterResource(id = R.drawable.security_scan),
            contentDescription = "Security Scan Header",
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            OutlinedTextField(value = registerNumber, onValueChange = { registerNumber = it }, label = { Text("Reg No") }, modifier = Modifier.weight(1f))
            Button(onClick = { fetchRecord(registerNumber) }, modifier = Modifier.padding(top = 8.dp, start = 8.dp), enabled = !isLoading) { Text("Fetch") }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { 
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(null)
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            }, modifier = Modifier.weight(1f)) { Text("Scan") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Upload") }
        }

        Spacer(modifier = Modifier.height(10.dp))
        
        // --- IMAGE PREVIEW SECTION ---
        if (bitmap != null || imageUri != null) {
            Card(modifier = Modifier.fillMaxWidth().height(250.dp).padding(vertical = 8.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    bitmap?.let { 
                        Image(bitmap = it.asImageBitmap(), contentDescription = "Captured Image Preview", modifier = Modifier.fillMaxSize()) 
                    }
                    imageUri?.let { 
                        AsyncImage(model = it, contentDescription = "Selected Image Preview", modifier = Modifier.fillMaxSize()) 
                    }
                }
            }
            
            Button(onClick = { 
                scope.launch {
                    try {
                        val file = File(context.cacheDir, "temp_verify.jpg")
                        val outputStream = FileOutputStream(file)
                        if (bitmap != null) {
                            bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        } else {
                            context.contentResolver.openInputStream(imageUri!!)?.use { it.copyTo(outputStream) }
                        }
                        outputStream.close()
                        verifyImage(file)
                    } catch (e: Exception) {
                        resultText = "Error preparing image: ${e.message}"
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
                Text("Verify This Image")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(resultText, fontWeight = FontWeight.Medium)
            }
        }
    }
}
