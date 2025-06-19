package draylar.goml.api;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public record ClaimBox(com.jamieswhiteshirt.rtree3i.Box rtree3iBox, Box minecraftBox, BlockPos origin, int radius, int radiusY, boolean noShift) {
    public static final ClaimBox EMPTY = new ClaimBox(BlockPos.ORIGIN, 0, 0, true);

    public ClaimBox(BlockPos origin, int radius, int radiusY) {
        this(
                origin,
                radius,
                radiusY,
                false
        );
    }

    public ClaimBox(BlockPos origin, int radius, int radiusY, boolean noShift) {
        this(
                noShift ? createBoxNoShift(origin, radius, radiusY) : createBox(origin, radius, radiusY),
                Box.enclosing(origin.add(-radius, -radiusY, -radius), noShift ? origin.add(radius, radiusY, radius) : origin.add(radius + 1, radiusY + 1, radius + 1)),
                origin, radius, radiusY, noShift
        );
    }

    private static com.jamieswhiteshirt.rtree3i.Box createBox(BlockPos origin, int radius, int radiusY) {
        BlockPos lower = origin.add(-radius, -radiusY, -radius);
        BlockPos upper = origin.add(radius + 1, radiusY + 1, radius + 1);
        return com.jamieswhiteshirt.rtree3i.Box.create(lower.getX(), lower.getY(), lower.getZ(), upper.getX(), upper.getY(), upper.getZ());
    }

    private static com.jamieswhiteshirt.rtree3i.Box createBoxNoShift(BlockPos origin, int radius, int radiusY) {
        BlockPos lower = origin.add(-radius, -radiusY, -radius);
        BlockPos upper = origin.add(radius, radiusY, radius);
        return com.jamieswhiteshirt.rtree3i.Box.create(lower.getX(), lower.getY(), lower.getZ(), upper.getX(), upper.getY(), upper.getZ());
    }

    public com.jamieswhiteshirt.rtree3i.Box toBox() {
        return this.rtree3iBox;
    }

    public BlockPos getOrigin() {
        return this.origin;
    }

    public int getRadius() {
        return this.radius;
    }

    public int getX() {
        return this.radius;
    }

    public int getY() {
        return this.radiusY;
    }

    public int getZ() {
        return this.radius;
    }

    public static ClaimBox readData(ReadView view, int i) {
        BlockPos originPos = BlockPos.fromLong(view.getLong("OriginPos", 0));
        var radius = view.getInt("Radius", 0);
        var height = view.getInt("Height", radius);
        if (radius > 0 && height > 0) {
            return new ClaimBox(originPos, radius, height, view.getBoolean("NoShift", false));
        }
        return EMPTY;
    }

    public void writeData(WriteView view) {
        view.putLong("OriginPos", this.getOrigin().asLong());
        view.putInt("Radius", this.getRadius());
        view.putInt("Height", this.getY());
        view.putBoolean("NoShift", this.noShift());
    }
}
