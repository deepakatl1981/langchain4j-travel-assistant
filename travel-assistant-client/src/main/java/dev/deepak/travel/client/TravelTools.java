package dev.deepak.travel.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Local tool: stays inside this JAR, called directly, no subprocess involved.
 * Compare with WeatherTools in the weather-mcp-server module, which is the
 * same idea but exposed to this app over MCP instead.
 *
 * Uses the free, keyless Frankfurter exchange-rate API (https://frankfurter.dev)
 * instead of a hardcoded rate.
 */
class TravelTools {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Tool("Converts an amount from one currency to another using current exchange rates")
    double convertCurrency(double amount, String from, String to) {
        String fromCode = from.trim().toUpperCase();
        String toCode = to.trim().toUpperCase();

        if (fromCode.equals(toCode)) {
            return amount;
        }

        try {
            String url = "https://api.frankfurter.dev/v1/latest?amount=" + amount
                    + "&from=" + encode(fromCode)
                    + "&to=" + encode(toCode);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "Exchange rate API returned HTTP " + response.statusCode() + ": " + response.body());
            }

            JsonNode rates = MAPPER.readTree(response.body()).get("rates");
            if (rates == null || !rates.has(toCode)) {
                throw new IllegalStateException("No exchange rate found for " + fromCode + " -> " + toCode);
            }
            return rates.get(toCode).asDouble();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to fetch exchange rate for " + fromCode + " -> " + toCode, e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
