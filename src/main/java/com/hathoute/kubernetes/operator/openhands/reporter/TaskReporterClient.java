package com.hathoute.kubernetes.operator.openhands.reporter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TaskReporterClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskReporterClient.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String SHUTDOWN_ENDPOINT = "/shutdown";
  private static final String HEALTH_ENDPOINT = "/health";
  private static final String EVENTS_ENDPOINT = "/events";

  private final String baseUrl;
  private final HttpClient httpClient;

  public TaskReporterClient(final String baseUrl) {
    this.baseUrl = baseUrl;
    this.httpClient = HttpClient.newHttpClient();
  }

  public Health.Status health() {
    try {
      final var response = sendGetRequest(HEALTH_ENDPOINT);
      if (response.statusCode() != 200) {
        LOGGER.warn("Received HTTP status code {}, health is UNKNOWN", response.statusCode());
        return Health.Status.UNKNOWN;
      }

      final var mapped = MAPPER.readValue(response.body(), Health.class);
      return mapped.status();
    } catch (final IOException e) {
      LOGGER.warn("Failed to read health response", e);
      return Health.Status.UNKNOWN;
    }
  }

  public List<Map<String, Object>> getEvents() throws IOException {
    final var response = sendGetRequest(EVENTS_ENDPOINT);
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Received status code " + response.statusCode());
    }

    return MAPPER.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {
    });
  }

  public boolean shutdown() {
    try {
      final var response = sendPostRequest(SHUTDOWN_ENDPOINT);
      return response.statusCode() == 200;
    } catch (final IOException e) {
      return false;
    }
  }

  private HttpResponse<String> sendGetRequest(final String path) throws IOException {
    final var request = HttpRequest.newBuilder().uri(URI.create(baseUrl + path)).GET().build();

    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Thread interrupted");
    }
  }

  private HttpResponse<String> sendPostRequest(final String path) throws IOException {
    final var request = HttpRequest.newBuilder()
                                   .uri(URI.create(baseUrl + path))
                                   .POST(HttpRequest.BodyPublishers.noBody())
                                   .build();
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Thread interrupted");
    }
  }

}
