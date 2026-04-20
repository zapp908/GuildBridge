package at.zapp908.guildbridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class GuildBridgeClient implements ClientModInitializer {

    public static final String MOD_ID = "guildbridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final int MAX_RELAY_LENGTH = 230;
    private static final long RELAY_CACHE_MS = 15_000L;
    private static final Map<String, Long> RECENT_RELAYED_MESSAGES = new LinkedHashMap<>();

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
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String raw = message.getString();
            processor.processChat(raw);
        });

        ClientPlayConnectionEvents.JOIN.register((clientPlayNetworkHandler, packetSender, minecraftClient) -> {
            if (GuildBridgeAuth.token == null) {
                GuildBridgeWebSocket.sendHelloIfReady();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((clientPlayNetworkHandler, minecraftClient) -> {
            if (GuildBridgeAuth.token != null) {
                // no-op for now
            }
        });

        ClientSendMessageEvents.COMMAND.register(command ->
                LOGGER.info("GuildBridge COMMAND event fired: {}", command)
        );

        ClientSendMessageEvents.COMMAND_CANCELED.register(command ->
                LOGGER.warn("GuildBridge COMMAND event canceled: {}", command)
        );

        LOGGER.info("Initialized");
    }

    private void registerDiscordChatReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(
                DiscordChatPayload.ID, (payload, context) -> {
                    if (!GuildBridgeState.enabled) return;

                    context.client().execute(() -> {
                        if (context.player() == null) return;

                        Text chatMessage = Text.literal("[SBZ] ")
                                .formatted(Formatting.AQUA)
                                .append(Text.literal(payload.author() + ": ").formatted(Formatting.YELLOW))
                                .append(Text.literal(payload.message()).formatted(Formatting.WHITE));

                        context.player().sendMessage(chatMessage, false);
                        relayDiscordMessageToGuildChat(payload.author(), payload.message());
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

    public static void relayDiscordMessageToGuildChat(String author, String message) {
        if (!GuildBridgeState.enabled) {
            LOGGER.info("GuildBridge relay skipped: mod disabled");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            LOGGER.info("GuildBridge relay skipped: client null");
            return;
        }

        if (client.player == null) {
            LOGGER.info("GuildBridge relay skipped: player null");
            return;
        }

        if (client.getNetworkHandler() == null) {
            LOGGER.info("GuildBridge relay skipped: network handler null");
            return;
        }

        String relayMessage = formatDiscordRelay(author, message);
        String command = "gc " + relayMessage;

        rememberRelayedMessage(relayMessage);
        LOGGER.info("GuildBridge about to send command: {}", command);

        client.execute(() -> {
            try {
                client.getNetworkHandler().sendChatCommand(command);
                LOGGER.info("GuildBridge sendChatCommand invoked: {}", command);
            } catch (Exception e) {
                LOGGER.error("GuildBridge failed to send guild chat relay", e);
            }
        });
    }

    public static String formatDiscordRelay(String author, String message) {
        String cleanAuthor = sanitize(author);
        String cleanMessage = sanitize(message);

        String formatted = "[SBZ] " + cleanAuthor + ": " + cleanMessage;
        if (formatted.length() > MAX_RELAY_LENGTH) {
            formatted = formatted.substring(0, MAX_RELAY_LENGTH);
        }
        return formatted;
    }

    public static void rememberRelayedMessage(String message) {
        pruneRecentRelays();
        RECENT_RELAYED_MESSAGES.put(message, System.currentTimeMillis());
    }

    public static boolean wasRecentlyRelayed(String message) {
        pruneRecentRelays();
        return RECENT_RELAYED_MESSAGES.containsKey(message);
    }

    private static void pruneRecentRelays() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = RECENT_RELAYED_MESSAGES.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > RELAY_CACHE_MS) {
                iterator.remove();
            }
        }
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        return input
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("§", "")
                .trim();
    }
}