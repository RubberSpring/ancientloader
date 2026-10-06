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

    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")

    // isn't needed but included for correctness
    //compileOnly("io.github.llamalad7:mixinextras-common:0.5.5")

    compileOnly("org.apache.logging.log4j:log4j-api:2.17.1")
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.1")
}

tasks.jar {
    manifest {
        attributes("AncientLoader-MixinConfigs" to "mixins.core.json")
    }
}
