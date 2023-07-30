package com.fraktalio

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.fraktalio.adapter.persistence.AggregateEventRepositoryImpl
import com.fraktalio.adapter.persistence.MaterializedViewStateRepositoryImpl
import com.fraktalio.adapter.persistence.OrderRepository
import com.fraktalio.adapter.persistence.RestaurantRepository
import com.fraktalio.adapter.persistence.eventstore.EventStore
import com.fraktalio.adapter.persistence.eventstream.EventStream
import com.fraktalio.adapter.persistence.extension.pooledConnectionFactory
import com.fraktalio.adapter.routes.restaurantRouting
import com.fraktalio.application.Aggregate
import com.fraktalio.application.aggregate
import com.fraktalio.application.materializedView
import com.fraktalio.application.paymentSagaManager
import com.fraktalio.domain.*
import com.fraktalio.plugins.configureSerialization
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.util.logging.*
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.awaitCancellation

/**
 * Simple logger
 */
internal val LOGGER = KtorSimpleLogger("com.fraktalio")

/**
 * Main entry point of the application
 * Arrow [SuspendApp] is used to gracefully handle termination of the application
 */
fun main(): Unit = SuspendApp {
    resourceScope {
        val httpEnv = Env.Http()
        val connectionFactory: ConnectionFactory = pooledConnectionFactory(Env.R2DBCDataSource())
        // ### Command Side - Event Sourcing ###
        val eventStore = EventStore(connectionFactory).apply { initSchema() }
        val aggregateEventRepository = AggregateEventRepositoryImpl(eventStore)
        val aggregate = aggregate(
            orderDecider(),
            restaurantDecider(),
            orderSaga(),
            restaurantSaga(),
            aggregateEventRepository
        )
        // ### Query Side - Event Streaming, Materialized Views and Sagas ###
        val eventStream = EventStream(connectionFactory).apply { initSchema() }
        val restaurantRepository = RestaurantRepository(connectionFactory).apply { initSchema() }
        val orderRepository = OrderRepository(connectionFactory).apply { initSchema() }
        val materializedViewStateRepository =
            MaterializedViewStateRepositoryImpl(restaurantRepository, orderRepository)

        @Suppress("UNUSED_VARIABLE")
        val materializedView = materializedView(
            restaurantView(),
            orderView(),
            materializedViewStateRepository
        ).also { eventStream.registerMaterializedViewAndStartPooling("view", it, this@SuspendApp) }

        @Suppress("UNUSED_VARIABLE")
        val sagaManager = paymentSagaManager(
            paymentSaga(),
            aggregate
        ).also { eventStream.registerSagaManagerAndStartPooling("saga", it, this@SuspendApp) }

        server(CIO, host = httpEnv.host, port = httpEnv.port) {
            configureSerialization()

            module(aggregate, restaurantRepository, orderRepository)
        }
        awaitCancellation()
    }
}

fun Application.module(
    aggregate: Aggregate,
    restaurantRepository: RestaurantRepository,
    orderRepository: OrderRepository
) {
    restaurantRouting(aggregate, restaurantRepository, orderRepository)
}
