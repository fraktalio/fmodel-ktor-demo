package com.fraktalio.persistence

import com.fraktalio.LOGGER
import io.r2dbc.postgresql.codec.Json
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.*

/**
 * An Event entity.
 */
internal data class EventEntity(
    val decider: String,
    val deciderId: String,
    val event: String,
    val data: ByteArray,
    val eventId: UUID,
    val commandId: UUID?,
    val previousId: UUID?,
    val final: Boolean,
    val createdAt: OffsetDateTime? = null,
    val offset: Long? = null
)

/**
 * An Event entity mapper / mapping from database row data into the EventEntity.
 */
internal val eventMapper: (Row, RowMetadata) -> EventEntity = { row, _ ->
    EventEntity(
        row.get("decider", String::class.java) ?: error("decider is null"),
        row.get("decider_id", String::class.java) ?: error("decider_id is null"),
        row.get("event", String::class.java) ?: error("event is null"),
        row.get("data", ByteArray::class.java) ?: error("data is null"),
        row.get("event_id", UUID::class.java) ?: error("event_id is null"),
        row.get("command_id", UUID::class.java) ?: error("command_id is null"),
        row.get("previous_id", UUID::class.java),
        row.get("final", String::class.java).toBoolean(),
        row.get("created_at", OffsetDateTime::class.java),
        row.get("offset", Number::class.java)?.toLong()
    )
}

/**
 * Event store implementation / Postgres implementation.
 */
internal class EventStore(private val connectionFactory: ConnectionFactory) {
    companion object {
        private const val CREATE_TABLE_DECIDERS =
            """
                CREATE TABLE IF NOT EXISTS deciders
                (
                    -- decider name/type
                    "decider" TEXT NOT NULL,
                    -- event name/type that this decider can publish
                    "event"   TEXT NOT NULL,
                    PRIMARY KEY ("decider", "event")
                );
            """
        private const val CREATE_TABLE_EVENTS =
            """
                CREATE TABLE IF NOT EXISTS events
                (
                    -- event name/type. Part of a composite foreign key to `deciders`
                    "event"       TEXT    NOT NULL,
                    -- event ID. This value is used by the next event as it's `previous_id` value to guard against a Lost-EventModel problem / optimistic locking.
                    "event_id"    UUID    NOT NULL UNIQUE,
                    -- decider name/type. Part of a composite foreign key to `deciders`
                    "decider"     TEXT    NOT NULL,
                    -- business identifier for the decider
                    "decider_id"  TEXT    NOT NULL,
                    -- event data in JSON format
                    "data"        JSONB   NOT NULL,
                    -- command ID causing this event
                    "command_id"  UUID    NULL,
                    -- previous event uuid; null for first event; null does not trigger UNIQUE constraint; we defined a function `check_first_event_for_decider`
                    "previous_id" UUID UNIQUE,
                    -- indicator if the event stream for the `decider_id` is final
                    "final"       BOOLEAN NOT NULL         DEFAULT FALSE,
                    -- The timestamp of the event insertion. AUTOPOPULATES—DO NOT INSERT
                    "created_at"  TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
                    -- ordering sequence/offset for all events in all deciders. AUTOPOPULATES—DO NOT INSERT
                    "offset"      BIGSERIAL PRIMARY KEY,
                    FOREIGN KEY ("decider", "event") REFERENCES deciders ("decider", "event")
                );
            """
        private const val DECIDER_INDEX = "CREATE INDEX IF NOT EXISTS decider_index ON events (decider, decider_id)"
        private const val IGNORE_DELETE_DECIDER_EVENTS =
            """
                CREATE OR REPLACE RULE ignore_delete_decider_events AS ON DELETE TO deciders
                DO INSTEAD NOTHING;
            """
        private const val IGNORE_UPDATE_DECIDER_EVENTS =
            """
                CREATE OR REPLACE RULE ignore_update_decider_events AS ON UPDATE TO deciders
                DO INSTEAD NOTHING;
            """
        private const val IGNORE_DELETE_EVENTS =
            """
                CREATE OR REPLACE RULE ignore_delete_events AS ON DELETE TO events
                DO INSTEAD NOTHING;
            """
        private const val IGNORE_UPDATE_EVENTS =
            """
                CREATE OR REPLACE RULE ignore_update_events AS ON UPDATE TO events
                DO INSTEAD NOTHING;
            """
        private const val CHECK_FINAL_EVENT_FOR_DECIDER =
            """
                CREATE OR REPLACE FUNCTION check_final_event_for_decider() RETURNS trigger AS
                '
                    BEGIN
                        IF EXISTS(SELECT 1
                                  FROM events
                                  WHERE NEW.decider_id = decider_id
                                    AND TRUE = final
                                    AND NEW.decider = decider)
                        THEN
                            RAISE EXCEPTION ''last event for this decider stream is already final. the stream is closed, you can not append events to it.'';
                        END IF;
                        RETURN NEW;
                    END;
                '
                    LANGUAGE plpgsql;
                    
                DROP TRIGGER IF EXISTS t_check_final_event_for_decider ON events;
                CREATE TRIGGER t_check_final_event_for_decider
                    BEFORE INSERT
                    ON events
                    FOR EACH ROW
                EXECUTE FUNCTION check_final_event_for_decider();
            """
        private const val CHECK_FIRST_EVENT_FOR_DECIDER =
            """
                -- SIDE EFFECT (trigger): Can only use null previousId for first event in an decider
                CREATE OR REPLACE FUNCTION check_first_event_for_decider() RETURNS trigger AS
                '
                    BEGIN
                        IF (NEW.previous_id IS NULL
                            AND EXISTS(SELECT 1
                                       FROM events
                                       WHERE NEW.decider_id = decider_id
                                         AND NEW.decider = decider))
                        THEN
                            RAISE EXCEPTION ''previous_id can only be null for first decider event'';
                        END IF;
                        RETURN NEW;
                    END;
                '
                    LANGUAGE plpgsql;
                
                DROP TRIGGER IF EXISTS t_check_first_event_for_decider ON events;
                CREATE TRIGGER t_check_first_event_for_decider
                    BEFORE INSERT
                    ON events
                    FOR EACH ROW
                EXECUTE FUNCTION check_first_event_for_decider();
            """
        private const val CHECK_PREVIOUS_ID_IN_SAME_DECIDER =
            """
                -- SIDE EFFECT (trigger): previousId must be in the same decider as the event
                CREATE OR REPLACE FUNCTION check_previous_id_in_same_decider() RETURNS trigger AS
                '
                    BEGIN
                        IF (NEW.previous_id IS NOT NULL
                            AND NOT EXISTS(SELECT 1
                                           FROM events
                                           WHERE NEW.previous_id = event_id
                                             AND NEW.decider_id = decider_id
                                             AND NEW.decider = decider))
                        THEN
                            RAISE EXCEPTION ''previous_id must be in the same decider'';
                        END IF;
                        RETURN NEW;
                    END;
                '
                    LANGUAGE plpgsql;
                
                DROP TRIGGER IF EXISTS t_check_previous_id_in_same_decider ON events;
                CREATE TRIGGER t_check_previous_id_in_same_decider
                    BEFORE INSERT
                    ON events
                    FOR EACH ROW
                EXECUTE FUNCTION check_previous_id_in_same_decider();
            """
        private const val GET_EVENTS_BY_DECIDER =
            """
                CREATE OR REPLACE FUNCTION get_events(v_decider_id TEXT)
                RETURNS SETOF events AS
            '
                BEGIN
                    RETURN QUERY SELECT *
                                 FROM events
                                 WHERE decider_id = v_decider_id
                                 ORDER BY "offset";
                END;
            ' LANGUAGE plpgsql;
            """
        private const val GET_LAST_EVENT_BY_DECIDER =
            """
                CREATE OR REPLACE FUNCTION get_last_event(v_decider_id TEXT)
                RETURNS SETOF events AS
            '
                BEGIN
                    RETURN QUERY SELECT *
                                 FROM events
                                 WHERE decider_id = v_decider_id
                                 ORDER BY "offset" DESC
                                 LIMIT 1;
                END;
            ' LANGUAGE plpgsql;
            """
        private const val APPEND_EVENT =
            """
                CREATE OR REPLACE FUNCTION append_event(v_event TEXT, v_event_id UUID, v_decider TEXT, v_decider_id TEXT, v_data JSONB,
                                        v_command_id UUID, v_previous_id UUID)
                RETURNS SETOF events AS
            '
                BEGIN
                    RETURN QUERY INSERT INTO events (event, event_id, decider, decider_id, data, command_id, previous_id)
                        VALUES (v_event, v_event_id, v_decider, v_decider_id, v_data, v_command_id, v_previous_id)
                        RETURNING *;
                END;
            ' LANGUAGE plpgsql;
            """

    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(10)

    /**
     * Initialize the schema for the event store database.
     */
    suspend fun initSchema() = withContext(dbDispatcher) {
        LOGGER.debug("###### Initializing Event Sourcing schema #######")
        LOGGER.debug(
            "####  Created table Deciders with result {} ####",
            connectionFactory.connection().alterSQLResource(CREATE_TABLE_DECIDERS)
        )
        LOGGER.debug(
            "####  Created table Events with result {} ####",
            connectionFactory.connection().alterSQLResource(CREATE_TABLE_EVENTS)
        )
        LOGGER.debug(
            "####  Created index decider_index with result {} ####",
            connectionFactory.connection().alterSQLResource(DECIDER_INDEX)
        )
        LOGGER.debug(
            "####  Created rule ignore_delete_decider_events with result {} ####",
            connectionFactory.connection().alterSQLResource(IGNORE_DELETE_DECIDER_EVENTS)
        )
        LOGGER.debug(
            "####  Created rule ignore_update_decider_events with result {} ####",
            connectionFactory.connection().alterSQLResource(IGNORE_UPDATE_DECIDER_EVENTS)
        )
        LOGGER.debug(
            "####  Created rule ignore_delete_events with result {} ####",
            connectionFactory.connection().alterSQLResource(IGNORE_DELETE_EVENTS)
        )
        LOGGER.debug(
            "####  Created rule ignore_update_events with result {} ####",
            connectionFactory.connection().alterSQLResource(IGNORE_UPDATE_EVENTS)
        )
        LOGGER.debug(
            "####  Created function check_final_event_for_decider with result {} ####",
            connectionFactory.connection().alterSQLResource(CHECK_FINAL_EVENT_FOR_DECIDER)
        )
        LOGGER.debug(
            "####  Created function check_first_event_for_decider with result {} ####",
            connectionFactory.connection().alterSQLResource(CHECK_FIRST_EVENT_FOR_DECIDER)
        )
        LOGGER.debug(
            "####  Created function check_previous_id_in_same_decider with result {} ####",
            connectionFactory.connection().alterSQLResource(CHECK_PREVIOUS_ID_IN_SAME_DECIDER)
        )
        LOGGER.debug(
            "####  Created function get_events with result {} ####",
            connectionFactory.connection().alterSQLResource(GET_EVENTS_BY_DECIDER)
        )
        LOGGER.debug(
            "####  Created function get_last_event with result {} ####",
            connectionFactory.connection().alterSQLResource(GET_LAST_EVENT_BY_DECIDER)
        )
        LOGGER.debug(
            "####  Created function append_event with result {} ####",
            connectionFactory.connection().alterSQLResource(APPEND_EVENT)
        )
        LOGGER.debug("###### Event Sourcing schema initialized #######")
    }

    /**
     * Get all events for a given decider
     * @param deciderId the decider id
     * @return a finite flow of events
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    fun getEvents(deciderId: String): Flow<EventEntity> = flow {
        connectionFactory.connection()
            .executeSql(
                """
                SELECT * FROM get_events($1)
                """,
                eventMapper
            ) {
                bind(0, deciderId)
            }
            .also { emitAll(it) }
    }.flowOn(dbDispatcher)

    /**
     * Get the last event for a given decider
     * @param deciderId the decider id
     * @return the last event or null if no event exists
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    suspend fun getLastEvent(deciderId: String): EventEntity? = withContext(dbDispatcher) {
        connectionFactory.connection()
            .executeSql(
                """
                SELECT * FROM get_last_event($1)
                """,
                eventMapper
            ) {
                bind(0, deciderId)
            }
            .singleOrNull()
    }

    /**
     * Append an event to the event store
     * @param eventEntity the event to append
     * @return the appended event
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    suspend fun append(eventEntity: EventEntity): EventEntity = withContext(dbDispatcher) {
        with(eventEntity) {
            connectionFactory.connection()
                .executeSql(
                    """
                    SELECT * FROM append_event($1, $2, $3, $4, $5, $6, $7)
                    """,
                    eventMapper
                ) {
                    bind(0, event)
                    bind(1, eventId)
                    bind(2, decider)
                    bind(3, deciderId)
                    bind(4, Json.of(data))
                    bindT(5, commandId)
                    bindT(6, previousId)
                }
                .single()
        }
    }
}

