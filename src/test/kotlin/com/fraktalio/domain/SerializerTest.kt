package com.fraktalio.domain

import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class SerializerTest {

    private val restaurantId = RestaurantId(UUID.fromString("dd881497-8f7c-4bc4-839b-9044c1511b6b"))
    private val restaurantName = RestaurantName("ce-vap")
    private val restaurantMenu: RestaurantMenu = RestaurantMenu(
        listOf(MenuItem(MenuItemId("item1"), MenuItemName("menuItemName"), Money(BigDecimal.TEN))).toImmutableList(),
        UUID.fromString("84aaed2f-7e58-49fb-8da5-c9e7c82fbf66")
    )

    @Test
    fun serializeEventsWithKotlinSerializer1Test() {
        val expectedRestaurantCreatedEventString =
            "{\"type\":\"com.fraktalio.domain.RestaurantCreatedEvent\",\"identifier\":\"dd881497-8f7c-4bc4-839b-9044c1511b6b\",\"name\":\"ce-vap\",\"menu\":{\"menuItems\":[{\"menuItemId\":\"item1\",\"name\":\"menuItemName\",\"price\":\"10\"}],\"menuId\":\"84aaed2f-7e58-49fb-8da5-c9e7c82fbf66\"}}"
        val restaurantCreatedEvent: Event = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        assertEquals(expectedRestaurantCreatedEventString, Json.encodeToString(restaurantCreatedEvent))
        assertEquals(restaurantCreatedEvent, Json.decodeFromString<Event>(expectedRestaurantCreatedEventString))
    }

    @Test
    fun serializeEventsWithKotlinSerializer2Test() {
        val expectedRestaurantNotCreatedEventString =
            "{\"type\":\"com.fraktalio.domain.RestaurantNotCreatedEvent\",\"identifier\":\"dd881497-8f7c-4bc4-839b-9044c1511b6b\",\"name\":\"ce-vap\",\"menu\":{\"menuItems\":[{\"menuItemId\":\"item1\",\"name\":\"menuItemName\",\"price\":\"10\"}],\"menuId\":\"84aaed2f-7e58-49fb-8da5-c9e7c82fbf66\"},\"reason\":\"test\"}"
        val restaurantNotCreatedEvent: Event = RestaurantNotCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
            Reason("test")
        )
        assertEquals(expectedRestaurantNotCreatedEventString, Json.encodeToString(restaurantNotCreatedEvent))
        assertEquals(restaurantNotCreatedEvent, Json.decodeFromString<Event>(expectedRestaurantNotCreatedEventString))
    }
}