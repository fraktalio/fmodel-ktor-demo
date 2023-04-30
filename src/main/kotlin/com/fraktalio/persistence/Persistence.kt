package com.fraktalio.persistence

import arrow.fx.coroutines.autoCloseable
import arrow.fx.coroutines.continuations.ResourceScope
import com.fraktalio.Env
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

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