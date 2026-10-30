package com.shopsphere.userservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleIdentityTokenVerifier {

    private final String clientId;
    private final JwtDecoder jwtDecoder;

    public GoogleIdentityTokenVerifier(@Value("${google.client-id:}") String clientId, JwtDecoder jwtDecoder) {
        this.clientId = clientId;
        this.jwtDecoder = jwtDecoder;
    }

    public GoogleIdentity verify(String credential) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Google sign-in is not configured");
        }

        try {
            Jwt jwt = jwtDecoder.decode(credential);
            List<String> audience = jwt.getAudience();
            String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            boolean verified = Boolean.TRUE.equals(jwt.getClaim("email_verified"));
            String email = jwt.getClaimAsString("email");
            String subject = jwt.getSubject();
            String hostedDomain = jwt.getClaimAsString("hd");
            boolean googleControlsEmail = email != null && (email.toLowerCase().endsWith("@gmail.com")
                    || (hostedDomain != null && !hostedDomain.isBlank()
                    && email.toLowerCase().endsWith("@" + hostedDomain.toLowerCase())));
            if (!(issuer.equals("https://accounts.google.com") || issuer.equals("accounts.google.com"))
                    || audience == null || !audience.contains(clientId)
                    || !verified || email == null || email.isBlank()
                    || !googleControlsEmail
                    || subject == null || subject.isBlank()) {
                throw new JwtException("Google identity token is missing required verified claims");
            }
            return new GoogleIdentity(subject, email, jwt.getClaimAsString("name"), true);
        } catch (JwtException exception) {
            throw new IllegalArgumentException("Google sign-in could not verify this account", exception);
        }
    }
}

@Configuration
class GoogleJwtConfiguration {
    @Bean
    JwtDecoder googleJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }
}
