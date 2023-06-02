package com.fraktalio.adapter.persistence


import com.fraktalio.LOGGER
import com.fraktalio.adapter.decider
import com.fraktalio.adapter.deciderId
import com.fraktalio.adapter.event
import com.fraktalio.adapter.persistence.eventstore.EventEntity
import com.fraktalio.adapter.persistence.eventstore.EventStore
import com.fraktalio.application.AggregateEventRepository
import com.fraktalio.domain.Command
import com.fraktalio.domain.Event
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*


/**
 * Implementation of the [AggregateEventRepository].
 *
 * @author Иван Дугалић / Ivan Dugalic / @idugalic
 */
internal class AggregateEventRepositoryImpl(
    private val eventStore: EventStore
) : AggregateEventRepository {

    private fun Flow<EventEntity>.appendAll(): Flow<EventEntity> = map { eventStore.append(it) }


    /**
     * Fetching the current state as a series/flow of Events
     */
    override fun Command?.fetchEvents(): Flow<Pair<Event, UUID?>> =
        when (this) {
            is Command -> eventStore.getEvents(deciderId()).map { it.toEventWithId() }
            null -> emptyFlow()
        }
            .onStart { LOGGER.debug("fetchEvents({}) started ...", this@fetchEvents) }
            .onEach { LOGGER.debug("fetched event: {}", it) }
            .onCompletion {
                when (it) {
                    null -> LOGGER.debug("fetchEvents({}) completed successfully", this@fetchEvents)
                    else -> LOGGER.warn("fetchEvents(${this@fetchEvents}) completed with exception $it")
                }
            }

    /**
     * The latest version provider
     */
    override val latestVersionProvider: (Event?) -> UUID? = { event ->
        when (event) {
            is Event -> runBlocking { eventStore.getLastEvent(event.deciderId())?.eventId }
            null -> null
        }
    }

    /**
     * Storing the new state as a series/flow of Events
     *
     * `latestVersionProvider` is used to fetch the latest version of the event stream, per need
     */
    override fun Flow<Event?>.save(latestVersionProvider: (Event?) -> UUID?): Flow<Pair<Event, UUID>> = flow {
        val previousIds: MutableMap<String, UUID?> = emptyMap<String, UUID?>().toMutableMap()
        emitAll(
            filterNotNull()
                .map {
                    previousIds.computeIfAbsent(it.deciderId()) { _ -> latestVersionProvider(it) }
                    val eventId = UUID.randomUUID()
                    val eventEntity = it.toEventEntity(eventId, previousIds[it.deciderId()])
                    previousIds[it.deciderId()] = eventId
                    eventEntity
                }
                .appendAll()
                .map { it.toEventWithId() }
        )
    }
        .onStart { LOGGER.debug("saving new events started ...") }
        .onEach { LOGGER.debug("saving new event: {}", it) }
        .onCompletion {
            when (it) {
                null -> LOGGER.debug("saving new events completed successfully")
                else -> LOGGER.warn("saving new events completed with exception $it")
            }
        }

    /**
     * Storing the new state as a series/flow of Events
     *
     * `latestVersion` is used to provide you with the latest known version of the state/stream
     */
    override fun Flow<Event?>.save(latestVersion: UUID?): Flow<Pair<Event, UUID>> = flow {
        var previousId: UUID? = latestVersion
        emitAll(
            filterNotNull()
                .map {
                    val eventId = UUID.randomUUID()
                    val eventEntity = it.toEventEntity(eventId, previousId)
                    previousId = eventId
                    eventEntity
                }
                .appendAll()
                .map { it.toEventWithId() }
        )
    }
        .onStart { LOGGER.debug("saving new events started ...") }
        .onEach { LOGGER.debug("saving new event: {}", it) }
        .onCompletion {
            when (it) {
                null -> LOGGER.debug("saving new events completed successfully")
                else -> LOGGER.warn("saving new events completed with exception $it")
            }
        }
}

internal fun EventEntity.toEventWithId() = Pair<Event, UUID>(Json.decodeFromString(data.decodeToString()), eventId)
internal fun Event.toEventEntity(eventId: UUID, previousId: UUID?, commandId: UUID? = null) = EventEntity(
    decider(),
    deciderId(),
    event(),
    Json.encodeToString(this).encodeToByteArray(),
    eventId,
    commandId,
    previousId,
    final
)