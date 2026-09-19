package dev.example.copycatroller;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(CopycatRoller.MOD_ID)
public final class CopycatRoller {
    public static final String MOD_ID = "copycat_roller";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CopycatRoller(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CopycatRollerConfig.SPEC);
        LOGGER.info("Copycat Roller initialized for Create 6 and Copycats+ 3.0.x");
    }
}
