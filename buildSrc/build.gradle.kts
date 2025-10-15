plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    maven { url = uri(findProperty("repo.proxy.url")!! as String) }
}

dependencies {
    implementation("net.agl.gradle:version-from-git:1.0.0")
}
