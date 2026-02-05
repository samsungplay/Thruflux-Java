import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.application
import org.gradle.kotlin.dsl.invoke

plugins {
    id("java")
    id("application")
}


tasks.named<Jar>("jar") {
    manifest { attributes["Implementation-Title"] = "thruflux" }
}

val serverStartScripts = tasks.register<CreateStartScripts>("serverStartScripts") {
    applicationName = "thruflux-server"
    mainClass.set("server.ServerEntryPoint")
    classpath = files(tasks.named("jar"), configurations.runtimeClasspath)
    outputDir = layout.buildDirectory.dir("tmp/serverScripts").get().asFile
}

tasks.register<Sync>("serverInstall") {
    dependsOn(tasks.named("jar"), serverStartScripts)

    into(layout.buildDirectory.dir("install/thruflux-server"))

    from(serverStartScripts) { into("bin") }

    from(tasks.named("jar")) { into("lib") }
    from(configurations.runtimeClasspath) { into("lib") }
}

val senderStartScripts = tasks.register<CreateStartScripts>("senderStartScripts") {
    applicationName = "thruflux-sender"
    mainClass.set("sender.SenderEntryPoint")
    classpath = files(tasks.named("jar"), configurations.runtimeClasspath)
    outputDir = layout.buildDirectory.dir("tmp/senderScripts").get().asFile
}

tasks.register<Sync>("senderInstall") {
    dependsOn(tasks.named("jar"), senderStartScripts)

    into(layout.buildDirectory.dir("install/thruflux-sender"))
    from(senderStartScripts) { into("bin") }
    from(tasks.named("jar")) { into("lib") }
    from(configurations.runtimeClasspath) { into("lib") }
}

val receiverStartScripts = tasks.register<CreateStartScripts>("receiverStartScripts") {
    applicationName = "thruflux-receiver"
    mainClass.set("receiver.ReceiverEntryPoint")
    classpath = files(tasks.named("jar"), configurations.runtimeClasspath)
    outputDir = layout.buildDirectory.dir("tmp/receiverScripts").get().asFile
}

tasks.register<Sync>("receiverInstall") {
    dependsOn(tasks.named("jar"), receiverStartScripts)

    into(layout.buildDirectory.dir("install/thruflux-receiver"))
    from(receiverStartScripts) { into("bin") }
    from(tasks.named("jar")) { into("lib") }
    from(configurations.runtimeClasspath) { into("lib") }
}

tasks.register("runServer", JavaExec::class) {
    group = "application"
    description = "Run signaling server"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "server.ServerEntryPoint"
}

tasks.register("runSender", JavaExec::class) {
    group = "application"
    description = "Run sender"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "sender.SenderEntryPoint"
}

tasks.register("runReceiver", JavaExec::class) {
    group = "application"
    description = "Run receiver"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "receiver.ReceiverEntryPoint"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    implementation("org.eclipse.jetty:jetty-server:11.0.17")
    implementation("org.eclipse.jetty.websocket:websocket-jetty-server:11.0.17")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.eclipse.jetty.websocket:websocket-jetty-client:11.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("org.jline:jline:3.25.1")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("org.jitsi:ice4j:3.2-5-g26210ef")
    implementation("com.aventrix.jnanoid:jnanoid:2.0.0")
    implementation("tech.kwik:kwik:0.10.8")
}

tasks.test {
    useJUnitPlatform()
}

application {
    applicationDefaultJvmArgs = listOf(
        "-XX:+UseZGC",
        "-XX:+ZGenerational",
    )
}