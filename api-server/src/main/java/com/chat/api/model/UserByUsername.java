package com.chat.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users_by_username")
public class UserByUsername {

    @PrimaryKey
    private String username;

    @Column("user_id")
    private UUID userId;

    @Column("password_hash")
    private String passwordHash;

    @Column("display_name")
    private String displayName;

    @Column("created_at")
    private Instant createdAt;
}
