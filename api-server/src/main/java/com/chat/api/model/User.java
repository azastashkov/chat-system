package com.chat.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {

    @PrimaryKey("user_id")
    private UUID userId;

    private String username;

    @org.springframework.data.cassandra.core.mapping.Column("password_hash")
    private String passwordHash;

    @org.springframework.data.cassandra.core.mapping.Column("display_name")
    private String displayName;

    @org.springframework.data.cassandra.core.mapping.Column("created_at")
    private Instant createdAt;
}
