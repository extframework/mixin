import dev.extframework.gradle.common.archives
import dev.extframework.gradle.common.extFramework

plugins {
    kotlin("jvm") version "2.0.21"
    id("dev.extframework.common") version "1.0.45"
}

group = "dev.extframework"
version = "1.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    extFramework()
}

dependencies {
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jgrapht:jgrapht-core:1.5.2")
    testImplementation("org.jgrapht:jgrapht-ext:1.5.2")
    archives(configurationName = "testImplementation")
    testImplementation("dev.extframework:archives:1.5-SNAPSHOT")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.17.0")
}

tasks.test {
    useJUnitPlatform()

}

kotlin {
    jvmToolchain(8)
    explicitApi()
}


common {
    defaultJavaSettings()
    publishing {
        repositories {
            extFramework(credentials = propertyCredentialProvider)
        }

        publication {
            withJava()
            withSources()
            withDokka()

            commonPom {
                packaging = "jar"

                withExtFrameworkRepo()
                defaultDevelopers()
                gnuLicense()
                extFrameworkScm("mixins")
            }
        }
    }
}
