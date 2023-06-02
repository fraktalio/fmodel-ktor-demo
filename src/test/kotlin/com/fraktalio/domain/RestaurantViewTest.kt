package com.fraktalio.example.fmodelspringdemo.domain

import com.fraktalio.domain.*
import com.fraktalio.givenEvents
import com.fraktalio.thenState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RestaurantViewTest {
    private val restaurantView = restaurantView()
    private val restaurantId = RestaurantId()
    private val restaurantName = RestaurantName("ce-vap")
    private val restaurantMenu: RestaurantMenu = RestaurantMenu(
        listOf(MenuItem(MenuItemId("item1"), MenuItemName("menuItemName"), Money(BigDecimal.TEN))).toImmutableList()
    )
    private val orderId = OrderId()
    private val orderLineItems = listOf(
        OrderLineItem(
            OrderLineItemId("1"),
            OrderLineItemQuantity(1),
            MenuItemId("item1"),
            MenuItemName("menuItemName")
        )
    ).toImmutableList()
    private val wrongOrderLineItems = listOf(
        OrderLineItem(
            OrderLineItemId("x"),
            OrderLineItemQuantity(1),
            MenuItemId("wrong_item"),
            MenuItemName("menuItemName")
        )
    ).toImmutableList()


    @Test
    fun testRestaurantCreated(): Unit = runBlocking {
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        val restaurantViewState = RestaurantViewState(restaurantId, restaurantName, restaurantMenu)

        with(restaurantView) {
            givenEvents(
                listOf(restaurantCreatedEvent)
            ) thenState restaurantViewState
        }
    }

    @Test
    fun testRestaurantMenuChanged(): Unit = runBlocking {
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        val restaurantMenuChangedEvent = RestaurantMenuChangedEvent(
            restaurantId,
            restaurantMenu.copy(cuisine = RestaurantMenuCuisine.SERBIAN),
        )
        val restaurantViewState = RestaurantViewState(restaurantId, restaurantName, restaurantMenuChangedEvent.menu)


        with(restaurantView) {
            givenEvents(
                listOf(restaurantCreatedEvent, restaurantMenuChangedEvent)
            ) thenState restaurantViewState
        }
    }

    @Test
    fun testOrderPlacedAtRestaurant(): Unit = runBlocking {
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        val orderPlacedAtRestaurantEvent = OrderPlacedAtRestaurantEvent(
            restaurantId,
            orderLineItems,
            orderId,
        )
        val restaurantViewState = RestaurantViewState(restaurantId, restaurantName, restaurantMenu)

        with(restaurantView) {
            givenEvents(
                listOf(restaurantCreatedEvent, orderPlacedAtRestaurantEvent)
            ) thenState restaurantViewState
        }
    }
}