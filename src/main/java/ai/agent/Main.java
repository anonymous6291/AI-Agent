package ai.agent;

import ai.agent.model.Model;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileInputStream;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    static void main() throws Exception {
        FileInputStream fis = new FileInputStream("./configuration.json");
        String configJSON = new String(fis.readAllBytes());
        fis.close();
        ObjectMapper objectMapper = new ObjectMapper();
        Config config = objectMapper.readValue(configJSON, Config.class);
        Logger.init(config.logger());
        Model.init(config.model());
        AuthorizationManager.init(config.authorization());
        CommunicationManager.init(config.telegram());
    }

    record Config(Model.Configuration model, AuthorizationManager.Configuration authorization,
                  CommunicationManager.Configuration telegram, Logger.Configuration logger) {
    }
}
