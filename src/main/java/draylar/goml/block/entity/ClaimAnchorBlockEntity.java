package draylar.goml.block.entity;

import com.mojang.serialization.Codec;
import draylar.goml.GetOffMyLawn;
import draylar.goml.api.Augment;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimBox;
import draylar.goml.api.ClaimUtils;
import draylar.goml.registry.GOMLEntities;
import eu.pb4.polymer.core.api.utils.PolymerObject;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClaimAnchorBlockEntity extends BlockEntity implements PolymerObject {

    private static final String AUGMENT_LIST_KEY = "AugmentPositions";

    private final List<BlockPos> loadPositions = new ArrayList<>();

    private Claim claim;
    private ClaimBox box;

    public ClaimAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(GOMLEntities.CLAIM_ANCHOR, pos, state);
    }

    public static <T extends BlockEntity> void tick(World eWorld, BlockPos pos, BlockState state, T blockEntity) {
        if (eWorld instanceof ServerWorld world && blockEntity instanceof ClaimAnchorBlockEntity anchor) {

            // Claim is null, world probably just loaded, re-grab claim
            if (anchor.claim == null) {
                var collect = ClaimUtils.getClaimsAt(anchor.world, anchor.pos).filter(x -> x.getValue().getOrigin().equals(pos)).collect(Collectors.toList());

                if (collect.isEmpty()) {
                    GetOffMyLawn.LOGGER.warn(String.format("A Claim Anchor at %s tried to initialize its claim, but one could not be found! Was the claim removed without the anchor?", anchor.pos));
                    world.breakBlock(pos, true);
                    for (var lPos : anchor.loadPositions) {
                        world.breakBlock(lPos, true);
                    }
                    return;
                } else {
                    var entry = collect.get(0);
                    anchor.claim = entry.getValue();
                    anchor.box = entry.getKey();
                }
            }

            if (anchor.claim.isDestroyed()) {
                world.breakBlock(pos, true);
                return;
            }

            // no augments, some queued from fromTag
            if (!anchor.loadPositions.isEmpty()) {
                anchor.claim.internal_disableUpdates();
                for (BlockPos foundPos : anchor.loadPositions) {
                    BlockEntity foundEntity = anchor.world.getBlockEntity(foundPos);

                    if (foundEntity instanceof ClaimAugmentBlockEntity be) {
                        anchor.claim.addAugment(foundPos, be.getAugment());
                    } else {
                        GetOffMyLawn.LOGGER.warn(String.format("A Claim Anchor at %s tried to load a child at %s, but none were found!", anchor.pos.toString(), foundPos.toString()));
                    }
                }

                anchor.loadPositions.clear();
                anchor.claim.internal_enableUpdates();
            }
        }
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        var positions = view.getListAppender(AUGMENT_LIST_KEY, Codec.LONG);
        for (BlockPos loadPosition : this.loadPositions) {
            positions.add(loadPosition.asLong());
        }
        if (this.claim != null) {
            for (Map.Entry<BlockPos, Augment> entry : this.claim.getAugments().entrySet()) {
                positions.add(entry.getKey().asLong());
            }
        }
    }

    @Override
    public void readData(ReadView view) {
        var positions = view.getTypedListView(AUGMENT_LIST_KEY, Codec.LONG);
        positions.forEach(sub -> {
            BlockPos foundPos = BlockPos.fromLong(sub);
            this.loadPositions.add(foundPos);
        });

        super.readData(view);
    }

    @Override
    public void onBlockReplaced(BlockPos pos, BlockState oldState) {
        super.onBlockReplaced(pos, oldState);
        if (this.world != null) {
            ClaimUtils.getClaimsAt(world, pos).forEach(claimedArea -> {
                if (ClaimUtils.canDestroyClaimBlock(claimedArea, null, pos)) {
                    claimedArea.getValue().destroy();
                }
            });
        }
    }

    public void addChild(BlockPos pos, Augment augment) {
        this.claim.addAugment(pos, augment);
    }

    public void removeChild(BlockPos pos) {
        this.claim.removeAugment(pos);
    }

    @Nullable
    public Claim getClaim() {
        return this.claim;
    }

    @Nullable
    public ClaimBox getBox() {
        return this.box;
    }

    public void setClaim(Claim claim, ClaimBox box) {
        this.claim = claim;
        this.box = box;
    }

    public boolean hasAugment(Augment augment) {
        return this.claim.hasAugment(augment);
    }

    @Deprecated
    public boolean hasAugment() {
        return this.claim.hasAugment();
    }

    @Deprecated
    public Map<BlockPos, Augment> getAugments() {
        return this.claim.getAugments();
    }

    @Deprecated
    public List<PlayerEntity> getPreviousTickPlayers() {
        return List.of();
    }

    public void from(ClaimAnchorBlockEntity be) {
        this.claim = be.claim;
    }

    @Override
    public void markRemoved() {
        // Reset players in claim
        super.markRemoved();
    }
}
