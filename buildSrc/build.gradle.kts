plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    maven { url = uri(findProperty("repo.proxy.url")!! as String) }
}

dependencies {
    implementation("net.agl.gradle:version-from-git:2.0.0-SNAPSHOT")
}
