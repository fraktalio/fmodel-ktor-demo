package com.fraktalio.persistence

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.continuations.ResourceScope
import arrow.fx.coroutines.resource
import arrow.fx.coroutines.resourceScope
import com.fraktalio.Env
import com.fraktalio.LOGGER
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
import javax.sql.DataSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

// https://arrow-kt.io/learn/coroutines/resource-safety/
suspend fun ResourceScope.dataSource(env: Env.DataSource): DataSource = autoCloseable {
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = env.jdbcUrl
            username = env.username
            password = env.password
            driverClassName = env.driverClassName
        }
    )
}

fun DataSource.connection(): Resource<java.sql.Connection> = resource({
    connection
}) { connection, exitCase ->
    LOGGER.debug("Releasing {} with exit: {}", connection, exitCase)
    connection.close()
}

// https://github.com/r2dbc/r2dbc-pool#getting-started
suspend fun ResourceScope.pooledConnectionFactory(
    env: Env.R2DBCDataSource
): ConnectionFactory = install({
    val connectionFactory = ConnectionFactories.get(
        builder()
            .option(DRIVER, env.driver)
            .option(PROTOCOL, env.protocol)
            .option(HOST, env.host)
            //.option(PORT, env.port)
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
    LOGGER.debug("Creating new connection: {}", conn)
    conn
}) { connection, exitCase ->
    LOGGER.debug("Releasing {} with exit: {}", connection, exitCase)
    connection.close().awaitFirstOrNull()
}


@OptIn(ExperimentalCoroutinesApi::class)
fun <R : Any> Resource<Connection>.query(
    sql: String,
    f: (Row, RowMetadata) -> R,
    prepare: Statement.() -> Unit = {}
): Flow<R> = flow {
    resourceScope {
        val connection = bind()
        emitAll(connection.createStatement(sql)
            .also(prepare)
            .execute()
            .asFlow()
            .flatMapConcat { it.map(f).asFlow() }
        )
    }
}
