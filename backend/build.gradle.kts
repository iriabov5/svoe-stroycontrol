import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.rshb"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation(platform("software.amazon.awssdk:bom:2.59.1"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:url-connection-client")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    enabled = false
}

// Javadoc читает исходники без Lombok и ошибочно считает сгенерированные конструкторы
// неописанными default-конструкторами. Ссылки, теги и HTML продолжают проверяться doclint.
tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
}

// Один форматтер для локальной разработки и проверки CI; код приложения не запускается.
val javaFormatter = configurations.create("javaFormatter")

dependencies {
    javaFormatter("com.google.googlejavaformat:google-java-format:2.0.0:all-deps")
}

fun JavaExec.configureJavaFormatter() {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    listOf("api", "file", "parser", "tree", "util", "code").forEach { part ->
        jvmArgs("--add-exports=jdk.compiler/com.sun.tools.javac.$part=ALL-UNNAMED")
    }
    args("--aosp", "--skip-reflowing-long-strings", "--replace")
}

// AOSP объединяет короткие аннотации полей; проект явно требует отдельные строки.
fun separateFieldAnnotations(source: String): String {
    val pattern = Regex("""(?m)^([ \t]*)((?:@[\w.]+[ \t]+)+)([^@\n]+;)[ \t]*$""")
    return pattern.replace(source) { match ->
        val indent = match.groupValues[1]
        match.groupValues[2].trim().split(Regex("""\s+"""))
            .joinToString("\n") { indent + it } + "\n" + indent + match.groupValues[3]
    }
}

val javaSources = fileTree("src") { include("**/*.java") }.files.sortedBy { it.path }

tasks.register<JavaExec>("javaFormat") {
    group = "formatting"
    description = "Применяет отступ 4 пробела и отдельные строки аннотаций"
    configureJavaFormatter()
    args(javaSources.map { it.absolutePath })
    doLast {
        javaSources.forEach { it.writeText(separateFieldAnnotations(it.readText())) }
    }
}

tasks.register<JavaExec>("javaFormatCheck") {
    group = "verification"
    description = "Проверяет форматирование Java без изменения исходников"
    configureJavaFormatter()
    doFirst {
        javaSources.forEach { source ->
            val copy = temporaryDir.resolve(source.relativeTo(projectDir).path)
            copy.parentFile.mkdirs()
            source.copyTo(copy, overwrite = true)
            args(copy.absolutePath)
        }
    }
    doLast {
        val offenders = javaSources.filter { source ->
            val copy = temporaryDir.resolve(source.relativeTo(projectDir).path)
            separateFieldAnnotations(copy.readText()) != source.readText()
        }
        check(offenders.isEmpty()) {
            "Запустите ./gradlew javaFormat:\n" + offenders.joinToString("\n") { it.relativeTo(projectDir).path }
        }
    }
}

tasks.register("javaImportCheck") {
    group = "verification"
    description = "Проверяет обычные импорты вместо полных имён типов"
    doLast {
        val qualifiedType = Regex("""\b(?:java|javax|jakarta|org|com|ru|tools|lombok)(?:\.[a-z_][a-z0-9_]*)+\.[A-Z][A-Za-z0-9_]*""")
        val importLine = Regex("""(?m)^import (?!static )([\w.]+);""")
        val offenders = fileTree("src") { include("**/*.java") }.files.flatMap { source ->
            val content = source.readText()
            val imports = importLine.findAll(content).map { it.groupValues[1] }.toSet()
            content.lineSequence().withIndex().filter { (_, line) ->
                !line.trimStart().startsWith("import ") && !line.trimStart().startsWith("package ")
            }.flatMap { (index, line) ->
                qualifiedType.findAll(line).filter { match ->
                    val shortName = match.value.substringAfterLast('.')
                    imports.none { it.endsWith(".$shortName") && it != match.value }
                }.map { "${source.relativeTo(projectDir)}:${index + 1}: ${it.value}" }
            }.toList()
        }
        check(offenders.isEmpty()) { "Замените полные имена типов импортами:\n" + offenders.joinToString("\n") }
    }
}

tasks.named("check") { dependsOn("javaFormatCheck", "javaImportCheck") }
