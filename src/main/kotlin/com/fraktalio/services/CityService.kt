package com.fraktalio.services

import com.fraktalio.persistence.connection
import com.fraktalio.persistence.executeSql
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.sql.DataSource

@Serializable
data class City(val name: String, val population: Int)

class CityService(private val dataSource: DataSource, private val connectionFactory: ConnectionFactory) {
    companion object {
        private const val CREATE_TABLE_CITIES =
            "CREATE TABLE CITIES (ID SERIAL PRIMARY KEY, NAME VARCHAR(255), POPULATION INT);"
        private const val SELECT_CITY_BY_ID = "SELECT name, population FROM cities WHERE id = ?"
        private const val SELECT_CITIES = "SELECT name, population FROM cities"
        private const val INSERT_CITY = "INSERT INTO cities (name, population) VALUES (?, ?)"
        private const val UPDATE_CITY = "UPDATE cities SET name = ?, population = ? WHERE id = ?"
        private const val DELETE_CITY = "DELETE FROM cities WHERE id = ?"

    }

    val cityMapper: (Row, RowMetadata) -> City = { row, _ ->
        City(row.get("name", String::class.java) ?: "", row.get("population", Number::class.java).toInt())
    }

    init {
        dataSource.connection.use {
            it.createStatement().run {
                executeUpdate(CREATE_TABLE_CITIES)
            }
        }
    }

    // Create new city
    suspend fun create(city: City): City = withContext(Dispatchers.IO) {
        connectionFactory.connection()
            .executeSql(INSERT_CITY, cityMapper) {
                bind(0, city.name)
                bind(1, city.population)
                returnGeneratedValues("id", "name", "population")
            }
            .single()
    }

    // Read all cities
    fun readAll(): Flow<City> = flow {
        connectionFactory.connection()
            .executeSql(SELECT_CITIES, cityMapper)
            .also { emitAll(it) }
    }.flowOn(Dispatchers.IO)

    // Read by id
    suspend fun read(id: Int): City = withContext(Dispatchers.IO) {
        connectionFactory.connection()
            .executeSql(SELECT_CITY_BY_ID, cityMapper) {
                bind(0, id)
            }
            .single()
    }

    // Update a city
    suspend fun update(id: Int, city: City): City = withContext(Dispatchers.IO) {
        connectionFactory.connection()
            .executeSql(UPDATE_CITY, cityMapper) {
                bind(0, city.name)
                bind(1, city.population)
                bind(2, id)
                returnGeneratedValues("id", "name", "population")
            }
            .single()
    }

    // Delete a city
    suspend fun delete(id: Int): Unit = withContext(Dispatchers.IO) {
        connectionFactory.connection()
            .executeSql(DELETE_CITY, cityMapper) {
                bind(0, id)
            }
            .collect()
    }
}