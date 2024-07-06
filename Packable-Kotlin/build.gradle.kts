
plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "io.github"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":packable-kotlin"))
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("MainKt")
}