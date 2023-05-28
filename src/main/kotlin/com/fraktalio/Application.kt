package com.fraktalio

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.fraktalio.persistence.pooledConnectionFactory
import com.fraktalio.plugins.*
import com.fraktalio.routes.cityRouting
import com.fraktalio.routes.homeRouting
import com.fraktalio.services.CityService
import com.fraktalio.services.EventSourcingService
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.util.logging.*
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.awaitCancellation

internal val LOGGER = KtorSimpleLogger("Fraktalio Logger")

// https://arrow-kt.io/ecosystem/suspendapp/
fun main(): Unit = SuspendApp {
    resourceScope {
        val httpEnv = Env.Http()
        val meterRegistry = meterRegistry()
        val connectionFactory: ConnectionFactory = pooledConnectionFactory(Env.R2DBCDataSource())
        val cityService = CityService(connectionFactory).apply { initSchema() }
        val eventSourcingService = EventSourcingService(connectionFactory).apply { initSchema() }

        // https://arrow-kt.io/ecosystem/suspendapp/ktor/
        server(CIO, host = httpEnv.host, port = httpEnv.port) {
            configureSerialization()
            configurePrometheusMonitoring(meterRegistry)
            configureTracing()
            configureSwagger()

            module(cityService)
        }

        awaitCancellation()
    }
}

fun Application.module(cityService: CityService) {
    homeRouting()
    cityRouting(cityService)
}
