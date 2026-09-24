package com.rafael.radarcorrida

import android.content.Context
import kotlin.math.roundToInt

object RideParser {
    private val moneyRegex = Regex("R\\$\\s*([0-9]{1,4}(?:[.,][0-9]{2})?)", RegexOption.IGNORE_CASE)
    private val kmRegex = Regex("([0-9]{1,3}(?:[.,][0-9]+)?)\\s*km", RegexOption.IGNORE_CASE)
    private val minRegex = Regex("([0-9]{1,3})\\s*min", RegexOption.IGNORE_CASE)
    private val explicitRateRegex = Regex(
        "R\\$\\s*([0-9]{1,3}(?:[.,][0-9]+)?)\\s*(?:/\\s*km|por\\s+km)",
        RegexOption.IGNORE_CASE
    )
    private val explicitHourRegex = Regex(
        "R\\$\\s*([0-9]{1,4}(?:[.,][0-9]+)?)\\s*(?:/\\s*h|por\\s+hora)",
        RegexOption.IGNORE_CASE
    )
    private val motoristaRegex = Regex(
        "\\(Motorista\\)\\s*R\\$\\s*([0-9]{1,4}(?:[.,][0-9]{2})?)",
        RegexOption.IGNORE_CASE
    )

    fun parse(context: Context, packageName: String, raw: String): RideOffer? {
        val text = raw.replace("\n", " | ").replace(Regex("\\s+"), " ").trim()
        if (!looksLikeRide(text)) return null

        val app = identifyApp(packageName, text)
        return if (app == "Rapidocar") parseRapidocar(context, app, text)
        else parseGeneric(context, app, text)
    }

    private fun parseRapidocar(context: Context, app: String, text: String): RideOffer? {
        val fare = motoristaRegex.find(text)?.groupValues?.getOrNull(1)?.num()
            ?: moneyRegex.findAll(text).mapNotNull { it.groupValues[1].num() }.toList().getOrNull(1)
            ?: return null

        val kmValues = kmRegex.findAll(text).mapNotNull { it.groupValues[1].num() }.toList()
        if (kmValues.isEmpty()) return null

        val explicitRate = explicitRateRegex.find(text)?.groupValues?.getOrNull(1)?.num()
        val explicitHour = explicitHourRegex.find(text)?.groupValues?.getOrNull(1)?.num()

        val totalDistance = when {
            explicitRate != null && explicitRate > 0 -> fare / explicitRate
            kmValues.size >= 3 -> kmValues.first()
            kmValues.size >= 2 -> kmValues.takeLast(2).sum()
            else -> kmValues.first()
        }

        val pickup = if (kmValues.size >= 3) kmValues[kmValues.size - 2] else kmValues.getOrNull(0)
        val trip = if (kmValues.size >= 3) kmValues.last() else kmValues.getOrNull(1)

        val minutes = minRegex.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val totalMinutes = when {
            kmValues.size >= 3 && minutes.isNotEmpty() -> minutes.first()
            minutes.size >= 2 -> minutes.takeLast(2).sum()
            else -> minutes.firstOrNull()
        }

        return build(
            context = context,
            app = app,
            fare = fare,
            totalDistance = totalDistance,
            pickup = pickup,
            trip = trip,
            totalMinutes = totalMinutes,
            rateDistance = totalDistance,
            rateMinutes = totalMinutes,
            explicitRate = explicitRate,
            explicitHour = explicitHour,
            text = text
        )
    }

    private fun parseGeneric(context: Context, app: String, text: String): RideOffer? {
        val money = moneyRegex.findAll(text).mapNotNull { it.groupValues[1].num() }.toList()
        val fare = money.firstOrNull { it in 2.0..500.0 } ?: return null

        val kmValues = kmRegex.findAll(text)
            .mapNotNull { it.groupValues[1].num() }
            .filter { it in 0.1..300.0 }
            .toList()
        if (kmValues.isEmpty()) return null

        val minutes = minRegex.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val explicitRate = explicitRateRegex.find(text)?.groupValues?.getOrNull(1)?.num()
        val explicitHour = explicitHourRegex.find(text)?.groupValues?.getOrNull(1)?.num()

        // Na tela de histórico da 99, o primeiro km/min é a própria viagem.
        // Isso permite conferir a leitura usando corridas já realizadas.
        val is99History = app == "99" && (
            text.contains("Viagem aceita", true) ||
            text.contains("Histórico", true)
        )

        if (is99History) {
            val trip = kmValues.first()
            val tripMinutes = minutes.firstOrNull()
            return build(
                context = context,
                app = app,
                fare = fare,
                totalDistance = trip,
                pickup = null,
                trip = trip,
                totalMinutes = tripMinutes,
                rateDistance = trip,
                rateMinutes = tripMinutes,
                explicitRate = explicitRate,
                explicitHour = explicitHour,
                text = text
            )
        }

        val pickup: Double?
        val trip: Double?
        val total: Double

        if (kmValues.size >= 2) {
            pickup = kmValues[0]
            trip = kmValues[1]
            total = pickup + trip
        } else {
            pickup = null
            trip = kmValues[0]
            total = kmValues[0]
        }

        val pickupMinutes = minutes.getOrNull(0)
        val tripMinutes = if (minutes.size >= 2) minutes[1] else minutes.firstOrNull()
        val totalMinutes = when {
            pickupMinutes != null && tripMinutes != null && minutes.size >= 2 -> pickupMinutes + tripMinutes
            else -> tripMinutes
        }

        // 99/Uber: R$/km e R$/h usam a viagem, sem somar o deslocamento até a coleta.
        return build(
            context = context,
            app = app,
            fare = fare,
            totalDistance = total,
            pickup = pickup,
            trip = trip,
            totalMinutes = totalMinutes,
            rateDistance = trip ?: total,
            rateMinutes = tripMinutes ?: totalMinutes,
            explicitRate = explicitRate,
            explicitHour = explicitHour,
            text = text
        )
    }

    private fun build(
        context: Context,
        app: String,
        fare: Double,
        totalDistance: Double,
        pickup: Double?,
        trip: Double?,
        totalMinutes: Int?,
        rateDistance: Double,
        rateMinutes: Int?,
        explicitRate: Double?,
        explicitHour: Double?,
        text: String
    ): RideOffer? {
        if (fare <= 0 || totalDistance <= 0 || rateDistance <= 0) return null

        val rate = explicitRate ?: (fare / rateDistance)
        val net = fare - (totalDistance * AppConfig.costPerKm(context))
        val perHour = explicitHour ?: rateMinutes?.takeIf { it > 0 }?.let { fare / it * 60.0 }

        val good = AppConfig.goodRate(context)
        val excellent = AppConfig.excellentRate(context)
        val minProfit = AppConfig.minProfit(context)

        val classification = when {
            rate >= excellent && net >= minProfit -> Classification.EXCELLENT
            rate >= good && net > 0 -> Classification.NORMAL
            else -> Classification.BAD
        }

        val rateScore = (((rate - 0.8) / 2.2) * 100.0).coerceIn(0.0, 100.0)
        val profitScore = ((net / (minProfit.coerceAtLeast(1.0) * 1.5)) * 100.0).coerceIn(0.0, 100.0)
        val pickupPenalty = ((pickup ?: 0.0) * 4.0).coerceIn(0.0, 25.0)
        val score = (rateScore * 0.60 + profitScore * 0.40 - pickupPenalty)
            .coerceIn(0.0, 100.0)
            .roundToInt()

        return RideOffer(
            appName = app,
            fare = fare,
            totalDistanceKm = totalDistance,
            pickupDistanceKm = pickup,
            tripDistanceKm = trip,
            totalMinutes = totalMinutes,
            ratePerKm = rate,
            ratePerHour = perHour,
            netProfit = net,
            score = score,
            classification = classification,
            sourceText = text
        )
    }

    private fun identifyApp(packageName: String, text: String): String {
        val p = packageName.lowercase()
        return when {
            p.contains("rapidocar") || text.contains("(Motorista)", true) -> "Rapidocar"
            p.contains("ubercab") || p.contains("uber") -> "Uber"
            p.contains("com.app99.driver") || p.contains("app99") || p.contains("taxis99") -> "99"
            else -> "Corrida"
        }
    }

    private fun looksLikeRide(text: String): Boolean {
        val hasMoney = text.contains("R$", true)
        val hasDistance = Regex("\\bkm\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasAction = listOf(
            "aceitar", "recusar", "corrida", "motorista", "coleta", "embarque",
            "viagem", "Viagem aceita", "Histórico"
        ).any { text.contains(it, true) }
        return hasMoney && hasDistance && hasAction
    }

    private fun String.num(): Double? {
        val clean = trim()
        return if (clean.contains(',')) {
            clean.replace(".", "").replace(',', '.').toDoubleOrNull()
        } else {
            clean.toDoubleOrNull()
        }
    }
}
