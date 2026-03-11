package com.chat.notification.repository;

import com.chat.notification.model.Notification;
import com.chat.notification.model.NotificationKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends CassandraRepository<Notification, NotificationKey> {

    @Query("SELECT * FROM notifications WHERE user_id = ?0 LIMIT ?1")
    List<Notification> findByUserId(UUID userId, int limit);

    @Query("SELECT * FROM notifications WHERE user_id = ?0 AND is_read = false LIMIT ?1 ALLOW FILTERING")
    List<Notification> findUnreadByUserId(UUID userId, int limit);
}
