package com.fraktalio.adapter.persistence

import com.fraktalio.LOGGER
import com.fraktalio.adapter.persistence.extension.alterSQLResource
import com.fraktalio.adapter.persistence.extension.connection
import com.fraktalio.adapter.persistence.extension.executeSql
import com.fraktalio.domain.OrderViewState
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.r2dbc.postgresql.codec.Json as JsonPostgres

class OrderRepository(private val connectionFactory: ConnectionFactory) {
    companion object {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(10)
    suspend fun initSchema() = withContext(dbDispatcher) {
        LOGGER.debug("# Initializing Order schema #")
        connectionFactory.connection().alterSQLResource(CREATE_ORDER)
        connectionFactory.connection().alterSQLResource(CREATE_OR_UPDATE_ORDER_FUN)

    }

    suspend fun upsertOrder(order: OrderViewState?) = withContext(dbDispatcher) {
        if (order != null)
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
                        JsonPostgres.of(
                            Json.encodeToString(order).encodeToByteArray()
                        )
                    )
                }
                .singleOrNull()
        else null
    }

    suspend fun findById(id: String) = withContext(dbDispatcher) {
        connectionFactory.connection()
            .executeSql(
                """
                SELECT * FROM orders WHERE order_id = $1
                """,
                orderMapper
            ) {
                bind(0, id)
            }
            .singleOrNull()
    }

    fun findAll() = flow {
        connectionFactory.connection()
            .executeSql(
                """
                SELECT * FROM orders
                """,
                orderMapper
            )
            .also { emitAll(it) }
    }.flowOn(dbDispatcher)
}

private val orderMapper: (Row, RowMetadata) -> OrderViewState = { row, _ ->
    Json.decodeFromString<OrderViewState>(row.get("order_data", ByteArray::class.java)!!.decodeToString())
}
