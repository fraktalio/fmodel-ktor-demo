package com.fraktalio

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.fraktalio.application.Aggregate
import com.fraktalio.application.aggregate
import com.fraktalio.domain.orderDecider
import com.fraktalio.domain.orderSaga
import com.fraktalio.domain.restaurantDecider
import com.fraktalio.domain.restaurantSaga
import com.fraktalio.persistence.AggregateEventRepositoryImpl
import com.fraktalio.persistence.EventStore
import com.fraktalio.persistence.pooledConnectionFactory
import com.fraktalio.plugins.*
import com.fraktalio.routes.homeRouting
import com.fraktalio.routes.restaurantRouting
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.util.logging.*
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.awaitCancellation

/**
 * Simple logger
 */
internal val LOGGER = KtorSimpleLogger("Fraktalio Logger")

/**
 * Main entry point of the application
 * Arrow [SuspendApp] is used to gracefully handle termination of the application
 */
fun main(): Unit = SuspendApp {
    resourceScope {
        val httpEnv = Env.Http()
        val meterRegistry = meterRegistry()
        val connectionFactory: ConnectionFactory = pooledConnectionFactory(Env.R2DBCDataSource())
        val eventStore = EventStore(connectionFactory).apply { initSchema() }
        val aggregate = aggregate(
            orderDecider(),
            restaurantDecider(),
            orderSaga(),
            restaurantSaga(),
            AggregateEventRepositoryImpl(eventStore)
        )

        server(CIO, host = httpEnv.host, port = httpEnv.port) {
            configureSerialization()
            configurePrometheusMonitoring(meterRegistry)
            configureTracing()
            configureSwagger()

            module(aggregate)
        }

        awaitCancellation()
    }
}

fun Application.module(aggregate: Aggregate) {
    homeRouting()
    restaurantRouting(aggregate)
}
