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
@Table("channels")
public class Channel {

    @PrimaryKey("channel_id")
    private UUID channelId;

    @Column("channel_type")
    private String channelType;

    private String name;

    @Column("created_by")
    private UUID createdBy;

    @Column("created_at")
    private Instant createdAt;
}
