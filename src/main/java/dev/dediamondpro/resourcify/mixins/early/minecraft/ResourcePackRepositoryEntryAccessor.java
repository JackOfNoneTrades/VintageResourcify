package dev.dediamondpro.resourcify.mixins.early.minecraft;

import java.io.File;

import net.minecraft.client.resources.ResourcePackRepository;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResourcePackRepository.Entry.class)
public interface ResourcePackRepositoryEntryAccessor {

    @Accessor("resourcePackFile")
    File getResourcePackFile();
}
