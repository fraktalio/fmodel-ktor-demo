package com.fraktalio.services

import arrow.fx.coroutines.resourceScope
import com.fraktalio.persistence.connection
import com.fraktalio.persistence.query
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.sql.Statement
import javax.sql.DataSource

@Serializable
data class City(val name: String, val population: Int)

class CityService(private val dataSource: DataSource, private val connectionFactory: ConnectionFactory) {
    companion object {
        private const val CREATE_TABLE_CITIES =
            "CREATE TABLE CITIES (ID SERIAL PRIMARY KEY, NAME VARCHAR(255), POPULATION INT);"
        private const val SELECT_CITY_BY_ID = "SELECT name, population FROM cities WHERE id = ?"
        private const val INSERT_CITY = "INSERT INTO cities (name, population) VALUES (?, ?)"
        private const val UPDATE_CITY = "UPDATE cities SET name = ?, population = ? WHERE id = ?"
        private const val DELETE_CITY = "DELETE FROM cities WHERE id = ?"

    }

    init {
        dataSource.connection.use {
            it.createStatement().run {
                executeUpdate(CREATE_TABLE_CITIES)
            }
        }

    }

    // Create new city
    suspend fun create(city: City): Int = withContext(Dispatchers.IO) {
        resourceScope {
            dataSource.connection().bind().let {
                it.prepareStatement(INSERT_CITY, Statement.RETURN_GENERATED_KEYS).run {
                    setString(1, city.name)
                    setInt(2, city.population)
                    executeUpdate()
                    val generatedKeys = generatedKeys
                    if (generatedKeys.next()) {
                        generatedKeys.getInt(1)
                    } else {
                        throw Exception("Unable to retrieve the id of the newly inserted city")
                    }
                }
            }
        }
    }

    // Read a city
    suspend fun read(id: Int): City = withContext(Dispatchers.IO) {
        resourceScope {
            dataSource.connection().bind().let {
                it.prepareStatement(SELECT_CITY_BY_ID).run {
                    setInt(1, id)
                    val resultSet = executeQuery()
                    if (resultSet.next()) {
                        val name = resultSet.getString("name")
                        val population = resultSet.getInt("population")
                        City(name, population)
                    } else {
                        throw Exception("Record not found")
                    }
                }
            }
        }
    }

    fun readAll(): Flow<City> = flow {
        connectionFactory.connection().query("SELECT name, population FROM cities", { row, _ ->
            City(row.get("name", String::class.java) ?: "", row.get("population", Number::class.java).toInt())
        }).let {
            emitAll(it)
        }
    }


    // Update a city
    suspend fun update(id: Int, city: City) = withContext(Dispatchers.IO) {
        resourceScope {
            dataSource.connection().bind().let {
                it.prepareStatement(UPDATE_CITY).run {
                    setString(1, city.name)
                    setInt(2, city.population)
                    setInt(3, id)
                    executeUpdate()
                }
            }
        }
    }

    // Delete a city
    suspend fun delete(id: Int) = withContext(Dispatchers.IO) {
        resourceScope {
            dataSource.connection().bind().let {
                it.prepareStatement(DELETE_CITY).run {
                    setInt(1, id)
                    executeUpdate()
                }
            }
        }
    }
}