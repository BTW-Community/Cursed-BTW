package btw.community.fabric.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "main", at = @At("HEAD"))
    private static void onServerStart(CallbackInfo ci) {
        System.out.println("Hello BTW server!");
        //throw new RuntimeException("BTW server is not supported yet!");
    }

}
