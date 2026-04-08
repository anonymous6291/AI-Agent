package ai.agent.model;

import ai.agent.Logger;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Manages communication with the LLM server.
 */
public class Model {
    private static String llmUrl;
    private static String modelName;
    private static float temperature;
    private static int maxTokens;
    private static boolean stream;
    private static List<String> stopCommand;
    private static HttpClient llmConnection;
    private static ObjectMapper jsonHandler;
    private static volatile boolean initialized = false;

    /**
     * Initializes the Model with given configuration.
     *
     * @param config Configuration of the LLM server.
     */
    public static void init(Configuration config) {
        if (initialized) {
            return;
        }
        llmUrl = config.model_url();
        modelName = config.model_name();
        temperature = config.temperature();
        maxTokens = config.max_tokens();
        stream = config.stream();
        stopCommand = config.stop();
        llmConnection = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        jsonHandler = new ObjectMapper();
        jsonHandler.enable(SerializationFeature.INDENT_OUTPUT);
        jsonHandler.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        initialized = true;
    }

    /**
     * Send messages to the LLM server.
     *
     * @param messages List of Messages to be sent.
     * @return Response of the model.
     * @throws Exception Thrown if exception occurred during communication or while parsing response returned by the LLM server.
     */
    public static Message sendPrompt(List<Message> messages) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Model not initialized.");
        }
        if (messages == null) {
            throw new IllegalArgumentException("Message cannot be null.");
        }
        try {
            ModelQuery query = new ModelQuery(modelName, messages, temperature, maxTokens, stream, stopCommand);
            String queryJson = jsonHandler.writeValueAsString(query);
            HttpRequest request = HttpRequest.newBuilder(URI.create(llmUrl)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(queryJson)).build();
            HttpResponse<String> response = llmConnection.send(request, HttpResponse.BodyHandlers.ofString());
            String responseText = response.body();
            return jsonHandler.treeToValue(jsonHandler.readTree(responseText).findPath("message"), Message.class);
        } catch (Exception e) {
            Logger.log("Exception occurred in sendPrompt of Model: " + e, Logger.Type.ERROR);
            throw e;
        }
    }

    record ModelQuery(String model, List<Message> messages, float temperature, int max_tokens, boolean stream,
                      List<String> stop) {
    }

    /**
     * Configuration of the LLM.
     *
     * @param model_url   LLM server url.
     * @param model_name  LLM name.
     * @param temperature Temperature
     * @param max_tokens  Max output tokens.
     * @param stream      Stream output.
     * @param stop        Stop indicator.
     */
    public record Configuration(String model_url, String model_name, int temperature, int max_tokens, boolean stream,
                                List<String> stop) {
    }
}


