package com.fraktalio.example.fmodelspringdemo.domain

import com.fraktalio.domain.*
import com.fraktalio.expectActions
import com.fraktalio.whenActionResult
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderSagaTest {
    private val orderSaga = orderSaga()
    private val orderId = OrderId()
    private val restaurantId = RestaurantId()
    private val restaurantName = RestaurantName("ce-vap")
    private val restaurantMenu: RestaurantMenu = RestaurantMenu(
        listOf(MenuItem(MenuItemId("item1"), MenuItemName("menuItemName"), Money(BigDecimal.TEN))).toImmutableList()
    )
    private val orderLineItems = listOf(
        OrderLineItem(
            OrderLineItemId("1"),
            OrderLineItemQuantity(1),
            MenuItemId("item1"),
            MenuItemName("menuItemName")
        )
    ).toImmutableList()


    @Test
    fun testOrderPlacedAtRestaurantEvent(): Unit = runBlocking {
        val orderPlacedAtRestaurantEvent = OrderPlacedAtRestaurantEvent(
            restaurantId,
            orderLineItems,
            orderId,
        )
        val createOrderCommand = CreateOrderCommand(orderId, restaurantId, orderLineItems)

        with(orderSaga) {
            whenActionResult(
                orderPlacedAtRestaurantEvent
            ) expectActions listOf(createOrderCommand)

        }
    }

    @Test
    fun testRestaurantCreatedEvent(): Unit = runBlocking {
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu
        )

        with(orderSaga) {
            whenActionResult(
                restaurantCreatedEvent
            ) expectActions emptyList()
        }
    }

}