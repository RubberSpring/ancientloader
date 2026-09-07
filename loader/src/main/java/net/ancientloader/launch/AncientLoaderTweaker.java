package net.ancientloader.launch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

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

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        MixinServiceLaunchWrapper.bootstrap();

        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment()
                .setSide(MixinEnvironment.Side.CLIENT);

        for (File mod : findMods()) {
            try {
                classLoader.addURL(mod.toURI().toURL());
                registerMixinConfigs(mod);
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not load mod " + mod, exception);
            }
        }
    }


    @Override
    public String getLaunchTarget() {
        return "com.mojang.rubydung.RubyDung";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    private static List<File> findMods() {
        File directory = new File("mods");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create mod directory: "
                    + directory.getAbsolutePath());
        }
        File[] files = directory.listFiles();
        if (files == null) return Collections.emptyList();

        List<File> mods = new ArrayList<File>();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".jar")) mods.add(file);
        }
        return mods;
    }

    private static void registerMixinConfigs(File mod) throws IOException {
        JarFile jar = new JarFile(mod);
        try {
            Attributes attributes = jar.getManifest() == null ? null
                    : jar.getManifest().getMainAttributes();
            if (attributes == null) return;
            String configs = attributes.getValue(MIXIN_CONFIGS);
            if (configs == null || configs.trim().isEmpty()) return;
            for (String config : configs.split(",")) {
                String name = config.trim();
                if (!name.isEmpty()) Mixins.addConfiguration(name);
            }
        } finally {
            jar.close();
        }
    }
}
