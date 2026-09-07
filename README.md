# AncientLoader

A WIP Minecraft mod loader for version rd-132211

## Building

Build the loader and a patched local client jar with:

```text
gradle build -PminecraftJar=/absolute/path/to/client.jar
```

Outputs:

- `build/libs/ancientloader-<version>.jar`: loader/mod runtime jar.
- `build/libs/minecraft-ancientloader.jar`: local patched client jar.

Run `gradle decompileMinecraft` if you need the game's source.

## Mixins

AncientLoader uses SpongePowered Mixin through LaunchWrapper. Use
`gradle :loader:runMinecraft` for local development: it starts the game with
`net.ancientloader.launch.AncientLoaderTweaker`, which discovers mod JARs in
`mods/` and registers the mixin configuration names listed in each JAR's
`AncientLoader-MixinConfigs` manifest attribute before RubyDung is loaded.

Prism installations must likewise launch through LaunchWrapper with that
tweaker; starting `com.mojang.rubydung.RubyDung` directly cannot apply mixins.
