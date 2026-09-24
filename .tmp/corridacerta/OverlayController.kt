package com.rafael.radarcorrida

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Cartão compacto do Corrida Certa.
 * O fundo permanece escuro e o semáforo indica a classificação da oferta.
 */
class OverlayController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var titleView: TextView? = null
    private var detailsView: TextView? = null
    private var extraView: TextView? = null
    private var redDot: TextView? = null
    private var yellowDot: TextView? = null
    private var greenDot: TextView? = null

    fun show(offer: RideOffer) {
        ensureView()
        updateTrafficLight(offer.classification)

        titleView?.text =
            "${offer.classification.label.uppercase()}  •  R$ ${fmt(offer.fare)}  •  R$ ${fmt(offer.ratePerKm)}/km"

        val routeParts = mutableListOf<String>()
        offer.pickupDistanceKm?.let { routeParts += "Coleta ${fmt1(it)} km" }
        offer.tripDistanceKm?.let { routeParts += "Viagem ${fmt1(it)} km" }
        offer.ratePerHour?.let { routeParts += "R$ ${fmt(it)}/h" }
        val routeLine = routeParts.joinToString("  •  ")

        detailsView?.apply {
            text = routeLine
            visibility = if (routeLine.isBlank()) View.GONE else View.VISIBLE
        }

        extraView?.text = "${offer.appName}  •  Líq. R$ ${fmt(offer.netProfit)}"
        root?.visibility = View.VISIBLE
    }

    private fun ensureView() {
        if (root != null) return

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(10))
            elevation = dp(10).toFloat()
            background = roundedBackground(Color.rgb(16, 24, 40))
        }

        val topRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(service).apply {
            text = "CORRIDA CERTA"
            setTextColor(Color.rgb(132, 202, 255))
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
            includeFontPadding = false
        }

        val spacer = View(service)

        val lights = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun dot(): TextView = TextView(service).apply {
            text = "●"
            textSize = 18f
            setTextColor(INACTIVE_DOT)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(2), 0, dp(2), 0)
        }

        val r = dot()
        val y = dot()
        val g = dot()
        lights.addView(r)
        lights.addView(y)
        lights.addView(g)

        topRow.addView(
            brand,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        topRow.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        topRow.addView(lights)

        val title = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.START
            maxLines = 2
            includeFontPadding = false
            setPadding(0, dp(5), 0, 0)
        }

        val details = TextView(service).apply {
            setTextColor(Color.rgb(234, 236, 240))
            textSize = 14f
            gravity = Gravity.START
            maxLines = 2
            includeFontPadding = false
            setPadding(0, dp(5), 0, 0)
        }

        val extra = TextView(service).apply {
            setTextColor(Color.rgb(152, 162, 179))
            textSize = 12f
            gravity = Gravity.START
            maxLines = 1
            includeFontPadding = false
            setPadding(0, dp(4), 0, 0)
        }

        container.addView(
            topRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            details,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            extra,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            this.y = dp(4)
        }

        windowManager.addView(container, params)
        root = container
        titleView = title
        detailsView = details
        extraView = extra
        redDot = r
        yellowDot = y
        greenDot = g
    }

    private fun updateTrafficLight(classification: Classification) {
        redDot?.setTextColor(INACTIVE_DOT)
        yellowDot?.setTextColor(INACTIVE_DOT)
        greenDot?.setTextColor(INACTIVE_DOT)

        when (classification) {
            Classification.EXCELLENT -> greenDot?.setTextColor(Color.rgb(18, 183, 106))
            Classification.NORMAL -> yellowDot?.setTextColor(Color.rgb(247, 144, 9))
            Classification.BAD -> redDot?.setTextColor(Color.rgb(240, 68, 56))
        }
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), Color.rgb(52, 64, 84))
    }

    fun hide() {
        root?.visibility = View.GONE
    }

    fun destroy() {
        root?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
        titleView = null
        detailsView = null
        extraView = null
        redDot = null
        yellowDot = null
        greenDot = null
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).roundToInt()

    private fun fmt(v: Double): String =
        ((v * 100.0).roundToInt() / 100.0)
            .let { "%.2f".format(it).replace('.', ',') }

    private fun fmt1(v: Double): String =
        ((v * 10.0).roundToInt() / 10.0)
            .let { "%.1f".format(it).replace('.', ',') }

    companion object {
        private val INACTIVE_DOT = Color.rgb(71, 84, 103)
    }
}
