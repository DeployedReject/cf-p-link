package com.cfplink;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TunnelDaemon {

    private static final String CONFIG_FILE_NAME = "config.json";
    private static final Map<String, ProcessHandler> handlers = new ConcurrentHashMap<>();
    private static final Map<String, String> currentUrls = new ConcurrentHashMap<>();
    
    private static WorkerManager workerManager;
    private static AppConfig config;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--install-systemd")) {
            generateSystemdService();
            return;
        }

        loadConfig();
        
        workerManager = new WorkerManager(config);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Daemon] Shutting down manager and stopping all tunnels...");
            handlers.values().forEach(ProcessHandler::stop);
            scheduler.shutdownNow();
        }));

        System.out.println("[Daemon] Starting tunnel processes...");
        
        for (AppConfig.ServiceConfig service : config.getServices()) {
            startService(service);
        }

        // Keep the main thread alive
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void loadConfig() {
        Path configPath = Paths.get(CONFIG_FILE_NAME);
        if (!Files.exists(configPath)) {
            System.err.println("[Daemon] Fatal Error: Configuration file " + CONFIG_FILE_NAME + " not found!");
            System.err.println("[Daemon] Please run the SetupCLI first to configure the system.");
            System.exit(1);
        }

        try {
            String jsonStr = new String(Files.readAllBytes(configPath));
            Gson gson = new Gson();
            config = gson.fromJson(jsonStr, AppConfig.class);
            System.out.println("[Daemon] Loaded config successfully with " + config.getServices().size() + " services.");
        } catch (IOException e) {
            System.err.println("[Daemon] Failed to read " + CONFIG_FILE_NAME + ": " + e.getMessage());
            System.exit(1);
        }
    }

    private static synchronized void startService(AppConfig.ServiceConfig service) {
        ProcessHandler handler = new ProcessHandler(
            service, 
            (url) -> {
                // On URL generated
                currentUrls.put(service.getPath(), url);
                checkAndDeployWorker();
            }, 
            () -> {
                // On Process Death
                System.out.println("[Daemon] Detected crash for " + service.getName() + " tunnel. Scheduling restart in 5 seconds.");
                currentUrls.remove(service.getPath());
                handlers.remove(service.getName());
                checkAndDeployWorker(); // Might want to deploy worker to clear dead route or serve fallback
                scheduler.schedule(() -> startService(service), 5, TimeUnit.SECONDS);
            }
        );
        handlers.put(service.getName(), handler);
        handler.start();
    }

    private static synchronized void checkAndDeployWorker() {
        // Deploy if all running services have a mapped URL
        // If some are restarting, maybe we still deploy to keep the healthy ones active?
        // We evaluate deployment every time there is a URL update or removal.
        Map<String, String> routes = new HashMap<>(currentUrls);
        
        // Wait, if no routes are available at all, maybe default page is better
        // The worker will still show the healthy ones.
        workerManager.deployWorker(routes);
    }

    private static void generateSystemdService() {
        String currentPath = Paths.get("").toAbsolutePath().toString();
        // Determine java path
        String javaExec = System.getProperty("java.home") + "/bin/java";
        String userName = System.getProperty("user.name");
        
        String serviceFile = "[Unit]\n" +
                "Description=Cloudflare Persistent Tunnel Manager\n" +
                "After=network-online.target\n" +
                "Wants=network-online.target\n\n" +
                "[Service]\n" +
                "Type=simple\n" +
                "User=" + userName + "\n" +
                "WorkingDirectory=" + currentPath + "\n" +
                "ExecStart=" + javaExec + " -cp target/setup-cli-jar-with-dependencies.jar target/tunnel-daemon-jar-with-dependencies.jar com.cfplink.TunnelDaemon\n" +
                "Restart=always\n" +
                "RestartSec=10\n\n" +
                "[Install]\n" +
                "WantedBy=multi-user.target\n";
        
        // Wait, the fat jar name is tunnel-daemon-jar-with-dependencies.jar but the assembly is setup-cli/tunnel-daemon! 
        // Actual exec string should just be the compiled target: `java -jar target/tunnel-daemon-jar-with-dependencies.jar` 
        // But since we use main class in manifest, let's just do `java -jar target/tunnel-daemon-1.0-SNAPSHOT-jar-with-dependencies.jar` 
        // Or wait, in pom.xml I used finalName "tunnel-daemon". And appendAssemblyId was false.
        // So the final jar will be "target/tunnel-daemon.jar".
        
        String correctExecStart = javaExec + " -jar " + currentPath + "/target/tunnel-daemon.jar";

        String finalServiceFile = serviceFile.replace("ExecStart=" + javaExec + " -cp target/setup-cli-jar-with-dependencies.jar target/tunnel-daemon-jar-with-dependencies.jar com.cfplink.TunnelDaemon", "ExecStart=" + correctExecStart);
        
        Path systemdPath = Paths.get("cf-p-link.service");
        try {
            Files.write(systemdPath, finalServiceFile.getBytes());
            System.out.println("Systemd service file created at " + systemdPath.toAbsolutePath());
            System.out.println("To install it, run:");
            System.out.println("  sudo cp cf-p-link.service /etc/systemd/system/");
            System.out.println("  sudo systemctl daemon-reload");
            System.out.println("  sudo systemctl enable --now cf-p-link.service");
        } catch (IOException e) {
            System.err.println("Failed to write systemd service file: " + e.getMessage());
        }
    }
}
