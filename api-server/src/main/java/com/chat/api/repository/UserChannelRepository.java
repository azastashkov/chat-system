package com.chat.api.repository;

import com.chat.api.model.UserChannel;
import com.chat.api.model.UserChannelKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserChannelRepository extends CassandraRepository<UserChannel, UserChannelKey> {

    @Query("SELECT * FROM user_channels WHERE user_id = ?0")
    List<UserChannel> findByUserId(UUID userId);
}
