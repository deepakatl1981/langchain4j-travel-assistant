package dev.deepak.travel.server;

import dev.langchain4j.agent.tool.Tool;

/**
 * The tool itself is defined exactly the way a local LangChain4j tool would be.
 * @Tool doesn't know or care whether it will be called locally or over MCP.
 */
class WeatherTools {

    @Tool("Returns the current weather forecast for a given city")
    String getWeather(String city) {
        // In a real app, this would call a live weather API
        return switch (city.toLowerCase()) {
            case "paris" -> "18°C, light rain";
            case "tokyo" -> "24°C, clear skies";
            case "london" -> "15°C, overcast";
            case "new york" -> "21°C, partly cloudy";
            default -> "Forecast unavailable for " + city;
        };
    }
}
