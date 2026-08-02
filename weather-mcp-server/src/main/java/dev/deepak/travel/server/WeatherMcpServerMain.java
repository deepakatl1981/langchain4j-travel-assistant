package dev.deepak.travel.server;

import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.protocol.McpImplementation;

import java.util.List;

/**
 * Standalone MCP server. Build a runnable jar with `mvn -pl weather-mcp-server package`
 * and start it with `java -jar weather-mcp-server/target/weather-mcp-server.jar`.
 *
 * CAUTION: StdioMcpServerTransport writes the JSON-RPC protocol itself to System.out.
 * Never println/log to stdout here - route logs to System.err instead, or you'll
 * corrupt the protocol stream and the client will silently disconnect.
 */
public class WeatherMcpServerMain {

    public static void main(String[] args) throws Exception {
        McpImplementation serverInfo = new McpImplementation();
        serverInfo.setName("travel-weather-mcp-server");
        serverInfo.setVersion("1.0.0");

        McpServer server = new McpServer(List.of(new WeatherTools()), serverInfo);
        new StdioMcpServerTransport(System.in, System.out, server);

        System.err.println("travel-weather-mcp-server is up, listening on stdio");

        // Keep the process alive while stdio is open
        Thread.currentThread().join();
    }
}
