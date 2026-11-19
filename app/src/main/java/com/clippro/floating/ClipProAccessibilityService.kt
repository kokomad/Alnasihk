package com.clippro.floating

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class ClipProAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams

    override fun onServiceConnected() {
        super.onServiceConnected()
        setupFloatingBubble()
    }

    private fun setupFloatingBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        val bubble = floatingView.findViewById<TextView>(R.id.btnFloatingBubble)
        val menuContainer = floatingView.findViewById<LinearLayout>(R.id.menuContainer)
        val cleanBtn = Button(this).apply {
            text = "تنظيف الحافظة"
            setOnClickListener {
                startActivity(android.content.Intent(this@ClipProAccessibilityService, ClipCleanActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                menuContainer.visibility = View.GONE
            }
        }
        menuContainer.addView(cleanBtn)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx < 12 && dy < 12) {
                        menuContainer.visibility =
                            if (menuContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }

        floatingView.findViewById<Button>(R.id.actionSelectAll).setOnClickListener {
            performSelectAll()
            menuContainer.visibility = View.GONE
        }

        floatingView.findViewById<Button>(R.id.actionSmartCopy).setOnClickListener {
            performSmartCopy()
            menuContainer.visibility = View.GONE
        }

        floatingView.findViewById<Button>(R.id.actionExactPaste).setOnClickListener {
            performExactPaste()
            menuContainer.visibility = View.GONE
        }

        floatingView.findViewById<Button>(R.id.actionCleanFormat).setOnClickListener {
            cleanClipboardCode()
            menuContainer.visibility = View.GONE
        }

        windowManager.addView(floatingView, params)
    }

    private fun getActiveEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstEditable(root)
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun performSelectAll() {
        val node = findTargetNode()
        if (node != null) {
            val textLength = node.text?.length ?: 0
            val args = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, textLength)
            }
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
            if (success) {
                Toast.makeText(this, "تم تحديد النص بالكامل ($textLength حرف)", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "اضغط داخل حقل النص أو محرر الأكواد أولاً", Toast.LENGTH_SHORT).show()
    }

    private fun performSmartCopy() {
        val node = findTargetNode()
        if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_COPY)) {
            cleanClipboardCode()
            Toast.makeText(this, "تم النسخ الذكي وتنظيف أرقام الأسطر", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "لم يتم العثور على نص محدد للنسخ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performExactPaste() {
        val node = findTargetNode()
        if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            Toast.makeText(this, "تم اللصق بدقة", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "اضغط داخل حقل الكتابة للصق", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanClipboardCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val rawText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        // Remove leading line numbers like "1. ", "12 | ", or "004: " while preserving code indentation
        val cleaned = rawText.lines().joinToString("\n") { line ->
            line.replace(Regex("^\\s*\\d+\\s*[:|.]\\s?"), "")
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ClipPro Smart Code", cleaned))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}

fun android.accessibilityservice.AccessibilityService.findTargetNode(): AccessibilityNodeInfo? {
    val roots = ArrayList<AccessibilityNodeInfo>()
    rootInActiveWindow?.let { roots.add(it) }
    for (w in windows) {
        if (w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
            w.root?.let { roots.add(it) }
        }
    }
    for (r in roots) {
        val f = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (f != null && f.isEditable) return f
    }
    for (r in roots) {
        val e = clipFindEditable(r)
        if (e != null) return e
    }
    return null
}

private fun clipFindEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
    if (node == null) return null
    if (node.isEditable) return node
    for (i in 0 until node.childCount) {
        val f = clipFindEditable(node.getChild(i))
        if (f != null) return f
    }
    return null
}
