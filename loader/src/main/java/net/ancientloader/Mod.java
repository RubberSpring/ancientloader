package net.ancientloader;

/**
 * Interface that provides basic API for mods.
 *
 * <p>A mod's main class must implement this.</p>
 */
public interface Mod {
    /**
     * Get the mod's name.
     * @return the mod's name
     */
    String getName();

    /**
     * This method is called when the mod is initialized.
     *
     * <p>Note: this will run before mixins are loaded.</p>
     */
    void onInitialize();
}
