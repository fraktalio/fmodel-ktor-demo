package com.fraktalio.adapter.persistence

import com.fraktalio.application.MaterializedViewState
import com.fraktalio.application.MaterializedViewStateRepository
import com.fraktalio.domain.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.*


/**
 * View repository implementation
 *
 * @property viewStore
 * @constructor Create Materialized View repository impl
 *
 * @author Иван Дугалић / Ivan Dugalic / @idugalic
 */

internal open class MaterializedViewStateRepositoryImpl(
    private val viewStore: ViewStore
) : MaterializedViewStateRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(10)
    override suspend fun Event?.fetchState(): MaterializedViewState =
        withContext(dbDispatcher) {
            TODO("Event?.fetchState() - Not yet implemented")
        }

    override suspend fun MaterializedViewState.save(): MaterializedViewState =
        withContext(dbDispatcher) {
            TODO("MaterializedViewState.save() - Not yet implemented")
        }


    // ###### Convenient mapping functions #######
    private fun RestaurantEntity?.toRestaurant(menu: RestaurantMenu) = when {
        this != null -> RestaurantViewState(
            RestaurantId(UUID.fromString(aggregateId)),
            RestaurantName(name),
            menu
        )

        else -> null
    }

    private fun OrderEntity?.toOrder(lineItems: ImmutableList<OrderLineItem>): OrderViewState? =
        when {
            this != null -> OrderViewState(
                OrderId(UUID.fromString(aggregateId)),
                RestaurantId(UUID.fromString(restaurantId)),
                state,
                lineItems
            )

            else -> null
        }

    private fun OrderItemEntity.toOrderLineItem(): OrderLineItem =
        OrderLineItem(
            OrderLineItemId(identifier ?: ""),
            OrderLineItemQuantity(quantity),
            MenuItemId(menuItemId),
            MenuItemName(name)
        )

    private fun MenuItemEntity.toMenuItem(): MenuItem =
        MenuItem(MenuItemId(identifier ?: ""), MenuItemName(name), Money(this.price))


    private fun RestaurantViewState.toRestaurantEntity(isNew: Boolean) = RestaurantEntity(
        id.value.toString(),
        Long.MIN_VALUE,
        name.value,
        menu.cuisine,
        menu.menuId.toString(),
        isNew
    )

    private fun MenuItem.toMenuItemEntity(menuId: String, restaurantId: String, isNew: Boolean) = MenuItemEntity(
        menuItemId.value, menuItemId.value, menuId, restaurantId, name.value, this.price.value, isNew
    )


    private fun OrderViewState.toOrderEntity(isNew: Boolean) = OrderEntity(
        id.value.toString(),
        Long.MIN_VALUE,
        restaurantId.value.toString(),
        status,
        isNew
    )

    private fun OrderLineItem.toOrderItemEntity(orderId: String, isNew: Boolean) = OrderItemEntity(
        id.value,
        orderId,
        menuItemId.value,
        name.value,
        quantity.value,
        isNew
    )
}

internal data class RestaurantEntity(
    var aggregateId: String? = null,
    val aggregateVersion: Long,
    val name: String,
    val cuisine: RestaurantMenuCuisine,
    val menuId: String,
    val newRestaurant: Boolean = false
)

internal data class MenuItemEntity(
    var identifier: String? = null,
    val menuItemId: String,
    val menuId: String,
    val restaurantId: String,
    val name: String,
    val price: BigDecimal,
    val newMenuItem: Boolean = false
)


internal data class OrderEntity(
    val aggregateId: String? = null,
    val aggregateVersion: Long,
    val restaurantId: String,
    val state: OrderStatus,
    val newRestaurantOrder: Boolean = false
)

internal data class OrderItemEntity(
    val identifier: String? = null,
    val orderId: String,
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val newRestaurantOrderItem: Boolean = false
)

