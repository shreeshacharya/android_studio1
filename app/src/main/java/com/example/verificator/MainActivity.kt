package com.example.verificator

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VerificatorApp() }
    }
}

@Composable
fun VerificatorApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Upload or scan a certificate to verify") }

    // Create Retrofit instance
    val retrofit = remember {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:3000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService = remember { retrofit.create(ApiService::class.java) }

    fun verifyCertificate(imageFile: File) {
        isLoading = true
        resultText = "Verifying certificate..."

        scope.launch {
            try {
                val requestBody = imageFile.asRequestBody("image/*".toMediaType())
                val multipartBody = MultipartBody.Part.createFormData("image", imageFile.name, requestBody)

                val response = withContext(Dispatchers.IO) {
                    apiService.verifyCertificate(multipartBody)
                }

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null) {
                        resultText = when (result.status) {
                            "genuine" -> "Genuine: ${result.message}\nRegister: ${result.registerNumber}\nName: ${result.name}\nSemester: ${result.semester}\nResult: ${result.result}\nCollege: ${result.college}"
                            "alert" -> "Alert: ${result.message}"
                            else -> "Error: ${result.message}"
                        }
                    } else {
                        resultText = "Error: Empty response body"
                    }
                } else {
                    resultText = "Error: ${response.code()} ${response.message()}"
                }
            } catch (e: Exception) {
                resultText = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun verifyBitmap(bmp: Bitmap) {
        val file = File(context.cacheDir, "certificate_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        verifyCertificate(file)
    }

    fun verifyUri(uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.cacheDir, "certificate_${System.currentTimeMillis()}.jpg")
            inputStream?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
            verifyCertificate(file)
        } catch (e: Exception) {
            resultText = "Error loading image: ${e.message}"
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { image ->
        if (image != null) {
            bitmap = image
            imageUri = null
            resultText = "Certificate scanned"
            verifyBitmap(image)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            bitmap = null
            resultText = "Certificate uploaded"
            verifyUri(uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(modifier = Modifier.height(30.dp))
        Text(text = "Verificator", fontSize = 32.sp)
        Spacer(modifier = Modifier.height(5.dp))
        Text(text = "AI Certificate Verification", fontSize = 16.sp)
        Spacer(modifier = Modifier.height(20.dp))

        Image(
            painter = painterResource(id = R.drawable.security_scan),
            contentDescription = "Security Scan",
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(6.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { cameraLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) { Text("Scan Certificate") }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) { Text("Upload Certificate") }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        bitmap?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Captured Image",
                modifier = Modifier.fillMaxWidth().height(250.dp))
        }

        imageUri?.let {
            AsyncImage(model = it, contentDescription = "Uploaded Image",
                modifier = Modifier.fillMaxWidth().height(250.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
            if (isLoading) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Verifying...", fontSize = 18.sp)
                }
            } else {
                Text(text = resultText, fontSize = 18.sp, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
