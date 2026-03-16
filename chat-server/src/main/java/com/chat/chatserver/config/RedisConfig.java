package com.chat.chatserver.config;

import com.chat.chatserver.service.ChatMessageStreamConsumer;
import com.chat.chatserver.service.RedisSubscriberService;
import com.chat.common.constant.RedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Configuration
public class RedisConfig {

    @Value("${chat-server.server-id}")
    private String serverId;

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisSubscriberService subscriberService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Presence changes (pub/sub)
        container.addMessageListener(subscriberService,
                new ChannelTopic(RedisKeys.PRESENCE_CHANGES));
        // Typing indicators (pub/sub pattern)
        container.addMessageListener(subscriberService,
                new PatternTopic("typing:*"));
        // Channel membership cache invalidation (pub/sub)
        container.addMessageListener(subscriberService,
                new ChannelTopic(RedisKeys.CHANNEL_MEMBERSHIP_INVALIDATE));
        return container;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> chatStreamListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatMessageStreamConsumer chatMessageStreamConsumer) {

        String streamKey = RedisKeys.chatServerStream(serverId);
        String groupName = "chat-group";

        // Create consumer group (and stream if needed)
        try (var connection = connectionFactory.getConnection()) {
            connection.streamCommands().xGroupCreate(
                    streamKey.getBytes(StandardCharsets.UTF_8),
                    groupName,
                    ReadOffset.from("0"),
                    true
            );
        } catch (Exception e) {
            log.debug("Consumer group setup for {}: {}", streamKey, e.getMessage());
        }

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .<String, MapRecord<String, String, String>>builder()
                .pollTimeout(Duration.ofSeconds(2))
                .serializer(new StringRedisSerializer())
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        container.receiveAutoAck(
                Consumer.from(groupName, serverId),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                chatMessageStreamConsumer
        );

        return container;
    }
}
