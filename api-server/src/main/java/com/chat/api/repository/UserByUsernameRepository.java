package com.chat.api.repository;

import com.chat.api.model.UserByUsername;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserByUsernameRepository extends CassandraRepository<UserByUsername, String> {

    Optional<UserByUsername> findByUsername(String username);
}
