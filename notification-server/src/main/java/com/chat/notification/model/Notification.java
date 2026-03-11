package com.chat.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("notifications")
public class Notification {

    @PrimaryKey
    private NotificationKey key;

    @Column("type")
    private String type;

    @Column("payload")
    private String payload;

    @Column("is_read")
    private boolean isRead;

    @Column("created_at")
    private Instant createdAt;
}
