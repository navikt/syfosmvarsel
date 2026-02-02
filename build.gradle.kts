import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_21

val coroutinesVersion = "1.10.2"
val kluentVersion = "1.73"
val ktorVersion = "3.4.0"
val logbackVersion = "1.5.26"
val prometheusVersion = "0.16.0"
val kotestVersion = "6.1.1"
val logstashEncoderVersion = "9.0"
val kafkaVersion = "3.9.1"
val jacksonVersion = "2.21.0"
val postgresVersion = "42.7.9"
val flywayVersion = "11.20.3"
val hikariVersion = "7.0.2"
val mockkVersion = "1.14.7"
val kotlinVersion = "2.3.0"
val testContainerVersion = "1.21.4"
val ktfmtVersion = "0.44"
val opentelemetryVersion = "2.24.0"
val varselVersion = "2.1.1"
//Added due to vulnerabilities
val snappyJavaVersion = "1.1.10.8"


plugins {
    id("application")
    kotlin("jvm") version "2.3.0"
    id("com.diffplug.spotless") version "7.0.4"
}

application {
    mainClass.set("no.nav.syfo.syfosmvarsel.BootstrapKt")
}

kotlin {
    compilerOptions {
        jvmTarget.set(javaVersion)
    }
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    constraints {
        implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
            because("override transient from org.apache.kafka:kafka_2.12")
        }
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")


    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    compileOnly("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:$opentelemetryVersion")
    implementation("no.nav.tms.varsel:kotlin-builder:$varselVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
        exclude(group = "commons-codec")
    }
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("org.testcontainers:kafka:$testContainerVersion")
}


tasks {        
        
    test {
        useJUnitPlatform {}
        testLogging {
            events("passed", "skipped", "failed")
            showStackTraces = true
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }


    spotless {
        kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
        check {
            dependsOn("spotlessApply")
        }
    }
}
