package tech.wenisch.kairos.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import tech.wenisch.kairos.entity.ApiKey;
import tech.wenisch.kairos.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    @Value("${kairos.api-keys.jwt-secret:change-me-super-long-random-secret-at-least-32-bytes}")
    private String jwtSecret;

    public List<ApiKey> findAllOrderedByCreatedAtDesc() {
        return apiKeyRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<ApiKey> findById(Long id) {
        return apiKeyRepository.findById(id);
    }

    public CreatedApiKey create(String name, String createdBy) {
        String keyId = UUID.randomUUID().toString();
        String token = createSignedToken(keyId, name, createdBy);

        ApiKey apiKey = ApiKey.builder()
                .keyId(keyId)
                .name(name)
                .createdBy(createdBy)
                .tokenHash(sha256(token))
                .createdAt(LocalDateTime.now())
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        return new CreatedApiKey(saved, token);
    }

    public void delete(Long id) {
        apiKeyRepository.deleteById(id);
    }

    public Optional<ApiKey> validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            MACVerifier verifier = new MACVerifier(secretBytes());
            if (!signedJWT.verify(verifier)) {
                return Optional.empty();
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            String tokenType = claims.getStringClaim("type");
            String keyId = claims.getStringClaim("keyId");

            if (!"api_key".equals(tokenType) || keyId == null || keyId.isBlank()) {
                return Optional.empty();
            }

            Date expiresAt = claims.getExpirationTime();
            if (expiresAt != null && expiresAt.toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }

            return apiKeyRepository.findByKeyId(keyId)
                    .filter(stored -> sha256(token).equals(stored.getTokenHash()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String createSignedToken(String keyId, String name, String createdBy) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .jwtID(UUID.randomUUID().toString())
                    .subject(createdBy)
                    .issueTime(Date.from(now))
                    .claim("type", "api_key")
                    .claim("keyId", keyId)
                    .claim("name", name)
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secretBytes()));
            return jwt.serialize();
        } catch (JOSEException e) {
            log.error("Failed to generate API key token", e);
            throw new IllegalStateException("Could not generate API key token", e);
        }
    }

    private byte[] secretBytes() {
        byte[] raw = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            return Base64.getEncoder().encode(raw);
        }
        return raw;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash token", e);
        }
    }

    public record CreatedApiKey(ApiKey apiKey, String token) {
    }
}
