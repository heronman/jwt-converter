import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("java-library")
    id("idea")
    id("maven-publish")
}

fun versionFromScript(): String {
    val fromScript = {
        val stdout = ByteArrayOutputStream()
        exec {
            workingDir = project.rootDir
            commandLine = listOf("bash", "version.sh")
            standardOutput = stdout
        }
        stdout.toString().trim()
    }

    return File(project.rootDir, ".version")
        .let {
            if (it.exists()) it.readLines(Charset.defaultCharset())
                .map(String::trim)
                .filter(String::isNotEmpty)
                .firstOrNull { !it.startsWith("#") }
            else null
        }
        ?: System.getenv("VERSION")?.ifBlank { null }
        ?: fromScript()
}

group = "net.agl.security"
version = versionFromScript()

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

val publishVersion = if (version.toString().contains("SNAPSHOT"))
    version.toString()
else Regex("^(\\d+\\.\\d+\\.\\d+)(.+)?\$")
    .find(version.toString())
    ?.let { it.groups[1]?.value + (it.groups[2]?.let { "-SNAPSHOT" } ?: "") }
    ?: ""

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = project.name
            version = publishVersion
        }
    }

    repositories {
        maven {
            name = "nexus"
            url = uri(
                if (publishVersion.contains("SNAPSHOT"))
                    project.findProperty("maven.publish.snapshots") as? String
                        ?: System.getenv("MAVEN_PUBLISH_SNAPSHOTS")
                else
                    project.findProperty("maven.publish.releases") as? String
                        ?: System.getenv("MAVEN_PUBLISH_RELEASES")
            )
            credentials {
                username =
                    project.findProperty("maven.publish.username") as? String
                        ?: System.getenv("MAVEN_PUBLISH_USERNAME")
                password =
                    project.findProperty("maven.publish.password") as? String
                        ?: System.getenv("MAVEN_PUBLISH_PASSWORD")
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs = listOf("-javaagent:${mockitoAgent.asPath}") + (jvmArgs ?: listOf())
}
