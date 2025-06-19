package draylar.goml.mixin;

import com.jamieswhiteshirt.rtree3i.Entry;
import com.jamieswhiteshirt.rtree3i.Selection;
import draylar.goml.api.ClaimBox;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimUtils;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin which prevents TNT primed by player A from going off in player B's claim.
 */
@Mixin(TntEntity.class)
public abstract class TntEntityMixin extends Entity {

    @Shadow @Nullable private LazyEntityReference<LivingEntity> causingEntity;

    public TntEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(at = @At("HEAD"), method = "explode", cancellable = true)
    private void goml_attemptExplosion(CallbackInfo ci) {
        if (getWorld().isClient) {
            return;
        }
        if (this.causingEntity != null) {
            var claimsFound = ClaimUtils.getClaimsAt(getWorld(), getBlockPos());
            var entity = this.causingEntity.resolve(getWorld(), LivingEntity.class);
            if (entity instanceof PlayerEntity player) {
                if (!claimsFound.isEmpty()) {
                    boolean noPermission = claimsFound.anyMatch((Entry<ClaimBox, Claim> boxInfo) -> !boxInfo.getValue().hasPermission(player));

                    if (noPermission) {
                        ci.cancel();
                    }
                }
            } else if (entity != null) {
                if (!claimsFound.isEmpty()) {
                    boolean noPermission = claimsFound.anyMatch((Entry<ClaimBox, Claim> boxInfo) -> !boxInfo.getValue().hasPermission(this.causingEntity.getUuid()));

                    if (noPermission) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}
