import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.1"

val coroutinesVersion = "1.2.1"
val kluentVersion = "1.39"
val ktorVersion = "1.2.1"
val logbackVersion = "1.2.3"
val prometheusVersion = "0.5.0"
val spekVersion = "2.0.4"
val logstashEncoderVersion = "5.1"
val kafkaVersion = "2.0.0"
val jacksonVersion = "2.9.7"
val syfosmCommonModelsVersion = "1.0.20"
val micrometerVersion = "1.1.4"
val kotlinxSerializationVersion= "0.9.0"
val kafkaEmbeddedVersion = "2.1.1"

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.31"
    id("org.jmailen.kotlinter") version "1.26.0"
    id("com.diffplug.gradle.spotless") version "3.14.0"
    id("com.github.johnrengelman.shadow") version "4.0.3"
}


repositories {
    mavenCentral()
    jcenter()
    maven ( url = "https://dl.bintray.com/kotlin/ktor")
    maven ( url = "http://packages.confluent.io/maven/")
    maven ( url = "https://repo.adeo.no/repository/maven-releases/")
    maven ( url =  "https://dl.bintray.com/spekframework/spek-dev")
    maven ( url = "https://kotlin.bintray.com/kotlinx")
}


dependencies {
    implementation(kotlin("stdlib"))

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation ("io.ktor:ktor-metrics-micrometer:$ktorVersion")
    implementation ("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation ("io.ktor:ktor-client-apache:$ktorVersion")
    implementation ("io.ktor:ktor-client-logging:$ktorVersion")
    implementation ("io.ktor:ktor-client-logging-jvm:$ktorVersion")

    implementation ("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation ("no.nav.syfo.sm:syfosm-common-models:$syfosmCommonModelsVersion")
    implementation ("no.nav.syfo.sm:syfosm-common-kafka:$syfosmCommonModelsVersion")

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    compile ("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationVersion")
    compile ("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:$kotlinxSerializationVersion")

    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation ("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")

    testRuntimeOnly ("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    testRuntimeOnly ("org.spekframework.spek2:spek-runner-junit5:$spekVersion")

    api ("io.ktor:ktor-client-mock:$ktorVersion")
    api ("io.ktor:ktor-client-mock-jvm:$ktorVersion")

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
        kotlinOptions.jvmTarget = "1.8"
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
    }

}