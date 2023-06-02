package com.fraktalio.example.fmodelspringdemo.domain

import com.fraktalio.domain.*
import com.fraktalio.givenEvents
import com.fraktalio.thenState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class OrderViewTest {
    private val orderView = orderView()
    private val orderId = OrderId()
    private val restaurantId = RestaurantId()
    private val orderLineItems = listOf(
        OrderLineItem(
            OrderLineItemId("1"),
            OrderLineItemQuantity(1),
            MenuItemId("item1"),
            MenuItemName("menuItemName")
        )
    ).toImmutableList()


    @Test
    fun testOrderCreated(): Unit = runBlocking {
        val orderCreatedEvent = OrderCreatedEvent(
            orderId,
            orderLineItems,
            restaurantId,
        )
        val orderViewState = OrderViewState(orderId, restaurantId, orderCreatedEvent.status, orderLineItems)
        with(orderView) {
            givenEvents(
                listOf(orderCreatedEvent)
            ) thenState orderViewState
        }
    }

    @Test
    fun testOrderPrepared(): Unit = runBlocking {
        val orderCreatedEvent = OrderCreatedEvent(
            orderId,
            orderLineItems,
            restaurantId,
        )
        val orderPreparedEvent = OrderPreparedEvent(orderId)
        val orderViewState = OrderViewState(orderId, restaurantId, orderPreparedEvent.status, orderLineItems)

        with(orderView) {
            givenEvents(
                listOf(orderCreatedEvent, orderPreparedEvent)
            ) thenState orderViewState
        }
    }

    @Test
    fun testOrderPrepared_DoesNotExistOrderError(): Unit = runBlocking {
        val orderPreparedEvent = OrderPreparedEvent(orderId)
        with(orderView) {
            givenEvents(
                listOf(orderPreparedEvent)
            ) thenState null
        }
    }

    @Test
    fun testOrderRejected(): Unit = runBlocking {
        val orderCreatedEvent = OrderCreatedEvent(
            orderId,
            orderLineItems,
            restaurantId,
        )
        val orderRejectedEvent = OrderRejectedEvent(orderId, Reason("why not"), true)
        val orderViewState = OrderViewState(orderId, restaurantId, orderRejectedEvent.status, orderLineItems)

        with(orderView) {
            givenEvents(
                listOf(orderCreatedEvent, orderRejectedEvent)
            ) thenState orderViewState
        }
    }

    @Test
    fun testOrderRejected_DoesNotExistOrderError(): Unit = runBlocking {
        val orderRejectedEvent = OrderRejectedEvent(orderId, Reason("why not"), true)

        with(orderView) {
            givenEvents(
                listOf(orderRejectedEvent)
            ) thenState null
        }
    }

}