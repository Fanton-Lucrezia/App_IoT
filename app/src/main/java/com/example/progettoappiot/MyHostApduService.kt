package com.example.progettoappiot

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class MyHostApduService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return hexStringToByteArray("6F00")

        val hexCommand = byteArrayToHexString(commandApdu)
        Log.d("HCE", "Ricevuto APDU: $hexCommand")

        // Rispondiamo con la nostra "Chiave Segreta" se l'Arduino ci seleziona correttamente
        // In un caso reale l'Arduino manda un comando "SELECT AID"
        // Qui restituiamo un ID fisso che l'Arduino dovrà riconoscere.
        // Esempio: "DOORmoticKey2024" in HEX + 9000 (Successo)
        return hexStringToByteArray("444F4F526D6F7469634B6579323032349000")
    }

    override fun onDeactivated(reason: Int) {
        Log.d("HCE", "Servizio Disattivato: $reason")
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
}