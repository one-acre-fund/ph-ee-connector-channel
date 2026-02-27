package org.mifos.connector.channel.camel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "identity.channel")
public class ClientProperties {

    private List<Client> clients = new ArrayList<>();

    public ClientProperties() {
    }

    public List<Client> getClients() {
        return clients;
    }

    public void setClients(List<Client> clients) {
        this.clients = clients;
    }

    public Client getClient(String tenant) {
        // This is the login client on PH Ops App. If we fail to get the country-specific login, we use the default OAF ones
        return getClients().stream()
                .filter(t -> t.getTenant().equals(tenant))
                .findFirst()
                .orElse(getClients().stream()
                        .filter(t -> t.getTenant().equals("oaf"))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Client for tenant: " + tenant + ", not configured!")));
    }
}
