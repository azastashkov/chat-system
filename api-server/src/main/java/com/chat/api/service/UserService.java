package com.chat.api.service;

import com.chat.api.exception.ApiException;
import com.chat.api.model.User;
import com.chat.api.model.UserByUsername;
import com.chat.api.repository.UserByUsernameRepository;
import com.chat.api.repository.UserRepository;
import com.chat.common.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserByUsernameRepository userByUsernameRepository;

    public UserDto findById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        return toDto(user);
    }

    public UserDto searchByUsername(String username) {
        UserByUsername user = userByUsernameRepository.findById(username)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND));

        return UserDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
