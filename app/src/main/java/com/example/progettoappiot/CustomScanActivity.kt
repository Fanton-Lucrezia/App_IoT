package com.example.progettoappiot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * Scanner QR con viewfinder quadrato personalizzato.
 * Usato da RegisterActivity al posto del launcher ZXing predefinito.
 * Restituisce il testo scansionato come Intent extra "SCAN_RESULT".
 */
class CustomScanActivity : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_scan)

        barcodeView = findViewById(R.id.barcode_scanner)

        // Nasconde il viewfinder predefinito di ZXing (usiamo il nostro overlay)
        barcodeView.setStatusText("")

        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                barcodeView.pause()
                val data = Intent().putExtra("SCAN_RESULT", result.text)
                setResult(Activity.RESULT_OK, data)
                finish()
            }
            override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
        })

        findViewById<ImageButton>(R.id.btnCloseScanner).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}
