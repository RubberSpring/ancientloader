plugins {
    java
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

dependencies {
    implementation(project(":loader"))

    compileOnly(project(
            path = ":loader",
            configuration = "rd132211MinecraftCompile"
    ))

    compileOnly(project(":core"))

    // mixins, pretty much essential
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")

    // uncomment the code below to enable mixinextras
    //compileOnly("io.github.llamalad7:mixinextras-common:0.5.5")

    // logging
    compileOnly("org.apache.logging.log4j:log4j-api:2.17.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.1")

    // lwjgl libraries, required for custom rendering/windowing code
    compileOnly("org.lwjgl.lwjgl:lwjgl:2.9.3")
    compileOnly("org.lwjgl.lwjgl:lwjgl_util:2.9.3")
}

tasks.jar {
    manifest {
        // mixin config file
        attributes("AncientLoader-MixinConfigs" to "mixins.examplemod.json")
    }
}
