package com.fraktalio.adapter.persistence.extension

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.resource
import arrow.fx.coroutines.resourceScope
import com.fraktalio.Env
import com.fraktalio.LOGGER
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.*
import io.r2dbc.spi.ConnectionFactoryOptions.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Mono
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * A constructor like function for [ConnectionFactory] that is `pooled`.
 * @receiver [ResourceScope] for the [Resource] that will be acquired and released gracefully
 * @param env of type [Env.R2DBCDataSource] - configuration
 */
suspend fun ResourceScope.pooledConnectionFactory(
    env: Env.R2DBCDataSource
): ConnectionFactory = install({
    val connectionFactory = ConnectionFactories.get(
        builder()
            .option(DRIVER, env.driver)
            .option(HOST, env.host)
            .option(PORT, env.port)
            .option(USER, env.username)
            .option(PASSWORD, env.password)
            .option(DATABASE, env.database)
            .build()
    )
    val connectionPoolConfiguration = ConnectionPoolConfiguration.builder(connectionFactory)
        .maxIdleTime(env.maxIdleTime.milliseconds.toJavaDuration())
        .maxSize(env.maxPoolSize)
        .initialSize(env.initialPoolSize)
        .build()

    ConnectionPool(connectionPoolConfiguration)
}) { connectionFactory, exitCase ->
    LOGGER.debug("Releasing {} with exit: {}", connectionFactory, exitCase)
    connectionFactory.dispose()
}

/**
 * A constructor like function for [Connection] that is `pooled`.
 * @receiver [ConnectionFactory]
 * @return [Resource]<[Connection]> that will be acquired and released gracefully within the defined [ResourceScope]
 */
suspend fun ConnectionFactory.connection(): Resource<Connection> = resource({
    val conn = create().awaitSingle()
    LOGGER.trace("Obtained new connection from the pool: {}", conn)
    conn
}) { connection, exitCase ->
    LOGGER.trace("Releasing connection {} with exit: {}", connection, exitCase)
    (connection.close() as Mono).awaitSingleOrNull()
    LOGGER.trace("Released connection {}", connection)

}

/**
 * A convenient extension function for the Statement / alternative reified function to `bind`
 * @param name of the parameter
 * @param value / type of the parameter
 */
inline fun <reified T : Any> Statement.bindNullable(name: String, value: T?) =
    if (value != null) bind(name, value) else bindNull(name, T::class.java)

/**
 * A convenient extension function for the Statement / alternative reified function to `bind`
 * @param index of the parameter
 * @param value / type of the parameter
 */
inline fun <reified T : Any> Statement.bindNullable(index: Int, value: T?) =
    if (value != null) bind(index, value) else bindNull(index, T::class.java)


@OptIn(ExperimentalCoroutinesApi::class)
private fun <R : Any> Connection.executeSql(
    sql: String,
    f: (Row, RowMetadata) -> R,
    prepare: Statement.() -> Unit = {}
): Flow<R> =
    createStatement(sql)
        .also(prepare)
        .execute()
        .asFlow()
        .flatMapConcat { it.map(f).asFlow() }


/**
 * A convenient extension function for the Resource<Connection> / gracefully acquiring and releasing the connection on the SQL execution
 * @param sql to be executed
 * @param f function that maps the [Row] and [RowMetadata] to the desired type
 * @param prepare function that prepares the [Statement] before execution
 * @return [Flow]<[R]>
 */
fun <R : Any> Resource<Connection>.executeSql(
    sql: String,
    f: (Row, RowMetadata) -> R,
    prepare: Statement.() -> Unit = {}
): Flow<R> = flow {
    resourceScope {
        val connection = bind()
        emitAll(connection.executeSql(sql, f, prepare))
    }
}

/**
 * A convenient extension function for the Resource<Connection> / gracefully acquiring and releasing the connection on the SQL execution
 * @param sql to be executed
 * @return number of rows updated or null
 */
suspend fun Resource<Connection>.alterSQLResource(
    sql: String
): Result? = resourceScope {
    val connection = bind()
    connection.createStatement(sql)
        .execute()
        .awaitFirstOrNull()

}



