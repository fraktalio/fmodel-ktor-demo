val fmodel_version: String by project
val kotlin_version: String by project
val coroutines_version: String by project
val ktor_version: String by project
val arrow_version: String by project
val suspendapp_version: String by project
val logback_version: String by project
val postgres_version: String by project
val r2dbc_version: String by project
val prometeus_version: String by project
val kotlinx_collections_immutable_version: String by project

plugins {
    kotlin("jvm") version "1.8.20"
    id("io.ktor.plugin") version "2.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.20"
}


group = "com.fraktalio"
version = "0.0.1"
application {

    kotlin {
        tasks.test {
            useJUnitPlatform()
        }
        jvmToolchain(17)
    }
    ktor {
        docker {
            localImageName.set("fmodel-ktor-demo")
            imageTag.set("0.0.1")
        }
    }
    mainClass.set("com.fraktalio.ApplicationKt")

    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=true"
    )
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fraktalio.fmodel:domain:$fmodel_version")
    implementation("com.fraktalio.fmodel:application-vanilla:$fmodel_version")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:$kotlinx_collections_immutable_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.arrow-kt:arrow-core:$arrow_version")
    implementation("io.arrow-kt:suspendapp:$suspendapp_version")
    implementation("io.arrow-kt:suspendapp-ktor:$suspendapp_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutines_version")
    implementation("io.r2dbc:r2dbc-spi:$r2dbc_version")
    implementation("io.r2dbc:r2dbc-pool:$r2dbc_version")
    implementation("io.r2dbc:r2dbc-postgresql:0.8.13.RELEASE")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cio-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation(kotlin("test"))
}