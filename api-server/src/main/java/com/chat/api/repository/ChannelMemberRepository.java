package com.chat.api.repository;

import com.chat.api.model.ChannelMember;
import com.chat.api.model.ChannelMemberKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelMemberRepository extends CassandraRepository<ChannelMember, ChannelMemberKey> {

    @Query("SELECT * FROM channel_members WHERE channel_id = ?0")
    List<ChannelMember> findByChannelId(UUID channelId);

    @Query("SELECT COUNT(*) FROM channel_members WHERE channel_id = ?0")
    long countByChannelId(UUID channelId);
}
