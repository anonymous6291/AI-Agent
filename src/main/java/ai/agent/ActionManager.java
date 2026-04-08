package ai.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.time.Duration;

/**
 * ActionManager manages the action to be performed.
 * It executes commands, send files, etc.
 */
public class ActionManager {
    private static final boolean IS_WIN_OS = System.getProperty("os.name").contains("ind");
    private static final String SHELL_NAME = IS_WIN_OS ? "cmd" : "bash";
    private static final ProcessBuilder processBuilder = new ProcessBuilder(SHELL_NAME);
    private static final String EXIT_STRING = "[END_OF_RESULT]";
    private static final String TRAILING_COMMAND = "echo \"\n" + EXIT_STRING + "\"\n";
    private static final String APPEND_COMMAND = " && " + TRAILING_COMMAND;
    private static final Duration BUFFER_RECHECK_DELAY = Duration.ofMillis(200);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private final User user;
    private final Process shellProcess;
    private final BufferedWriter shellWriter;
    private final BufferedReader shellReader;

    /**
     * Create a new instance.
     *
     * @param user User information.
     * @throws Exception Thrown if error occurs during process creation.
     */
    ActionManager(User user) throws Exception {
        this.user = user;
        shellProcess = processBuilder.start();
        shellWriter = shellProcess.outputWriter();
        shellReader = shellProcess.inputReader();
    }

    private String runCommand(String command) throws Exception {
        shellWriter.write(command.concat(APPEND_COMMAND));
        shellWriter.flush();
        shellWriter.write(TRAILING_COMMAND);
        shellWriter.flush();
        StringBuilder result = new StringBuilder();
        while (shellProcess.isAlive()) {
            if (shellReader.ready()) {
                String output = shellReader.readLine();
                if (output != null) {
                    if (output.contains(EXIT_STRING)) {
                        break;
                    }
                    result.append(output).append("\n");
                }
            }
            String response = user.readMessage();
            if (response != null) {
                shellWriter.write(response);
                shellWriter.flush();
            }
            try {
                Thread.sleep(BUFFER_RECHECK_DELAY);
            } catch (Exception _) {
            }
        }
        return result.toString();
    }

    private boolean askForUserPermission(String task) throws Exception {
        user.sendMessage(task.concat(" [Y/N]?"));
        return user.waitForMessage().matches("[y|Y]");
    }

    /**
     * Performs the action.
     *
     * @param action Action to be performed.
     * @return Result of the action.
     */
    public ActionResult perform(String action) {
        try {
            ActionCommand actionCommand = objectMapper.readValue(action, ActionCommand.class);
            String actionType = actionCommand.action();
            switch (actionType) {
                case "run" -> {
                    String command = actionCommand.parameter();
                    Logger.log("[" + user.getChatID() + "] => Executing the command [" + command + "].", Logger.Type.WARN);
                    if (askForUserPermission("You want to execute [".concat(command).concat("] ?"))) {
                        user.sendMessage("Executing the command.");
                        Logger.log("[" + user.getChatID() + "] => Executed the command [" + command + "].", Logger.Type.WARN);
                        String result = runCommand(command);
                        user.sendMessage("Result:\n".concat(result));
                        return new ActionResult(ResultType.PENDING, result);
                    } else {
                        Logger.log("[" + user.getChatID() + "] => Aborted execution of command [" + command + "].", Logger.Type.INFO);
                        user.sendMessage("Command execution aborted.");
                        return new ActionResult(ResultType.FAILED, "Execution of command denied by user.");
                    }
                }
                case "send" -> {
                    String path = actionCommand.parameter();
                    File target = new File(path);
                    if (target.exists() && target.isFile()) {
                        if (askForUserPermission("You want to receive file [".concat(path).concat("] ?"))) {
                            user.sendMessage("Sending file [".concat(path).concat("] ."));
                            Logger.log("[" + user.getChatID() + "] => Sending file [".concat(path).concat("] ."), Logger.Type.WARN);
                            if (user.sendFile(path)) {
                                Logger.log("[" + user.getChatID() + "] => File [".concat(path).concat("] sent."), Logger.Type.WARN);
                                return new ActionResult(ResultType.PENDING, "File [".concat(path).concat("] sent."));
                            } else {
                                return new ActionResult(ResultType.FAILED, "File [".concat(path).concat("] failed to send."));
                            }
                        } else {
                            Logger.log("[" + user.getChatID() + "] => Sending of file [".concat(path).concat("] denied."), Logger.Type.WARN);
                            return new ActionResult(ResultType.FAILED, "Sending of file denied by user.");
                        }
                    } else {
                        return new ActionResult(ResultType.FAILED, "File [".concat(path).concat("] doesn't exist."));
                    }
                }
                case "status" -> {
                    if (actionCommand.parameter().equals("done")) {
                        return new ActionResult(ResultType.COMPLETED, "Task completed.");
                    } else {
                        return new ActionResult(ResultType.FAILED, "Task failed to complete.");
                    }
                }
                default -> {
                    return new ActionResult(ResultType.INVALID, "Invalid action.");
                }
            }
        } catch (Exception e) {
            Logger.log("[" + user.getChatID() + "] => Exception occurred in ActionManager: ".concat(e.toString()), Logger.Type.ERROR);
            return new ActionResult(ResultType.FAILED, e.toString());
        }
    }

    /**
     * Forcibly terminates the shell and ends all tasks.
     */
    public void endTasks() {
        if (!shellProcess.isAlive()) {
            return;
        }
        try {
            try {
                user.sendMessage("Killing process.");
            } catch (Exception _) {
            }
            ProcessBuilder destroy;
            if (IS_WIN_OS) {
                destroy = new ProcessBuilder("taskkill", "/F", "/PID", Long.toString(shellProcess.pid()));
            } else {
                destroy = new ProcessBuilder("kill", "-SIGKILL", Long.toString(shellProcess.pid()));
            }
            destroy.start().waitFor();
            user.sendMessage("Process killed.");
        } catch (Exception _) {
            try {
                user.sendMessage("Failed to kill the process.");
            } catch (Exception _) {
            }
        }
    }

    /**
     * Result type of the action performed.
     */
    public enum ResultType {
        FAILED, INVALID, PENDING, COMPLETED
    }

    private record ActionCommand(String action, String parameter) {
    }

    /**
     * Result of the action.
     *
     * @param resultType  Type of result.
     * @param resultValue Value of the result.
     */
    public record ActionResult(ActionManager.ResultType resultType, String resultValue) {
    }
}

