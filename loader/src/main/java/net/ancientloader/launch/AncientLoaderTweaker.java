package net.ancientloader.launch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.ancientloader.Loader;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapperBootstrap;

/**
 * Starts Mixin and registers all mod configurations before the game entry
 * class is defined. This class is loaded by LaunchWrapper, not RubyDung.
 */
public final class AncientLoaderTweaker implements ITweaker {
    private static final String MIXIN_CONFIGS = "AncientLoader-MixinConfigs";

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir,
                              String profile) {
    }

    /**
     * Injects mod classes into LaunchClassLoader
     * @param classLoader the LaunchClassLoader
     */
    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        Thread.currentThread().setContextClassLoader(classLoader);

        List<File> mods = findMods();

        for (File mod : mods) {
            try {
                classLoader.addURL(mod.toURI().toURL());
            } catch (IOException e) {
                throw new IllegalStateException(
                        "failed to load mod " + mod, e
                );
            }
        }

        new MixinServiceLaunchWrapperBootstrap().bootstrap();

        MixinBootstrap.init();
        MixinExtrasBootstrap.init();
        MixinEnvironment.getDefaultEnvironment()
                .setSide(MixinEnvironment.Side.CLIENT);

        Loader loader = new Loader();
        for (File mod : mods) {
            try {
                registerMixinConfigs(mod);
                loader.loadMod(mod);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "Could not load mod " + mod, exception);
            }
        }
    }


    /**
     * Tweak launch target
     * @return the target class
     */
    @Override
    public String getLaunchTarget() {
        return "com.mojang.rubydung.RubyDung";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    /**
     * Finds mod jar files
     * @return the list of mod jar files
     */
    private static List<File> findMods() {
        File directory = new File("mods");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create mod directory: "
                    + directory.getAbsolutePath());
        }
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();

        List<File> mods = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".jar")) mods.add(file);
        }
        return mods;
    }

    /**
     * Registers mod Mixin configurations
     * @param mod the mod file
     * @throws IOException when fails to register
     */
    private static void registerMixinConfigs(File mod) throws IOException {
        try (JarFile jar = new JarFile(mod)) {
            Attributes attributes = jar.getManifest() == null ? null
                    : jar.getManifest().getMainAttributes();
            if (attributes == null) return;
            String configs = attributes.getValue(MIXIN_CONFIGS);
            if (configs == null || configs.trim().isEmpty()) return;
            for (String config : configs.split(",")) {
                String name = config.trim();
                if (!name.isEmpty()) Mixins.addConfiguration(name);
            }
        }
    }
}
