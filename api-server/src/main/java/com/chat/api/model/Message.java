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
@Table("messages")
public class Message {

    @PrimaryKey
    private MessageKey key;

    @Column("sender_id")
    private UUID senderId;

    @Column("sender_name")
    private String senderName;

    private String content;

    @Column("message_type")
    private String messageType;

    @Column("created_at")
    private Instant createdAt;
}
