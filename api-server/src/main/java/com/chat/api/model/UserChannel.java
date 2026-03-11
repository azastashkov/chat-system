package com.chat.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_channels")
public class UserChannel {

    @PrimaryKey
    private UserChannelKey key;

    @Column("channel_id")
    private UUID channelId;

    @Column("channel_type")
    private String channelType;

    @Column("channel_name")
    private String channelName;
}
