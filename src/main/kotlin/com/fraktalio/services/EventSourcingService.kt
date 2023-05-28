package com.fraktalio.services

import com.fraktalio.LOGGER
import com.fraktalio.persistence.alterSQLResource
import com.fraktalio.persistence.connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.util.*

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

internal val eventMapper: (Row, RowMetadata) -> EventEntity = { row, _ ->
    EventEntity(
        row.get("decider", String::class.java) ?: "",
        row.get("decider_id", String::class.java) ?: "",
        row.get("event", String::class.java) ?: "",
        row.get("data", ByteArray::class.java) ?: ByteArray(0),
        row.get("event_id", UUID::class.java) ?: UUID.randomUUID(),
        row.get("command_id", UUID::class.java) ?: UUID.randomUUID(),
        row.get("previous_id", UUID::class.java),
        row.get("final", String::class.java).toBoolean(),
        row.get("created_at", OffsetDateTime::class.java),
        row.get("offset", Number::class.java)?.toLong()
    )
}

class EventSourcingService(private val connectionFactory: ConnectionFactory) {
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

    }

    // Initialize schema
    suspend fun initSchema() = withContext(Dispatchers.IO) {
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
        LOGGER.debug("###### Event Sourcing schema initialized #######")
    }

}

