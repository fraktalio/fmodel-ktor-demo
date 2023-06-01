# fmodel-ktor-demo (EventSourcing)

A demo/example project for the imaginary restaurant and order management.

![event model image](.assets/restaurant-model.jpg)

## Fmodel

This project is using [Fmodel](https://github.com/fraktalio/fmodel) - Kotlin, multiplatform library.

**Fmodel** is:

- enabling functional, algebraic and reactive domain modeling with Kotlin programming language.
- inspired by DDD, EventSourcing and Functional programming communities, yet implements these ideas and
  concepts in idiomatic Kotlin, which in turn makes our code
    - less error-prone,
    - easier to understand,
    - easier to test,
    - type-safe and
    - thread-safe.
- enabling illustrating requirements using examples
    - the requirements are presented as scenarios.
    - a scenario is an example of the system’s behavior from the users’ perspective,
    - and they are specified using the Given-When-Then structure to create a testable/runnable specification
        - Given `< some precondition(s) / events >`
        - When `< an action/trigger occurs / commands>`
        - Then `< some post condition / events >`

Check the tests!

```kotlin
with(orderDecider) {
    givenEvents(listOf(orderCreatedEvent)) {         // PRE CONDITIONS
        whenCommand(createOrderCommand)              // ACTION
    } thenEvents listOf(orderRejectedEvent)          // POST CONDITIONS
}
```

## Fstore-SQL

This project is using [PostgreSQL powered event store](https://github.com/fraktalio/fstore-sql), optimized for event
sourcing and event streaming.

**Fstore-SQL** is enabling event-sourcing and *pool-based* event-streaming patterns by using SQL (PostgreSQL) only.

- `event-sourcing` data pattern (by using PostgreSQL database) to durably store events
    - Append events to the ordered, append-only log, using `entity id`/`decider id` as a key
    - Load all the events for a single entity/decider, in an ordered sequence, using the `entity id`/`decider id` as a
      key
    - Support optimistic locking/concurrency
- `event-streaming` to concurrently coordinate read over a streams of events from multiple consumer instances
    - Support real-time concurrent consumers to project events into view/query models

## Patterns

- EventSourcing
- CQRS

## Prerequisites

- [Java 17](https://adoptium.net/)
- [Docker](https://www.docker.com/products/docker-desktop/)

## Technology

- [Fmodel - Domain modeling with Kotlin](https://github.com/fraktalio/fmodel)
- [Ktor](https://ktor.io/) (HTTP)
- [Kotlin](https://kotlinlang.org/) (Coroutines, Serialization)
- [Arrow](https://arrow-kt.io/) (Fx Coroutines, ...)
- [Testcontainers](https://www.testcontainers.org/)
- [PostgreSQL](https://www.postgresql.org/) ([event store](https://github.com/fraktalio/fstore-sql), projections)
- [Jaeger](https://www.jaegertracing.io/) (Distributed Tracing)

## Run & Test

This project is using [Gradle](https://docs.gradle.org) as a build and automation tool.

### Test:

```shell
./gradlew check
```

### Run:

> Make sure you have PostgreSQL installed and running.

```shell
docker run --name postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres
```

> Make sure you have [Jaeger](https://www.jaegertracing.io/) installed and running.

```shell
docker run -d --name jaeger \
-e COLLECTOR_ZIPKIN_HOST_PORT=:9411 \
-e COLLECTOR_OTLP_ENABLED=true \
-p 6831:6831/udp \
-p 6832:6832/udp \
-p 5778:5778 \
-p 16686:16686 \
-p 4317:4317 \
-p 4318:4318 \
-p 14250:14250 \
-p 14268:14268 \
-p 14269:14269 \
-p 9411:9411 \
jaegertracing/all-in-one:1.38
```

Once running, you can view the Jaeger UI at http://localhost:16686/

> Check the connection URL in [application enviroment variables](src/main/kotlin/com/fraktalio/Env.kt)

```shell
./gradlew run
```

### Run in Docker

> Make sure you have [Docker](https://www.docker.com/products/docker-desktop/) installed and running.

Build OCI (docker) image:

```shell
./gradlew publishImageToLocalRegistry
```

Run application and PostgreSQL:

```shell
docker-compose up
```

## Further Reading

- Check the [source code](https://github.com/fraktalio/fmodel)
- Read the [blog](https://fraktalio.com/blog/)
- Learn by example on the [playground](https://fraktalio.com/blog/playground)
- [https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/ktor/ktor-2.0/library](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/ktor/ktor-2.0/library)
- [https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/7755](https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/7755)
- [https://github.com/mpeyper/open-telemetry-coroutine-tracing-repro/tree/main/src/main/kotlin/example](https://github.com/mpeyper/open-telemetry-coroutine-tracing-repro/tree/main/src/main/kotlin/example)

---
Created with :heart: by [Fraktalio](https://fraktalio.com/)

Excited to launch your next IT project with us? Let's get started! Reach out to our team at `info@fraktalio.com` to
begin the journey to success.
