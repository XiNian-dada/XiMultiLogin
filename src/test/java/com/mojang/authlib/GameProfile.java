package com.mojang.authlib;

import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;

public class GameProfile {
    private final UUID id;
    private final String name;
    private final PropertyMap properties;

    public GameProfile(UUID id, String name) {
        this(id, name, new PropertyMap());
    }

    public GameProfile(UUID id, String name, PropertyMap properties) {
        this.id = id;
        this.name = name;
        this.properties = properties != null ? properties : new PropertyMap();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PropertyMap getProperties() {
        return properties;
    }
}
