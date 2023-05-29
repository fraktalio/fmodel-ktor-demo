package com.fraktalio.plugins

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.continuations.ResourceScope
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

/**
 * Configure Prometheus monitoring
 */
fun Application.configurePrometheusMonitoring(appMicrometerRegistry: PrometheusMeterRegistry) {

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        // ...
    }
    routing {
        get("/metrics-micrometer") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}

/**
 * Create Prometheus meter registry
 * @return [PrometheusMeterRegistry]
 * @receiver [ResourceScope]
 */
suspend fun ResourceScope.meterRegistry(): PrometheusMeterRegistry =
    install({ PrometheusMeterRegistry(PrometheusConfig.DEFAULT) }) { p, _: ExitCase -> p.close() }
