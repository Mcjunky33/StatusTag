package de.mcjunky33.statustag.mixin;

import de.mcjunky33.statustag.Statustag;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class TeamInjectorMixin {

    @Inject(at = @At("HEAD"), method = "loadWorld")
    private void init(CallbackInfo info) {
        // Übergibt den MinecraftServer selbst
        Statustag.INSTANCE.reloadStatusTeams((MinecraftServer)(Object)this);
    }
}
