package dev.dwm.liftlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.dwm.liftlog.data.db.createDatabase
import dev.dwm.liftlog.ui.App
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
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
            )
        }
    }
}
