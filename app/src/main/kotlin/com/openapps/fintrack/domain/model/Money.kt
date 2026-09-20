/*
 * FinTrack
 * Copyright (C) 2026 Bhuvan (app.upstream242@passmail.com)
 * SPDX-License-Identifier: GPL-3.0-or-later

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
 */



package com.openapps.fintrack.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

/**
 * Represents a monetary amount in a specific currency using minor units (e.g., cents, paisa).
 * This avoids precision issues inherent in floating-point arithmetic.
 */
data class Money(
    val minorUnits: Long,
    val currency: String = "INR"
) {
    /**
     * Returns the decimal value of the money (e.g., 12.50 for 1250 minor units).
     */
    fun toDecimal(): Double {
        return minorUnits.toDouble() / 100.0
    }

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Cannot add different currencies: $currency and ${other.currency}" }
        return Money(minorUnits + other.minorUnits, currency)
    }

    operator fun minus(other: Money): Money {
        require(currency == other.currency) { "Cannot subtract different currencies: $currency and ${other.currency}" }
        return Money(minorUnits - other.minorUnits, currency)
    }

    operator fun times(factor: Double): Money {
        val result = (minorUnits.toDouble() * factor).toLong()
        return Money(result, currency)
    }

    operator fun div(factor: Double): Money {
        require(factor != 0.0) { "Division by zero" }
        val result = (minorUnits.toDouble() / factor).toLong()
        return Money(result, currency)
    }

    override fun toString(): String {
        val decimal = BigDecimal(minorUnits).movePointLeft(2)
        return String.format(Locale.getDefault(), "%s %.2f", currency, decimal)
    }

    companion object {
        val ZERO = Money(0, "INR")

        fun fromDecimal(amount: Double, currency: String = "INR"): Money {
    
            val minorUnits = BigDecimal.valueOf(amount)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .toLong()
            return Money(minorUnits, currency)
        }
    }
}
