package net.ancientloader;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Loader {
    private static final Logger LOGGER = LogManager.getLogger();

    public void loadMod(File jar) throws Exception {

        LOGGER.info("loading mod {}", jar.getName());

        URL url = jar.toURI().toURL();

        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{url},
                Loader.class.getClassLoader()
        );

        ServiceLoader<Mod> serviceLoader =
                ServiceLoader.load(Mod.class, classLoader);

        for (Mod mod : serviceLoader) {
            LOGGER.info("loaded mod {}", mod.getName());

            mod.onInitialize();
        }
    }
}
