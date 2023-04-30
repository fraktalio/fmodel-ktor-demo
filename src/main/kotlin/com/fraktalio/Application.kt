package com.fraktalio

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.fraktalio.persistence.dataSource
import com.fraktalio.plugins.*
import com.fraktalio.routes.cityRouting
import com.fraktalio.routes.homeRouting
import com.fraktalio.services.CityService
import com.fraktalio.services.EventSourcingService
import io.ktor.server.application.*
import io.ktor.server.cio.*
import kotlinx.coroutines.awaitCancellation

// https://arrow-kt.io/ecosystem/suspendapp/
fun main(): Unit = SuspendApp {
    resourceScope {
        val httpEnv = Env.Http()
        val meterRegistry = meterRegistry()
        val dataSource = dataSource(Env.DataSource())
        val cityService = CityService(dataSource)
        val eventSourcingService = EventSourcingService(dataSource)

        // https://arrow-kt.io/ecosystem/suspendapp/ktor/
        server(CIO, host = httpEnv.host, port = httpEnv.port) {
            configureSerialization()
            configurePrometheusMonitoring(meterRegistry)
            configureTracing()
            configureSwagger()

            module(cityService, eventSourcingService)
        }

        awaitCancellation()
    }
}

fun Application.module(cityService: CityService, eventSourcingService: EventSourcingService) {
    homeRouting()
    cityRouting(cityService)
}
