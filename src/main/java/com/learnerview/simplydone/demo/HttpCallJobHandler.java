package com.learnerview.simplydone.demo;

import com.learnerview.simplydone.handler.JobContext;
import com.learnerview.simplydone.handler.JobHandler;
import com.learnerview.simplydone.handler.JobResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpCallJobHandler implements JobHandler {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String getJobType() { return "http-call"; }

    @Override
    public String getDescription() { return "Makes an HTTP GET/POST request. Payload: url (required), method (GET|POST), headers (object), body (string)"; }

    @Override
    public JobResult execute(JobContext ctx) {
        String url = (String) ctx.getPayload().get("url");
        if (url == null) return JobResult.failure("Missing 'url' in payload");

        String method = (String) ctx.getPayload().getOrDefault("method", "GET");

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            // Apply custom headers from payload
            Object headersObj = ctx.getPayload().get("headers");
            if (headersObj instanceof Map<?, ?> headersMap) {
                for (Map.Entry<?, ?> entry : headersMap.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        reqBuilder.header(entry.getKey().toString(), entry.getValue().toString());
                    }
                }
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = (String) ctx.getPayload().getOrDefault("body", "");
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                reqBuilder.GET();
            }

            HttpResponse<String> resp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            return JobResult.success("HTTP " + resp.statusCode(),
                    Map.of("statusCode", resp.statusCode(), "bodyLength", resp.body().length()));
        } catch (Exception e) {
            return JobResult.failure("HTTP call failed: " + e.getMessage());
        }
    }
}
