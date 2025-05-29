package com.mobapp.ocr_app

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var imageViewId: ImageView
    private lateinit var textView: TextView
    private lateinit var imageFile: File
    private lateinit var photoURI: Uri
    private lateinit var captureButton: Button
    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                imageView.setImageBitmap(imageBitmap)
                imageView.visibility = View.VISIBLE
                imageViewId.visibility = View.GONE
                captureButton.visibility = View.VISIBLE

                val image = InputImage.fromFilePath(this, photoURI)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        processRecognizedText(visionText)
                    }
                    .addOnFailureListener {
                        textView.text = "Erro ao reconhecer texto"
                    }
            }
        }


    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
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
        imageView.visibility = View.GONE
        imageViewId = findViewById<ImageView>(R.id.imageViewId)
        imageViewId.visibility = View.VISIBLE
        textView = findViewById<TextView>(R.id.textView)
        captureButton = findViewById(R.id.captureButton)
        val scanButton = findViewById<Button>(R.id.scanButton)

        captureButton.setOnClickListener {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        scanButton.setOnClickListener {
            startDocumentScanner()
        }

        scannerLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val scanningResult =
                        GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                    scanningResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                        imageView.setImageURI(uri)
                        imageView.visibility = View.VISIBLE
                        imageViewId.visibility = View.GONE
                        captureButton.visibility = View.VISIBLE
                        val image = InputImage.fromFilePath(this, uri)
                        val recognizer =
                            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                processRecognizedText(visionText)
                            }
                            .addOnFailureListener {
                                textView.text = "Erro ao reconhecer texto"
                            }
                    }
                }
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


    private fun startDocumentScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.CAPTURE_MODE_AUTO)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Log.e("DocumentScanner", "Erro ao iniciar o scanner: ${e.message}")
            }
    }


    private fun extractName(texto: String): String {
        val nomeRegex = Regex("(?i)nome[:\\s]*([a-zà-ú]+(?:\\s[a-zà-ú]+)+)")
        return nomeRegex.find(texto)?.groupValues?.get(1)?.trim() ?: "Nome não encontrado"
    }


    private fun extractCPF(texto: String): String {
        val cpfRegex = Regex("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b|\\b\\d{11}\\b")
        return cpfRegex.find(texto)?.value ?: "CPF não encontrado"
    }


    private fun extractFiliation(texto: String): String {
        val filiacaoRegex = Regex("(FILIA[ÇC][ÃA]O)[:\\s]+([A-ZÁÉÍÓÚÂÊÔÃÕÇ\\s]{5,})")
        return filiacaoRegex.find(texto)?.groupValues?.get(2)?.trim() ?: "Filiação não encontrada"
    }


    private fun processRecognizedText(result: Text) {
        val resultText = result.text
        val nome = extractName(resultText)
        val cpf = extractCPF(resultText)
        val filiacao = extractFiliation(resultText)

        val builder = StringBuilder()
        //builder.append("📝 Texto completo:\n$resultText\n\n")
        builder.append("🔍 Campos principais:\n")
        builder.append("Nome: $nome\n")
        builder.append("CPF: $cpf\n")
        builder.append("Filiação: $filiacao\n\n")

        builder.append("🔍 Blocos Detalhados:\n")
        for (block in result.textBlocks) {
            builder.append("📦 Bloco: ${block.text}\n")
            //builder.append("Posição: ${block.boundingBox}\n")
            for (line in block.lines) {
                builder.append("➖ Linha: ${line.text} (${line.boundingBox})\n")
                for (element in line.elements) {
                    builder.append("• Palavra: ${element.text} (${element.boundingBox})\n")
                    val confidence = element.confidence
                    builder.append(
                        "Elemento: ${element.text} (Nivel de confiança: ${
                            "%.2f".format(
                                confidence
                            )
                        })\n"
                    )
                }
            }
            builder.append("\n")
        }

        textView.text = builder.toString()
        evaluateAverageConfidence(result)
    }


    private fun evaluateAverageConfidence(result: Text) {
        var totalConfidence = 0.0f
        var elements = 0

        for (block in result.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    val confidence = element.confidence
                    if (confidence > 0) {
                        totalConfidence += confidence
                        elements++
                    }
                }
            }
        }

        val media = totalConfidence / elements

        if (media >= 0.60f) {
            showDialog(
                "Documento legível \nNível de confiança: ${(media * 100).toInt()}%",
                "✅ Documento capturado com sucesso! Pronto para análise."
            )
        } else if (elements == 0) {
            showDialog(
                "❌ Nenhuma informação identificada",
                "Não foi possivel avaliar o seu documento. Por favor, envie a imagem novamente."
            )

        } else {
            showDialog(
                "Documento ilegível \nNível de confiança: ${(media * 100).toInt()}%",
                "⚠️ As informações não estão legíveis. Por favor, tente novamente."
            )
        }
    }

    private fun showDialog(title: String, message: String) {
        val view = layoutInflater.inflate(R.layout.custom_dialog, null)

        val titleView = view.findViewById<TextView>(R.id.dialog_title)
        val messageView = view.findViewById<TextView>(R.id.dialog_message)
        val okButton = view.findViewById<Button>(R.id.dialog_ok_button)

        titleView.text = title
        messageView.text = message

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        okButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.show()
    }

}
