package net.fabricmc.example.mixin;

import btw.BTWAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BTWAddon.class)
public class ExampleMixin {
	@Inject(at = @At("RETURN"), method = "<init>")
	private void init(CallbackInfo info) {
		System.out.println("This line is printed by an example mod mixin!");
	}
}
