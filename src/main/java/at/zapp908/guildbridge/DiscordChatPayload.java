package at.zapp908.guildbridge;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DiscordChatPayload(String author, String message)  implements CustomPayload {
    public static final Id<DiscordChatPayload> ID = new Id<>(Identifier.of("guildbridge", "discord_chat"));

    public static final PacketCodec<PacketByteBuf, DiscordChatPayload> CODEC = PacketCodec.of((payload, buf) -> {
                buf.writeString(payload.author());
                buf.writeString(payload.message());
            },
            buf -> new DiscordChatPayload(
                    buf.readString(),
                    buf.readString()
            ));

            @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
            }
}
