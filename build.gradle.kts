val kotlin_version: String by project
val coroutines_version: String by project
val ktor_version: String by project
val arrow_version: String by project
val suspendapp_version: String by project
val logback_version: String by project
val postgres_version: String by project
val h2_version: String by project
val r2dbc_version: String by project
val prometeus_version: String by project
val opentelemetry_version: String by project

plugins {
    kotlin("jvm") version "1.8.20"
    id("io.ktor.plugin") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.20"
}

group = "com.fraktalio"
version = "0.0.1"
application {
    mainClass.set("com.fraktalio.ApplicationKt")

    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=true",
        "-javaagent:./opentelemetry-javaagent.jar",
        "-Dotel.service.name=fmodel-ktor-demo",
        "-Dotel.traces.exporter=jaeger",
        "-Dotel.exporter.jaeger.endpoint=http://localhost:14250",
        "-Dotel.metrics.exporter=none",
        "-Dotel.instrumentation.experimental.span-suppression-strategy=none",
        "-Dotel.instrumentation.kotlinx-coroutines.enabled=true"
    )
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.arrow-kt:arrow-core:$arrow_version")
    implementation("io.arrow-kt:suspendapp:$suspendapp_version")
    implementation("io.arrow-kt:suspendapp-ktor:$suspendapp_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutines_version")
    implementation("io.r2dbc:r2dbc-spi:$r2dbc_version")
    implementation("io.r2dbc:r2dbc-h2:$r2dbc_version")
    implementation("io.r2dbc:r2dbc-pool:$r2dbc_version")
    implementation("io.r2dbc:r2dbc-postgresql:0.8.13.RELEASE")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktor_version")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometeus_version")
    implementation("io.ktor:ktor-server-cio-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation(platform("io.opentelemetry:opentelemetry-bom:$opentelemetry_version"))
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-extension-kotlin")
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-2.0:1.25.0-alpha")
    implementation("io.ktor:ktor-server-openapi:$ktor_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor_version")
}