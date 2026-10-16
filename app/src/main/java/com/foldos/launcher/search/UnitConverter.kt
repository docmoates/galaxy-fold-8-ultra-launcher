package com.foldos.launcher.search

import java.text.NumberFormat
import java.util.Locale

/**
 * Unit conversion for the search sheet: length, weight, temperature and currency (static sample
 * rates; see [CURRENCY_NOTE]). Understands "5 km to mi", "72 f in c", "100 usd eur", "10 kg" (no
 * target: the unit's natural counterpart) and the `->` / `=` / `as` connectors.
 */
object UnitConverter {

    /** Shown on every currency answer: the rates are a bundled sample, not live. */
    const val CURRENCY_NOTE: String = "sample rates"

    /** A conversion the search sheet can show. */
    data class Result(
        /** "5 km". */
        val input: String,
        /** "3.11 mi". */
        val output: String,
        /** "Length", "Weight", "Temperature" or "Currency". */
        val category: String,
        val note: String? = null,
    )

    private class UnitDef(
        val id: String,
        val symbol: String,
        val category: String,
        /** Multiplier to the category's base unit (metres, grams, USD). Unused for temperature. */
        val toBase: Double,
        val aliases: List<String>,
        /** The unit a bare "5 km" converts to. */
        val counterpart: String,
    )

    private const val LENGTH = "Length"
    private const val WEIGHT = "Weight"
    private const val TEMPERATURE = "Temperature"
    private const val CURRENCY = "Currency"

    private val units: List<UnitDef> = listOf(
        UnitDef("mm", "mm", LENGTH, 0.001, listOf("millimeter", "millimeters", "millimetre", "millimetres"), "in"),
        UnitDef("cm", "cm", LENGTH, 0.01, listOf("centimeter", "centimeters", "centimetre", "centimetres"), "in"),
        UnitDef("m", "m", LENGTH, 1.0, listOf("meter", "meters", "metre", "metres"), "ft"),
        UnitDef("km", "km", LENGTH, 1000.0, listOf("kilometer", "kilometers", "kilometre", "kilometres", "kms"), "mi"),
        UnitDef("in", "in", LENGTH, 0.0254, listOf("inch", "inches", "\""), "cm"),
        UnitDef("ft", "ft", LENGTH, 0.3048, listOf("foot", "feet", "'"), "m"),
        UnitDef("yd", "yd", LENGTH, 0.9144, listOf("yard", "yards"), "m"),
        UnitDef("mi", "mi", LENGTH, 1609.344, listOf("mile", "miles"), "km"),
        UnitDef("nmi", "nmi", LENGTH, 1852.0, listOf("nautical", "nauticalmile", "nauticalmiles"), "km"),

        UnitDef("mg", "mg", WEIGHT, 0.001, listOf("milligram", "milligrams"), "oz"),
        UnitDef("g", "g", WEIGHT, 1.0, listOf("gram", "grams", "gramme", "grammes"), "oz"),
        UnitDef("kg", "kg", WEIGHT, 1000.0, listOf("kilogram", "kilograms", "kilo", "kilos", "kgs"), "lb"),
        UnitDef("t", "t", WEIGHT, 1_000_000.0, listOf("tonne", "tonnes", "ton", "tons", "metricton"), "lb"),
        UnitDef("oz", "oz", WEIGHT, 28.349523125, listOf("ounce", "ounces"), "g"),
        UnitDef("lb", "lb", WEIGHT, 453.59237, listOf("lbs", "pound", "pounds"), "kg"),
        UnitDef("st", "st", WEIGHT, 6350.29318, listOf("stone", "stones"), "kg"),

        UnitDef("c", "°C", TEMPERATURE, 1.0, listOf("°c", "celsius", "centigrade", "degc"), "f"),
        UnitDef("f", "°F", TEMPERATURE, 1.0, listOf("°f", "fahrenheit", "degf"), "c"),
        UnitDef("k", "K", TEMPERATURE, 1.0, listOf("kelvin", "kelvins"), "c"),

        // Sample rates per 1 USD; replaced by a live feed in a later phase.
        UnitDef("usd", "USD", CURRENCY, 1.0, listOf("$", "dollar", "dollars", "us$"), "eur"),
        UnitDef("eur", "EUR", CURRENCY, 1.0 / 0.92, listOf("€", "euro", "euros"), "usd"),
        UnitDef("gbp", "GBP", CURRENCY, 1.0 / 0.79, listOf("£", "pound sterling", "quid", "sterling"), "usd"),
        UnitDef("jpy", "JPY", CURRENCY, 1.0 / 150.0, listOf("¥", "yen"), "usd"),
        UnitDef("cad", "CAD", CURRENCY, 1.0 / 1.36, listOf("c$", "canadian"), "usd"),
        UnitDef("aud", "AUD", CURRENCY, 1.0 / 1.52, listOf("a$", "australian"), "usd"),
        UnitDef("chf", "CHF", CURRENCY, 1.0 / 0.88, listOf("franc", "francs"), "usd"),
        UnitDef("cny", "CNY", CURRENCY, 1.0 / 7.2, listOf("rmb", "yuan"), "usd"),
        UnitDef("inr", "INR", CURRENCY, 1.0 / 83.0, listOf("₹", "rupee", "rupees"), "usd"),
        UnitDef("krw", "KRW", CURRENCY, 1.0 / 1350.0, listOf("₩", "won"), "usd"),
        UnitDef("mxn", "MXN", CURRENCY, 1.0 / 17.0, listOf("peso", "pesos"), "usd"),
        UnitDef("brl", "BRL", CURRENCY, 1.0 / 5.0, listOf("real", "reais"), "usd"),
        UnitDef("sek", "SEK", CURRENCY, 1.0 / 10.5, listOf("krona", "kronor"), "usd"),
        UnitDef("nok", "NOK", CURRENCY, 1.0 / 10.6, listOf("krone", "kroner"), "usd"),
    )

    private val byAlias: Map<String, UnitDef> = buildMap {
        for (unit in units) {
            put(unit.id, unit)
            put(unit.symbol.lowercase(Locale.ROOT), unit)
            for (alias in unit.aliases) put(alias.lowercase(Locale.ROOT).replace(" ", ""), unit)
        }
    }

    private val pattern = Regex(
        "^\\s*([-+]?\\d+(?:[.,]\\d+)?)\\s*([^\\d\\s]+)" +
            "(?:\\s+(?:to|in|into|as)\\s+|\\s*(?:->|→|=)\\s*|\\s+)?([^\\d\\s]+)?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** Parses and converts [text]; null when it is not a conversion request. */
    fun convert(text: String): Result? {
        val m = pattern.matchEntire(text.trim()) ?: return null
        val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = lookup(m.groupValues[2]) ?: return null
        val targetText = m.groupValues[3]
        val to: UnitDef = if (targetText.isBlank()) {
            byAlias[from.counterpart] ?: return null
        } else {
            lookup(targetText) ?: return null
        }
        if (from.category != to.category) return null
        val value = when (from.category) {
            TEMPERATURE -> temperature(amount, from.id, to.id)
            else -> amount * from.toBase / to.toBase
        }
        if (value.isNaN() || value.isInfinite()) return null
        return Result(
            input = "${formatAmount(amount)} ${from.symbol}",
            output = "${formatAmount(value)} ${to.symbol}",
            category = from.category,
            note = if (from.category == CURRENCY) CURRENCY_NOTE else null,
        )
    }

    private fun lookup(raw: String): UnitDef? {
        val key = raw.trim().lowercase(Locale.ROOT).replace(" ", "").removeSuffix(".")
        if (key.isEmpty()) return null
        return byAlias[key] ?: byAlias[key.removeSuffix("s")]
    }

    private fun temperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "c" -> value
            "f" -> (value - 32.0) * 5.0 / 9.0
            else -> value - 273.15
        }
        return when (to) {
            "c" -> celsius
            "f" -> celsius * 9.0 / 5.0 + 32.0
            else -> celsius + 273.15
        }
    }

    /** Up to 4 decimals (fewer for large values), trailing zeros dropped, thousands grouped. */
    fun formatAmount(value: Double): String {
        val abs = kotlin.math.abs(value)
        val decimals = when {
            abs == 0.0 -> 0
            abs >= 1000 -> 1
            abs >= 10 -> 2
            abs >= 1 -> 3
            else -> 4
        }
        val format = NumberFormat.getNumberInstance(Locale.getDefault())
        format.maximumFractionDigits = decimals
        format.minimumFractionDigits = 0
        format.isGroupingUsed = true
        return format.format(value)
    }
}
