package dev.dediamondpro.resourcify;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import dev.dediamondpro.resourcify.config.Config;

@Mod(
    modid = VintageResourcify.MODID,
    version = Tags.VERSION,
    name = "VintageResourcify",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    guiFactory = "dev.dediamondpro.resourcify.gui.config.ResourcifyGuiFactory",
    customProperties = { @Mod.CustomProperty(k = "license", v = "LGPLv3+SNEED"),
        @Mod.CustomProperty(
            k = "issueTrackerUrl",
            v = "https://github.com/JackOfNoneTrades/VintageResourcify/issues") })
public class VintageResourcify {

    public static final String MODID = "resourcify";
    public static final String MODGROUP = "dev.dediamondpro.resourcify";
    public static final Logger LOG = LogManager.getLogger(MODID);

    private static boolean DEBUG_MODE;

    @SidedProxy(clientSide = MODGROUP + ".ClientProxy", serverSide = MODGROUP + ".CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        String debugVar = System.getenv("MCMODDING_DEBUG_MODE");
        DEBUG_MODE = debugVar != null;
        VintageResourcify.LOG.info("MCMODDING_DEBUG_MODE env var: {}", DEBUG_MODE);
        proxy.preInit(event);
        VintageResourcify.LOG.info(
            "debugMode config option: {}",
            Config.Companion.getInstance()
                .getDebugMode());
        VintageResourcify.LOG.info("isDebugMode: {}", isDebugMode());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    public static boolean isDebugMode() {
        return DEBUG_MODE || Config.Companion.getInstance()
            .getDebugMode();
    }

    public static void debug(String message) {
        if (isDebugMode()) {
            LOG.info("DEBUG: {}", message);
        }
    }
}
