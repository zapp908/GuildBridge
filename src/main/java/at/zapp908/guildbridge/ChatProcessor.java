package at.zapp908.guildbridge;

public class ChatProcessor {

    public ChatProcessor() {}

    public void processChat(String message) {
        message = message.replaceAll("§.", "");
        message = message.strip();
//        GuildBridgeClient.LOGGER.info("replaced color codes in " + message);

        if (message.startsWith("Guild >")) {
            GuildBridgeClient.LOGGER.info("Detected guild message " + message);
            message = message.replace("Guild > ", "");

//            if (message.contains("joined.") && !message.contains(":")) {
//                // handle join message
//            }
//
//            if (message.contains("left.") && !message.contains(":")) {
//                // handle leaving message
//            }

            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("was promoted from") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild message " + message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("was demoted from") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild message " + message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("left the guild!") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild message " + message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("joined the guild!") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild message " + message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("GUILD QUEST TIER ") && message.contains("COMPLETED") && !message.contains(":")) {
            GuildBridgeClient.LOGGER.info("Detected guild message " + message);
            GuildBridgeWebSocket.sendChat(message);
        }

        if (message.contains("The guild has reached Level") && !message.contains("!")) {
            GuildBridgeClient.LOGGER.info("Detected guild message " + message);
            GuildBridgeWebSocket.sendChat(message);
        }


    }
}
