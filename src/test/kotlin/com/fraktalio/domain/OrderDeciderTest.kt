package com.fraktalio.example.fmodelspringdemo.domain

import com.fraktalio.domain.*
import com.fraktalio.givenEvents
import com.fraktalio.thenEvents
import com.fraktalio.whenCommand
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class OrderDeciderTest {
    private val orderDecider = orderDecider()
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
    fun testCreateOrder(): Unit = runBlocking {
        val createOrderCommand = CreateOrderCommand(orderId, restaurantId, orderLineItems)
        val orderCreatedEvent = OrderCreatedEvent(
            orderId,
            orderLineItems,
            restaurantId,
        )
        with(orderDecider) {
            givenEvents(emptyList()) {                      // PRE CONDITIONS
                whenCommand(createOrderCommand)             // ACTION
            } thenEvents listOf(orderCreatedEvent)          // POST CONDITIONS
        }
    }

    @Test
    fun testCreateOrderAlreadyExistsError(): Unit = runBlocking {
        val orderCreatedEvent = OrderCreatedEvent(
            orderId,
            orderLineItems,
            restaurantId,
        )
        val createOrderCommand = CreateOrderCommand(orderId, restaurantId, orderLineItems)
        val orderRejectedEvent =
            OrderRejectedEvent(orderId, Reason("Order already exists"))
        with(orderDecider) {
            givenEvents(listOf(orderCreatedEvent)) {         // PRE CONDITIONS
                whenCommand(createOrderCommand)              // ACTION
            } thenEvents listOf(orderRejectedEvent)          // POST CONDITIONS
        }
    }

    @Test
    fun testMarkOrderAsPrepared(): Unit = runBlocking {
        val orderCreatedEvent = OrderCreatedEvent(
            orderId,
            orderLineItems,
            restaurantId,
        )
        val markOrderAsPreparedCommand = MarkOrderAsPreparedCommand(orderId)
        val orderPreparedEvent = OrderPreparedEvent(orderId)

        with(orderDecider) {
            givenEvents(listOf(orderCreatedEvent)) {         // PRE CONDITIONS
                whenCommand(markOrderAsPreparedCommand)      // ACTION
            } thenEvents listOf(orderPreparedEvent)          // POST CONDITIONS
        }
    }

    @Test
    fun testMarkOrderAsPreparedDoesNotExistError(): Unit = runBlocking {
        val markOrderAsPreparedCommand = MarkOrderAsPreparedCommand(orderId)
        val orderNotPreparedEvent = OrderNotPreparedEvent(orderId, Reason("Order does not exist"))

        with(orderDecider) {
            givenEvents(emptyList()) {                       // PRE CONDITIONS
                whenCommand(markOrderAsPreparedCommand)      // ACTION
            } thenEvents listOf(orderNotPreparedEvent)       // POST CONDITIONS
        }
    }

    @Test
    fun testMarkOrderAsPreparedNotInCreatedStatusError(): Unit = runBlocking {
        val orderCreatedEvent = OrderCreatedEvent(
            orderId,
            orderLineItems,
            restaurantId,
        )
        val orderPreparedEvent = OrderPreparedEvent(orderId)
        val markOrderAsPreparedCommand = MarkOrderAsPreparedCommand(orderId)
        val orderNotPreparedEvent = OrderNotPreparedEvent(orderId, Reason("Order not in CREATED status"))

        with(orderDecider) {
            givenEvents(listOf(orderCreatedEvent, orderPreparedEvent)) {    // PRE CONDITIONS
                whenCommand(markOrderAsPreparedCommand)                     // ACTION
            } thenEvents listOf(orderNotPreparedEvent)                      // POST CONDITIONS
        }
    }
}