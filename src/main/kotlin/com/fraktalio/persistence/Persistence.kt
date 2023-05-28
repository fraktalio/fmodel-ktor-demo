package com.fraktalio.persistence

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

suspend fun ResourceScope.pooledConnectionFactory(
    env: Env.R2DBCDataSource
): ConnectionFactory = install({
    val connectionFactory = ConnectionFactories.get(
        builder()
            .option(DRIVER, env.driver)
            //.option(PROTOCOL, env.protocol)
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

suspend fun ConnectionFactory.connection(): Resource<Connection> = resource({
    val conn = create().awaitSingle()
    LOGGER.debug("Obtained new connection from the pool: {}", conn)
    conn
}) { connection, exitCase ->
    LOGGER.debug("Releasing connection {} with exit: {}", connection, exitCase)
    (connection.close() as Mono).awaitSingleOrNull()
    LOGGER.debug("Released connection {}", connection)

}

inline fun <reified T : Any> Statement.bindT(name: String, value: T?) =
    bind(name, if (value != null) Parameters.`in`(value) else Parameters.`in`(T::class.java))

inline fun <reified T : Any> Statement.bindT(index: Int, value: T?) =
    bind(index, if (value != null) Parameters.`in`(value) else Parameters.`in`(T::class.java))

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

suspend fun Resource<Connection>.alterSQLResource(
    sql: String
): Long? = resourceScope {
    val connection = bind()
    connection.createStatement(sql)
        .execute()
        .awaitFirstOrNull()
        ?.rowsUpdated
        ?.awaitFirstOrNull()
}



