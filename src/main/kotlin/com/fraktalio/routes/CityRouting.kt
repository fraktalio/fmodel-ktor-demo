package com.fraktalio.routes

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
            try {
                val city = withSpan("request") { call.receive<City>() }
                val createdCity = withSpan("service") { cityService.create(city) }
                withSpan("response") { call.respond(HttpStatusCode.Created, createdCity) }
            } catch (e: Exception) {
                withSpan("response-exception") { call.respond(HttpStatusCode.BadRequest) }
            }
        }

        // Read city
        get("/cities/{id}") {
            try {
                val id = withSpan("request") {
                    call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
                }
                val city = withSpan("service") { cityService.read(id) }
                withSpan("response") { call.respond(HttpStatusCode.OK, city) }
            } catch (e: Exception) {
                withSpan("response-exception") { call.respond(HttpStatusCode.BadRequest) }
            }
        }

        get("/cities") {
            try {
                val cities = cityService.readAll().withSpan("service - flow").toList()
                withSpan("response") { call.respond(HttpStatusCode.OK, cities) }
            } catch (e: Exception) {
                withSpan("response-exception") { call.respond(HttpStatusCode.NotFound) }
            }
        }
        // Update city
        put("/cities/{id}") {
            try {
                val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
                val user = call.receive<City>()
                val updatedCity = cityService.update(id, user)
                call.respond(HttpStatusCode.OK, updatedCity)
            } catch (e: Exception) {
                withSpan("response-exception") { call.respond(HttpStatusCode.BadRequest) }
            }
        }
        // Delete city
        delete("/cities/{id}") {
            try {
                val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
                cityService.delete(id)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                withSpan("response-exception") { call.respond(HttpStatusCode.BadRequest) }
            }
        }
    }
}
