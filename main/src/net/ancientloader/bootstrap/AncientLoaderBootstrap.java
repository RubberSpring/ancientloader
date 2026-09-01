package net.ancientloader.bootstrap;

import java.io.File;
import net.ancientloader.Loader;

/** Entry point injected at the beginning of {@code RubyDung.init()}. */
public final class AncientLoaderBootstrap {
    private static boolean initialized;

    private AncientLoaderBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        System.out.println("Minecraft AncientLoader 0.1.0, loading mods");
        File mods = new File("mods");
        if (!mods.exists() && !mods.mkdirs()) {
            throw new IllegalStateException("Could not create mod directory: " + mods.getAbsolutePath());
        }
        File[] files = mods.listFiles();
        if (files == null) System.out.println("no mods found, stopping loader...");;
        Loader loader = new Loader();
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) continue;
            try {
                System.out.println("loading mod " + file);
                loader.loadMod(file);
                System.out.println("loaded mod successfully: " + file);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load mod " + file.getName(), exception);
            }
        }
    }
}
