package com.foldos.widgetsplus

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * The Widgets + app screen: grant calendar and contacts access, add the widget to the home screen and jump to
 * FoldOS Hub. Everything here is optional; the widget itself shows a tap-to-allow prompt when a permission is missing.
 */
class SetupActivity : Activity() {

    private lateinit var statusCalendar: TextView
    private lateinit var statusContacts: TextView
    private lateinit var allow: Button
    private lateinit var hubStatus: TextView
    private lateinit var hubButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        // targetSdk 35 draws edge to edge: keep the content clear of the system bars.
        findViewById<View>(R.id.setup_root).setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        statusCalendar = findViewById(R.id.status_calendar)
        statusContacts = findViewById(R.id.status_contacts)
        allow = findViewById(R.id.btn_allow)
        hubStatus = findViewById(R.id.hub_status)
        hubButton = findViewById(R.id.btn_hub)

        allow.setOnClickListener { onAllow() }
        findViewById<Button>(R.id.btn_add).setOnClickListener { onAddWidget() }
        hubButton.setOnClickListener {
            startActivity(Intent().setClassName("com.foldos.launcher", "com.foldos.launcher.hub.HubActivity"))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val calendar = WidgetData.hasCalendar(this)
        val contacts = WidgetData.hasContacts(this)
        statusCalendar.text = (if (calendar) "✓ " else "• ") + "Calendar: " + (if (calendar) "allowed" else "not allowed yet")
        statusContacts.text = (if (contacts) "✓ " else "• ") + "Contacts: " + (if (contacts) "allowed" else "not allowed yet")
        allow.visibility = if (calendar && contacts) View.GONE else View.VISIBLE
        val hub = WidgetRenderer.hubInstalled(this)
        hubStatus.text = if (hub) {
            "FoldOS is installed. The widget's Mail and AI tiles open the matching Hub tab."
        } else {
            "FoldOS is not installed. The widget uses your default calendar, mail and assistant apps instead."
        }
        hubButton.visibility = if (hub) View.VISIBLE else View.GONE
    }

    private fun onAllow() {
        val missing = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.READ_CONTACTS)
            .filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return
        // Once Android stops asking, the only way to allow is the app's system settings page.
        if (missing.any { !shouldShowRequestPermissionRationale(it) && wasAsked() }) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))
        } else {
            getSharedPreferences("setup", MODE_PRIVATE).edit().putBoolean("asked", true).apply()
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    private fun wasAsked(): Boolean = getSharedPreferences("setup", MODE_PRIVATE).getBoolean("asked", false)

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
        WidgetsPlusProvider.requestRefresh(this)
    }

    private fun onAddWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager != null && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(WidgetsPlusProvider.component(this), null, null)
        } else {
            Toast.makeText(this, "Long-press the home screen, choose Widgets, then Widgets +", Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
    }
}
