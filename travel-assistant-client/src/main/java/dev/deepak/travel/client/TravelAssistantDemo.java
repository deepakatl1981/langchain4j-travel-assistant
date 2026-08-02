package dev.deepak.travel.client;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;

import java.util.List;

/**
 * Full demo from the article: personality + a local tool (convertCurrency) +
 * a tool served over MCP (getWeather, from the weather-mcp-server module) +
 * per-conversation memory, all wired into one AI Service.
 *
 * Before running:
 *   1. export OPENAI_API_KEY=sk-...
 *   2. Build the server:  mvn -pl weather-mcp-server package
 *   3. Update WEATHER_SERVER_JAR below to the resulting jar's absolute path
 *   4. Run this class (see travel-assistant-client's exec-maven-plugin config,
 *      or just run TravelAssistantDemo.main from your IDE)
 */
public class TravelAssistantDemo {

    // Point this at weather-mcp-server/target/weather-mcp-server.jar after building it
    private static final String WEATHER_SERVER_JAR =
            System.getProperty("user.dir") + "/../weather-mcp-server/target/weather-mcp-server.jar";

    public static void main(String[] args) throws Exception {
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini")
                .build();

        McpTransport transport = StdioMcpTransport.builder()
                .command(List.of("java", "-jar", WEATHER_SERVER_JAR))
                .logEvents(true) // see the JSON-RPC traffic in your logs
                .build();

        McpClient mcpClient = DefaultMcpClient.builder()
                .key("weather-server")
                .transport(transport)
                .build();

        ToolProvider weatherToolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new TravelTools())              // convertCurrency: still local
                .toolProvider(weatherToolProvider)      // getWeather: now over MCP
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        try {
            String conversationId = "demo-user";

            System.out.println(assistant.chat(conversationId, "I'm planning a trip to Paris"));
            System.out.println(assistant.chat(conversationId, "What's the weather like there?"));
            System.out.println(assistant.chat(conversationId, "If I bring 500 USD, how much is that in EUR?"));
        } finally {
            mcpClient.close();
        }
    }
}
