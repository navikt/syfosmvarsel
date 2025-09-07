[![Build status](https://github.com/navikt/syfosmvarsel/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmvarsel/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# syfosmvarsel
Application for creating sykmeldingsvarsler

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest
* Jackson
* Docker

### :scroll: Prerequisites
* JDK 21
  Make sure you have the Java JDK 21 installed
  You can check which version you have installed using this command:
``` shell
java -version
```

* Docker
  Make sure you have the Docker installed
  You can check which version you have installed using this command:
``` shell
docker -version
```

#### Build and run tests
To build locally and run the integration tests you can simply run
``` bash
./gradlew installDist
``` 
or on windows 
`gradlew.bat installDist`


### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

``` bash
./gradlew wrapper --gradle-version $gradleVersjon
```

### Contact

This project is maintained by [navikt/teamsykmelding](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/syfosmvarsel/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)
