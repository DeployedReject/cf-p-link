package com.cfplink;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;

public class WorkerManager {

    private final AppConfig config;
    private final HttpClient httpClient;

    public WorkerManager(AppConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
    }

    public synchronized void deployWorker(Map<String, String> pathToUrlMap) {
        System.out.println("[WorkerManager] Generating Worker script...");
        String scriptContent = generateWorkerScript(pathToUrlMap);

        System.out.println("[WorkerManager] Deploying Worker to Cloudflare...");
        
        try {
            String url = String.format("https://api.cloudflare.com/client/v4/accounts/%s/workers/scripts/%s",
                    config.getCloudflareAccountId(), config.getWorkerName());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + config.getCloudflareApiToken())
                    .header("Content-Type", "application/javascript")
                    .PUT(HttpRequest.BodyPublishers.ofString(scriptContent))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("[WorkerManager] Successfully uploaded worker script!");
                
                // Deploying to workers.dev subdomain
                System.out.println("[WorkerManager] Enabling worker on workers.dev subdomain...");
                String subdomainUrl = String.format("https://api.cloudflare.com/client/v4/accounts/%s/workers/scripts/%s/subdomain",
                        config.getCloudflareAccountId(), config.getWorkerName());
                
                HttpRequest subdomainRequest = HttpRequest.newBuilder()
                        .uri(URI.create(subdomainUrl))
                        .header("Authorization", "Bearer " + config.getCloudflareApiToken())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"enabled\":true}"))
                        .build();

                HttpResponse<String> subResponse = httpClient.send(subdomainRequest, HttpResponse.BodyHandlers.ofString());

                if (subResponse.statusCode() >= 200 && subResponse.statusCode() < 300) {
                    System.out.println("[WorkerManager] The services should be accessible at: https://" 
                                       + config.getWorkerName() + ".*.workers.dev (your workers.dev subdomain)");
                } else {
                    System.err.println("[WorkerManager] Failed to enable subdomain! HTTP Status: " + subResponse.statusCode());
                    System.err.println("[WorkerManager] Response body: " + subResponse.body());
                }
            } else {
                System.err.println("[WorkerManager] Failed to upload Worker script! HTTP Status: " + response.statusCode());
                System.err.println("[WorkerManager] Response body: " + response.body());
            }

        } catch (Exception e) {
            System.err.println("[WorkerManager] Exception during deployment: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String generateWorkerScript(Map<String, String> pathToUrlMap) {
        StringBuilder serviceMapJs = new StringBuilder("const serviceMap = {\n");
        StringBuilder serviceNamesJs = new StringBuilder("const serviceNames = {\n");

        for (AppConfig.ServiceConfig service : config.getServices()) {
            if (pathToUrlMap.containsKey(service.getPath())) {
                String tunnelUrl = pathToUrlMap.get(service.getPath());
                serviceMapJs.append(String.format("  \"%s\": \"%s\",\n", service.getPath(), tunnelUrl));
                serviceNamesJs.append(String.format("  \"%s\": \"%s (Port %d)\",\n", service.getPath(), service.getName(), service.getPort()));
            }
        }
        serviceMapJs.append("};\n\n");
        serviceNamesJs.append("};\n\n");

        return serviceMapJs.toString() + serviceNamesJs.toString() +
               "addEventListener('fetch', event => {\n" +
               "  event.respondWith(handleRequest(event.request));\n" +
               "});\n" +
               "\n" +
               "async function handleRequest(request) {\n" +
               "  const url = new URL(request.url);\n" +
               "  const path = url.pathname;\n" +
               "\n" +
               "  if (path === \"/\" || path === \"\") {\n" +
               "    let html = `<html><head><title>Hosted Services</title>\n" +
               "    <style>\n" +
               "      body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 2rem; background: #0f172a; color: #f8fafc; }\n" +
               "      a { color: #38bdf8; text-decoration: none; font-size: 1.25rem; transition: color 0.2s ease; }\n" +
               "      a:hover { color: #7dd3fc; }\n" +
               "      .container { max-width: 800px; margin: 0 auto; background: #1e293b; padding: 2rem; border-radius: 12px; box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1); }\n" +
               "      h1 { border-bottom: 2px solid #334155; padding-bottom: 1rem; margin-bottom: 1.5rem; }\n" +
               "      ul { list-style: none; padding: 0; }\n" +
               "      li { padding: 1rem; background: #0f172a; margin-bottom: 0.5rem; border-radius: 8px; }\n" +
               "    </style></head><body>\n" +
               "    <div class=\"container\">\n" +
               "      <h1>Available Hosted Services</h1>\n" +
               "      <ul>`;\n" +
               "    for (const [p, name] of Object.entries(serviceNames)) {\n" +
               "       let urlObj = new URL(serviceMap[p]);\n" +
               "       let displayUrl = serviceMap[p];\n" +
               "       html += `<li><a href=\"${p}\">${name} <span>&rarr;</span> <code>${p}</code></a> <br><small style=\"color:#94a3b8\">Proxies to ${displayUrl}</small></li>`;\n" +
               "    }\n" +
               "    html += `</ul></div></body></html>`;\n" +
               "    return new Response(html, { headers: { 'Content-Type': 'text/html' } });\n" +
               "  }\n" +
               "\n" +
               "  for (const [prefix, targetUrl] of Object.entries(serviceMap)) {\n" +
               "    if (path === prefix || path.startsWith(prefix + \"/\")) {\n" +
               "       const suffix = path.substring(prefix.length);\n" +
               "       let destUrl = targetUrl;\n" +
               "       if (destUrl.endsWith(\"/\") && suffix.startsWith(\"/\")) {\n" +
               "          destUrl = destUrl.slice(0, -1) + suffix;\n" +
               "       } else if (!destUrl.endsWith(\"/\") && !suffix.startsWith(\"/\")) {\n" +
               "          destUrl = destUrl + \"/\" + suffix;\n" +
               "       } else {\n" +
               "          destUrl = destUrl + suffix;\n" +
               "       }\n" +
               "       const mappedUrl = destUrl + url.search;\n" +
               "       const response = await fetch(mappedUrl, request);\n" +
               "       return new Response(response.body, response);\n" +
               "    }\n" +
               "  }\n" +
               "\n" +
               "  return new Response(\"cf-p-link router: Service mapping not found for path \" + path, { status: 404 });\n" +
               "}\n";
    }
}
