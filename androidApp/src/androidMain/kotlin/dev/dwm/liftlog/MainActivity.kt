package dev.dwm.liftlog

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.dwm.liftlog.data.db.createDatabase
import dev.dwm.liftlog.ui.App
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var photoContinuation: ((Bitmap?) -> Unit)? = null

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            photoContinuation?.invoke(bitmap)
            photoContinuation = null
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private var speechContinuation: ((String?) -> Unit)? = null

    private val recognizeSpeech =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            speechContinuation?.invoke(text)
            speechContinuation = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dev.dwm.liftlog.ui.appContext = applicationContext
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val db = createDatabase(applicationContext)
        val scanner = GmsBarcodeScanning.getClient(this)
        setContent {
            App(
                db,
                scanBarcode = {
                    suspendCancellableCoroutine { cont ->
                        scanner.startScan()
                            .addOnSuccessListener { cont.resume(it.rawValue) }
                            .addOnFailureListener { cont.resume(null) }
                            .addOnCanceledListener { cont.resume(null) }
                    }
                },
                saveExport = { content ->
                    val file = java.io.File(getExternalFilesDir(null), "liftlog-export-${System.currentTimeMillis()}.json")
                    file.writeText(content)
                    file.absolutePath
                },
                takePhoto = {
                    val bitmap = suspendCancellableCoroutine<Bitmap?> { cont ->
                        photoContinuation = { cont.resume(it) }
                        takePicture.launch(null)
                    }
                    bitmap?.let {
                        val out = ByteArrayOutputStream()
                        it.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    }
                },
                voiceInput = {
                    suspendCancellableCoroutine { cont ->
                        speechContinuation = { cont.resume(it) }
                        runCatching {
                            recognizeSpeech.launch(
                                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                    )
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say e.g. \"225 for 8\"")
                                }
                            )
                        }.onFailure {
                            speechContinuation = null
                            cont.resume(null)
                        }
                    }
                },
            )
        }
    }
}
