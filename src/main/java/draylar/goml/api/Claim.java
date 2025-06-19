package draylar.goml.api;

import com.mojang.serialization.Codec;
import draylar.goml.GetOffMyLawn;
import draylar.goml.api.event.ClaimEvents;
import draylar.goml.api.group.PlayerGroup;
import draylar.goml.api.group.PlayerGroupProvider;
import draylar.goml.block.ClaimAnchorBlock;
import draylar.goml.block.entity.ClaimAnchorBlockEntity;
import draylar.goml.other.LegacyNbtHelper;
import draylar.goml.registry.GOMLAugments;
import draylar.goml.registry.GOMLBlocks;
import draylar.goml.registry.GOMLTextures;
import draylar.goml.ui.AdminAugmentGui;
import draylar.goml.ui.ClaimAugmentGui;
import draylar.goml.ui.ClaimPlayerListGui;
import draylar.goml.ui.PagedGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.registry.RegistryKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a claim on land with an origin {@link BlockPos}, owners, and other allowed players.
 * <p>While this class stores information about the origin of a claim, the actual bounding box is stored by the world.
 */
public class Claim {
    public static final String POSITION_KEY = "Pos";
    public static final String OWNERS_KEY = "Owners";
    public static final String TRUSTED_KEY = "Trusted";
    public static final String TRUSTED_GROUP_KEY = "TrustedGroups";
    public static final String ICON_KEY = "Icon";
    public static final String TYPE_KEY = "Type";
    public static final String AUGMENTS_KEY = "Augments";
    public static final String CUSTOM_DATA_KEY = "CustomData";
    private static final String BOX_KEY = "Box";

    private static final Codec<Map<Identifier, NbtElement>> CUSTOM_DATA_CODEC = Codec.unboundedMap(Identifier.CODEC, Codecs.NBT_ELEMENT);

    private final Set<UUID> owners = new HashSet<>();
    private final Set<UUID> trusted = new HashSet<>();
    @Nullable
    private Set<PlayerGroup> trustedGroups;

    private final MinecraftServer server;
    private final Set<PlayerGroup.Key> trustedGroupKeys = new HashSet<>();
    private final BlockPos origin;
    private ClaimAnchorBlock type = GOMLBlocks.MAKESHIFT_CLAIM_ANCHOR.getFirst();
    private Identifier world;
    @Nullable
    private ItemStack icon;

    private Map<DataKey<Object>, Object> customData = new HashMap<>();
    private ClaimBox claimBox;
    private int chunksLoadedCount;
    private final Map<BlockPos, Augment> augments = new HashMap<>();

    private final List<PlayerEntity> previousTickPlayers = new ArrayList<>();
    private boolean destroyed = false;
    private boolean updatable = false;

    @ApiStatus.Internal
    public Claim(MinecraftServer server, Set<UUID> owners, Set<UUID> trusted, BlockPos origin) {
        this.server = server;
        this.owners.addAll(owners);
        this.trusted.addAll(trusted);
        this.origin = origin;
    }

    public boolean isOwner(PlayerEntity player) {
        return isOwner(player.getUuid());
    }

    public boolean isOwner(UUID uuid) {
        return owners.contains(uuid);
    }

    public void addOwner(PlayerEntity player) {
        addOwner(player.getUuid());
    }

    public void addOwner(UUID id) {
        this.owners.add(id);
        onUpdated();
    }

    public boolean hasPermission(PlayerEntity player) {
        return hasPermission(player.getUuid());
    }

    public boolean hasPermission(UUID uuid) {
        for (var group : this.getGroups()) {
            if (group.isPartOf(uuid)) {
                return true;
            }
        }
        return hasDirectPermission(uuid);
    }

    public boolean hasDirectPermission(UUID uuid) {
        return owners.contains(uuid) || trusted.contains(uuid);
    }

    public void trust(PlayerEntity player) {
        trust(player.getUuid());
    }

    public void trust(UUID uuid) {
        trusted.add(uuid);
        onUpdated();
    }

    public void trust(PlayerGroup group) {
        getGroups().add(group);
        group.addClaim(this);
    }

    public void untrust(PlayerEntity player) {
        untrust(player.getUuid());
    }

    public void untrust(PlayerGroup group) {
        getGroups().remove(group);
        group.removeClaim(this);
    }

    public void untrust(UUID uuid) {
        trusted.remove(uuid);
        onUpdated();
    }

    /**
     * Returns the {@link UUID}s of the owners of the claim.
     *
     * <p>The owner is defined as the player who placed the claim block, or someone added through the goml command.
     *
     * @return  claim owner's UUIDs
     */
    public Set<UUID> getOwners() {
        return owners;
    }

    public Set<UUID> getTrusted() {
        return trusted;
    }

    /**
     * Returns the origin position of the claim as a {@link BlockPos}.
     *
     * <p>The origin position of a claim is the position the center Claim Anchor was placed at.
     *
     * @return  origin position of this claim
     */
    public BlockPos getOrigin() {
        return origin;
    }

    /**
     * Serializes this {@link Claim} to a {@link NbtCompound} and returns it.
     *
     * <p>The following tags are stored at the top level of the tag:
     * <ul>
     * <li>"Owners" - list of {@link UUID}s of claim owners
     * <li>"Pos" - origin {@link BlockPos} of claim
     *
     * @return  this object serialized to a {@link NbtCompound}
     */
    public void writeData(WriteView view) {
        // collect owner UUIDs into list
        var ownersTag = view.getListAppender(OWNERS_KEY, Uuids.INT_STREAM_CODEC);
        for (UUID ownerUUID : owners) {
            ownersTag.add(ownerUUID);
        }

        // collect trusted UUIDs into list
        var trustedTag = view.getListAppender(TRUSTED_KEY, Uuids.INT_STREAM_CODEC);
        for (UUID trustedUUID : trusted) {
            trustedTag.add(trustedUUID);
        }

        // collect trusted UUIDs into list
        var trustedGroupsTag = view.getListAppender(TRUSTED_KEY, Codec.STRING);
        if (this.trustedGroups != null) {
            for (var group : this.trustedGroups) {
                if (group.canSave()) {
                    var id = group.getKey();
                    if (id != null) {
                        trustedGroupsTag.add(id.compact());
                    }
                }
            }
        }
        for (var group : this.trustedGroupKeys) {
            trustedGroupsTag.add(group.compact());
        }
        view.putLong(POSITION_KEY, origin.asLong());
        if (this.icon != null) {
            view.put(ICON_KEY, ItemStack.OPTIONAL_CODEC, this.icon);
        }
        view.putString(TYPE_KEY, Registries.BLOCK.getId(this.type).toString());

        var customData = new HashMap<Identifier, NbtElement>();

        for (var entry : this.customData.entrySet()) {
            var value = entry.getKey().serializer().apply(entry.getValue());

            if (value != null) {
                customData.put(entry.getKey().key(), value);
            }
        }

        view.put(CUSTOM_DATA_KEY, CUSTOM_DATA_CODEC, customData);


        var augments = view.getList(AUGMENTS_KEY);

        for (var entry : this.augments.entrySet()) {
            var value = augments.add();
            value.put("Pos", NbtCompound.CODEC, LegacyNbtHelper.fromBlockPos(entry.getKey()));
            value.putString("Type", GOMLAugments.getId(entry.getValue()).toString());
        }

        this.claimBox.writeData(view.get(BOX_KEY));
    }

    @ApiStatus.Internal
    public static Claim readData(MinecraftServer server, ReadView view, int version) {
        // Collect UUID of owners
        Set<UUID> ownerUUIDs = new HashSet<>();
        for (var ownerUUID : view.getTypedListView(OWNERS_KEY, Uuids.INT_STREAM_CODEC)) {
            ownerUUIDs.add(ownerUUID);
        }

        // Collect UUID of trusted
        Set<UUID> trustedUUIDs = new HashSet<>();
        for (var trustedUUID : view.getTypedListView(TRUSTED_KEY, Uuids.INT_STREAM_CODEC)) {
            trustedUUIDs.add(trustedUUID);
        }

        var claim = new Claim(server, ownerUUIDs, trustedUUIDs, BlockPos.fromLong(view.getLong(POSITION_KEY, 0)));

        claim.icon = view.read(ICON_KEY, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        if (!view.getString(TYPE_KEY, "").isEmpty()) {
            var block = Registries.BLOCK.get(Identifier.tryParse(view.getString(TYPE_KEY, "")));
            if (block instanceof ClaimAnchorBlock anchorBlock) {
                claim.type = anchorBlock;
            }
        }

        var customData = view.read(CUSTOM_DATA_KEY, CUSTOM_DATA_CODEC);

        if (customData.isPresent()) {
            for (var key : customData.get().keySet()) {
                var dataKey = DataKey.getKey(key);

                if (dataKey != null) {
                    claim.customData.put((DataKey<Object>) dataKey, dataKey.deserializer().apply(customData.get().get(key)));
                }
            }
        }

        if (version == 0) {
            claim.claimBox = ClaimBox.EMPTY;
        } else {
            claim.claimBox = ClaimBox.readData(view.getReadView(BOX_KEY), 0);
        }

        for (var value : view.getListReadView(AUGMENTS_KEY)) {
            var pos = LegacyNbtHelper.toBlockPos(value.read("Pos", NbtCompound.CODEC).orElseGet(NbtCompound::new));
            var type = GOMLAugments.get(Identifier.tryParse(value.getString("Type", "")));

            if (pos != null && type != null) {
                claim.augments.put(pos, type);
            }
        }

        for (var string : view.getTypedListView(TRUSTED_GROUP_KEY, Codec.STRING)) {
            claim.trustedGroupKeys.add(PlayerGroup.Key.of(string));
        }

        for (var augment : claim.augments.entrySet()) {
            augment.getValue().onLoaded(claim, augment.getKey());
        }

        return claim;
    }

    public Identifier getWorld() {
        return this.world != null ? this.world : Identifier.of("undefined");
    }

    @Nullable
    public ServerWorld getWorldInstance(MinecraftServer server) {
        return server.getWorld(RegistryKey.of(RegistryKeys.WORLD, getWorld()));
    }

    @Nullable
    @Deprecated
    public ClaimAnchorBlockEntity getBlockEntityInstance(MinecraftServer server) {
        if (server.getWorld(RegistryKey.of(RegistryKeys.WORLD, getWorld())).getBlockEntity(this.origin) instanceof ClaimAnchorBlockEntity claimAnchorBlock) {
            return claimAnchorBlock;
        }
        return null;
    }

    public ItemStack getIcon() {
        return this.icon != null ? this.icon.copy() : Items.STONE.getDefaultStack();
    }

    @Nullable
    public <T> T getData(DataKey<T> key) {
        try {
            var val = this.customData.get(key);
            if (val == null) {
                val = key.defaultSupplier().get();
                this.customData.put((DataKey<Object>) key, val);
            }

            return (T) val;
        } catch (Exception e) {
            return key.defaultValue();
        }
    }

    public <T> void setData(DataKey<T> key, T data) {
        if (data != null) {
            this.customData.put((DataKey<Object>) key, data);
        } else {
            this.customData.remove(key);
        }
    }

    public <T> void removeData(DataKey<T> key) {
        setData(key, null);
    }

    public Collection<DataKey<?>> getDataKeys() {
        return Collections.unmodifiableCollection(this.customData.keySet());
    }

    public void openUi(ServerPlayerEntity player) {
        var gui = new SimpleGui(ScreenHandlerType.HOPPER, player, false);
        gui.setTitle(Text.translatable("text.goml.gui.claim.title"));

        gui.addSlot(GuiElementBuilder.from(this.icon)
                .setName(Text.translatable("text.goml.gui.claim.about"))
                .setLore(ClaimUtils.getClaimText(player.getServer(), this))
        );

        gui.addSlot(new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(Text.translatable("text.goml.gui.claim.players").formatted(Formatting.WHITE))
                .setCallback((x, y, z) -> {
                    PagedGui.playClickSound(player);
                    ClaimPlayerListGui.open(player, this, ClaimUtils.isInAdminMode(player), () -> openUi(player));
                })
        );

        gui.addSlot(new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(Text.translatable("text.goml.gui.claim.augments").formatted(Formatting.WHITE))
                .setSkullOwner(GOMLTextures.ANGELIC_AURA)
                .setCallback((x, y, z) -> {
                    PagedGui.playClickSound(player);
                    new ClaimAugmentGui(player, this, ClaimUtils.isInAdminMode(player) || this.isOwner(player), () -> openUi(player));
                })
        );

        if (this.type == GOMLBlocks.ADMIN_CLAIM_ANCHOR.getFirst()) {
            gui.addSlot(new GuiElementBuilder(Items.PLAYER_HEAD)
                    .setName(Text.translatable("text.goml.gui.admin_settings").formatted(Formatting.WHITE))
                    .setSkullOwner("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmY3YTQyMmRiMzVkMjhjZmI2N2U2YzE2MTVjZGFjNGQ3MzAwNzI0NzE4Nzc0MGJhODY1Mzg5OWE0NGI3YjUyMCJ9fX0=")
                    .setCallback((x, y, z) -> {
                        PagedGui.playClickSound(player);
                        new AdminAugmentGui(this, player, () -> openUi(player));
                    })
            );
        }

        while (gui.getFirstEmptySlot() != -1) {
            gui.addSlot(PagedGui.DisplayElement.filler().element());
        }

        gui.open();
    }

    public ClaimAnchorBlock getType() {
        return this.type;
    }

    @ApiStatus.Internal
    public void internal_enableUpdates() {
        this.updatable = true;
    }

    @ApiStatus.Internal
    public void internal_disableUpdates() {
        this.updatable = false;
    }

    @ApiStatus.Internal
    public void internal_setIcon(ItemStack stack) {
        this.icon = stack.copy();
        onUpdated();
    }

    @ApiStatus.Internal
    public void internal_setType(ClaimAnchorBlock anchorBlock) {
        this.type = anchorBlock;
        onUpdated();
    }

    @ApiStatus.Internal
    public void internal_setWorld(Identifier world) {
        this.world = world;
    }

    @ApiStatus.Internal
    public void internal_setClaimBox(ClaimBox box) {
        this.claimBox = box;
    }

    @ApiStatus.Internal
    public void internal_incrementChunks() {
        this.chunksLoadedCount++;
    }

    @ApiStatus.Internal
    public void internal_decrementChunks() {
        this.chunksLoadedCount--;
        if (this.chunksLoadedCount == 0) {
            this.clearTickedPlayers();
        }
    }

    @ApiStatus.Internal
    public void internal_updateChunkCount(ServerWorld world) {
        var minX = ChunkSectionPos.getSectionCoord(this.claimBox.toBox().x1());
        var minZ = ChunkSectionPos.getSectionCoord(this.claimBox.toBox().z1());

        var maxX = ChunkSectionPos.getSectionCoord(this.claimBox.toBox().x2());
        var maxZ = ChunkSectionPos.getSectionCoord(this.claimBox.toBox().z2());

        for (var x = minX; x <= maxX; x++) {
            for (var z = minZ; z <= maxZ; z++) {
                if (world.isChunkLoaded(x, z)) {
                    this.chunksLoadedCount++;
                }
            }
        }

        if (this.chunksLoadedCount == 0) {
            this.clearTickedPlayers();
        }
    }

    private void clearTickedPlayers() {
        if (!this.previousTickPlayers.isEmpty()) {
            for (var augment : this.augments.values()) {
                if (augment != null) {
                    // Tick exit behavior
                    for (var player : this.previousTickPlayers) {
                        augment.onPlayerExit(this, player);
                    }
                }
            }
        }
    }

    public void addAugment(BlockPos pos, Augment augment) {
        this.augments.put(pos, augment);
        for (var player : this.previousTickPlayers) {
            augment.onPlayerEnter(this, player);
        }
        onUpdated();
    }

    public void removeAugment(BlockPos pos) {
        var augment = this.augments.remove(pos);
        if (augment != null) {
            for (var player : this.previousTickPlayers) {
                augment.onPlayerExit(this, player);
            }
            onUpdated();
        }
    }

    public boolean hasAugment() {
        return !this.augments.isEmpty();
    }

    public boolean hasAugment(Augment augment) {
        for (var a : this.augments.values()) {
            if (a == augment) {
                return true;
            }
        }
        return false;
    }

    public Map<BlockPos, Augment> getAugments() {
        return this.augments;
    }

    public ClaimBox getClaimBox() {
        return this.claimBox != null ? this.claimBox : ClaimBox.EMPTY;
    }

    public int getRadius() {
        return (this.claimBox != null ? this.claimBox.radius() : 0);
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

    public Collection<ServerPlayerEntity> getPlayersIn(MinecraftServer server) {
        var world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, this.world));

        if (world == null) {
            return Collections.emptyList();
        }

        var box = this.claimBox.minecraftBox();
        return world.getPlayers(x -> x.getBoundingBox().intersects(box));
    }

    public void tick(ServerWorld world) {
        if (this.chunksLoadedCount > 0) {
            var box = this.claimBox.minecraftBox();
            var playersInClaim = world.getPlayers(x -> x.getBoundingBox().intersects(box));

            // Tick all augments
            for (var augment : this.augments.values()) {

                if (augment != null && augment.isEnabled(this, world)) {
                    if (augment.ticks()) {
                        augment.tick(this, world);
                        for (var playerEntity : playersInClaim) {
                            augment.playerTick(this, playerEntity);
                        }
                    }

                    // Enter/Exit behavior
                    for (var playerEntity : playersInClaim) {
                        // this player was NOT in the claim last tick, call entry method
                        if (!this.previousTickPlayers.contains(playerEntity)) {
                            augment.onPlayerEnter(this, playerEntity);
                        }
                    }

                    // Tick exit behavior
                    for (var player : this.previousTickPlayers) {
                        if (!playersInClaim.contains(player)) {
                            augment.onPlayerExit(this, player);
                        }
                    }
                }
            }

            // Reset players in claim
            this.previousTickPlayers.clear();
            this.previousTickPlayers.addAll(playersInClaim);
        }
    }

    public Collection<PlayerGroup> getGroups() {
        var g = this.trustedGroups;
        if (g == null) {
            g = new HashSet<>();
            var iter = this.trustedGroupKeys.iterator();

            while (iter.hasNext()) {
                var key = iter.next();
                var group = PlayerGroupProvider.getGroup(this.server, key);
                if (group != null) {
                    g.add(group);
                    iter.remove();
                }
            }
            this.trustedGroups = g;
        }

        return g;
    }

    public void destroy() {
        for (var group : this.getGroups()) {
            group.removeClaim(this);
        }

        var world = getWorldInstance(this.server);
        if (world != null) {
            GetOffMyLawn.CLAIM.get(world).remove(this);
        }
        if (!this.destroyed) {
            this.destroyed = true;
            ClaimEvents.CLAIM_DESTROYED.invoker().onEvent(this);
            this.clearTickedPlayers();
        }
    }

    public boolean hasPermission(Collection<UUID> trusted) {
        for (var x : trusted) {
            if (hasPermission(x)) {
                return true;
            }
        }
        return false;
    }

    private void onUpdated() {
        if (this.updatable && !this.destroyed) {
            ClaimEvents.CLAIM_UPDATED.invoker().onEvent(this);
        }
    }
}
