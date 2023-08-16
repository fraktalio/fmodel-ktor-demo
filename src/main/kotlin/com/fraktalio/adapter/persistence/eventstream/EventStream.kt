package com.fraktalio.adapter.persistence.eventstream

import com.fraktalio.LOGGER
import com.fraktalio.adapter.deciderId
import com.fraktalio.adapter.persistence.eventstore.eventMapper
import com.fraktalio.adapter.persistence.extension.alterSQLResource
import com.fraktalio.adapter.persistence.extension.connection
import com.fraktalio.adapter.persistence.extension.executeSql
import com.fraktalio.application.MaterializedViewState
import com.fraktalio.domain.Command
import com.fraktalio.domain.Event
import com.fraktalio.fmodel.application.MaterializedView
import com.fraktalio.fmodel.application.SagaManager
import com.fraktalio.fmodel.application.handle
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

// ######################
// ######## VIEW ########
// ######################
/**
 * A view entity - The views table is a registry of all views/subscribers that are able to subscribe to all events with a "pooling_delay" frequency.
 */
internal data class ViewEntity(
    val view: String,
    val poolingDelayMilliseconds: Long = 500L,
    val startAt: LocalDateTime = LocalDateTime.of(1, Month.JANUARY, 1, 1, 1),
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null
)

/**
 * A view mapper - Maps a row from the database to a view entity.
 */
internal val viewMapper: (Row, RowMetadata) -> ViewEntity = { row, _ ->
    ViewEntity(
        row.get("view", String::class.java) ?: error("view is null"),
        row.get("pooling_delay", Number::class.java)?.toLong() ?: error("pooling_delay is null"),
        row.get("start_at", LocalDateTime::class.java) ?: error("start_at is null"),
        row.get("created_at", OffsetDateTime::class.java),
        row.get("updated_at", OffsetDateTime::class.java),
    )
}

/**
 * An user facing representation of the ViewEntity - DTO
 */
@Serializable
data class View(val view: String, val poolingDelayMilliseconds: Long)

/**
 * Maps a view entity to a view.
 */
internal fun ViewEntity.asView() = View(view, poolingDelayMilliseconds)

// ######################
// ######## LOCK ########
// ######################

/**
 * A lock entity - The locks table is a registry of all locks that are used to prevent multiple concurrent views/subscribers to process the same event.
 */
internal data class LockEntity(
    val view: String,
    val deciderId: String,
    val offset: Long = -1L,
    val lastOffset: Long = 0L,
    val lockedUntil: OffsetDateTime? = null,
    val offsetFinal: Boolean,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
)

/**
 * A lock mapper - Maps a row from the database to a lock entity.
 */
internal val lockMapper: (Row, RowMetadata) -> LockEntity = { row, _ ->
    LockEntity(
        row.get("view", String::class.java) ?: error("view is null"),
        row.get("decider_id", String::class.java) ?: error("decider_id is null"),
        row.get("offset", Number::class.java)?.toLong() ?: error("offset is null"),
        row.get("last_offset", Number::class.java)?.toLong() ?: error("last_offset is null"),
        row.get("locked_until", OffsetDateTime::class.java),
        row.get("offset_final", String::class.java).toBoolean(),
        row.get("created_at", OffsetDateTime::class.java),
        row.get("updated_at", OffsetDateTime::class.java),
    )
}

/**
 * Actions that can be performed on a lock.
 * ACK - Acknowledge the event (with `offset`) processing as successful.
 * NACK - Negative acknowledge of the event processing. - The event will be processed again, immediately.
 * SCHEDULE_NACK - Negative acknowledge of the event processing. - The event will be processed again, after `milliseconds`.
 */
@Serializable
sealed class Action

@Serializable
data class Ack(val offset: Long, val deciderId: String) : Action()

@Serializable
data class Nack(val deciderId: String) : Action()

@Serializable
data class ScheduleNack(val milliseconds: Long, val deciderId: String) : Action()

/**
 * An user facing representation of the LockEntity - DTO
 */
@Serializable
data class Lock(
    val view: String,
    val deciderId: String,
    val offset: Long,
    val lastOffset: Long,
    val lockedUntil: String?,
    val offsetFinal: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
)

/**
 * Maps a lock entity to a lock DTO.
 */
internal fun LockEntity.asLock() = Lock(
    view,
    deciderId,
    offset,
    lastOffset,
    lockedUntil?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    offsetFinal,
    createdAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    updatedAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
)

/**
 * Event Stream enables registration of views/subscribers and event streaming/pooling from the database.
 *
 *  @author Иван Дугалић / Ivan Dugalic / @idugalic
 */
internal class EventStream(private val connectionFactory: ConnectionFactory) {
    companion object {
        private const val CREATE_TABLE_VIEWS =
            """
                CREATE TABLE IF NOT EXISTS views
                (
                    -- view identifier/name
                    "view"          TEXT,
                    -- pooling_delay represent the frequency of pooling the database for the new events / 500 ms by default
                    "pooling_delay" BIGINT                   DEFAULT 500   NOT NULL,
                    -- the point in time form where the event streaming/pooling should start / NOW is by default, but you can specify the binging of time if you want
                    "start_at"      TIMESTAMP                DEFAULT NOW() NOT NULL,
                    -- the timestamp of the view insertion.
                    "created_at"    TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
                    -- the timestamp of the view update.
                    "updated_at"    TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
                    PRIMARY KEY ("view")
              );
            """
        private const val CREATE_TABLE_LOCKS =
            """
                CREATE TABLE IF NOT EXISTS locks
                (
                    -- view identifier/name
                    "view"         TEXT                                                    NOT NULL,
                    -- business identifier for the decider
                    "decider_id"   TEXT                                                    NOT NULL,
                    -- current offset of the event stream for decider_id
                    "offset"       BIGINT                                                  NOT NULL,
                    -- the offset of the last event being processed
                    "last_offset"  BIGINT                                                  NOT NULL,
                    -- a lock / is this event stream for particular decider_id locked for reading or not
                    "locked_until" TIMESTAMP WITH TIME ZONE DEFAULT NOW() - INTERVAL '1ms' NOT NULL,
                    -- an indicator if the offset is final / offset will not grow any more
                    "offset_final" BOOLEAN                                                 NOT NULL,
                    -- the timestamp of the view insertion.
                    "created_at"   TIMESTAMP WITH TIME ZONE DEFAULT NOW()                  NOT NULL,
                    -- the timestamp of the view update.
                    "updated_at"   TIMESTAMP WITH TIME ZONE DEFAULT NOW()                  NOT NULL,
                    PRIMARY KEY ("view", "decider_id"),
                    FOREIGN KEY ("view") REFERENCES views ("view") ON DELETE CASCADE
                );
            """
        private const val CREATE_LOCK_INDEX =
            """
                CREATE INDEX IF NOT EXISTS locks_index ON locks ("decider_id", "locked_until", "last_offset");
            """
        private const val BEFORE_UPDATE_VIEWS =
            """
                CREATE OR REPLACE FUNCTION "before_update_views_table"() RETURNS trigger AS
                '
                    BEGIN
                        NEW.updated_at = NOW();
                        RETURN NEW;
                    END;
                '
                    LANGUAGE plpgsql;
                
                DROP TRIGGER IF EXISTS "t_before_update_views_table" ON "views";
                CREATE TRIGGER "t_before_update_views_table"
                    BEFORE UPDATE
                    ON "views"
                    FOR EACH ROW
                EXECUTE FUNCTION "before_update_views_table"();
            """
        private const val BEFORE_UPDATE_LOCKS =
            """
                CREATE OR REPLACE FUNCTION "before_update_locks_table"() RETURNS trigger AS
                '
                    BEGIN
                        NEW.updated_at = NOW();
                        RETURN NEW;
                    END;
                '
                    LANGUAGE plpgsql;
                
                DROP TRIGGER IF EXISTS "t_before_update_locks_table" ON "locks";
                CREATE TRIGGER "t_before_update_locks_table"
                    BEFORE UPDATE
                    ON "locks"
                    FOR EACH ROW
                EXECUTE FUNCTION "before_update_locks_table"();
            """
        private const val ON_INSERT_ON_EVENTS =
            """
                --  SIDE EFFECT: after appending a new event (with new decider_id), the lock is upserted
                CREATE OR REPLACE FUNCTION on_insert_on_events() RETURNS trigger AS
                '
                    BEGIN
                
                        INSERT INTO locks
                        SELECT t1.view        AS view,
                               NEW.decider_id AS decider_id,
                               NEW.offset     AS offset,
                               0              AS last_offset,
                               NOW()          AS locked_until,
                               NEW.final      AS offset_final
                        FROM views AS t1
                        ON CONFLICT ON CONSTRAINT "locks_pkey" DO UPDATE SET "offset"     = NEW."offset",
                                                                             offset_final = NEW.final;
                        RETURN NEW;
                    END;
                '
                    LANGUAGE plpgsql;
                
                DROP TRIGGER IF EXISTS t_on_insert_on_events ON events;
                CREATE TRIGGER t_on_insert_on_events
                    AFTER INSERT
                    ON events
                    FOR EACH ROW
                EXECUTE FUNCTION on_insert_on_events();
            """
        private const val ON_INSERT_OR_UPDATE_ON_VIEWS =
            """
                -- SIDE EFFECT: after upserting a views, all the locks should be re-upserted so to keep the correct matrix of `view-deciderId` locks
                CREATE OR REPLACE FUNCTION on_insert_or_update_on_views() RETURNS trigger AS
                '
                    BEGIN
                        INSERT INTO locks
                        SELECT NEW."view"    AS "view",
                               t1.decider_id AS decider_id,
                               t1.max_offset AS "offset",
                               COALESCE(
                                       (SELECT t2."offset" - 1 AS "offset"
                                        FROM events AS t2
                                        WHERE t2.decider_id = t1.decider_id
                                          AND t2.created_at >= NEW.start_at
                                        ORDER BY t2."offset" ASC
                                        LIMIT 1),
                                       (SELECT t2."offset" AS "offset"
                                        FROM events AS t2
                                        WHERE t2.decider_id = t1.decider_id
                                        ORDER BY "t2"."offset" DESC
                                        LIMIT 1)
                                   )         AS last_offset,
                               NOW()         AS locked_until,
                               t1.final      AS offset_final
                        FROM (SELECT DISTINCT ON (decider_id) decider_id AS decider_id,
                                                              "offset"   AS max_offset,
                                                              final      AS final
                              FROM events
                              ORDER BY decider_id, "offset" DESC) AS t1
                        ON CONFLICT ON CONSTRAINT "locks_pkey"
                            DO UPDATE
                            SET "offset"     = EXCLUDED."offset",
                                last_offset  = EXCLUDED.last_offset,
                                offset_final = EXCLUDED.offset_final;
                        RETURN NEW;
                    END;
                ' LANGUAGE plpgsql;
                
                DROP TRIGGER IF EXISTS t_on_insert_or_update_on_views ON "views";
                CREATE TRIGGER t_on_insert_or_update_on_views
                    AFTER INSERT OR UPDATE
                    ON "views"
                    FOR EACH ROW
                EXECUTE FUNCTION on_insert_or_update_on_views();

            """
        private const val REGISTER_VIEW =
            """
                CREATE OR REPLACE FUNCTION register_view(v_view TEXT, v_pooling_delay BIGINT, v_start_at TIMESTAMP)
                    RETURNS SETOF "views" AS
                '
                    BEGIN
                        RETURN QUERY
                            INSERT INTO "views" ("view", pooling_delay, start_at)
                                VALUES (v_view, v_pooling_delay, v_start_at) RETURNING *;
                    END;
                ' LANGUAGE plpgsql;
            """

        private const val STREAM_EVENTS =
            """
                CREATE OR REPLACE FUNCTION stream_events(v_view_name TEXT)
                RETURNS SETOF events AS
                '
                    DECLARE
                        v_last_offset INTEGER;
                        v_decider_id  TEXT;
                    BEGIN
                        -- Check if there are events with a greater id than the last_offset and acquire lock on views table/row for the first decider_id/stream you can find
                        SELECT decider_id,
                               last_offset
                        INTO v_decider_id, v_last_offset
                        FROM locks
                        WHERE view = v_view_name
                          AND locked_until < NOW() -- locked = false
                          AND last_offset < "offset"
                        ORDER BY "offset"
                        LIMIT 1 FOR UPDATE SKIP LOCKED;
                
                        -- Update views locked status to true
                        UPDATE locks
                        SET locked_until = NOW() + INTERVAL ''5m'' -- locked = true, for next 5 minutes
                        WHERE view = v_view_name
                          AND locked_until < NOW() -- locked = false
                          AND decider_id = v_decider_id;
                
                        -- Return the events that have not been locked yet
                        RETURN QUERY SELECT *
                                     FROM events
                                     WHERE decider_id = v_decider_id
                                       AND "offset" > v_last_offset
                                     ORDER BY "offset"
                                     LIMIT 1;
                    END;
                ' LANGUAGE plpgsql;
            """
        private const val ACK_EVENT =
            """
                CREATE OR REPLACE FUNCTION ack_event(v_view TEXT, v_decider_id TEXT, v_offset BIGINT)
                    RETURNS SETOF "locks" AS
                '
                    BEGIN
                        -- Update locked status to false
                        RETURN QUERY
                            UPDATE locks
                                SET locked_until = NOW(), -- locked = false,
                                    last_offset = v_offset
                                WHERE "view" = v_view
                                    AND decider_id = v_decider_id
                                RETURNING *;
                    END;
                ' LANGUAGE plpgsql;
            """
        private const val NACK_EVENT =
            """
                CREATE OR REPLACE FUNCTION nack_event(v_view TEXT, v_decider_id TEXT)
                    RETURNS SETOF "locks" AS
                '
                    BEGIN
                        -- Nack: Update locked status to false, without updating the offset
                        RETURN QUERY
                            UPDATE locks
                                SET locked_until = NOW() -- locked = false
                                WHERE "view" = v_view
                                    AND decider_id = v_decider_id
                                RETURNING *;
                    END;
                ' LANGUAGE plpgsql;
            """
        private const val SCHEDULE_NACK =
            """
                CREATE OR REPLACE FUNCTION schedule_nack_event(v_view TEXT, v_decider_id TEXT, v_milliseconds BIGINT)
                    RETURNS SETOF "locks" AS
                '
                    BEGIN
                        -- Schedule the nack
                        RETURN QUERY
                            UPDATE locks
                                SET "locked_until" = NOW() + (v_milliseconds || ''ms'')::INTERVAL
                                WHERE "view" = v_view
                                    AND decider_id = v_decider_id
                                RETURNING *;
                    END;
                ' LANGUAGE plpgsql;
            """
        private const val VIEW_DATA =
            """
                INSERT INTO views
                VALUES ('view', 500)
                ON CONFLICT (view) DO NOTHING;
            """

        private const val SAGA_VIEW_DATA =
            """
                INSERT INTO views
                VALUES ('saga', 500)
                ON CONFLICT (view) DO NOTHING;
            """
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(10)

    /**
     * Initialize the DB schema
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    suspend fun initSchema() = withContext(dbDispatcher) {
        LOGGER.debug("# Initializing View schema #")
        connectionFactory.connection().alterSQLResource(CREATE_TABLE_VIEWS)
        connectionFactory.connection().alterSQLResource(CREATE_TABLE_LOCKS)
        connectionFactory.connection().alterSQLResource(CREATE_LOCK_INDEX)
        connectionFactory.connection().alterSQLResource(BEFORE_UPDATE_VIEWS)
        connectionFactory.connection().alterSQLResource(BEFORE_UPDATE_LOCKS)
        connectionFactory.connection().alterSQLResource(ON_INSERT_ON_EVENTS)
        connectionFactory.connection().alterSQLResource(ON_INSERT_OR_UPDATE_ON_VIEWS)
        connectionFactory.connection().alterSQLResource(REGISTER_VIEW)
        connectionFactory.connection().alterSQLResource(STREAM_EVENTS)
        connectionFactory.connection().alterSQLResource(ACK_EVENT)
        connectionFactory.connection().alterSQLResource(NACK_EVENT)
        connectionFactory.connection().alterSQLResource(SCHEDULE_NACK)
        LOGGER.debug("## Inserting data in View schema ##")
        connectionFactory.connection().alterSQLResource(VIEW_DATA)
        connectionFactory.connection().alterSQLResource(SAGA_VIEW_DATA)

    }


    // ######################
    // ######## VIEW ########
    // ######################
    /**
     * Register a view
     * @param view the view name
     * @param poolingDelayMilliseconds the pooling delay in milliseconds
     * @param startAt the start date
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    suspend fun registerView(
        view: String,
        poolingDelayMilliseconds: Long,
        startAt: LocalDateTime
    ): View = withContext(dbDispatcher) {
        connectionFactory.connection()
            .executeSql(
                """
              INSERT INTO "views"
              ("view", "pooling_delay", "start_at") VALUES ($1, $2, $3)
              ON CONFLICT ON CONSTRAINT "views_pkey"
              DO UPDATE SET "updated_at" = NOW(), "start_at" = EXCLUDED."start_at", "pooling_delay" = EXCLUDED."pooling_delay"
              RETURNING *
            """,
                viewMapper
            ) {
                bind(0, view)
                bind(1, poolingDelayMilliseconds)
                bind(2, startAt)
            }
            .single()
            .asView()
    }


    /**
     * Find all views
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    fun findAllViews() = flow {
        connectionFactory
            .connection()
            .executeSql(
                "select * from views",
                viewMapper
            )
            .map { it.asView() }
            .also { emitAll(it) }
    }.flowOn(dbDispatcher)

    /**
     * Find a view by its name
     * @param view the view name
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    suspend fun findViewById(view: String): View? = withContext(dbDispatcher) {
        connectionFactory
            .connection()
            .executeSql(
                "select * from views where view = $1",
                viewMapper
            ) {
                bind(0, view)
            }
            .singleOrNull()
            ?.asView()
    }

    // ######################
    // ######## LOCK ########
    // ######################
    /**
     * Find all locks
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    fun findAllLocks() = flow {
        connectionFactory
            .connection()
            .executeSql(
                "select * from locks",
                lockMapper
            )
            .map { it.asLock() }
            .also { emitAll(it) }
    }.flowOn(dbDispatcher)


    private suspend fun executeLockAction(view: String, action: Action): Lock? = withContext(dbDispatcher) {
        when (action) {
            is Ack ->
                connectionFactory
                    .connection()
                    .executeSql(
                        "SELECT * FROM ack_event($1, $2, $3)",
                        lockMapper
                    ) {
                        bind(2, action.offset)
                        bind(0, view)
                        bind(1, action.deciderId)
                    }
                    .singleOrNull()
                    ?.asLock()

            is Nack ->
                connectionFactory
                    .connection()
                    .executeSql(
                        "SELECT * FROM nack_event($1, $2)",
                        lockMapper
                    ) {
                        bind(0, view)
                        bind(1, action.deciderId)
                    }
                    .singleOrNull()
                    ?.asLock()

            is ScheduleNack ->
                connectionFactory
                    .connection()
                    .executeSql(
                        "SELECT * FROM schedule_nack_event($1, $2, $3)",
                        lockMapper
                    ) {
                        bind(0, view)
                        bind(2, action.milliseconds)
                        bind(1, action.deciderId)
                    }
                    .singleOrNull()
                    ?.asLock()
        }
    }

    // #################################
    // ######## EVENT STREAMING  #######
    // #################################
    private suspend fun getEvent(view: String): Pair<Event, Long>? = withContext(dbDispatcher) {
        connectionFactory
            .connection()
            .executeSql(
                "SELECT * FROM stream_events($1)",
                eventMapper
            ) {
                bind(0, view)
            }.singleOrNull()?.let { Pair(Json.decodeFromString(it.data.decodeToString()), it.offset ?: -1) }
    }

    private fun poolEvents(view: String, poolingDelayMilliseconds: Long): Flow<Pair<Event, Long>> =
        flow {
            while (currentCoroutineContext().isActive) {
                LOGGER.info("# stream loop #: pulling the db for the view $view")
                val event = getEvent(view)
                if (event != null) {
                    LOGGER.debug("# stream loop #: emitting the event {}", event)
                    emit(event)
                    LOGGER.debug("# stream loop #: event emitted")
                } else {
                    LOGGER.debug("# stream loop #: scheduling new pool in $poolingDelayMilliseconds milliseconds")
                    delay(poolingDelayMilliseconds)
                }
            }
        }.flowOn(dbDispatcher)

    /**
     * Stream events for a view
     * @param view the view name
     * @param actions the actions to send to the view
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    private fun streamEvents(
        view: String,
        actions: Flow<Action>,
        scope: CoroutineScope
    ): Flow<Pair<Event, Long>> =
        flow {
            findViewById(view)?.let { viewEntity ->
                scope.launch {
                    actions.collect {
                        executeLockAction(view, it)
                    }
                }
                poolEvents(view, viewEntity.poolingDelayMilliseconds)
                    .collect { emit(it) }
            }

        }.flowOn(dbDispatcher)

    /**
     * Register a materialized view and start pooling events
     * @param view the view name
     * @param materializedView the materialized view to register - event handler
     * @param scope the coroutine scope
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    fun registerMaterializedViewAndStartPooling(
        view: String,
        materializedView: MaterializedView<MaterializedViewState, Event?>,
        scope: CoroutineScope
    ) {
        LOGGER.info("### Registering materialized view $view")
        scope.launch(dbDispatcher) {
            val actions = Channel<Action>()
            streamEvents(view, actions.receiveAsFlow(), scope)
                .onStart {
                    launch {
                        actions.send(Ack(-1, "start"))
                    }
                    LOGGER.info("### Materialized view $view subscribed to events")
                }
                .retry(5) { cause ->
                    cause !is CancellationException
                }
                .collect {
                    try {
                        LOGGER.debug("View - Handling event {}", it.first)
                        materializedView.handle(it.first)
                        LOGGER.debug("View - Handled event {}", it.first)
                        actions.send(Ack(it.second, it.first.deciderId()))
                    } catch (e: Exception) {
                        LOGGER.error("Error while handling event, retrying in 10 seconds ${it.first}", e)
                        actions.send(ScheduleNack(10000, it.first.deciderId()))
                    }
                }
        }
    }

    /**
     * Register a saga manager and start pooling events
     * @param view the saga manager view name - saga needs a view to track the token/position of events being read.
     * @param sagaManager the saga manager to register - event handler
     * @param scope the coroutine scope
     *
     * Uses [Dispatchers.IO] dispatcher with a limited parallelism
     */
    fun registerSagaManagerAndStartPooling(
        view: String,
        sagaManager: SagaManager<Event?, Command>,
        scope: CoroutineScope
    ) {
        LOGGER.info("### Registering saga manager with the view $view")
        scope.launch(dbDispatcher) {
            val actions = Channel<Action>()
            streamEvents(view, actions.receiveAsFlow(), scope)
                .onStart {
                    launch {
                        actions.send(Ack(-1, "start"))
                    }
                    LOGGER.info("### Saga manager $view subscribed to events")
                }
                .retry(5) { cause ->
                    cause !is CancellationException
                }
                .collect {
                    try {
                        LOGGER.debug("Saga - Handling event {}", it.first)
                        sagaManager.handle(it.first).collect()
                        LOGGER.debug("Saga - Handled event {}", it.first)
                        actions.send(Ack(it.second, it.first.deciderId()))
                    } catch (e: Exception) {
                        LOGGER.error("Error while handling event, retrying in 10 seconds ${it.first}", e)
                        actions.send(ScheduleNack(10000, it.first.deciderId()))
                    }
                }
        }
    }
}
