package draylar.goml.compat;

import draylar.goml.GetOffMyLawn;
import draylar.goml.api.Claim;
import draylar.goml.api.group.PlayerGroup;
import draylar.goml.api.group.PlayerGroupProvider;
//import earth.terrarium.argonauts.api.guild.Guild;
//import earth.terrarium.argonauts.api.guild.GuildApi;
//import earth.terrarium.argonauts.common.handlers.guild.members.GuildMember;
//import earth.terrarium.argonauts.fabric.events.GuildEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ArgonautsCompat {
    public static void init() {
        GetOffMyLawn.LOGGER.warn("Tried to enable compat with argonauts, but it's not implemented!");
        /*PlayerGroupProvider.register("argonauts_guild", GuildProvider.INSTANCE);
        GuildEvents.REMOVED.register(((b, guild) -> {
            var cached = GuildGroup.CACHE.get(guild);
            if (cached != null) {
                for (var claim : List.copyOf(cached.claims)) {
                    claim.untrust(cached);
                }
            }
            GuildGroup.CACHE.remove(guild);
        }));*/
    }
    /*
    private record GuildProvider() implements PlayerGroupProvider {
        public static final PlayerGroupProvider INSTANCE = new GuildProvider();
        @Override
        public @Nullable PlayerGroup getGroupOf(PlayerEntity player) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                var g = GuildApi.API.get(serverPlayer);
                return g != null ? GuildGroup.of(g) : null;
            }

            return null;
        }

        @Override
        public @Nullable PlayerGroup getGroupOf(MinecraftServer server, UUID uuid) {
            var g = GuildApi.API.getPlayerGuild(server, uuid);
            return g != null ? GuildGroup.of(g) : null;
        }

        @Override
        @Nullable
        public PlayerGroup fromKey(MinecraftServer server, PlayerGroup.Key key) {
            var guild = GuildApi.API.get(server, UUID.fromString(key.groupId()));
            return guild != null ? GuildGroup.of(guild) : null;
        }

        @Override
        public Text getName() {
            return Text.translatable("text.goml.argonauts.guild_type.name");
        }
    }

    private record GuildGroup(Guild guild, HashSet<Claim> claims) implements PlayerGroup {
        private GuildGroup(Guild guild) {
            this(guild, new HashSet<>());
        }

        private static WeakHashMap<Guild, GuildGroup> CACHE = new WeakHashMap<>();

        public static PlayerGroup of(Guild guild) {
            return CACHE.computeIfAbsent(guild, GuildGroup::new);
        }

        @Override
        public Text selfDisplayName() {
            return guild.displayName();
        }

        @Override
        public Text fullDisplayName() {
            return Text.translatable("text.goml.argonauts.guild_type.display", Text.empty().append(this.selfDisplayName()).formatted(Formatting.WHITE));
        }

        @Override
        public Key getKey() {
            return new Key("argonauts_guild", this.guild.id().toString());
        }

        @Override
        public ItemStack icon() {
            var stack = new ItemStack(Items.LEATHER_CHESTPLATE);
            var i = this.guild.color().getColorValue();
            stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(i != null ? i : 0xFFFFFF));
            stack.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.DYED_COLOR, false));
            return stack;
        }

        @Override
        public PlayerGroupProvider provider() {
            return GuildProvider.INSTANCE;
        }

        @Override
        public boolean isPartOf(UUID uuid) {
            return this.guild.members().isMember(uuid);
        }

        @Override
        public boolean canSave() {
            return true;
        }

        @Override
        public List<Member> getMembers() {
            List<Member> list = new ArrayList<>();
            for (GuildMember x : guild.members().allMembers()) {
                Member member = new Member(x.profile(), x.getRole());
                list.add(member);
            }
            return list;
        }

        @Override
        public boolean addClaim(Claim claim) {
            return this.claims.add(claim);
        }

        @Override
        public boolean removeClaim(Claim claim) {
            return this.claims.remove(claim);
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }

        @Override
        public int hashCode() {
            return this.guild.id().hashCode() + 31 * "argonauts".hashCode();
        }
    }*/
}
