package dev.deepak.travel.client;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * A plain Java interface. LangChain4j generates a working implementation of
 * this at runtime (see AiServices.builder(...) in TravelAssistantDemo) -
 * no implementation class needed, the model is the implementation.
 */
interface Assistant {

    @SystemMessage("""
        You are a friendly travel planning assistant.
        Use the weather, currency, and flight search tools when relevant.
        The flight search tool needs an origin, a destination, and a travel
        date - ask the user for any of these that are missing before calling it.
        Keep answers short and practical.
        """)
    String chat(@MemoryId String conversationId, @UserMessage String message);
}
