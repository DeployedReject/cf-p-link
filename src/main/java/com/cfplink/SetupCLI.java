package com.cfplink;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class SetupCLI {

    private static final String CONFIG_FILE_NAME = "config.json";

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println("      Cloudflare Persistent Tunnel Manager - Setup");
        System.out.println("=========================================================");
        System.out.println("Welcome to the Setup Wizard! We will help you configure ");
        System.out.println("your reverse proxy mappings and Cloudflare Worker API.");
        System.out.println("");
        System.out.println("TUTORIAL: Getting your Cloudflare API Token");
        System.out.println("1. Log in to your Cloudflare Dashboard (dash.cloudflare.com).");
        System.out.println("2. Go to 'My Profile' (top right) -> 'API Tokens'.");
        System.out.println("3. Click 'Create Token' -> 'Create Custom Token'.");
        System.out.println("4. Permissions needed: ");
        System.out.println("   - Account -> Worker Scripts -> Edit");
        System.out.println("   - Account -> Account Settings -> Read");
        System.out.println("5. Continue to summary and 'Create Token'.");
        System.out.println("=========================================================\n");

        Scanner scanner = new Scanner(System.in);
        AppConfig config = new AppConfig();

        System.out.print("Enter your Cloudflare API Token: ");
        String token = scanner.nextLine().trim();
        config.setCloudflareApiToken(token);

        System.out.println("Fetching your Cloudflare Account ID automatically...");
        String accountId = fetchAccountId(token);
        if (accountId == null) {
            System.err.println("Could not automatically retrieve Account ID. Please ensure your API Token is valid and has 'Account Settings -> Read' permissions.");
            System.exit(1);
        }
        System.out.println("Found Account ID: " + accountId);
        config.setCloudflareAccountId(accountId);

        System.out.print("Enter a name for your Cloudflare Worker (default: cf-p-link-router): ");
        String wName = scanner.nextLine().trim();
        if (wName.isEmpty()) {
            wName = "cf-p-link-router";
        }
        config.setWorkerName(wName);

        int numServices = 0;
        while (true) {
            System.out.print("Enter the number of services you wish to host (e.g., 2): ");
            try {
                numServices = Integer.parseInt(scanner.nextLine().trim());
                if (numServices > 0) break;
                System.out.println("Please enter a number greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }

        for (int i = 1; i <= numServices; i++) {
            System.out.println("\n--- Service " + i + " ---");
            System.out.print("Enter the name of the service (e.g., WebApp): ");
            String name = scanner.nextLine().trim();

            int port = 80;
            while (true) {
                System.out.print("Enter the local port where '" + name + "' is running (e.g., 8080): ");
                try {
                    port = Integer.parseInt(scanner.nextLine().trim());
                    if (port > 0 && port <= 65535) break;
                    System.out.println("Please enter a valid port number (1-65535).");
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a valid port number.");
                }
            }

            System.out.print("Enter the URL path for this service (e.g., /webapp, default is /" + name.toLowerCase().replace(" ", "") + "): ");
            String path = scanner.nextLine().trim();
            if (path.isEmpty()) {
                path = "/" + name.toLowerCase().replace(" ", "");
            } else if (!path.startsWith("/")) {
                path = "/" + path;
            }

            System.out.print("Is this an API endpoint (uses transparent reverse proxy instead of 302 Redirect)? (y/n, default: n): ");
            String isApiInput = scanner.nextLine().trim().toLowerCase();
            boolean isApi = isApiInput.equals("y") || isApiInput.equals("yes");

            config.getServices().add(new AppConfig.ServiceConfig(name, port, path, isApi));
        }

        System.out.println("\nConfiguration complete! Saving to " + CONFIG_FILE_NAME + "...");
        
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(config);

        Path configPath = Paths.get(CONFIG_FILE_NAME);
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            writer.write(jsonOutput);
            System.out.println("Success! The configuration has been saved.");
            System.out.println("\nYou can now start the daemon using:");
            System.out.println("  java -cp target/cf-p-link-1.0-SNAPSHOT-jar-with-dependencies.jar com.cfplink.TunnelDaemon");
        } catch (IOException e) {
            System.err.println("Fatal Error: Could not write to " + CONFIG_FILE_NAME);
            e.printStackTrace();
        }

        scanner.close();
    }

    private static String fetchAccountId(String apiToken) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.cloudflare.com/client/v4/accounts"))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                if (jsonResponse.has("success") && jsonResponse.get("success").getAsBoolean()) {
                    JsonArray results = jsonResponse.getAsJsonArray("result");
                    if (results.size() > 0) {
                        return results.get(0).getAsJsonObject().get("id").getAsString();
                    } else {
                        System.err.println("API returned success, but no accounts were found.");
                    }
                } else {
                    System.err.println("API returned an error: " + response.body());
                }
            } else {
                System.err.println("HTTP Error " + response.statusCode() + " when fetching accounts. Ensure your token is correct.");
            }
        } catch (Exception e) {
            System.err.println("Exception while fetching Account ID: " + e.getMessage());
        }
        return null;
    }
}
