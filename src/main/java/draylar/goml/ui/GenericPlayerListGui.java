package draylar.goml.ui;

import com.mojang.authlib.GameProfile;
import draylar.goml.registry.GOMLTextures;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApiStatus.Internal
public abstract class GenericPlayerListGui extends PagedGui {
    public List<UUID> uuids = new ArrayList<>();

    public GenericPlayerListGui(ServerPlayerEntity player, @Nullable Runnable onClose) {
        super(player, onClose);
    }

    @Override
    protected int getPageAmount() {
        return (this.getEntryCount()) / PAGE_SIZE;
    }

    protected int getEntryCount() {
        return this.uuids.size();
    }

    @Override
    protected DisplayElement getElement(int id) {
        return getPlayerElement(id);
    }

    protected DisplayElement getPlayerElement(int id) {
        if (this.uuids.size() > id) {
            return getPlayerElement(this.uuids.get(id));
        }

        return DisplayElement.empty();
    }

    protected DisplayElement getPlayerElement(UUID uuid) {
        var optional = this.player.getServer().getUserCache().getByUuid(uuid);
        var exist = optional.isPresent();
        var gameProfile = exist ? optional.get() : null;

        var builder = new GuiElementBuilder(exist ? Items.PLAYER_HEAD : Items.SKELETON_SKULL)
                .setName(Text.literal(exist ? gameProfile.getName() : "<" + uuid.toString() + ">")
                        .formatted(Formatting.WHITE)
                );

        if (exist) {
            builder.setSkullOwner(gameProfile, null);
        } else {
            builder.setSkullOwner(GOMLTextures.GUI_QUESTION_MARK);
        }

        this.modifyBuilder(builder, optional, uuid);
        return DisplayElement.of(builder);
    }

    protected void modifyBuilder(GuiElementBuilder builder, Optional<GameProfile> optional, UUID uuid) {
    }


}
