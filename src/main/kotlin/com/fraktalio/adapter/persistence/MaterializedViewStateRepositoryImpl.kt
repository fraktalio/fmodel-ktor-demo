package com.fraktalio.adapter.persistence

import com.fraktalio.LOGGER
import com.fraktalio.adapter.extension.alterSQLResource
import com.fraktalio.adapter.extension.connection
import com.fraktalio.adapter.extension.deciderId
import com.fraktalio.adapter.extension.executeSql
import com.fraktalio.application.MaterializedViewState
import com.fraktalio.application.MaterializedViewStateRepository
import com.fraktalio.domain.*
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.util.*


/**
 * View repository implementation
 *
 * @constructor Create Materialized View repository impl
 *
 * @author Иван Дугалић / Ivan Dugalic / @idugalic
 */

internal open class MaterializedViewStateRepositoryImpl(private val connectionFactory: ConnectionFactory) :
    MaterializedViewStateRepository {
    companion object {
        private const val CREATE_RESTAURANT =
            """
              CREATE TABLE IF NOT EXISTS restaurants (
                restaurant_id VARCHAR PRIMARY KEY,
                restaurant_data JSONB
                );
            """
        private const val CREATE_OR_UPDATE_RESTAURANT_FUN =
            """
                -- Function to insert or update a restaurant entity and return the updated/created row
                CREATE OR REPLACE FUNCTION insert_update_restaurant(
                    p_restaurant_id VARCHAR, p_restaurant_data JSONB
                )
                RETURNS SETOF "restaurants" AS $$
                BEGIN
                    -- Update or insert the restaurant table
                    RETURN QUERY
                        INSERT INTO restaurants (restaurant_id, restaurant_data)
                        VALUES (p_restaurant_id, p_restaurant_data)
                        ON CONFLICT (restaurant_id) DO UPDATE
                        SET restaurant_data = EXCLUDED.restaurant_data
                        RETURNING *;
                END;
                $$ LANGUAGE plpgsql;

            """
        private const val CREATE_ORDER =
            """
              CREATE TABLE IF NOT EXISTS orders (
                order_id VARCHAR PRIMARY KEY,
                order_data JSONB
                );
            """
        private const val CREATE_OR_UPDATE_ORDER_FUN =
            """
                -- Function to insert or update a order entity and return the updated/created row
                CREATE OR REPLACE FUNCTION insert_update_order(
                    p_order_id VARCHAR, p_order_data JSONB
                )
                RETURNS SETOF "orders" AS $$
                BEGIN
                    -- Update or insert the order table
                    RETURN QUERY
                        INSERT INTO orders (order_id, order_data)
                        VALUES (p_order_id, p_order_data)
                        ON CONFLICT (order_id) DO UPDATE
                        SET order_data = EXCLUDED.order_data
                        RETURNING *;
                END;
                $$ LANGUAGE plpgsql;

            """
    }

    suspend fun initSchema() = withContext(dbDispatcher) {
        LOGGER.debug("# Initializing Restaurant and Order schema #")
        connectionFactory.connection().alterSQLResource(CREATE_RESTAURANT)
        connectionFactory.connection().alterSQLResource(CREATE_OR_UPDATE_RESTAURANT_FUN)
        connectionFactory.connection().alterSQLResource(CREATE_ORDER)
        connectionFactory.connection().alterSQLResource(CREATE_OR_UPDATE_ORDER_FUN)
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(10)
    val mockResult = MaterializedViewState(
        RestaurantViewState(
            RestaurantId(UUID.randomUUID()),
            RestaurantName("name"),
            RestaurantMenu(
                listOf<MenuItem>(
                    MenuItem(
                        MenuItemId(UUID.randomUUID().toString()),
                        MenuItemName("name"),
                        Money(BigDecimal.ONE)
                    )
                ).toImmutableList()
            )
        ),
        OrderViewState(
            OrderId(UUID.randomUUID()),
            RestaurantId(UUID.randomUUID()),
            OrderStatus.PREPARED,
            listOf<OrderLineItem>(
                OrderLineItem(
                    OrderLineItemId(UUID.randomUUID().toString()),
                    OrderLineItemQuantity(2),
                    MenuItemId("menuItemId"),
                    MenuItemName("menuItemId")
                )
            ).toImmutableList()
        )
    )

    override suspend fun Event?.fetchState(): MaterializedViewState =
        withContext(dbDispatcher) {
            val event = this@fetchState
            if (event != null) {
                LOGGER.debug("view / event-handler: fetchState({}) started ...", event)
                MaterializedViewState(
                    connectionFactory.connection()
                        .executeSql(
                            """
                        SELECT * FROM restaurants WHERE restaurant_id = $1
                        """,
                            restaurantMapper
                        )
                        { bind(0, event.deciderId()) }
                        .singleOrNull(),
                    connectionFactory.connection()
                        .executeSql(
                            """
                        SELECT * FROM orders WHERE order_id = $1
                        """,
                            orderMapper
                        )
                        { bind(0, event.deciderId()) }
                        .singleOrNull()
                )
            } else
                MaterializedViewState(null, null)
        }

    override suspend fun MaterializedViewState.save(): MaterializedViewState =
        withContext(dbDispatcher) {
            LOGGER.debug("view / event-handler: save({}) started ... #########", this@save)
            with(this@MaterializedViewStateRepositoryImpl) {
                MaterializedViewState(
                    if (restaurant != null) {
                        connectionFactory.connection()
                            .executeSql(
                                """
                                    SELECT * FROM insert_update_restaurant($1, $2)
                                    """,
                                restaurantMapper
                            ) {
                                bind(0, restaurant.id.value.toString())
                                bind(
                                    1,
                                    io.r2dbc.postgresql.codec.Json.of(
                                        Json.encodeToString(restaurant).encodeToByteArray()
                                    )
                                )
                            }
                            .single()
                    } else restaurant,
                    if (order != null) {
                        connectionFactory.connection()
                            .executeSql(
                                """
                                    SELECT * FROM insert_update_order($1, $2)
                                    """,
                                orderMapper
                            ) {
                                bind(0, order.id.value.toString())
                                bind(
                                    1,
                                    io.r2dbc.postgresql.codec.Json.of(
                                        Json.encodeToString(order).encodeToByteArray()
                                    )
                                )
                            }
                            .single()
                    } else order,
                )
            }
        }
}

internal val restaurantMapper: (Row, RowMetadata) -> RestaurantViewState = { row, _ ->
    Json.decodeFromString<RestaurantViewState>(row.get("restaurant_data", ByteArray::class.java).decodeToString())
}

internal val orderMapper: (Row, RowMetadata) -> OrderViewState = { row, _ ->
    Json.decodeFromString<OrderViewState>(row.get("order_data", ByteArray::class.java).decodeToString())

}