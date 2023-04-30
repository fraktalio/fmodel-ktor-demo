package com.fraktalio.services

import javax.sql.DataSource


class EventSourcingService(private val dataSource: DataSource) {
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

    }

    init {
        val statement = dataSource.connection.createStatement()
        statement.executeUpdate(CREATE_TABLE_DECIDERS)
        statement.executeUpdate(CREATE_TABLE_EVENTS)
    }
}

