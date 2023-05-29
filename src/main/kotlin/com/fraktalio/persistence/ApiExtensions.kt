package com.fraktalio.persistence

import com.fraktalio.domain.*

fun Command.deciderId() = when (this) {
    is RestaurantCommand -> identifier.value.toString()
    is OrderCommand -> identifier.value.toString()
}

fun Event.deciderId() = when (this) {
    is RestaurantEvent -> identifier.value.toString()
    is OrderEvent -> identifier.value.toString()
}

fun Event.decider() = when (this) {
    is RestaurantEvent -> "Restaurant"
    is OrderEvent -> "Order"
}

fun Event.event() = this.javaClass.name


