package net.minepass.gs.mt.wrapper;

import net.minepass.api.gameserver.MPConfig;

public class MTConfig extends MPConfig {
    public MTConfig(String worldPath) {
        super();
        this.variant_config.put("worldpath", worldPath);
    }
}
