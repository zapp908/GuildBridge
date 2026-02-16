package at.zapp908.guildbridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class GuildBridgeWebSocket {
    private static final String WS_URL = "wss://api.zapp908.dev/ws"; // path to ws
    private static WebSocket webSocket;
    private static boolean socketOpen = false;
    private static boolean manuallyClosed = false;

    private static int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final long BASE_RECONNECT_DELAY_MS = 2000;

    private static final long HEARTBEAT_INTERVAL_MS = 25_000;
    private static Thread heartbeatThread;



    public static void connect() {
        if (webSocket != null) return;

        manuallyClosed = false;

        HttpClient client = HttpClient.newHttpClient();

        client.newWebSocketBuilder()
                .buildAsync(URI.create(WS_URL), new Listener())
                .thenAccept(ws -> {webSocket = ws; reconnectAttempts = 0;})
                .exceptionally(err -> {
                    err.printStackTrace();
                    scheduleReconnect();
                    return null;
                });

        startHeartbeat();
    }

    public static void disconnect() {
        manuallyClosed = true;
        socketOpen = false;
        reconnectAttempts = 0;

        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            webSocket = null;
        }
    }

    private static void scheduleReconnect() {
        if (manuallyClosed) return;
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return;

        long delay = BASE_RECONNECT_DELAY_MS * (1L << reconnectAttempts);
        reconnectAttempts++;

        new Thread(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ignored) {}

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.execute(GuildBridgeWebSocket::connect);
            }
        }, "GuildBridge-Reconnect").start();
    }


    public static void sendChat(String message) {
        if (webSocket == null || GuildBridgeAuth.token == null) return;


        String json = """
                {
                  "type": "chat",
                  "token": "%s",
                  "message": "%s"
                }
                """.formatted(GuildBridgeAuth.token, message.replace("\"", "\\\""));

        webSocket.sendText(json, true);
    }

    public static void sendHelloIfReady() {
        if (!socketOpen || webSocket == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String hello = """
            {
              "type": "hello",
              "uuid": "%s",
              "username": "%s"
            }
            """.formatted(
                client.player.getUuidAsString(),
                client.player.getName().getString()
        );

        webSocket.sendText(hello, true);
    }

    private static void stopHeartbeat() {
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
    }

    private static void startHeartbeat() {
        stopHeartbeat();

        heartbeatThread = new Thread(() -> {
            try {
                while (socketOpen && webSocket != null) {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);

                    if (!socketOpen || webSocket == null) break;

                    webSocket.sendText(
                            """
                            { "type": "ping" }
                            """,
                            true
                    );
                }
            } catch (InterruptedException ignored) {}
        }, "GuildBridge-Heartbeat");

        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }



    private static class Listener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            socketOpen = true;
            webSocket.request(1);

            startHeartbeat();

            MinecraftClient.getInstance().execute(() -> {
                if (GuildBridgeAuth.token == null) {
                    sendHelloIfReady();
                }
            });
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socketOpen = false;
            GuildBridgeAuth.token = null;
            GuildBridgeWebSocket.webSocket = null;
            stopHeartbeat();

            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(
                            Text.literal("GuildBridge disconnected, reconnecting…"),
                            false
                    );
                }
            });

            scheduleReconnect();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socketOpen = false;
            GuildBridgeAuth.token = null;
            GuildBridgeWebSocket.webSocket = null;

            stopHeartbeat();
            scheduleReconnect();
        }



        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1);

            String text = data.toString();

            if (text.contains("\"auth_ok\"")) {
                GuildBridgeAuth.token = extract(text, "token");

                MinecraftClient.getInstance().execute(() ->
                        MinecraftClient.getInstance().player.sendMessage(
                                Text.literal("GuildBridge connected"),
                                false
                        )
                );
            }

            if (text.contains("\"type\":\"discord_chat\"")) {
                if (GuildBridgeState.enabled) {
                    String author = extract(text, "author");
                    String message = extract(text, "message");

                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player == null) return;

                        MinecraftClient.getInstance().player.sendMessage(
                                Text.literal("[Discord] ")
                                        .formatted(Formatting.DARK_PURPLE)
                                        .append(Text.literal(author + ": ")
                                                .formatted(Formatting.AQUA))
                                        .append(Text.literal(message).formatted(Formatting.WHITE)),
                                false
                        );
                    });
                }

            }


            return CompletableFuture.completedFuture(null);
        }

        private String extract(String json, String key) {
            int start = json.indexOf("\"" + key + "\":");
            if (start == -1) return "";
            start = json.indexOf("\"", start + key.length() + 3) + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
    }
}
