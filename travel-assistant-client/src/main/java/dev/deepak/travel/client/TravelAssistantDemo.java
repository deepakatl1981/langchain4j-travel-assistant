package dev.deepak.travel.client;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;

import java.time.Duration;
import java.util.List;
import java.util.Scanner;

/**
 * Full demo from the article: personality + a local tool (convertCurrency) +
 * two tools served over MCP (getWeather, from the weather-mcp-server module,
 * and flight search, from the public Kiwi.com MCP server) + per-conversation
 * memory, all wired into one AI Service.
 *
 * Runs as an interactive console loop: type messages, get replies, type
 * "exit" or "quit" (or Ctrl-D) to stop.
 *
 * Before running:
 *   1. Make sure Ollama is running locally (ollama serve) with the model
 *      below pulled (ollama pull llama3.2).
 *   2. Build the server:  mvn -pl weather-mcp-server package
 *   3. Update WEATHER_SERVER_JAR below to the resulting jar's absolute path
 *   4. Run this class (see travel-assistant-client's exec-maven-plugin config,
 *      or just run TravelAssistantDemo.main from your IDE)
 *
 * Note: plain "llama3" is not reliable at tool calling. Use llama3.2 (or
 * llama3.1+) here - those are the versions Ollama/LangChain4j tool calling
 * actually targets.
 */
public class TravelAssistantDemo {

    // Point this at weather-mcp-server/target/weather-mcp-server.jar after building it
    private static final String WEATHER_SERVER_JAR =
            System.getProperty("user.dir") + "/../weather-mcp-server/target/weather-mcp-server.jar";

    public static void main(String[] args) throws Exception {
        ChatModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2")
                .timeout(Duration.ofMinutes(2)) // local inference is slower than a hosted API
                .build();

        // weather-mcp-server: our own MCP server, launched as a local subprocess over stdio
        McpTransport weatherTransport = StdioMcpTransport.builder()
                .command(List.of("java", "-jar", WEATHER_SERVER_JAR))
                .logEvents(true) // see the JSON-RPC traffic in your logs
                .build();

        McpClient weatherMcpClient = DefaultMcpClient.builder()
                .key("weather-server")
                .transport(weatherTransport)
                .build();

        // Kiwi.com's public flight search MCP server: someone else's server, reached over
        // Streamable HTTP. No subprocess, no API key - just a URL.
        McpTransport flightTransport = StreamableHttpMcpTransport.builder()
                .url("https://mcp.kiwi.com")
                .logRequests(true)
                .logResponses(true)
                .build();

        McpClient flightMcpClient = DefaultMcpClient.builder()
                .key("kiwi-flight-search")
                .transport(flightTransport)
                .build();

        ToolProvider toolProvider = McpToolProvider.builder()
                .mcpClients(weatherMcpClient, flightMcpClient)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new TravelTools())     // convertCurrency: still local
                .toolProvider(toolProvider)   // getWeather + search-flight: both over MCP
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        try (Scanner scanner = new Scanner(System.in)) {
            String conversationId = "console-user";

            System.out.println("Travel Assistant ready. Type a message (or 'exit' to quit).");
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break; // Ctrl-D / stdin closed
                }
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    continue;
                }
                if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                    break;
                }

                String response = assistant.chat(conversationId, input);
                System.out.println(response);
            }
        } finally {
            weatherMcpClient.close();
            flightMcpClient.close();
        }
    }
}
