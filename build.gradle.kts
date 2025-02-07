plugins {
    kotlin("jvm") version "2.0.21"
}

group = "dev.extframework"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.extframework.dev/snapshots")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    testImplementation("org.jgrapht:jgrapht-core:1.5.2")
    testImplementation("org.jgrapht:jgrapht-ext:1.5.2")

    testImplementation("dev.extframework:archives:1.5-SNAPSHOT")
    // https://mvnrepository.com/artifact/net.bytebuddy/byte-buddy-agent
    testImplementation("net.bytebuddy:byte-buddy-agent:1.17.0")
}

tasks.test {
    useJUnitPlatform()

}
kotlin {
    jvmToolchain(21)
    explicitApi()
}