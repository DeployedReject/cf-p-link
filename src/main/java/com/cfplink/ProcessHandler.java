package com.cfplink;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessHandler {

    private final AppConfig.ServiceConfig service;
    private final Consumer<String> onUrlGenerated;
    private final Runnable onProcessDeath;

    private Process process;
    private Thread outputThread;
    private AtomicBoolean isRunning;
    private String currentUrl;

    // Pattern to match trycloudflare URL, e.g.:
    // https://some-random-words.trycloudflare.com
    private static final Pattern URL_PATTERN = Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");

    public ProcessHandler(AppConfig.ServiceConfig service, Consumer<String> onUrlGenerated, Runnable onProcessDeath) {
        this.service = service;
        this.onUrlGenerated = onUrlGenerated;
        this.onProcessDeath = onProcessDeath;
        this.isRunning = new AtomicBoolean(false);
    }

    public synchronized void start() {
        if (isRunning.get()) return;
        currentUrl = null;

        try {
            System.out.println("[ProcessHandler] Starting tunnel for service " + service.getName() + " on port " + service.getPort());
            
            ProcessBuilder builder = new ProcessBuilder(
                    "cloudflared", "tunnel", "--protocol", "http2", "--url", "http://localhost:" + service.getPort()
            );

            // Merge start out and err so we don't need two readers
            builder.redirectErrorStream(true);
            
            process = builder.start();
            isRunning.set(true);

            outputThread = new Thread(() -> parseOutput(process.getInputStream()));
            outputThread.setDaemon(true);
            outputThread.start();

            // Monitor thread to handle process death
            Thread monitorThread = new Thread(() -> {
                try {
                    process.waitFor();
                    isRunning.set(false);
                    System.out.println("[ProcessHandler] Tunnel for " + service.getName() + " exited.");
                    onProcessDeath.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            monitorThread.setDaemon(true);
            monitorThread.start();

        } catch (IOException e) {
            System.err.println("[ProcessHandler] Failed to start cloudflared for " + service.getName() + ": " + e.getMessage());
            isRunning.set(false);
        }
    }

    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            System.out.println("[ProcessHandler] Stopping tunnel for " + service.getName());
            process.destroy();
        }
        isRunning.set(false);
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    private void parseOutput(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Uncomment line below to see raw output from cloudflared
                // System.out.println("[cloudflared - " + service.getName() + "] " + line);

                if (currentUrl == null) {
                    Matcher matcher = URL_PATTERN.matcher(line);
                    if (matcher.find()) {
                        currentUrl = matcher.group();
                        System.out.println("[ProcessHandler] Found tunnel URL for " + service.getName() + ": " + currentUrl);
                        onUrlGenerated.accept(currentUrl);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[ProcessHandler] Error reading cloudflared output for " + service.getName() + ": " + e.getMessage());
        }
    }
}
