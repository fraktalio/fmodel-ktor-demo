package com.fraktalio.adapter.persistence

import com.fraktalio.LOGGER
import com.fraktalio.adapter.persistence.extension.alterSQLResource
import com.fraktalio.adapter.persistence.extension.connection
import com.fraktalio.adapter.persistence.extension.executeSql
import com.fraktalio.domain.RestaurantViewState
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

class RestaurantRepository(private val connectionFactory: ConnectionFactory) {
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
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(10)
    suspend fun initSchema() = withContext(dbDispatcher) {
        LOGGER.debug("# Initializing Restaurant schema #")
        connectionFactory.connection().alterSQLResource(CREATE_RESTAURANT)
        connectionFactory.connection().alterSQLResource(CREATE_OR_UPDATE_RESTAURANT_FUN)

    }


    suspend fun upsertRestaurant(restaurant: RestaurantViewState?) = withContext(dbDispatcher) {
        if (restaurant != null)
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
                        JsonPostgres.of(
                            Json.encodeToString(restaurant).encodeToByteArray()
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
                SELECT * FROM restaurants WHERE restaurant_id = $1
                """,
                restaurantMapper
            ) {
                bind(0, id)
            }
            .singleOrNull()
    }

    fun findAll() = flow {
        connectionFactory.connection()
            .executeSql(
                """
                SELECT * FROM restaurants
                """,
                restaurantMapper
            )
            .also { emitAll(it) }
    }.flowOn(dbDispatcher)
}

private val restaurantMapper: (Row, RowMetadata) -> RestaurantViewState = { row, _ ->
    Json.decodeFromString<RestaurantViewState>(row.get("restaurant_data", ByteArray::class.java).decodeToString())
}