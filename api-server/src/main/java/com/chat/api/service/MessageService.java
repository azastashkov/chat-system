package com.chat.api.service;

import com.chat.common.dto.MessageDto;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final CqlSession cqlSession;

    public List<MessageDto> getMessages(UUID channelId, UUID beforeMessageId, int limit) {
        if (limit <= 0 || limit > 100) {
            limit = 50;
        }

        SimpleStatement statement;
        if (beforeMessageId != null) {
            statement = SimpleStatement.newInstance(
                    "SELECT * FROM messages WHERE channel_id = ? AND message_id < ? ORDER BY message_id DESC LIMIT ?",
                    channelId, beforeMessageId, limit
            );
        } else {
            statement = SimpleStatement.newInstance(
                    "SELECT * FROM messages WHERE channel_id = ? ORDER BY message_id DESC LIMIT ?",
                    channelId, limit
            );
        }

        ResultSet resultSet = cqlSession.execute(statement);

        List<MessageDto> messages = new ArrayList<>();
        for (Row row : resultSet) {
            messages.add(MessageDto.builder()
                    .messageId(row.getUuid("message_id"))
                    .channelId(row.getUuid("channel_id"))
                    .senderId(row.getUuid("sender_id"))
                    .senderName(row.getString("sender_name"))
                    .content(row.getString("content"))
                    .messageType(row.getString("message_type"))
                    .createdAt(row.getInstant("created_at"))
                    .build());
        }

        return messages;
    }
}
