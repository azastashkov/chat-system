package com.chat.chatserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("messages")
public class Message {

    @PrimaryKeyColumn(name = "channel_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID channelId;

    @PrimaryKeyColumn(name = "message_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private UUID messageId;

    @Column("sender_id")
    private UUID senderId;

    @Column("sender_name")
    private String senderName;

    @Column("content")
    private String content;

    @Column("message_type")
    private String messageType;

    @Column("sequence_number")
    private Long sequenceNumber;

    @Column("created_at")
    private Instant createdAt;
}
