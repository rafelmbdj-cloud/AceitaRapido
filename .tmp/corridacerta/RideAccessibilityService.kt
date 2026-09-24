package com.rafael.radarcorrida

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RideAccessibilityService : AccessibilityService() {
    private lateinit var overlay: OverlayController
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var lastSignature = ""
    private var tone: ToneGenerator? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = OverlayController(this)
        tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        TrialManager.ensureStarted(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!TrialManager.isActive(this)) {
            if (::overlay.isInitialized) overlay.hide()
            return
        }

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

        val signature =
            "${offer.appName}-${offer.fare}-${offer.totalDistanceKm}-${offer.pickupDistanceKm}-${offer.tripDistanceKm}"

        overlay.show(offer)

        if (signature != lastSignature) {
            lastSignature = signature
            playAlert(offer.classification)
        }
    }

    fun showTestOverlay() {
        if (!TrialManager.isActive(this)) return
        if (!::overlay.isInitialized) return
        val testOffer = RideOffer(
            appName = "99",
            fare = 11.40,
            totalDistanceKm = 2.3,
            pickupDistanceKm = null,
            tripDistanceKm = 2.3,
            totalMinutes = 6,
            ratePerKm = 4.96,
            ratePerHour = 114.00,
            netProfit = 9.56,
            score = 0,
            classification = Classification.NORMAL,
            sourceText = "Teste da faixa Corrida Certa"
        )
        overlay.show(testOffer)
        playAlert(Classification.NORMAL)
        handler.postDelayed({ if (::overlay.isInitialized) overlay.hide() }, 5000)
    }

    private fun playAlert(classification: Classification) {
        // O antigo botão de voz agora controla somente os bipes.
        if (!AppConfig.voiceEnabled(this)) return

        when (classification) {
            Classification.BAD -> Unit
            Classification.NORMAL -> {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
            }
            Classification.EXCELLENT -> {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                handler.postDelayed({
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                }, 230)
            }
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null || out.size > 300) return
        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collectTexts(node.getChild(i), out)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        pending?.let(handler::removeCallbacks)
        if (::overlay.isInitialized) overlay.destroy()
        tone?.release()
        tone = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: RideAccessibilityService? = null
            private set
    }
}
