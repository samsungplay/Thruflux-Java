plugins {
    id("java")
    id("application")
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