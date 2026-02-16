package at.zapp908.guildbridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;


public class GuildBridgeClient implements ClientModInitializer {

    public static final String MOD_ID = "guildbridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(DiscordChatPayload.ID, DiscordChatPayload.CODEC);
        registerDiscordChatReceiver();
        registerCommands();

        GuildBridgeWebSocket.connect();
        LOGGER.info("WebSocket Connected");
        ChatProcessor processor = new ChatProcessor();

        ClientSendMessageEvents.CHAT.register(message -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

//            GuildBridgeClient.LOGGER.info(client.player.getName().getString() + ": " + message);
//            GuildBridgeClient.LOGGER.info(client.player.getUuid().toString());
//            GuildBridgeClient.LOGGER.info(GuildBridgeAuth.token);
//
//
//            processor.processChat(message);

//            GuildBridgeWebSocket.sendChat( message );


        });

        ClientReceiveMessageEvents.GAME.register(((message, overlay) -> {
//            GuildBridgeClient.LOGGER.info(message.getString());
//            GuildBridgeClient.LOGGER.info(message.getContent().toString());
            String raw = message.getString();
            processor.processChat(raw);
        }));

        ClientPlayConnectionEvents.JOIN.register(((clientPlayNetworkHandler, packetSender, minecraftClient) -> {
            if (GuildBridgeAuth.token == null) {
                GuildBridgeWebSocket.sendHelloIfReady();
            }

        }));

        ClientPlayConnectionEvents.DISCONNECT.register((clientPlayNetworkHandler, minecraftClient) -> {
            if (GuildBridgeAuth.token != null) {

            }
        });

        LOGGER.info("Initialized");


    }

    private void registerDiscordChatReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
                DiscordChatPayload.ID, (payload, context) -> {

                    if (!GuildBridgeState.enabled) return;

                    // Always switch to client thread
                    context.client().execute(() -> {
                        if (context.player() == null) return;

                        Text chatMessage = Text.literal("[Discord] ").formatted(Formatting.AQUA).append(Text.literal(payload.author() + ": ").formatted(Formatting.YELLOW)).append(Text.literal(payload.message()).formatted(Formatting.WHITE));

                        context.player().sendMessage(chatMessage, false);
                    });
                }
        );
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("guildbridge")
                            .then(literal("enable")
                                    .executes(context -> {
                                        if (GuildBridgeState.enabled) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("GuildBridge is already enabled.")
                                                            .formatted(Formatting.YELLOW)
                                            );
                                            return 1;
                                        }
                                        GuildBridgeState.enabled = true;
                                        context.getSource().sendFeedback(
                                                Text.literal("GuildBridge enabled.")
                                                        .formatted(Formatting.GREEN)
                                        );
                                        return 1;
                                    })
                            )
                            .then(literal("disable")
                                    .executes(context -> {
                                        if (!GuildBridgeState.enabled) {
                                            context.getSource().sendFeedback(
                                                    Text.literal("GuildBridge is already disabled.")
                                                            .formatted(Formatting.YELLOW)
                                            );
                                            return 1;
                                        }
                                        GuildBridgeState.enabled = false;
                                        context.getSource().sendFeedback(
                                                Text.literal("GuildBridge disabled.")
                                                        .formatted(Formatting.RED)
                                        );
                                        return 1;
                                    })
                            )
            );
        });
    }

}
