package com.foldos.widgetsplus

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * The Widgets + app screen. It works on its own: sign in with your Crosspoint account (the same one FoldOS uses),
 * link Gmail once, allow calendar and contacts, add the widget, and try Ask AI. Everything is optional; the widget
 * says what is missing when something is not set up.
 */
class SetupActivity : Activity() {

    private lateinit var acctStatus: TextView
    private lateinit var signinForm: View
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var server: EditText
    private lateinit var signIn: Button
    private lateinit var link: Button
    private lateinit var signOut: Button
    private lateinit var statusCalendar: TextView
    private lateinit var statusContacts: TextView
    private lateinit var allow: Button
    private var polling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        // targetSdk 35 draws edge to edge: keep the content clear of the system bars.
        findViewById<View>(R.id.setup_root).setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        acctStatus = findViewById(R.id.acct_status)
        signinForm = findViewById(R.id.signin_form)
        email = findViewById(R.id.input_email)
        password = findViewById(R.id.input_password)
        server = findViewById(R.id.input_server)
        signIn = findViewById(R.id.btn_signin)
        link = findViewById(R.id.btn_link)
        signOut = findViewById(R.id.btn_signout)
        statusCalendar = findViewById(R.id.status_calendar)
        statusContacts = findViewById(R.id.status_contacts)
        allow = findViewById(R.id.btn_allow)

        signIn.setOnClickListener { onSignIn() }
        link.setOnClickListener { onLinkGmail() }
        signOut.setOnClickListener { onSignOut() }
        allow.setOnClickListener { onAllow() }
        findViewById<Button>(R.id.btn_add).setOnClickListener { onAddWidget() }
        findViewById<Button>(R.id.btn_ask).setOnClickListener { startActivity(AskActivity.intent(this, null)) }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        showAccount(MailService.savedAccount(this))
        // Ask the server for the real state (is Gmail linked yet?) without blocking the screen.
        thread(name = "widgetsplus-account") {
            val status = MailService.refreshAccount(this) ?: return@thread
            runOnUiThread { if (!isFinishing) showAccount(status) }
        }
    }

    // ---- Account -------------------------------------------------------------------------------------

    private fun showAccount(status: AccountStatus) {
        val signedIn = status.signedInAs != null
        signinForm.visibility = if (signedIn) View.GONE else View.VISIBLE
        signOut.visibility = if (signedIn) View.VISIBLE else View.GONE
        link.visibility = if (signedIn && (!status.linked || status.needsRelink) && status.available) View.VISIBLE else View.GONE
        link.text = if (status.needsRelink) "Reconnect Gmail" else "Link Gmail"
        acctStatus.text = when {
            !signedIn -> "• Not signed in. Use the same email and password as in FoldOS."
            !status.available -> "✓ Signed in as ${status.signedInAs}. The server has no mail set up yet."
            status.needsRelink -> "✓ Signed in as ${status.signedInAs}. Gmail needs to be connected again."
            status.linked -> "✓ Signed in as ${status.signedInAs}. Gmail: ${status.gmail ?: "linked"}"
            else -> "✓ Signed in as ${status.signedInAs}. Gmail is not linked yet."
        }
    }

    private fun onSignIn() {
        signIn.isEnabled = false
        val addr = email.text.toString()
        val pass = password.text.toString()
        val srv = server.text.toString()
        thread(name = "widgetsplus-signin") {
            val error = MailService.signIn(this, addr, pass, srv)
            val status = if (error == null) MailService.refreshAccount(this) ?: MailService.savedAccount(this) else null
            runOnUiThread {
                signIn.isEnabled = true
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                } else {
                    password.setText("")
                    status?.let { showAccount(it) }
                    WidgetsPlusProvider.requestRefresh(this)
                }
            }
        }
    }

    private fun onSignOut() {
        thread(name = "widgetsplus-signout") {
            MailService.signOut(this)
            runOnUiThread {
                showAccount(MailService.savedAccount(this))
                WidgetsPlusProvider.requestRefresh(this)
            }
        }
    }

    /** Opens the server's one-time Gmail approval page in the browser, then polls until it reports Gmail linked. */
    private fun onLinkGmail() {
        link.isEnabled = false
        thread(name = "widgetsplus-link") {
            val url = try {
                MailService.linkUrl(this)
            } catch (e: IllegalStateException) {
                runOnUiThread {
                    link.isEnabled = true
                    Toast.makeText(this, e.message ?: "Could not start the Gmail link.", Toast.LENGTH_LONG).show()
                }
                return@thread
            }
            runOnUiThread {
                link.isEnabled = true
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                } catch (_: Exception) {
                    Toast.makeText(this, "No browser could open the Gmail page.", Toast.LENGTH_LONG).show()
                }
            }
            pollForLink()
        }
    }

    /** Checks /me every 2 seconds for up to 2 minutes; stops early once Gmail is linked. */
    private fun pollForLink() {
        if (polling) return
        polling = true
        try {
            repeat(60) {
                Thread.sleep(2_000)
                val status = MailService.refreshAccount(this) ?: return@repeat
                if (status.linked) {
                    runOnUiThread { if (!isFinishing) showAccount(status) }
                    WidgetsPlusProvider.requestRefresh(this)
                    return
                }
            }
        } finally {
            polling = false
        }
    }

    // ---- Permissions and the widget ------------------------------------------------------------------

    private fun refreshPermissions() {
        val calendar = WidgetData.hasCalendar(this)
        val contacts = WidgetData.hasContacts(this)
        statusCalendar.text = (if (calendar) "✓ " else "• ") + "Calendar: " + (if (calendar) "allowed" else "not allowed yet")
        statusContacts.text = (if (contacts) "✓ " else "• ") + "Contacts: " + (if (contacts) "allowed" else "not allowed yet")
        allow.visibility = if (calendar && contacts) View.GONE else View.VISIBLE
    }

    private fun onAllow() {
        val missing = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.READ_CONTACTS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return
        // Once Android stops asking, the only way to allow is the app's system settings page.
        if (missing.any { !shouldShowRequestPermissionRationale(it) && wasAsked() }) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
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
        refreshPermissions()
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
