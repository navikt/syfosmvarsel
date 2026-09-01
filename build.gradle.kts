import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "no.nav.syfo"
version = "1.0.0"

val javaVersion = JvmTarget.JVM_25

val coroutinesVersion = "1.10.2"
val kluentVersion = "1.73"
val ktorVersion = "3.5.2"
val logbackVersion = "1.6.3"
val prometheusVersion = "0.16.0"
val kotestVersion = "6.2.4"
val logstashEncoderVersion = "9.0"
val kafkaVersion = "4.3.1"
val jacksonVersion = "3.2.2"
val postgresVersion = "42.7.7"
val flywayVersion = "11.10.1"
val hikariVersion = "6.3.0"
val mockkVersion = "1.14.4"
val kotlinVersion = "2.4.10"
val testcontainerVersion = "2.0.3"
val ktfmtVersion = "0.56"
val opentelemetryVersion = "2.17.0"
val varselVersion = "2.1.1"


plugins {
    id("application")
    kotlin("jvm") version "2.4.10"
    id("com.diffplug.spotless") version "8.10.1"
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
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson3:$ktorVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("tools.jackson.module:jackson-module-kotlin:${jacksonVersion}")


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
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainerVersion")
    testImplementation("org.testcontainers:testcontainers-kafka:$testcontainerVersion")
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
