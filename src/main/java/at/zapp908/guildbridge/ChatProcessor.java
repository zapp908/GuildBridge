package at.zapp908.guildbridge;

public class ChatProcessor {

    public ChatProcessor() {}

    public void processChat(String message) {
        message = message.replaceAll("§.", "");
        message = message.strip();

        if (GuildBridgeClient.wasRecentlyRelayed(message)) {
            GuildBridgeClient.LOGGER.info("Skipping recently relayed guild message: {}", message);
            return;
        }

        if (message.startsWith("Guild >")) {
            GuildBridgeClient.LOGGER.info("Detected guild message {}", message);
            message = message.replace("Guild > ", "");
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("was promoted from") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild event {}", message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("was demoted from") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild event {}", message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("left the guild!") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild event {}", message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("joined the guild!") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild event {}", message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("GUILD QUEST TIER ") && message.contains("COMPLETED") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild event {}", message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("The guild has reached Level") && !message.contains("!")) {
            GuildBridgeClient.LOGGER.info("Detected guild event {}", message);
            GuildBridgeWebSocket.sendChat(message);
        }
    }
}