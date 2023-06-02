package com.fraktalio.domain

import com.fraktalio.givenEvents
import com.fraktalio.thenEvents
import com.fraktalio.whenCommand
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RestaurantDeciderTest {
    private val restaurantDecider = restaurantDecider()
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
    fun testCreateRestaurant(): Unit = runBlocking {
        val createRestaurantCommand = CreateRestaurantCommand(restaurantId, restaurantName, restaurantMenu)
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        with(restaurantDecider) {
            givenEvents(emptyList()) {                      // PRE CONDITIONS
                whenCommand(createRestaurantCommand)        // ACTION
            } thenEvents listOf(restaurantCreatedEvent)     // POST CONDITIONS
        }
    }

    @Test
    fun testCreateRestaurantAlreadyExistsError(): Unit = runBlocking {
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        val createRestaurantCommand = CreateRestaurantCommand(restaurantId, restaurantName, restaurantMenu)
        val restaurantNotCreatedEvent = RestaurantNotCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
            Reason("Restaurant already exists"),
            true
        )
        with(restaurantDecider) {
            givenEvents(listOf(restaurantCreatedEvent)) {   // PRE CONDITIONS
                whenCommand(createRestaurantCommand)        // ACTION
            } thenEvents listOf(restaurantNotCreatedEvent)  // POST CONDITIONS
        }
    }

    @Test
    fun testChangeRestaurantMenu(): Unit = runBlocking {
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        val changeRestaurantMenuCommand =
            ChangeRestaurantMenuCommand(restaurantId, restaurantMenu.copy(cuisine = RestaurantMenuCuisine.SERBIAN))
        val restaurantMenuChangedEvent = RestaurantMenuChangedEvent(
            restaurantId,
            restaurantMenu.copy(cuisine = RestaurantMenuCuisine.SERBIAN),
        )
        with(restaurantDecider) {
            givenEvents(listOf(restaurantCreatedEvent)) {   // PRE CONDITIONS
                whenCommand(changeRestaurantMenuCommand)    // ACTION
            } thenEvents listOf(restaurantMenuChangedEvent) // POST CONDITIONS
        }
    }

    @Test
    fun testChangeRestaurantMenuDoesNotExistError(): Unit = runBlocking {
        val changeRestaurantMenuCommand =
            ChangeRestaurantMenuCommand(restaurantId, restaurantMenu.copy(cuisine = RestaurantMenuCuisine.SERBIAN))
        val restaurantMenuNotChangedEvent = RestaurantMenuNotChangedEvent(
            restaurantId,
            restaurantMenu.copy(cuisine = RestaurantMenuCuisine.SERBIAN),
            Reason("Restaurant does not exist"),
        )
        with(restaurantDecider) {
            givenEvents(emptyList()) {                      // PRE CONDITIONS
                whenCommand(changeRestaurantMenuCommand)    // ACTION
            } thenEvents listOf(restaurantMenuNotChangedEvent) // POST CONDITIONS
        }
    }

    @Test
    fun testPlaceOrder(): Unit = runBlocking {
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        val placeOrderCommand = PlaceOrderCommand(restaurantId, orderId, orderLineItems)
        val orderPlacedAtRestaurantEvent = OrderPlacedAtRestaurantEvent(
            restaurantId,
            orderLineItems,
            orderId,
        )
        with(restaurantDecider) {
            givenEvents(listOf(restaurantCreatedEvent)) {     // PRE CONDITIONS
                whenCommand(placeOrderCommand)                // ACTION
            } thenEvents listOf(orderPlacedAtRestaurantEvent) // POST CONDITIONS
        }
    }

    @Test
    fun testPlaceOrderRejectedError(): Unit = runBlocking {
        val restaurantCreatedEvent = RestaurantCreatedEvent(
            restaurantId,
            restaurantName,
            restaurantMenu,
        )
        val placeOrderCommand = PlaceOrderCommand(restaurantId, orderId, wrongOrderLineItems)
        val orderRejectedByRestaurantEvent = OrderRejectedByRestaurantEvent(
            restaurantId,
            orderId,
            Reason("Not on the menu"),
        )
        with(restaurantDecider) {
            givenEvents(listOf(restaurantCreatedEvent)) {       // PRE CONDITIONS
                whenCommand(placeOrderCommand)                  // ACTION
            } thenEvents listOf(orderRejectedByRestaurantEvent) // POST CONDITIONS
        }
    }

    @Test
    fun testPlaceOrderDoesNotExistError(): Unit = runBlocking {
        val placeOrderCommand = PlaceOrderCommand(restaurantId, orderId, orderLineItems)
        val orderNotPlacedAtRestaurantEvent = OrderNotPlacedAtRestaurantEvent(
            restaurantId,
            orderLineItems,
            orderId,
            Reason("Restaurant does not exist"),
        )
        with(restaurantDecider) {
            givenEvents(emptyList()) {                           // PRE CONDITIONS
                whenCommand(placeOrderCommand)                   // ACTION
            } thenEvents listOf(orderNotPlacedAtRestaurantEvent) // POST CONDITIONS
        }
    }

}