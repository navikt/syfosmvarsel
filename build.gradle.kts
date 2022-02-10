import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.6.0"
val kluentVersion = "1.68"
val ktorVersion = "1.6.7"
val logbackVersion = "1.2.10"
val prometheusVersion = "0.14.1"
val spekVersion = "2.0.17"
val logstashEncoderVersion = "7.0.1"
val kafkaVersion = "2.8.0"
val jacksonVersion = "2.13.1"
val smCommonVersion = "1.a92720c"
val avroVersion = "1.8.2"
val confluentVersion = "6.2.2"
val postgresVersion = "42.3.2"
val flywayVersion = "8.4.3"
val hikariVersion = "5.0.1"
val vaultJavaDriveVersion = "3.1.0"
val brukernotifikasjonAvroVersion = "1.2021.06.21-08.21-7998a39f216a"
val mockkVersion = "1.12.2"
val kotlinVersion = "1.6.0"
val testContainerVersion = "1.16.3"

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.0"
    id("org.jmailen.kotlinter") version "3.6.0"
    id("com.diffplug.spotless") version "5.16.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("com.github.navikt:brukernotifikasjon-schemas:$brukernotifikasjonAvroVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")

    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.bettercloud:vault-java-driver:$vaultJavaDriveVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty") // conflicts with WireMock
    }
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("org.testcontainers:kafka:$testContainerVersion")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }

    api("io.ktor:ktor-client-mock:$ktorVersion")
    api("io.ktor:ktor-client-mock-jvm:$ktorVersion")

}


tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.syfosmvarsel.BootstrapKt"
    }

    create("printVersion") {

        doLast {
            println(project.version)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging {
            showStandardStreams = true
        }
        maxHeapSize = "512m"
    }

    "check" {
        dependsOn("formatKotlin")
    }
}
