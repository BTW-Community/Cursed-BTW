package btw.community.fabric.mixin;

import net.minecraft.src.Minecraft;
import net.minecraft.src.NetClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetClientHandler.class)
public class NetClientHandlerMixin {
    @Inject(method = "isTerrainAroundPlayerLoaded", remap = false, at = @At("HEAD"), cancellable = true)
    private void isTerrainAroundPlayerLoadedInject(CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getMinecraft().thePlayer == null) {
            cir.setReturnValue(false);
        }
    }
}
