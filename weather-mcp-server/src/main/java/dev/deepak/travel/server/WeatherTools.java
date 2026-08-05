package dev.deepak.travel.server;

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
import java.util.Map;

/**
 * The tool itself is defined exactly the way a local LangChain4j tool would be.
 * @Tool doesn't know or care whether it will be called locally or over MCP.
 *
 * Uses Open-Meteo (https://open-meteo.com), a free, keyless geocoding +
 * forecast API, instead of a hardcoded city table: first resolves the city
 * name to coordinates, then fetches current conditions for those coordinates.
 */
class WeatherTools {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // WMO weather interpretation codes -> human-readable text.
    // https://open-meteo.com/en/docs (see "WMO Weather interpretation codes")
    private static final Map<Integer, String> WEATHER_CODES = Map.ofEntries(
            Map.entry(0, "clear sky"),
            Map.entry(1, "mainly clear"),
            Map.entry(2, "partly cloudy"),
            Map.entry(3, "overcast"),
            Map.entry(45, "fog"),
            Map.entry(48, "depositing rime fog"),
            Map.entry(51, "light drizzle"),
            Map.entry(53, "moderate drizzle"),
            Map.entry(55, "dense drizzle"),
            Map.entry(61, "slight rain"),
            Map.entry(63, "moderate rain"),
            Map.entry(65, "heavy rain"),
            Map.entry(71, "slight snow fall"),
            Map.entry(73, "moderate snow fall"),
            Map.entry(75, "heavy snow fall"),
            Map.entry(80, "slight rain showers"),
            Map.entry(81, "moderate rain showers"),
            Map.entry(82, "violent rain showers"),
            Map.entry(95, "thunderstorm"),
            Map.entry(96, "thunderstorm with slight hail"),
            Map.entry(99, "thunderstorm with heavy hail")
    );

    @Tool("Returns the current weather forecast for a given city")
    String getWeather(String city) {
        try {
            double[] coordinates = geocode(city);
            if (coordinates == null) {
                return "Could not find a location matching \"" + city + "\"";
            }
            return currentConditions(coordinates[0], coordinates[1]);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Weather lookup failed for " + city + ": " + e.getMessage();
        }
    }

    private double[] geocode(String city) throws IOException, InterruptedException {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + encode(city);
        JsonNode results = get(url).get("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return null;
        }
        JsonNode first = results.get(0);
        return new double[] {first.get("latitude").asDouble(), first.get("longitude").asDouble()};
    }

    private String currentConditions(double latitude, double longitude) throws IOException, InterruptedException {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                + "&longitude=" + longitude
                + "&current=temperature_2m,weather_code";
        JsonNode current = get(url).get("current");
        if (current == null) {
            return "Forecast unavailable";
        }
        double temperature = current.get("temperature_2m").asDouble();
        int code = current.get("weather_code").asInt();
        String description = WEATHER_CODES.getOrDefault(code, "conditions code " + code);
        return String.format("%.0f°C, %s", temperature, description);
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
