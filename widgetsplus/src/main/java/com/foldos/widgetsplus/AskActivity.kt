package com.foldos.widgetsplus

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * The Ask AI sheet: a bottom sheet over the home screen with a real text box (Android widgets cannot hold one),
 * opened from the widget's AI tab. Questions run through [AssistantEngine] on a worker thread; answers that can start
 * something (a call, a timer, navigation) show a button, and nothing starts until it is tapped.
 */
class AskActivity : Activity() {

    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ask)
        messages = findViewById(R.id.ask_messages)
        scroll = findViewById(R.id.ask_scroll)
        input = findViewById(R.id.ask_input)
        val card = findViewById<View>(R.id.ask_card)

        // Edge to edge (targetSdk 35): lift the sheet above the keyboard and the navigation bar; cap its height.
        val cardHeight = (resources.displayMetrics.heightPixels * 0.72f).toInt()
        findViewById<FrameLayout>(R.id.ask_root).setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            val bottom = maxOf(bars.bottom, ime.bottom)
            card.setPadding(card.paddingLeft, card.paddingTop, card.paddingRight, dp(16) + bottom)
            card.layoutParams = (card.layoutParams as FrameLayout.LayoutParams).apply { height = cardHeight + bottom }
            insets
        }
        findViewById<View>(R.id.ask_root).setOnClickListener { finish() }
        findViewById<View>(R.id.ask_close).setOnClickListener { finish() }
        findViewById<View>(R.id.ask_send).setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else {
                false
            }
        }

        addBubble("Hi. Ask about your day, your mail or your contacts, or tell me to call someone or set a timer.", fromUser = false)
        handlePrompt(intent)
        if (intent.getStringExtra(EXTRA_PROMPT) == null) showKeyboard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePrompt(intent)
    }

    private fun handlePrompt(intent: Intent) {
        intent.getStringExtra(EXTRA_PROMPT)?.takeIf { it.isNotBlank() }?.let { ask(it) }
    }

    private fun submit() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        ask(text)
    }

    private fun ask(question: String) {
        addBubble(question, fromUser = true)
        val pending = addBubble("Thinking…", fromUser = false)
        thread(name = "widgetsplus-ask") {
            val reply = try {
                AssistantEngine.answer(applicationContext, question)
            } catch (_: Exception) {
                AssistantReply("Something went wrong. Please try again.")
            }
            main.post {
                if (isFinishing || isDestroyed) return@post
                pending.text = reply.text
                reply.actions.forEach { addAction(it) }
                scrollToEnd()
            }
        }
    }

    private fun addBubble(text: String, fromUser: Boolean): TextView {
        val bubble = TextView(this).apply {
            this.text = text
            setTextColor(getColor(R.color.w_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(getColor(if (fromUser) R.color.w_accent_container else R.color.w_card))
            }
            setTextIsSelectable(true)
        }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            gravity = if (fromUser) Gravity.END else Gravity.START
            if (fromUser) marginStart = dp(48) else marginEnd = dp(48)
        }
        messages.addView(bubble, params)
        scrollToEnd()
        return bubble
    }

    private fun addAction(action: AssistantAction) {
        val button = TextView(this).apply {
            text = action.label
            setTextColor(getColor(R.color.w_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            background = getDrawable(R.drawable.pill_bg)
            setOnClickListener {
                try {
                    startActivity(action.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    Toast.makeText(this@AskActivity, "No app can do that on this phone.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        messages.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply { topMargin = dp(8) })
    }

    private fun scrollToEnd() {
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showKeyboard() {
        input.requestFocus()
        input.post { getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_PROMPT = "com.foldos.widgetsplus.PROMPT"

        /** Opens the sheet; [prompt] is asked at once, null just opens the keyboard. */
        fun intent(context: android.content.Context, prompt: String?): Intent =
            Intent(context, AskActivity::class.java).apply { if (prompt != null) putExtra(EXTRA_PROMPT, prompt) }
    }
}
