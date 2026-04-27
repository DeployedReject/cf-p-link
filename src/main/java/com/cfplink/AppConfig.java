package com.cfplink;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    private String cloudflareApiToken;
    private String cloudflareAccountId;
    private String workerName;
    private List<ServiceConfig> services = new ArrayList<>();

    public String getCloudflareApiToken() { return cloudflareApiToken; }
    public void setCloudflareApiToken(String cloudflareApiToken) { this.cloudflareApiToken = cloudflareApiToken; }

    public String getCloudflareAccountId() { return cloudflareAccountId; }
    public void setCloudflareAccountId(String cloudflareAccountId) { this.cloudflareAccountId = cloudflareAccountId; }

    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }

    public List<ServiceConfig> getServices() { return services; }
    public void setServices(List<ServiceConfig> services) { this.services = services; }

    public static class ServiceConfig {
        private String name;
        private int port;
        private String path;

        public ServiceConfig() {}

        public ServiceConfig(String name, int port, String path) {
            this.name = name;
            this.port = port;
            this.path = path;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
