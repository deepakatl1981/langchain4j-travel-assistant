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
        Use the weather and currency tools when relevant.
        Keep answers short and practical.
        """)
    String chat(@MemoryId String conversationId, @UserMessage String message);
}
