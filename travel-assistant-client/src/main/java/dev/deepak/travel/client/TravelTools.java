package dev.deepak.travel.client;

import dev.langchain4j.agent.tool.Tool;

/**
 * Local tool: stays inside this JAR, called directly, no subprocess involved.
 * Compare with WeatherTools in the weather-mcp-server module, which is the
 * same idea but exposed to this app over MCP instead.
 */
class TravelTools {

    @Tool("Converts an amount from one currency to another")
    double convertCurrency(double amount, String from, String to) {
        // In a real app, this would call a live exchange rate API
        double usdRate = from.equalsIgnoreCase("USD") ? 1.0 : 0.92;
        double targetRate = to.equalsIgnoreCase("USD") ? 1.0 : 0.92;
        return amount * (targetRate / usdRate);
    }
}
