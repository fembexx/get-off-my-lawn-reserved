package draylar.goml.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import draylar.goml.api.ClaimUtils;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @ModifyExpressionValue(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BlockItem;getPlacementContext(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/item/ItemPlacementContext;"))
    private ItemPlacementContext preventPlacingInClaim(ItemPlacementContext original) {
        if (original == null) {
            return null;
        }

        Block blockBeingPlaced = ((BlockItem) (Object) this).getBlock();
        
        if (original.getPlayer() == null && blockBeingPlaced instanceof ShulkerBoxBlock) {
            return original;
        }

        if (!ClaimUtils.canModify(original.getWorld(), original.getBlockPos(), original.getPlayer())) {
            return null;
        }

        return original;
    }
}
