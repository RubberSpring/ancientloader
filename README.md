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
