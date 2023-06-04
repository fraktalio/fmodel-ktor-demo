# fmodel-ktor-demo (EventSourcing)

A demo/example project for the imaginary restaurant and order management.
It is backing the [Fmodel Reference Guide](https://fraktalio.com/fmodel/).

It demonstrates how to run our unique domain and orchestrate it in an EventSourced/EventDriven way.

![event model image](.assets/restaurant-model.jpg)
*this bleuprint is an outcome of the [event-modeling](https://eventmodeling.org/posts/what-is-event-modeling/) process*

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

## Tools

- [EventModeling](https://eventmodeling.org/posts/what-is-event-modeling/) - a method of describing systems using an
  example of how information has changed within them over time.

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
- [Postgres](https://www.postgresql.org/) ([event store](https://github.com/fraktalio/fstore-sql), projections)

## Run & Test

This project is using [Gradle](https://docs.gradle.org) as a build and automation tool.

### Test:

```shell
./gradlew check
```

### Run:

> Make sure you have [Postgres](https://www.postgresql.org/download/) installed and running.

> Alternatively, you can use [Docker](https://www.docker.com/products/docker-desktop/) to run PostgreSQL.

```shell
docker run --name postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres
```

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

Run application and Postgres database:

```shell
docker-compose up
```

## Further Reading

- Check the [FModel reference guide](https://github.com/fraktalio/fmodel)
- Read the [blog](https://fraktalio.com/blog/)
- Learn by example on the [playground](https://fraktalio.com/blog/playground)
- [Spring Boot version of this project](https://github.com/fraktalio/fmodel-spring-demo).

---
Created with :heart: by [Fraktalio](https://fraktalio.com/)

Excited to launch your next IT project with us? Let's get started! Reach out to our team at `info@fraktalio.com` to
begin the journey to success.
