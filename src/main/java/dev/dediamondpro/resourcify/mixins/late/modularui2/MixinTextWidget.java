package dev.dediamondpro.resourcify.mixins.late.modularui2;

import java.util.function.IntSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widgets.TextWidget;

import dev.dediamondpro.resourcify.VintageResourcify;

@Mixin(value = TextWidget.class, remap = false)
public abstract class MixinTextWidget {

    @Accessor("color")
    abstract IntSupplier resourcify$color();

    @Inject(method = "draw", at = @At("HEAD"))
    private void resourcify$logColor(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme, CallbackInfo ci) {
        IntSupplier supplier = resourcify$color();
        Integer raw = supplier != null ? supplier.getAsInt() : null;
        int themeColor = widgetTheme.getTheme(false)
            .getTextColor();
        VintageResourcify.LOG.info(
            "TextWidget.draw color={} themeFallback=0x{}",
            raw == null ? "null" : ("0x" + Integer.toHexString(raw)),
            Integer.toHexString(themeColor));
    }
}
