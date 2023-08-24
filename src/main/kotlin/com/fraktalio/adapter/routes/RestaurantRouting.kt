package com.fraktalio.adapter.routes

import com.fraktalio.LOGGER
import com.fraktalio.adapter.persistence.OrderRepository
import com.fraktalio.adapter.persistence.RestaurantRepository
import com.fraktalio.application.Aggregate
import com.fraktalio.domain.Command
import com.fraktalio.domain.Event
import com.fraktalio.fmodel.application.handleOptimistically
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

@OptIn(ExperimentalCoroutinesApi::class)
fun Application.restaurantRouting(
    aggregate: Aggregate,
    restaurantRepository: RestaurantRepository,
    orderRepository: OrderRepository
) {

    routing {
        post("/commands") {
            try {
                val command = call.receive<Command>()
                val resultEvents: List<Event> =
                    aggregate.handleOptimistically(command).map { it.first }.filterNotNull().toList()

                call.respond(HttpStatusCode.Created, resultEvents)
            } catch (e: Exception) {
                LOGGER.error("Error: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest)
            }
        }
        get("/restaurants") {
            try {
                val restaurants = restaurantRepository.findAll().toList()
                call.respond(restaurants)
            } catch (e: Exception) {
                LOGGER.error("Error: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest)
            }
        }
        get("/orders") {
            try {
                val orders = orderRepository.findAll().toList()
                call.respond(orders)
            } catch (e: Exception) {
                LOGGER.error("Error: ${e.message}", e)
                call.respond(HttpStatusCode.BadRequest)
            }
        }
    }
}
