package dev.dediamondpro.resourcify.core;

import org.fentanylsolutions.fentlib.core.FentMixins;
import org.fentanylsolutions.fentlib.util.MiscUtil.Side;
import org.fentanylsolutions.fentlib.util.MixinUtil;
import org.fentanylsolutions.fentlib.util.MixinUtil.Phase;

public class Mixins extends FentMixins {

    private static final Mixins INSTANCE = new Mixins();

    @Override
    protected void registerMixins(MixinUtil.Registry registry) {
        registry.mixin("AbstractResourcePackAccessor")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("GuiMainMenuAccessor")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("PackScreenAccessor")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("MixinGuiScreen")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("MixinGuiScreenResourcePacks")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("ResourcePackEntryAccessor")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("ResourcePackRepositoryEntryAccessor")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("MixinResourcePackListEntry")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        // Angelica ships Iris's ShaderPackScreen. Only inject when angelica
        // is loaded - the mixin target class is otherwise absent and the
        // mixin processor would refuse to apply.
        registry.mixin("MixinShaderPackScreen")
            .modid("angelica")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("MixinIrisShaderPackEntry")
            .modid("angelica")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("MixinIrisShaderPackSelectionList")
            .modid("angelica")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("MixinSwanSongGuiShaders")
            .modid("swansong")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
        registry.mixin("MixinSwanSongGuiSlotShaders")
            .modid("swansong")
            .side(Side.CLIENT)
            .phase(Phase.EARLY)
            .build();
    }

    public static java.util.List<String> getEarlyMixinsForLoader() {
        return INSTANCE.getEarlyMixins();
    }

    public static java.util.List<String> getLateMixinsForLoader(java.util.Set<String> loadedCoreMods) {
        return INSTANCE.getLateMixins(loadedCoreMods);
    }
}
