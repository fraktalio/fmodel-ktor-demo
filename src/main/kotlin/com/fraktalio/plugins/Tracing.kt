package com.fraktalio.plugins

import io.ktor.server.application.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.extension.kotlin.getOpenTelemetryContext
import io.opentelemetry.instrumentation.ktor.v2_0.server.KtorServerTracing
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Configure OpenTelemetry tracing
 */
fun Application.configureTracing() {
    val openTelemetry: OpenTelemetry = GlobalOpenTelemetry.get()

    install(KtorServerTracing) {
        setOpenTelemetry(openTelemetry)
    }
}

/**
 * Start a new span, with the given name, attributes and span kind, by reference to the current/parent span.
 */
suspend fun startSpan(name: String, attributes: List<Pair<String, String>>, spanKind: SpanKind?): Span =
    GlobalOpenTelemetry
        .getTracer(object {}.javaClass.packageName)
        .spanBuilder(name)
        .run {
            setParent(currentCoroutineContext().getOpenTelemetryContext())
            spanKind?.let { setSpanKind(it) }
            currentCoroutineContext()[CoroutineName]?.let { coroutineName ->
                setAttribute("coroutine.name", coroutineName.name)
                attributes.forEach { setAttribute(it.first, it.second) }
            }
            startSpan()
        }

/**
 * Start a new span, with the given name, attributes and span kind, by reference to the current/parent span.
 * @receiver [Flow]
 */
fun <R> Flow<R>.withSpan(
    name: String = getDefaultSpanName(),
    attributes: List<Pair<String, String>> = emptyList(),
    spanKind: SpanKind? = null
) = flow {
    val span: Span = startSpan(name, attributes, spanKind)
    emitAll(
        flowOn(span.asContextElement())
            .onCompletion { throwable ->
                if (throwable != null) {
                    span.setStatus(StatusCode.ERROR)
                    span.recordException(throwable)
                }
                span.end()
            })
}

/**
 * Start a new span, with the given name, attributes and span kind, by reference to the current/parent span, and run the `block` within
 */
suspend inline fun <R> withSpan(
    name: String = getDefaultSpanName(),
    attributes: List<Pair<String, String>> = emptyList(),
    spanKind: SpanKind? = null,
    crossinline block: suspend (span: Span?) -> R
): R {
    val span: Span = startSpan(name, attributes, spanKind)

    return withContext(span.asContextElement()) {
        try {
            block(span)
        } catch (throwable: Throwable) {
            span.setStatus(StatusCode.ERROR)
            span.recordException(throwable)
            throw throwable
        } finally {
            span.end()
        }
    }
}

@Suppress("NOTHING_TO_INLINE") // inlining to remove this function from the stack trace
inline fun getDefaultSpanName(): String {
    val callingStackFrame = Thread.currentThread().stackTrace[1]

    val simpleClassName = Class.forName(callingStackFrame.className).simpleName
    val methodName = callingStackFrame.methodName

    return "$simpleClassName.$methodName"
}
