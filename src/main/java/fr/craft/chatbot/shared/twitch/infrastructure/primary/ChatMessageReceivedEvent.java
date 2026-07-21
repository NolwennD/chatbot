package fr.craft.chatbot.shared.twitch.infrastructure.primary;

import fr.craft.chatbot.shared.twitch.domain.ChatMessage;

public record ChatMessageReceivedEvent(ChatMessage chatMessage) {}
