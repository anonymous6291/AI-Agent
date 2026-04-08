package ai.agent.model;

/**
 * Represents a single message.
 *
 * @param role    Role
 * @param content Content
 */
public record Message(String role, String content) {
}