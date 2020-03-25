import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.owasp.dependencycheck.reporting.ReportGenerator.Format.ALL
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import java.net.InetAddress
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter.ISO_DATE

plugins {
  kotlin("jvm") version "1.3.71"
  kotlin("plugin.spring") version "1.3.71"
  kotlin("plugin.jpa") version "1.3.71"
  id("org.springframework.boot") version "2.2.5.RELEASE"
  id("io.spring.dependency-management") version "1.0.9.RELEASE"
  id("org.owasp.dependencycheck") version "5.3.1"
  id("com.github.ben-manes.versions") version "0.28.0"
  id("com.gorylenko.gradle-git-properties") version "2.2.2"
  id("se.patrikerdes.use-latest-versions") version "0.2.13"
}

repositories {
  mavenLocal()
  mavenCentral()
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    jvmTarget = "11"
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

dependencyCheck {
  failBuildOnCVSS = 5f
  suppressionFiles = listOf()
  format = ALL
  analyzers.assemblyEnabled = false
}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isStable = stableKeyword || regex.matches(version)
  return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
  rejectVersionIf {
    isNonStable(candidate.version) && !isNonStable(currentVersion)
  }
}

group = "uk.gov.justice.digital.hmpps"

val todaysDate: String = LocalDate.now().format(ISO_DATE)
version = if (System.getenv().contains("BUILD_NUMBER")) System.getenv("BUILD_NUMBER") else todaysDate

springBoot {
  buildInfo {
    properties {
      time = Instant.now()
      additional = mapOf(
          "by" to System.getProperty("user.name"),
          "operatingSystem" to "${System.getProperty("os.name")} (${System.getProperty("os.version")})",
          "machine" to InetAddress.getLocalHost().hostName
      )
    }
  }
}

configurations {
  implementation { exclude(mapOf("module" to "tomcat-jdbc")) }
}

dependencyManagement {
  imports { mavenBom(SpringBootPlugin.BOM_COORDINATES) }
}

dependencies {
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlin:kotlin-reflect")

  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")

  implementation("io.springfox:springfox-swagger2:2.9.2")
  implementation("io.springfox:springfox-swagger-ui:2.9.2")
  implementation("io.springfox:springfox-bean-validators:2.9.2")

  runtimeOnly("org.flywaydb:flyway-core:6.3.1")

  implementation("net.logstash.logback:logstash-logback-encoder:6.3")
  implementation("com.microsoft.azure:applicationinsights-spring-boot-starter:2.5.1")
  implementation("com.microsoft.azure:applicationinsights-logging-logback:2.5.1")
  implementation("com.github.timpeeters:spring-boot-graceful-shutdown:2.2.1")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.3")
  implementation("com.google.guava:guava:28.2-jre")

  testImplementation("org.springframework.boot:spring-boot-starter-test") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }
  testImplementation("org.springframework.boot:spring-boot-starter-webflux")
  testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
  testImplementation("it.ozimov:embedded-redis:0.7.2")
}

tasks {
  test { useJUnitPlatform() }

  val agentDeps by configurations.register("agentDeps") {
    dependencies {
      "agentDeps"("com.microsoft.azure:applicationinsights-agent:2.5.1") {
        isTransitive = false
      }
    }
  }

  val copyAgent by registering(Copy::class) {
    from(agentDeps)
    into("$buildDir/libs")
  }

  assemble { dependsOn(copyAgent) }

  bootJar {
    manifest {
      attributes("Implementation-Version" to rootProject.version, "Implementation-Title" to rootProject.name)
    }
  }
}
