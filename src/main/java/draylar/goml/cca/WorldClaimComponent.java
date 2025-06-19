package draylar.goml.cca;

import com.jamieswhiteshirt.rtree3i.ConfigurationBuilder;
import com.jamieswhiteshirt.rtree3i.RTreeMap;
import draylar.goml.api.Claim;
import draylar.goml.api.ClaimBox;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class WorldClaimComponent implements ClaimComponent {

    private RTreeMap<ClaimBox, Claim> claims = RTreeMap.create(new ConfigurationBuilder().star().build(), ClaimBox::toBox);
    private final World world;

    public WorldClaimComponent(World world) {
        this.world = world;
    }

    @Override
    public RTreeMap<ClaimBox, Claim> getClaims() {
        return claims;
    }

    @Override
    public void add(Claim info) {
        this.claims = this.claims.put(info.getClaimBox(), info);
    }

    @Override
    public void remove(Claim info) {
        this.claims = this.claims.remove(info.getClaimBox());
    }

    @Override
    public void readData(ReadView view) {
        this.claims = RTreeMap.create(new ConfigurationBuilder().star().build(), ClaimBox::rtree3iBox);
        var world = this.world.getRegistryKey().getValue();

        var version = view.getInt("Version", 0);
        var nbtList = view.getListReadView("Claims");

        if (version == 0) {
            nbtList.forEach(child -> {
                ClaimBox box = boxFromTag(child.getReadView("Box"));
                if (box != null) {
                    Claim claimInfo = Claim.readData(this.world.getServer(), child.getReadView("Info"), version);
                    claimInfo.internal_setWorld(world);
                    claimInfo.internal_setClaimBox(box);
                    if (this.world instanceof ServerWorld world1) {
                        claimInfo.internal_updateChunkCount(world1);
                    }
                    claimInfo.internal_enableUpdates();
                    add(claimInfo);
                }
            });
        } else {
            nbtList.forEach(child -> {
                Claim claimInfo = Claim.readData(this.world.getServer(), child, version);
                claimInfo.internal_setWorld(world);
                if (this.world instanceof ServerWorld world1) {
                    claimInfo.internal_updateChunkCount(world1);
                }
                claimInfo.internal_enableUpdates();
                add(claimInfo);
            });
        }
    }

    @Override
    public void writeData(WriteView view) {
        var nbtListClaims = view.getList("Claims");
        view.putInt("Version", 1);
        claims.values().forEach(claim -> claim.writeData(nbtListClaims.add()));
    }

    @Nullable
    @Deprecated
    public ClaimBox boxFromTag(ReadView tag) {
        return ClaimBox.readData(tag, 0);
    }
}
