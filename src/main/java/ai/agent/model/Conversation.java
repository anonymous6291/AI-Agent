package ai.agent.model;

import ai.agent.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a single conversation between the model and the agent.
 * It also saves the messages during the conversation.
 */
public class Conversation {
    private final static ObjectMapper objectMapper = new ObjectMapper();
    private final List<Message> messages;

    /**
     * @param initialRole    Initial role
     * @param initialContent Initial prompt
     */
    public Conversation(String initialRole, String initialContent) {
        messages = new ArrayList<>();
        messages.add(new Message(initialRole, initialContent));
    }

    /**
     * Sends prompt to the model.
     *
     * @param content The prompt to be sent.
     * @return Response of the model
     * @throws Exception If communication resulted in an exception.
     */
    public String sendPrompt(String content) throws Exception {
        Message currentQuery = new Message("user", content);
        messages.add(currentQuery);
        try {
            Message response = Model.sendPrompt(messages);
            messages.add(response);
            return response.content();
        } catch (Exception e) {
            messages.remove(currentQuery);
            Logger.log("Exception occurred in sendPrompt of Conversation: " + e, Logger.Type.ERROR);
            throw e;
        }
    }

    @Override
    public String toString() {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            return "";
        }
    }
}