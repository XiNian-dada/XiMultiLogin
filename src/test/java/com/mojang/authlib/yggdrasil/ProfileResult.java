package com.mojang.authlib.yggdrasil;

import com.mojang.authlib.GameProfile;

public class ProfileResult {
    private final GameProfile profile;

    public ProfileResult(GameProfile profile) {
        this.profile = profile;
    }

    public GameProfile profile() {
        return profile;
    }

    public GameProfile getProfile() {
        return profile;
    }
}
