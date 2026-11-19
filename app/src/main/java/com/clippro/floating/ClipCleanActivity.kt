package com.clippro.floating

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

class ClipCleanActivity : Activity() {
    private var done = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !done) {
            done = true
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val raw = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (raw.isNullOrEmpty()) {
                Toast.makeText(this, "الحافظة فارغة", Toast.LENGTH_SHORT).show()
            } else {
                val regex = Regex("^\\s*\\d+\\s*[:|.]\\s?")
                val cleaned = raw.lines().joinToString("\n") { it.replace(regex, "") }
                cm.setPrimaryClip(ClipData.newPlainText("ClipPro", cleaned))
                Toast.makeText(this, "تم تنظيف الحافظة", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
