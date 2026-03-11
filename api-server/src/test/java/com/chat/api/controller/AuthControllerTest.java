package com.chat.api.controller;

import com.chat.api.exception.ApiException;
import com.chat.api.exception.GlobalExceptionHandler;
import com.chat.api.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void register_shouldReturnCreatedWithTokenAndUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "jwt-token-value";

        when(authService.register("testuser", "password123", "Test User"))
                .thenReturn(Map.of("token", token, "userId", userId));

        String requestBody = objectMapper.writeValueAsString(
                new AuthController.RegisterRequest("testuser", "password123", "Test User"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void register_shouldReturnConflictWhenUsernameExists() throws Exception {
        when(authService.register(anyString(), anyString(), anyString()))
                .thenThrow(new ApiException("Username already taken", HttpStatus.CONFLICT));

        String requestBody = objectMapper.writeValueAsString(
                new AuthController.RegisterRequest("existing", "password123", "Test"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already taken"));
    }

    @Test
    void login_shouldReturnOkWithTokenAndUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "jwt-token-value";

        when(authService.login("testuser", "password123"))
                .thenReturn(Map.of("token", token, "userId", userId));

        String requestBody = objectMapper.writeValueAsString(
                new AuthController.LoginRequest("testuser", "password123"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void login_shouldReturnUnauthorizedForInvalidCredentials() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new ApiException("Invalid username or password", HttpStatus.UNAUTHORIZED));

        String requestBody = objectMapper.writeValueAsString(
                new AuthController.LoginRequest("wrong", "wrong"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }
}
