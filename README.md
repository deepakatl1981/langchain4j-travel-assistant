# LangChain4j Travel Assistant

Companion code for the Medium article *"Building Your First AI Agent in Java: A LangChain4j Beginner's Guide."*

Two independent Maven modules:

- **weather-mcp-server** — a standalone MCP server exposing one tool, `getWeather`, over stdio (JSON-RPC). It knows nothing about the client that will call it.
- **travel-assistant-client** — the AI Service (`Assistant`). It has a personality (`@SystemMessage`), a local tool (`convertCurrency`), per-conversation memory (`ChatMemoryProvider`), and it pulls `getWeather` from the MCP server above via `McpToolProvider`.

Requires JDK 17+ and a local [Ollama](https://ollama.com) server running with `llama3.2` pulled (`ollama pull llama3.2`). Plain `llama3` is not reliable at tool calling, so use `llama3.2` or newer. Swap `langchain4j-ollama` for `langchain4j-open-ai` or `langchain4j-anthropic` in `travel-assistant-client/pom.xml` if you'd rather use a hosted provider.

## 1. Build everything

```bash
ollama serve          # if not already running as a background service
mvn clean package
```

This builds both modules. `weather-mcp-server/target/weather-mcp-server.jar` is a runnable fat jar (via `maven-shade-plugin`).

## 2. Run the MCP server on its own (optional sanity check)

```bash
java -jar weather-mcp-server/target/weather-mcp-server.jar
```

It will print `travel-weather-mcp-server is up, listening on stdio` to stderr and then sit there waiting for JSON-RPC input. Ctrl-C to stop it — this step is just to confirm it starts cleanly; the client will launch and manage it as a subprocess automatically.

## 3. Run the full demo

`TravelAssistantDemo` launches the weather server itself (via `StdioMcpTransport`), so you don't need to start it manually. It looks for the server jar at `../weather-mcp-server/target/weather-mcp-server.jar` relative to the client module — adjust `WEATHER_SERVER_JAR` in `TravelAssistantDemo.java` if you move things around.

```bash
cd travel-assistant-client
mvn exec:java
```

Expected output (wording will vary, the model writes the final sentences itself):

```
Paris is a great choice! Let me know if you'd like help with anything specific.
Right now it's 18°C with light rain in Paris, you might want a light jacket.
500 USD converts to about 460 EUR at current rates.
```

The second line comes back over MCP, round-tripping through the separate `weather-mcp-server` process. The third line is a plain local method call. Same `.chat(...)` call both times.

## Project layout

```
langchain4j-travel-assistant/
├── pom.xml                          parent (dependency management, shared build config)
├── weather-mcp-server/
│   ├── pom.xml
│   └── src/main/java/dev/deepak/travel/server/
│       ├── WeatherTools.java        the @Tool method
│       └── WeatherMcpServerMain.java the stdio server entry point
└── travel-assistant-client/
    ├── pom.xml
    └── src/main/java/dev/deepak/travel/client/
        ├── Assistant.java           the AI Service interface
        ├── TravelTools.java         the local @Tool (convertCurrency)
        └── TravelAssistantDemo.java wires model + tools + memory + MCP, runs the demo
```

## Where each part is explained

See the article for the walkthrough of each piece: AI Services, `@SystemMessage`, `@Tool`, `ChatMemoryProvider` / `@MemoryId`, and the MCP client/server split.
