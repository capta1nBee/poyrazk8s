package com.k8s.platform.service.helm;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class HelmProxyService {

    private final RestTemplate restTemplate = new RestTemplate();

    public ResponseEntity<String> fetchValues(String packageId, String version) {
        String url = String.format("https://artifacthub.io/api/v1/packages/%s/%s/values", packageId, version);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.valueOf("application/yaml"));
                return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
            } else {
                return ResponseEntity.status(response.getStatusCode())
                        .body("{\"error\":\"Failed to fetch values\"}");
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public ResponseEntity<String> searchPackages(Integer kind, Integer category, String ts_query_web,
                                                 Boolean official, Boolean verified_publisher,
                                                 Boolean cncf, Boolean deprecated,
                                                 String sort, String direction) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://artifacthub.io/api/v1/packages/search");

        if (ts_query_web != null && !ts_query_web.isEmpty()) {
            builder.queryParam("ts_query_web", ts_query_web);
        }
        
        // Defaults if not searching globally
        if (kind != null) {
            builder.queryParam("kind", kind); 
        } else if (ts_query_web == null || ts_query_web.isEmpty()) {
            builder.queryParam("kind", 0); // Helm kind
        }
        
        if (category != null) {
            builder.queryParam("category", category);
        } else if (ts_query_web == null || ts_query_web.isEmpty()) {
            builder.queryParam("category", 1);
        }

        if (Boolean.TRUE.equals(official)) builder.queryParam("official", "true");
        if (Boolean.TRUE.equals(verified_publisher)) builder.queryParam("verified_publisher", "true");
        if (Boolean.TRUE.equals(cncf)) builder.queryParam("cncf", "true");
        if (Boolean.TRUE.equals(deprecated)) builder.queryParam("deprecated", "true");
        
        if (sort != null && !sort.isEmpty()) builder.queryParam("sort", sort);
        if (direction != null && !direction.isEmpty()) builder.queryParam("direction", direction);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    builder.build().toUri(),
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
            } else {
                return ResponseEntity.status(response.getStatusCode())
                        .body("{\"error\":\"Failed to fetch packages\"}");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public ResponseEntity<String> fetchHelmPackage(String repoName, String packageName) {
        String url = String.format("https://artifacthub.io/api/v1/packages/helm/%s/%s", repoName, packageName);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
            } else {
                return ResponseEntity.status(response.getStatusCode())
                        .body("{\"error\":\"Failed to fetch package details\"}");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
