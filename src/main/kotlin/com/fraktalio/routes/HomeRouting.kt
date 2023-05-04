package com.fraktalio.routes

import com.fraktalio.LOGGER
import com.fraktalio.plugins.withSpan
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class)
fun Application.homeRouting() {

    routing {
        get("/") {
            withSpan("section-1") {
                newSingleThreadContext("childService1-THREAD").use { ctx ->
                    withContext(ctx) { childService1() }
                }
            }
            withSpan("section-2") {
                newSingleThreadContext("childService2-THREAD").use { ctx ->
                    childService2().flowOn(ctx).collect {
                        LOGGER.debug("Collecting from childService2")


                    }
                }
            }

            withSpan("respondText") {
                call.respondText(
                    childService3().reduce { first, second -> first + second })
            }
        }


    }


}


private suspend fun childService1() = withSpan("childService1") {
    LOGGER.debug("Hello from childService1")
    delay(500)
}

private fun childService2() = flow {
    emit(1)
    emit(1)
    emit(1)
    emit(1)
    LOGGER.debug("Emitted from childService2")
}.withSpan("childService2")

private fun childService3() =
    flowOf("Hello World!", "Hello World Again!", "Hello World Again and Again!").withSpan("childService3")


