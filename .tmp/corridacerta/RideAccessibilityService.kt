package com.rafael.radarcorrida

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class RideAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {
    private lateinit var overlay: OverlayController
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var lastSignature = ""
    private var lastSpokenAt = 0L
    private var ttsReady = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = OverlayController(this)
        tts = TextToSpeech(this, this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        pending?.let(handler::removeCallbacks)
        pending = Runnable { inspect(pkg) }.also { handler.postDelayed(it, 300) }
    }

    private fun inspect(pkg: String) {
        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>(80)
        collectTexts(root, texts)
        val joined = texts.distinct().joinToString(" | ")

        val offer = RideParser.parse(this, pkg, joined)
        if (offer == null) {
            handler.postDelayed({ if (::overlay.isInitialized) overlay.hide() }, 1200)
            return
        }

        val signature = "${offer.appName}-${offer.fare}-${offer.totalDistanceKm}-${offer.pickupDistanceKm}-${offer.tripDistanceKm}"
        overlay.show(offer)

        if (signature != lastSignature) {
            lastSignature = signature
            speak(offer)
        }
    }

    fun showTestOverlay() {
        if (!::overlay.isInitialized) return
        val testOffer = RideOffer(
            appName = "TESTE",
            fare = 12.00,
            totalDistanceKm = 4.3,
            pickupDistanceKm = 3.2,
            tripDistanceKm = 1.1,
            totalMinutes = 8,
            ratePerKm = 2.07,
            ratePerHour = 66.38,
            netProfit = 5.41,
            score = 87,
            classification = Classification.NORMAL,
            sourceText = "Teste da faixa Corrida Certa"
        )
        overlay.show(testOffer)
        if (AppConfig.voiceEnabled(this) && ttsReady) {
            tts?.speak(
                "Corrida Certa. Teste da faixa. Corrida normal.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "overlay-test"
            )
        }
        handler.postDelayed({ if (::overlay.isInitialized) overlay.hide() }, 5000)
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || out.size > 300) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collectTexts(node.getChild(i), out)
    }

    private fun speak(offer: RideOffer) {
        if (!AppConfig.voiceEnabled(this) || !ttsReady) return
        val now = System.currentTimeMillis()
        if (now - lastSpokenAt < 4000) return
        lastSpokenAt = now

        val phrase = buildString {
            append("Corrida Certa, ")
            append(offer.classification.label.lowercase())
            append(", ")
            append(String.format(Locale("pt", "BR"), "%.2f reais por quilômetro", offer.ratePerKm))
            offer.pickupDistanceKm?.let {
                append(", coleta ")
                append(String.format(Locale("pt", "BR"), "%.1f quilômetros", it))
            }
            append(", lucro estimado ")
            append(String.format(Locale("pt", "BR"), "%.2f reais", offer.netProfit))
        }
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "ride-analysis")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("pt", "BR")
            ttsReady = true
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        pending?.let(handler::removeCallbacks)
        if (::overlay.isInitialized) overlay.destroy()
        tts?.stop()
        tts?.shutdown()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: RideAccessibilityService? = null
            private set
    }
}
