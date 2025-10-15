import net.agl.gradle.versionFromGit

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
version = versionFromGit(project.rootDir.absolutePath)

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven { url = uri(findProperty("repo.proxy.url")!! as String) }
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
        create<MavenPublication>("maven") {
            version = project.version.toString()
            groupId = project.group.toString()
            artifactId = project.name
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "maven"
            url = uri(
                findProperty(
                    if (project.version.toString().endsWith("-SNAPSHOT"))
                        "repo.publish.snapshots"
                    else
                        "repo.publish.releases"
                )!! as String
            )
            credentials {
                username = findProperty("repo.publish.username")!! as String
                password = findProperty("repo.publish.password")!! as String
            }
        }
    }
}
