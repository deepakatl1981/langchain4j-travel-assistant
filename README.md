# LangChain4j Travel Assistant

Companion code for the Medium article *"Building Your First AI Agent in Java: A LangChain4j Beginner's Guide."*

Two independent Maven modules:

- **weather-mcp-server** — a standalone MCP server exposing one tool, `getWeather`, over stdio (JSON-RPC). It knows nothing about the client that will call it.
- **travel-assistant-client** — the AI Service (`Assistant`). It has a personality (`@SystemMessage`), a local tool (`convertCurrency`), per-conversation memory (`ChatMemoryProvider`), and it pulls tools from two separate MCP servers via `McpToolProvider`: `getWeather` from the module above (stdio) and flight search from Kiwi.com's public MCP server (Streamable HTTP).

Requires JDK 17+, internet access, and a local [Ollama](https://ollama.com) server running with `llama3.2` pulled (`ollama pull llama3.2`). Plain `llama3` is not reliable at tool calling, so use `llama3.2` or newer. Swap `langchain4j-ollama` for `langchain4j-open-ai` or `langchain4j-anthropic` in `travel-assistant-client/pom.xml` if you'd rather use a hosted provider.

All tools call free, keyless external services instead of hardcoded data:
- `convertCurrency` calls the [Frankfurter](https://frankfurter.dev) exchange-rate REST API directly (no MCP involved — it's a local `@Tool`).
- `getWeather` calls [Open-Meteo](https://open-meteo.com) — first its geocoding API to resolve a city name to coordinates, then its forecast API for current conditions — from inside the `weather-mcp-server` module, over MCP.
- Flight search comes from Kiwi.com's official public MCP server at `https://mcp.kiwi.com` (tool name `search-flight`). This one isn't a REST call we wrote — it's a *second, independent MCP server* that `travel-assistant-client` connects to directly over Streamable HTTP, alongside the stdio connection to `weather-mcp-server`. No code of ours implements it; `McpToolProvider` discovers and exposes whatever tools that remote server advertises.

None of these require an API key, but all three require outbound internet access from wherever you run the app.

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

This is now an interactive console loop — type a message, press Enter, read the reply, repeat. Type `exit` or `quit` (or Ctrl-D) to stop. Try things like:

```
> I'm planning a trip to Paris
> What's the weather like there?
> If I bring 500 USD, how much is that in EUR?
> Find me a flight from London to Paris on 2026-09-15
> exit
```

The weather question round-trips over MCP through the separate `weather-mcp-server` process. The currency question is a plain local method call. The flight question round-trips over MCP too, but to `https://mcp.kiwi.com` instead — a server we didn't write and don't run. Same `.chat(...)` call every time — same `conversationId` throughout, so the assistant remembers earlier turns (up to the last 10 messages).

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

## Connecting to a second, external MCP server

`TravelAssistantDemo` builds two `McpClient` instances and passes both to a single `McpToolProvider.builder().mcpClients(weatherMcpClient, flightMcpClient)`:

- `weatherMcpClient` uses `StdioMcpTransport` — it launches `weather-mcp-server` as a local subprocess we built and own.
- `flightMcpClient` uses `StreamableHttpMcpTransport` pointed at `https://mcp.kiwi.com` — a server owned and run by Kiwi.com, reached over plain HTTP. No subprocess, no jar to build, no API key.

`McpToolProvider` merges the tools from both servers into one pool the model can call from. This is the same pattern you'd use to connect to any other public MCP server — swap the URL, drop in the client, add it to `mcpClients(...)`.
