package net.ancientloader.bootstrap;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import net.ancientloader.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Entry point injected at the beginning of {@code RubyDung.init()}. */
public final class AncientLoaderBootstrap {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean initialized;

    private AncientLoaderBootstrap() {
    }

    public static String getStackTrace(Throwable throwable) {

        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);

        throwable.printStackTrace(printer);

        printer.flush();

        return writer.toString();
    }


    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        LOGGER.info("Minecraft AncientLoader 0.1.0, loading mods");
        File mods = new File("mods");
        if (!mods.exists() && !mods.mkdirs()) {
            throw new IllegalStateException("Could not create mod directory: " + mods.getAbsolutePath());
        }
        File[] files = mods.listFiles();
        if (files == null) System.out.println("no mods found, stopping loader...");
        Loader loader = new Loader();
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) continue;
            try {
                loader.loadMod(file);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load mod " + file.getName(), exception);
            }
        }
    }
}
