package com.chat.api.repository;

import com.chat.api.model.Channel;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChannelRepository extends CassandraRepository<Channel, UUID> {
}
