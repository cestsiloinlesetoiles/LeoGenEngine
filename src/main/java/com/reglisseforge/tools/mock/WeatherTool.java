package com.reglisseforge.tools.mock;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reglisseforge.tools.base.Param;
import com.reglisseforge.tools.base.Tool;

public class WeatherTool {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Tool(name = "get_weather", description = "Get current mocked weather for a given city.")
	public String getWeatherByCity(
			@Param(name = "city", description = "City name", required = true) String city,
			@Param(name = "unit", description = "Temperature unit: 'C' or 'F'", required = false, defaultValue = "C") String unit
	) throws Exception {
		String normalizedUnit = normalizeUnit(unit);
		MockWeather mock = mockForCity(city);

		double temperature = mock.temperatureCelsius;
		double feelsLike = mock.feelsLikeCelsius;
		if ("F".equalsIgnoreCase(normalizedUnit)) {
			temperature = cToF(temperature);
			feelsLike = cToF(feelsLike);
		}

		Map<String, Object> payload = new HashMap<>();
		payload.put("city", mock.cityLabel);
		payload.put("unit", normalizedUnit.toUpperCase());
		payload.put("temperature", round1(temperature));
		payload.put("feels_like", round1(feelsLike));
		payload.put("humidity", mock.humidityPercent);
		payload.put("wind_kph", mock.windKph);
		payload.put("conditions", mock.conditions);
		payload.put("observed_at", OffsetDateTime.now().toString());

		return JSON.writeValueAsString(payload);
	}

	private static String normalizeUnit(String unit) {
		if (unit == null) return "C";
		String u = unit.trim().toUpperCase();
		return ("F".equals(u)) ? "F" : "C";
	}

	private static double cToF(double c) {
		return (c * 9.0 / 5.0) + 32.0;
	}

	private static double round1(double v) {
		return Math.round(v * 10.0) / 10.0;
	}

	private static MockWeather mockForCity(String city) {
		String key = city == null ? "" : city.trim().toLowerCase();
		return switch (key) {
			case "paris" -> new MockWeather("Paris", 18.2, 17.5, 62, 14.0, "Partly cloudy");
			case "london" -> new MockWeather("London", 16.7, 15.9, 70, 18.5, "Light rain");
			case "new york", "nyc" -> new MockWeather("New York", 22.4, 23.0, 55, 12.3, "Sunny");
			case "tokyo" -> new MockWeather("Tokyo", 24.1, 25.0, 68, 9.8, "Humid, mostly cloudy");
			case "dakar" -> new MockWeather("Dakar", 28.3, 30.1, 58, 20.2, "Clear sky");
			case "casablanca" -> new MockWeather("Casablanca", 23.0, 23.8, 65, 16.2, "Breezy, clear");
			case "abidjan" -> new MockWeather("Abidjan", 29.4, 31.0, 74, 12.7, "Hot and humid");
			default -> new MockWeather(capitalize(city), 21.0, 21.0, 60, 10.0, "Fair");
		};
	}

	private static String capitalize(String input) {
		if (input == null || input.isBlank()) return "Unknown";
		String trimmed = input.trim();
		return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
	}

	private record MockWeather(
			String cityLabel,
			double temperatureCelsius,
			double feelsLikeCelsius,
			int humidityPercent,
			double windKph,
			String conditions
	) {}
}
