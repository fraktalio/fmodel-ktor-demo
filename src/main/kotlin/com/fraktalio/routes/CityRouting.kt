package com.fraktalio.routes

import com.fraktalio.LOGGER
import com.fraktalio.plugins.withSpan
import com.fraktalio.services.City
import com.fraktalio.services.CityService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList

fun Application.cityRouting(cityService: CityService) {

    routing {
        // Create city
        post("/cities") {
            val city = withSpan("request") { call.receive<City>() }
            val id = withSpan("service") { cityService.create(city) }
            withSpan("response") { call.respond(HttpStatusCode.Created, id) }
        }
        // Read city
        get("/cities/{id}") {

            val id =
                withSpan("request") { call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID") }
            try {
                val city = withSpan("service") {
                    cityService.read(id)
                }
                withSpan("response") { call.respond(HttpStatusCode.OK, city) }
            } catch (e: Exception) {
                withSpan("response-exception") { call.respond(HttpStatusCode.NotFound) }
            }


        }

        get("/cities") {
            try {
                withSpan("request") {
                    LOGGER.debug("Request: {}", call.request)
                }
                val cities = cityService.readAll().withSpan("service - flow").toList()
                withSpan("response") { call.respond(HttpStatusCode.OK, cities) }
            } catch (e: Exception) {

                withSpan("response-exception") {
                    LOGGER.error("Error: $e")
                    call.respond(HttpStatusCode.NotFound)
                }
            }


        }
        // Update city
        put("/cities/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = call.receive<City>()
            cityService.update(id, user)
            call.respond(HttpStatusCode.OK)
        }
        // Delete city
        delete("/cities/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            cityService.delete(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}
