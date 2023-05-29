package com.fraktalio.routes

import com.fraktalio.LOGGER
import com.fraktalio.application.Aggregate
import com.fraktalio.domain.Command
import com.fraktalio.domain.Event
import com.fraktalio.fmodel.application.handleOptimistically
import com.fraktalio.plugins.withSpan
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class)
fun Application.restaurantRouting(aggregate: Aggregate) {

    routing {
        post("/commands") {
            try {
                val command = withSpan("request") { call.receive<Command>() }
                val resultEvents: List<Event> = withSpan("aggregate-handle") {
                    aggregate.handleOptimistically(command).map { it.first }.filterNotNull().toList()
                }
                withSpan("response") { call.respond(HttpStatusCode.Created, resultEvents) }
            } catch (e: Exception) {
                LOGGER.error("Error: ${e.message}", e)
                withSpan("response-exception") { call.respond(HttpStatusCode.BadRequest) }
            }
        }
    }
}
