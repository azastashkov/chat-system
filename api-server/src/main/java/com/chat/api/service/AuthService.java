package com.chat.api.service;

import com.chat.api.exception.ApiException;
import com.chat.api.model.User;
import com.chat.api.model.UserByUsername;
import com.chat.api.repository.UserByUsernameRepository;
import com.chat.api.repository.UserRepository;
import com.chat.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserByUsernameRepository userByUsernameRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public Map<String, Object> register(String username, String password, String displayName) {
        if (userByUsernameRepository.findById(username).isPresent()) {
            throw new ApiException("Username already taken", HttpStatus.CONFLICT);
        }

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        String passwordHash = hashPassword(password);

        User user = User.builder()
                .userId(userId)
                .username(username)
                .passwordHash(passwordHash)
                .displayName(displayName)
                .createdAt(now)
                .build();

        UserByUsername userByUsername = UserByUsername.builder()
                .username(username)
                .userId(userId)
                .passwordHash(passwordHash)
                .displayName(displayName)
                .createdAt(now)
                .build();

        userRepository.save(user);
        userByUsernameRepository.save(userByUsername);

        String token = jwtTokenProvider.generateToken(userId, username);

        log.info("User registered: userId={}, username={}", userId, username);

        return Map.of(
                "token", token,
                "userId", userId
        );
    }

    public Map<String, Object> login(String username, String password) {
        UserByUsername user = userByUsernameRepository.findById(username)
                .orElseThrow(() -> new ApiException("Invalid username or password", HttpStatus.UNAUTHORIZED));

        String passwordHash = hashPassword(password);
        if (!passwordHash.equals(user.getPasswordHash())) {
            throw new ApiException("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtTokenProvider.generateToken(user.getUserId(), username);

        log.info("User logged in: userId={}, username={}", user.getUserId(), username);

        return Map.of(
                "token", token,
                "userId", user.getUserId()
        );
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
