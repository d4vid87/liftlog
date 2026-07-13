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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            )
        }
    }
}
