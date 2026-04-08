package ai.agent;

import ai.agent.model.Conversation;

import java.time.Duration;

/**
 * Agent represents a single agent.
 * It manages the conversation and performs the tasks.
 */
public class Agent {
    private static final String ROLE = "system";
    private static final String OBSERVATION = "OBSERVATION: ";
    private static final String PROMPT = """
            Your are an AI agent operating in {OS_NAME} operating system. I am a controller and I will give you
            a task provided by user and you have to tell me how to perform the task. You can break the task into
            smaller actions and provide me one at a time. If you need result then you can wait for OBSERVATION: .
            Always return response in valid JSON format as: {"action" : "action_type", "parameter" : "parameter1"}
            where "action" is just a direction to the me, not any code. List of valid actions along with their parameters
            are:
            
            1) {"action" : "run", "parameters" : "shell_command"} = I will execute the command "shell_command" in shell.
            2) {"action" : "send", "parameter" : "file_path"} = I will send the file represented by "file_path" to the user.
            3) {"action" : "status", "parameter" : "invalid"} = tells me that the task cannot be performed
            4) {"action" : "status", "parameter" : "done"} = tells me that the task was completed
            5) {"action" : "status", "parameter" : "fail"} = tells me that the task failed to complete
            
            Points to remember strictly:
            1) Actions are not valid shell command, they are just directions to the controller.
            2) You can use at most one action at a time and wait for OBSERVATION: if required.
            3) Action inside action is invalid for example {"run" : "run command"} is invalid.
            
            Few necessary details are:
            
            1) Current working directory: {CURRENT_WORKING_DIRECTORY}
            2) Environment variables: {ENV_VAR}
            """.replace("{OS_NAME}", System.getProperty("os.name")).replace("CURRENT_WORKING_DIRECTORY", System.getProperty("user.dir")).replace("ENV_VAR", System.getenv().toString());
    private final User user;
    private volatile ActionManager currentActionManager;
    private volatile boolean stop;
    private volatile boolean running;

    /**
     * Creates a new instance of Agent.
     *
     * @param user User information.
     */
    public Agent(User user) {
        this.user = user;
        stop = false;
        running = false;
    }

    private String performSingleTask(ActionManager actionManager, String task) {
        for (int i = 0; i < 5; i++) {
            try {
                user.sendMessage("Performing action [".concat(task).concat("] ."));
                ActionManager.ActionResult result = actionManager.perform(task);
                switch (result.resultType()) {
                    case FAILED, COMPLETED, INVALID -> {
                        user.sendMessage(result.resultValue());
                        return "";
                    }
                    case PENDING -> {
                        return OBSERVATION.concat(result.resultValue());
                    }
                }
            } catch (Exception e) {
                try {
                    user.sendMessage("Error while performing single task: " + e);
                } catch (Exception _) {
                }
                Logger.log("[" + user.getChatID() + "] => Exception inside performTask of Agent: " + e, Logger.Type.ERROR);
            }
        }
        return "";
    }

    private void run() {
        if (isRunning()) {
            return;
        }
        stop = false;
        ActionManager actionManager;
        try {
            user.sendMessage("Agent started.");
            actionManager = new ActionManager(user);
            currentActionManager = actionManager;
        } catch (Exception e) {
            try {
                user.sendMessage("Agent failed to start.");
            } catch (Exception _) {
            }
            return;
        }
        running = true;
        while (!stop) {
            try {
                Conversation conversation = new Conversation(ROLE, PROMPT);
                user.sendMessage("Assign task:");
                String nextTask = user.readMessage();
                while (!stop && nextTask == null) {
                    Thread.sleep(Duration.ofSeconds(2));
                    nextTask = user.readMessage();
                }
                if (stop) {
                    break;
                }
                int i = 0;
                while (i < 10 && !stop) {
                    user.sendMessage("Sending prompt.");
                    String response = conversation.sendPrompt(nextTask);
                    if (stop) {
                        break;
                    }
                    StringBuilder reply = new StringBuilder();
                    int len = response.length(), si = -1, m = 0;
                    for (int j = 0; j < len && !stop; j++) {
                        if (response.charAt(j) == '{') {
                            if (si == -1) {
                                si = j;
                            }
                            m++;
                        } else if (response.charAt(j) == '}') {
                            m--;
                            if (m == 0) {
                                reply.append(performSingleTask(actionManager, response.substring(si, j + 1))).append("\n");
                                si = -1;
                            }
                        }
                    }
                    nextTask = reply.toString();
                    if (!nextTask.contains(OBSERVATION)) {
                        break;
                    }
                    i++;
                }
            } catch (Exception e) {
                try {
                    user.sendMessage("Error: ".concat(e.toString()));
                } catch (Exception _) {
                }
                Logger.log("[" + user.getChatID() + "] => Exception occurred in performTask of Agent: ".concat(e.toString()), Logger.Type.ERROR);
            }
        }
        try {
            actionManager.endTasks();
            user.sendMessage("Agent stopped.");
        } catch (Exception _) {
        } finally {
            running = false;
        }
    }

    /**
     * Starts the agent. It listens for user input, manages the conversation with the LLM and executes actions.
     */
    public void start() {
        if (isRunning()) {
            return;
        }
        new Thread(this::run).start();
    }

    /**
     * Stops the currently running agent and forcibly terminates the action being performed.
     */
    public void stop() {
        stop = true;
        if (currentActionManager != null) {
            currentActionManager.endTasks();
        }
    }

    /**
     * Checks if the agent is running or not.
     *
     * @return True if the agent is running, false otherwise.
     */
    public boolean isRunning() {
        return running;
    }
}