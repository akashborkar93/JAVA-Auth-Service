plugins {
    id("org.sonarqube") version "6.0.1.5171"
    id("org.springframework.boot") version "4.1.2" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.example.platform"
    // Overridden by CI (-PappVersion=1.0.<run number>) so every pipeline run publishes a unique version.
    version = (findProperty("appVersion") as String?) ?: "1.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    // Lock resolved versions so Trivy can scan exact dependency versions from gradle.lockfile
    dependencyLocking {
        lockAllConfigurations()
    }
}

// Project settings live in sonar-project.properties; only the server URL and token come from the environment.
val sonarFile = rootProject.file("sonar-project.properties")
val sonarFileProps = java.util.Properties().apply {
    if (sonarFile.exists()) sonarFile.inputStream().use { load(it) }
}

sonar {
    properties {
        sonarFileProps.forEach { (key, value) -> property(key.toString(), value.toString()) }
        System.getenv("SONAR_HOST_URL")?.takeIf { it.isNotBlank() }?.let { property("sonar.host.url", it) }
        System.getenv("SONAR_TOKEN")?.takeIf { it.isNotBlank() }?.let { property("sonar.token", it) }
    }
}
