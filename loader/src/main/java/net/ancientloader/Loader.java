package net.ancientloader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;
import net.ancientloader.mixin.MixinManager;

public class Loader {
    public void loadMod(File jar) throws Exception {

        System.out.println("Loading mod " + jar.getName());

        URL url = jar.toURI().toURL();

        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{url},
                Loader.class.getClassLoader()
        );

        MixinManager.discover(classLoader, jar);

        ServiceLoader<Mod> serviceLoader =
                ServiceLoader.load(Mod.class, classLoader);

        for (Mod mod : serviceLoader) {
            System.out.println("Loaded mod " + mod.getName());

            mod.onInitialize();
        }

        MixinManager.applyPending();
    }
}
