package com.foldos.launcher.search

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A small arithmetic evaluator for the calculator provider: `+ - * / % ^`, parentheses, unary
 * minus, decimals, `×` `÷` `−` typed from a phone keyboard, `pi` / `e`, `sqrt` `abs` `sin` `cos`
 * `tan` `log` `ln`, and the "15% of 80" phrasing. `%` between two numbers is the remainder;
 * a `%` directly after a number is a percentage (`20% * 50` = 10).
 */
object Calculator {

    private val looksLikeMath = Regex("^[\\d\\s.,+\\-*/%^()×÷−xX]*$")
    private val percentOf = Regex("^\\s*([\\d.,]+)\\s*%\\s*of\\s*([\\d.,]+)\\s*$", RegexOption.IGNORE_CASE)
    private val functionNames = setOf("sqrt", "abs", "sin", "cos", "tan", "log", "ln")
    private const val MAX_INPUT_LENGTH = 200

    /**
     * Evaluates [text] when it is an arithmetic expression; null when it is not one (plain words,
     * a lone number, a division by zero, unbalanced parentheses...). The result is formatted
     * with up to 10 significant decimals and no trailing zeros.
     */
    fun evaluate(text: String): String? {
        val raw = text.trim()
        // Also keeps the recursive-descent parser far from a stack overflow on pasted junk.
        if (raw.isEmpty() || raw.length > MAX_INPUT_LENGTH) return null
        percentOf.matchEntire(raw)?.let { m ->
            val pct = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val base = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
            val percent = pct / 100.0 * base
            if (percent.isNaN() || percent.isInfinite()) return null
            return format(percent)
        }
        val lower = raw.lowercase()
        val hasFunction = functionNames.any { lower.contains(it) } || lower.contains("pi")
        if (!hasFunction && !looksLikeMath.matches(raw)) return null
        if (!raw.any { it.isDigit() } && !lower.contains("pi") && !lower.contains("e")) return null
        // A lone number is not a calculation.
        if (!hasFunction && raw.none { it in "+-*/%^()×÷−xX" || it == 'x' }) return null
        if (!hasFunction && raw.replace(" ", "").toDoubleOrNull() != null) return null
        val value = try {
            Parser(normalize(raw)).parseAll()
        } catch (e: ArithmeticException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        } catch (e: IndexOutOfBoundsException) {
            return null
        } catch (e: RuntimeException) {
            return null
        }
        if (value.isNaN() || value.isInfinite()) return null
        return format(value)
    }

    private fun normalize(text: String): String = text
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace(',', '.')
        .lowercase()
        .replace(Regex("(?<=[\\d)])\\s*[x]\\s*(?=[\\d(])"), "*")
        .replace(" ", "")

    /** "2.5", "1234.5678", "3" (never "3.0"); large values in plain notation. */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        if (value.isNaN() || value.isInfinite()) return value.toString()
        val decimal = BigDecimal(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = decimal.toPlainString()
        return if (plain.length > 24) String.format(java.util.Locale.ROOT, "%.6e", value) else plain
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun parseAll(): Double {
            val value = expression()
            if (pos != s.length) throw IllegalArgumentException("trailing input at $pos")
            return value
        }

        private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'

        private fun expression(): Double {
            var value = term()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; value += term() }
                    '-' -> { pos++; value -= term() }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; value *= factor() }
                    '/' -> {
                        pos++
                        val divisor = factor()
                        if (divisor == 0.0) throw ArithmeticException("division by zero")
                        value /= divisor
                    }
                    '%' -> {
                        pos++
                        val divisor = factor()
                        if (divisor == 0.0) throw ArithmeticException("modulo by zero")
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        /** Right-associative power: 2^3^2 = 2^9. */
        private fun factor(): Double {
            val base = unary()
            if (peek() == '^') {
                pos++
                val exponent = factor()
                return base.pow(exponent)
            }
            return base
        }

        private fun unary(): Double {
            return when (peek()) {
                '-' -> { pos++; -unary() }
                '+' -> { pos++; unary() }
                else -> postfix(primary())
            }
        }

        /** A `%` right after a value (not followed by another operand) is a percentage. */
        private fun postfix(value: Double): Double {
            if (peek() == '%') {
                val next = if (pos + 1 < s.length) s[pos + 1] else '\u0000'
                if (!(next.isDigit() || next == '(' || next == '.' || next.isLetter())) {
                    pos++
                    return value / 100.0
                }
            }
            return value
        }

        private fun primary(): Double {
            val c = peek()
            if (c == '(') {
                pos++
                val value = expression()
                if (peek() != ')') throw IllegalArgumentException("missing )")
                pos++
                return value
            }
            if (c.isDigit() || c == '.') return number()
            if (c.isLetter()) return identifier()
            throw IllegalArgumentException("unexpected '$c' at $pos")
        }

        private fun number(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            val token = s.substring(start, pos)
            return token.toDoubleOrNull() ?: throw IllegalArgumentException("bad number $token")
        }

        private fun identifier(): Double {
            val start = pos
            while (pos < s.length && s[pos].isLetter()) pos++
            val name = s.substring(start, pos)
            when (name) {
                "pi" -> return Math.PI
                "e" -> return Math.E
            }
            if (peek() != '(') throw IllegalArgumentException("unknown name $name")
            pos++
            val arg = expression()
            if (peek() != ')') throw IllegalArgumentException("missing )")
            pos++
            return when (name) {
                "sqrt" -> if (arg < 0) throw ArithmeticException("sqrt of negative") else sqrt(arg)
                "abs" -> abs(arg)
                "sin" -> sin(Math.toRadians(arg))
                "cos" -> cos(Math.toRadians(arg))
                "tan" -> tan(Math.toRadians(arg))
                "log" -> if (arg <= 0) throw ArithmeticException("log of non-positive") else log10(arg)
                "ln" -> if (arg <= 0) throw ArithmeticException("ln of non-positive") else ln(arg)
                else -> throw IllegalArgumentException("unknown function $name")
            }
        }
    }
}
