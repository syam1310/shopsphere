package com.shopsphere.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(
            RestClient.Builder builder,
            @Value("${user-service.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public UserResponse getCurrentUser(String authorizationHeader) {
        return restClient.get()
                .uri("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .body(UserResponse.class);
    }

    public record UserResponse(Long id, String name, String email) {}
}
