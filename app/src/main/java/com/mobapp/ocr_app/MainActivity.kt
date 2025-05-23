package com.mobapp.ocr_app

import android.Manifest
import android.R.attr.rotation
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.SparseIntArray
import android.view.Surface
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var textView: TextView
    private lateinit var imageFile: File
    private lateinit var photoURI: Uri

    private val takePicture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            imageView.setImageBitmap(imageBitmap)

            val image = InputImage.fromFilePath(this, photoURI)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val textoExtraido = visionText.text
                    textView.text = "📝 Texto Extraído:\n$textoExtraido"
                }
                .addOnFailureListener {
                    textView.text = "Erro ao reconhecer texto"
                }
        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        imageView = findViewById<ImageView>(R.id.imageView)
        textView = findViewById<TextView>(R.id.textView)
        val captureButton = findViewById<Button>(R.id.captureButton)

        captureButton.setOnClickListener {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

    }

    private fun launchCamera() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        imageFile = File(getExternalFilesDir("Pictures"), "IMG_$timeStamp.jpg")
        photoURI = FileProvider.getUriForFile(this, "$packageName.provider", imageFile)

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        }
        takePicture.launch(intent)
    }
}