import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.openapi.generator") version "7.10.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.openapitools:jackson-databind-nullable:0.2.6")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.28")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.6.0")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val generatedDir = layout.buildDirectory.dir("generated")

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set(file("../bank-openapi.yml").absolutePath)
    outputDir.set(generatedDir.get().asFile.absolutePath)
    apiPackage.set("com.example.bank.generated.api")
    modelPackage.set("com.example.bank.generated.model")
    invokerPackage.set("com.example.bank.generated.invoker")
    globalProperties.set(
        mapOf(
            "apiDocs" to "false",
            "modelDocs" to "false"
        )
    )
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "dateLibrary" to "java8",
            "enumPropertyNaming" to "UPPERCASE",
            "useTags" to "true",
            "serializationLibrary" to "jackson"
        )
    )
}

sourceSets {
    main {
        kotlin.srcDir("${generatedDir.get().asFile}/src/main/kotlin")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}
