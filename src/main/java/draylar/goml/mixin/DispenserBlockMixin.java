package draylar.goml.mixin;

import draylar.goml.api.ClaimUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DispenserBlock.class)
public class DispenserBlockMixin {

    @Shadow @Final public static EnumProperty<Direction> FACING;

    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void safeSetBlock(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        var nextPos = pos.offset(state.get(FACING));
        var targetState = world.getBlockState(nextPos);
        var targetBlock = targetState.getBlock();

        BlockEntity dispenserEntity = world.getBlockEntity(pos);
        if (dispenserEntity instanceof DispenserBlockEntity dispenserBE) {
            int slot = dispenserBE.chooseNonEmptySlot(world.random);
            if (slot >= 0) {
                ItemStack itemToDispense = dispenserBE.getStack(slot);
                if (!itemToDispense.isEmpty()) {
                    if (itemToDispense.getItem() instanceof BlockItem blockItem) {
                        var block = blockItem.getBlock();
                        
                        if (block instanceof ShulkerBoxBlock) {
                            return;
                        }
                    }
                }
            }
        }

        if (targetBlock instanceof ShulkerBoxBlock) {
            return;
        }

        BlockEntity blockEntity = world.getBlockEntity(nextPos);
        if (blockEntity instanceof ShulkerBoxBlockEntity) {
            return;
        }

        if (!ClaimUtils.hasMatchingClaims(world, nextPos, pos)) {
            ci.cancel();
        }
    }
}
