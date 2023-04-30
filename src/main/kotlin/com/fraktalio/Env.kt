package com.fraktalio

import java.lang.System.getenv

private const val PORT: Int = 8080
private const val JDBC_URL: String = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON;"
private const val JDBC_USER: String = "root"
private const val JDBC_PW: String = ""
private const val JDBC_DRIVER_CLASS_NAME: String = "org.h2.Driver"


data class Env(
    val dataSource: DataSource = DataSource(),
    val http: Http = Http()
) {
    data class DataSource(
        val jdbcUrl: String = getenv("JDBC_URL") ?: JDBC_URL,
        val username: String = getenv("JDBC_USERNAME") ?: JDBC_USER,
        val password: String = getenv("JDBC_PASSWORD") ?: JDBC_PW,
        val driverClassName: String = getenv("JDBC_DRIVER_CLASS_NAME") ?: JDBC_DRIVER_CLASS_NAME,
    )

    data class Http(
        val host: String = getenv("HOST") ?: "0.0.0.0",
        val port: Int = getenv("SERVER_PORT")?.toIntOrNull() ?: PORT,
    )
}