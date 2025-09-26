import org.gradle.internal.extensions.stdlib.capitalized

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("java-library")
    id("idea")
    id("maven-publish")
}

group = "net.agl.security"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect")
    //
    testImplementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    //
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.mockito)
    mockitoAgent(libs.mockito) { isTransitive = false }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        jvmArgs?.add(0, "-javaagent:${mockitoAgent.asPath}")
    }
}

publishing {
    publications {
        create<MavenPublication>("aglNexus") {
            val projectVersion = project.version.toString()
            version = if (projectVersion.endsWith("-SNAPSHOT")) projectVersion
            else Regex("""^(\d+\.\d+\.\d+).+$""").matchEntire(projectVersion)?.groupValues?.get(1)
                ?.plus("-SNAPSHOT")
                ?: projectVersion
            groupId = project.group.toString()
            artifactId = project.name
            from(components["java"])
        }
    }

    repositories {
        val username = findProperty("agl.repo.publish.username")!! as String
        val password = findProperty("agl.repo.publish.password")!! as String
        listOf("agl.repo.url.releases", "agl.repo.url.snapshots").forEach {
            maven {
                name = "aglNexus${it.split(".").last().capitalized()}"
                url = uri(findProperty(it)!! as String)
                credentials {
                    this.username = username
                    this.password = password
                }
            }
        }
    }
}

extra["versionSet"] = false

gradle.taskGraph.whenReady {
    if (hasTask(":classes") && extra["versionSet"] == false) {
        version = providers.exec {
            commandLine("bash", "version.sh")
        }.standardOutput.asText.get().trim()
    }
}

afterEvaluate {
    if (gradle.startParameter.taskNames.contains("publish")) {
        version = providers.exec {
            commandLine("bash", "version.sh", "-s")
        }.standardOutput.asText.get().trim()
        extra["versionSet"] = true

        (publishing.publications["aglNexus"] as MavenPublication).version = version.toString()

        if (version.toString().endsWith("-SNAPSHOT")) {
            tasks.named("publish") {
                setDependsOn(listOf("publishAglNexusPublicationToAglNexusSnapshotsRepository"))
            }
        } else {
            tasks.named("publish") {
                setDependsOn(listOf("publishAglNexusPublicationToAglNexusReleasesRepository"))
            }
        }
    }
}
