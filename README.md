[![Build status](https://github.com/navikt/syfosmvarsel/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmvarsel/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)

# syfosmvarsel
Application for creating sykmeldingsvarsler

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest
* Jackson

#### Requirements

* JDK 17

#### Build and run tests
To build locally and run the integration tests you can simply run
``` bash
./gradlew shadowJar
``` 
or on windows 
`gradlew.bat shadowJar`


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
