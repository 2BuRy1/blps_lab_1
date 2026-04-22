plugins {
    `java-library`
}

group = "com.example"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("jakarta.resource:jakarta.resource-api:2.1.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

val rar by tasks.registering(Zip::class) {
    dependsOn(tasks.jar)
    archiveFileName.set("bitrix24-ra.rar")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    from("src/main/rar")
    from(tasks.jar) {
        into("lib")
    }
}

tasks.assemble {
    dependsOn(rar)
}
