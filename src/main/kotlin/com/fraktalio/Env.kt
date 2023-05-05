package com.fraktalio

import java.lang.System.getenv

private const val PORT: Int = 8080
private const val JDBC_URL: String =
    "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON;"
private const val JDBC_USER: String = "root"
private const val JDBC_PW: String = ""
private const val JDBC_DRIVER_CLASS_NAME: String = "org.h2.Driver"

private const val R2DBC_DRIVER: String = "h2"
private const val R2DBC_PROTOCOL: String = "mem"
private const val R2DBC_USERNAME: String = "root"
private const val R2DBC_PASSWORD: String = ""
private const val R2DBC_DATABASE: String = "test"
private const val R2DBC_HOST: String = "localhost"
private const val R2DBC_PORT: Int = 5432
private const val R2DBC_INITIAL_POOL_SIZE: Int = 10
private const val R2DBC_MAX_POOL_SIZE: Int = 10
private const val R2DBC_MAX_IDLE_TIME: Long = 30_000

data class Env(
    val dataSource: DataSource = DataSource(),
    val r2dbcDataSource: R2DBCDataSource = R2DBCDataSource(),
    val http: Http = Http()
) {
    data class DataSource(
        val jdbcUrl: String = getenv("JDBC_URL") ?: JDBC_URL,
        val username: String = getenv("JDBC_USERNAME") ?: JDBC_USER,
        val password: String = getenv("JDBC_PASSWORD") ?: JDBC_PW,
        val driverClassName: String = getenv("JDBC_DRIVER_CLASS_NAME") ?: JDBC_DRIVER_CLASS_NAME,
    )

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

    data class Http(
        val host: String = getenv("HOST") ?: "0.0.0.0",
        val port: Int = getenv("SERVER_PORT")?.toIntOrNull() ?: PORT,
    )
}