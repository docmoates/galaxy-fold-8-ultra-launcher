package com.foldos.launcher.ai

import android.content.Context
import android.content.pm.PackageManager
import com.foldos.launcher.core.repo.AiProvider

/**
 * The `AI_MODE_ONDEVICE` engine: Gemini Nano through Google's AICore.
 *
 * **Deliberately a stub in this build.** The experimental SDK
 * (`com.google.ai.edge.aicore:aicore:0.0.1-exp01` on the `google()` repository) could not be
 * verified against a compiler here, and its API surface (`GenerativeModel`, `generationConfig {}`,
 * `generateContent`) is still "exp": pulling it in blind would risk the whole CI build for a feature
 * that only works on devices with the AICore system app and a downloaded model. So this class
 * reports [available] = false, and [complete] throws, which makes [FoldOsAiCommandRouter] fall back
 * to the template engine and tell the user why.
 *
 * To enable it later:
 * 1. `implementation("com.google.ai.edge.aicore:aicore:0.0.1-exp01")` in `app/build.gradle.kts`.
 * 2. Replace the body of [complete] with roughly
 *    ```
 *    val model = GenerativeModel(generationConfig { context = appContext; temperature = 0.2f; topK = 16; maxOutputTokens = 256 })
 *    return model.generateContent("$context\n\nUser: $prompt").text ?: ""
 *    ```
 *    and make [available] call `model.prepareInferenceEngine()` inside `runCatching`.
 * 3. Keep [aiCoreInstalled] as the cheap pre-check (the `com.google.android.aicore` package).
 */
class AiCoreProvider(context: Context) : AiProvider {

    private val appContext: Context = context.applicationContext

    override val name: String = "Gemini Nano"

    /** True when the AICore system component is present on this device (not the same as usable). */
    fun aiCoreInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(AICORE_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
    }.isSuccess

    /** Why the engine cannot run right now, for Settings › AI. */
    fun unavailableReason(): String =
        if (aiCoreInstalled()) {
            "AICore is on this device, but this FoldOS build does not bundle the Gemini Nano SDK yet."
        } else {
            "This device has no AICore (Gemini Nano) component."
        }

    override suspend fun available(): Boolean = false

    override suspend fun complete(prompt: String, context: String): String {
        throw IllegalStateException(unavailableReason())
    }

    companion object {
        const val AICORE_PACKAGE: String = "com.google.android.aicore"
    }
}
