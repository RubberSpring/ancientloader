package net.ancientloader.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

public class Registry<T> {
    private HashMap<String, T> entries;

    public T get(String key) {
        return entries.get(key);
    }

    public void add(String key, T entry) {
        entries.put(key, entry);
    }

    public void remove(String key) {
        entries.remove(key);
    }

    public Set<String> keySet() {
        return entries.keySet();
    }

    public Collection<T> values() {
        return entries.values();
    }
}