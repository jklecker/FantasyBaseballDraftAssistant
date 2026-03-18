plugins {
    id("org.springframework.boot") version "3.2.4"
    // id("io.spring.dependency-management") version "1.1.4" // Removed for Gradle 9.x compatibility
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.2.4")
    // Thymeleaf removed — React SPA is served as static resources from /static
    implementation("com.opencsv:opencsv:5.9")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.4")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Bundle the Vite production build into the Spring Boot JAR as static resources.
// In Docker, the frontend/build dir is populated by Stage 1 before Gradle runs.
// Locally, run `npm run build` in /frontend first, then `./gradlew bootJar`.
tasks.named<Copy>("processResources") {
    from("frontend/build") {
        into("static")
    }
}

tasks.test {
    useJUnitPlatform()
}

