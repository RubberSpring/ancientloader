package net.ancientloader.examplemod;

import net.ancientloader.Mod;

public class Main implements Mod{
    @Override
    public String getName() {
        return "AncientLoader test mod";
    }

    @Override
    public void onInitialize() {
        System.out.println("hello from mod");
    }
}
