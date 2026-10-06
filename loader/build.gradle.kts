import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    `java-library`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven("https://repo.carm.cc/repository/maven-public/")
}

configurations {
    create("decompiler")
    create("lwjglNatives")

    named("implementation") {
        extendsFrom(named("shadow").get())
    }
}

dependencies {
    add("shadow", "net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    add("shadow", "io.github.llamalad7:mixinextras-common:0.5.5")

    implementation("org.apache.logging.log4j:log4j-api:2.17.1")
    implementation("org.apache.logging.log4j:log4j-core:2.17.1")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("net.minecraft:launchwrapper:1.12")
    implementation("com.google.guava:guava:33.7.1-jre")

    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-tree:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")

    implementation("org.lwjgl.lwjgl:lwjgl:2.9.3")
    implementation("org.lwjgl.lwjgl:lwjgl_util:2.9.3")

    add("decompiler", "org.vineflower:vineflower:1.10.1")

    add(
        "lwjglNatives",
        "org.lwjgl.lwjgl:lwjgl-platform:2.9.3:natives-windows"
    )
    add(
        "lwjglNatives",
        "org.lwjgl.lwjgl:lwjgl-platform:2.9.3:natives-linux"
    )
    add(
        "lwjglNatives",
        "org.lwjgl.lwjgl:lwjgl-platform:2.9.3:natives-osx"
    )
}

tasks.shadowJar {
    configurations = listOf(project.configurations["shadow"])

    mergeServiceFiles()
}

fun getOs(): String {
    return when {
        Os.isFamily(Os.FAMILY_WINDOWS) -> "win"
        Os.isFamily(Os.FAMILY_MAC) -> "osx"
        Os.isFamily(Os.FAMILY_UNIX) -> "linux"
        else -> throw GradleException(
            "Your OS does not seem to be supported"
        )
    }
}

val clients = mapOf(
    "rd132211" to file("clients/rd-132211.jar"),
    "rd132328" to file("clients/rd-132328.jar"),
    "rd20090515" to file("clients/rd-20090515.jar"),
    "rd160052" to file("clients/rd-160052.jar"),
    "rd161348" to file("clients/rd-161348.jar")
)

tasks.register<Sync>("extractLwjglNatives") {
    val os = getOs()

    from(configurations["lwjglNatives"].map { zipTree(it) })

    into(layout.buildDirectory.dir("natives/$os"))

    include("*.dll")
    include("*.so")
    include("*.dylib")
}

tasks.register<Copy>("copyCore") {
    dependsOn(project(":core").tasks.named("build"))

    outputs.upToDateWhen { false }

    from(
        project(":core")
            .tasks.named<Jar>("jar")
            .flatMap { it.archiveFile }
    )

    into(layout.projectDirectory.dir("mods"))
}

tasks.register<Copy>("copyMod") {
    dependsOn(project(":examplemod").tasks.named("build"))

    outputs.upToDateWhen { false }

    from(
        project(":examplemod")
            .tasks.named<Jar>("jar")
            .flatMap { it.archiveFile }
    )

    into(layout.projectDirectory.dir("mods"))
}

clients.forEach { (clientName, minecraftJar) ->
    val sourceSet = sourceSets.create(clientName)

    val decompiledDir = layout.buildDirectory.dir(
        "generated/$clientName/minecraft-source"
    )

    val decompileTask = tasks.register<JavaExec>(
        "decompile${clientName.replaceFirstChar { it.uppercase() }}"
    ) {
        group = "minecraft"
        description = "Decompiles ${minecraftJar.name}"

        classpath = configurations["decompiler"]

        mainClass.set(
            "org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler"
        )

        inputs.file(minecraftJar)
        outputs.dir(decompiledDir)

        doFirst {
            if (!minecraftJar.isFile) {
                throw GradleException(
                    "Minecraft JAR does not exist: $minecraftJar"
                )
            }

            decompiledDir.get().asFile.mkdirs()
        }

        args(
            "-dgs=1",
            minecraftJar.absolutePath,
            decompiledDir.get().asFile.absolutePath
        )
    }

    sourceSet.java.srcDir(decompiledDir)

    configurations.named("${clientName}Implementation") {
        extendsFrom(configurations["implementation"])
    }

    configurations.named("${clientName}CompileOnly") {
        extendsFrom(configurations["compileOnly"])
    }

    configurations.named("${clientName}RuntimeOnly") {
        extendsFrom(configurations["runtimeOnly"])
    }

    tasks.named(sourceSet.compileJavaTaskName) {
        dependsOn(decompileTask)
    }

    configurations.create("${clientName}MinecraftCompile") {
        isCanBeConsumed = true
        isCanBeResolved = false
    }

    dependencies {
        add(
            "${clientName}MinecraftCompile",
            files(sourceSet.output.classesDirs)
        )
    }

    tasks.register<Jar>("${clientName}Jar") {
        group = "build"
        description = "Builds $clientName.jar"

        dependsOn(sourceSet.classesTaskName)
        dependsOn("copyCore")
        dependsOn("copyMod")

        archiveFileName.set("$clientName.jar")

        from(sourceSet.output)
        from(sourceSets.main.get().output)
        from(sourceSets.main.get().resources)

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }
}

tasks.register("buildClients") {
    group = "build"
    description = "Builds all Minecraft clients"

    dependsOn(tasks.named("build"))

    dependsOn(
        clients.keys.map { "${it}Jar" }
    )
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA"
    )
}

tasks.named<Javadoc>("javadoc") {
    source(fileTree("src/main/java"))
}

tasks.register<JavaExec>("runMinecraft") {
    val clientName = providers
        .gradleProperty("client")
        .orNull

    if (clientName == null || clientName !in clients) {
        throw GradleException(
            "missing or invalid -Pclient. clients: ${clients.keys.joinToString(", ")}"
        )
    }

    dependsOn(tasks.named("extractLwjglNatives"))
    dependsOn(tasks.named("${clientName}Jar"))

    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    )

    mainClass.set("net.minecraft.launchwrapper.Launch")

    classpath = files(
        clients[clientName],
        tasks.named<Jar>("${clientName}Jar").get().archiveFile,
        configurations.runtimeClasspath
    )

    args(
        "--tweakClass",
        "net.ancientloader.launch.AncientLoaderTweaker"
    )

    systemProperty(
        "java.library.path",
        file("build/natives/${getOs()}").absolutePath
    )
}
