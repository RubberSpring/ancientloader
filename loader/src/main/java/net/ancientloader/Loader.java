package net.ancientloader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;

public class Loader {
    public void loadMod(File jar) throws Exception {

        System.out.println("loading mod " + jar.getName());

        URL url = jar.toURI().toURL();

        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{url},
                Loader.class.getClassLoader()
        );

        ServiceLoader<Mod> serviceLoader =
                ServiceLoader.load(Mod.class, classLoader);

        for (Mod mod : serviceLoader) {
            System.out.println("loaded mod " + mod.getName());

            mod.onInitialize();
        }
    }
}
