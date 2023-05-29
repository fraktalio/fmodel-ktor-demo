package com.fraktalio

import java.lang.System.getenv

private const val PORT: Int = 8080

private const val R2DBC_DRIVER: String = "postgres"
private const val R2DBC_PROTOCOL: String = "mem"
private const val R2DBC_USERNAME: String = "postgres"
private const val R2DBC_PASSWORD: String = "postgres"
private const val R2DBC_DATABASE: String = "postgres"
private const val R2DBC_HOST: String = "localhost"
private const val R2DBC_PORT: Int = 5432
private const val R2DBC_INITIAL_POOL_SIZE: Int = 10
private const val R2DBC_MAX_POOL_SIZE: Int = 10
private const val R2DBC_MAX_IDLE_TIME: Long = 30_000

/**
 * Environment variables
 * @property r2dbcDataSource R2DBC data source configuration
 * @property http HTTP configuration
 */
data class Env(
    val r2dbcDataSource: R2DBCDataSource = R2DBCDataSource(),
    val http: Http = Http()
) {
    /**
     * R2DBC data source configuration
     */
    data class R2DBCDataSource(
        val driver: String = getenv("R2DBC_DRIVER") ?: R2DBC_DRIVER,
        val username: String = getenv("R2DBC_USERNAME") ?: R2DBC_USERNAME,
        val password: String = getenv("R2DBC_PASSWORD") ?: R2DBC_PASSWORD,
        val protocol: String = getenv("R2DBC_PROTOCOL") ?: R2DBC_PROTOCOL,
        val database: String = getenv("R2DBC_DATABASE") ?: R2DBC_DATABASE,
        val host: String = getenv("R2DBC_HOST") ?: R2DBC_HOST,
        val port: Int = getenv("R2DBC_PORT")?.toIntOrNull() ?: R2DBC_PORT,
        val initialPoolSize: Int = getenv("R2DBC_INITIAL_POOL_SIZE")?.toIntOrNull() ?: R2DBC_INITIAL_POOL_SIZE,
        val maxPoolSize: Int = getenv("R2DBC_MAX_POOL_SIZE")?.toIntOrNull() ?: R2DBC_MAX_POOL_SIZE,
        val maxIdleTime: Long = getenv("R2DBC_MAX_IDLE_TIME")?.toLongOrNull() ?: R2DBC_MAX_IDLE_TIME
    )

    /**
     * HTTP configuration
     */
    data class Http(
        val host: String = getenv("HOST") ?: "0.0.0.0",
        val port: Int = getenv("SERVER_PORT")?.toIntOrNull() ?: PORT,
    )
}