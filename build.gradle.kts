buildscript {
	repositories {
		mavenCentral()
	}
	dependencies {
		// Flyway 10.x requires database-specific modules on plugin classpath
		classpath("org.flywaydb:flyway-database-postgresql:10.21.0")
		classpath("org.postgresql:postgresql:42.7.4")
	}
}

plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.9-SNAPSHOT"
	id("io.spring.dependency-management") version "1.1.7"
	id("nu.studer.jooq") version "8.2"
	id("io.gitlab.arturbosch.detekt") version "1.23.4"
	id("org.openapi.generator") version "7.10.0"
	id("org.flywaydb.flyway") version "10.21.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "Demo project for Spring Boot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

extra["jooq.version"] = "3.19.28"

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-jooq")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.flywaydb:flyway-core:10.21.0")
	implementation("org.flywaydb:flyway-database-postgresql:10.21.0")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.postgresql:postgresql:42.7.4")
	jooqGenerator("org.postgresql:postgresql:42.7.4")

	// OpenAPI annotations
	implementation("io.swagger.core.v3:swagger-annotations:2.2.27")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

	developmentOnly("org.springframework.boot:spring-boot-devtools")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// Kotest
	testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
	testImplementation("io.kotest:kotest-assertions-core:5.9.1")
	testImplementation("io.kotest:kotest-property:5.9.1")
	testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
		exclude(group = "org.mockito", module = "mockito-core")
	}
	testImplementation("io.mockk:mockk:1.13.12")
	testImplementation("com.ninja-squad:springmockk:4.0.2")

    implementation(kotlin("stdlib-jdk8"))
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

jooq {
	configurations {
		create("main") {
			jooqConfiguration.apply {
				jdbc.apply {
					driver = "org.postgresql.Driver"
					url = "jdbc:postgresql://localhost:5433/simple_ec"
					user = "postgres"
					password = "postgres"
				}
				generator.apply {
					name = "org.jooq.codegen.KotlinGenerator"
					database.apply {
						name = "org.jooq.meta.postgres.PostgresDatabase"
						inputSchema = "public"
						excludes = "flyway_schema_history"
					}
					generate.apply {
						isDeprecated = false
						isRecords = true
						isFluentSetters = true
						isKotlinNotNullPojoAttributes = true
						isKotlinNotNullRecordAttributes = true
						isKotlinNotNullInterfaceAttributes = true
					}
					target.apply {
						packageName = "com.example.simple_ec_backend.infrastructure.jooq"
						directory = "build/generated-src/jooq/main"
					}
					strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
				}
			}
		}
	}
}

tasks.named<nu.studer.gradle.jooq.JooqGenerate>("generateJooq") {
	allInputsDeclared.set(true)
	dependsOn("flywayMigrate")
}

detekt {
	buildUponDefaultConfig = true
	allRules = false
	config.setFrom(files("$projectDir/detekt.yml"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
	reports {
		html.required.set(true)
		xml.required.set(false)
		txt.required.set(false)
		sarif.required.set(false)
	}
}

openApiGenerate {
	generatorName.set("kotlin-spring")
	inputSpec.set("$rootDir/src/main/resources/openapi/api.yaml")
	outputDir.set("$buildDir/generated/openapi")
	apiPackage.set("com.example.simple_ec_backend.presentation.api")
	modelPackage.set("com.example.simple_ec_backend.presentation.model")
	configOptions.set(mapOf(
		"interfaceOnly" to "true",
		"useSpringBoot3" to "true",
		"useTags" to "true",
		"serializationLibrary" to "jackson",
		"enumPropertyNaming" to "UPPERCASE"
	))
}

sourceSets {
	main {
		java {
			srcDir("build/generated-src/jooq/main")
		}
		kotlin {
			srcDir("$buildDir/generated/openapi/src/main/kotlin")
		}
	}
}

tasks.named("compileKotlin") {
	dependsOn("openApiGenerate")
}

flyway {
	url = "jdbc:postgresql://localhost:5433/simple_ec"
	user = "postgres"
	password = "postgres"
	locations = arrayOf("classpath:db/migration")
}
