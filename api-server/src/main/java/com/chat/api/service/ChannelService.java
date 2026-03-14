package com.chat.api.service;

import com.chat.api.exception.ApiException;
import com.chat.api.model.Channel;
import com.chat.api.model.ChannelMember;
import com.chat.api.model.ChannelMemberKey;
import com.chat.api.model.UserChannel;
import com.chat.api.model.UserChannelKey;
import com.chat.api.repository.ChannelMemberRepository;
import com.chat.api.repository.ChannelRepository;
import com.chat.api.repository.UserChannelRepository;
import com.chat.api.repository.UserRepository;
import com.chat.common.dto.ChannelDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private static final int MAX_GROUP_MEMBERS = 100;

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserChannelRepository userChannelRepository;
    private final UserRepository userRepository;

    public ChannelDto createChannel(String channelType, String name, List<UUID> memberIds, UUID creatorId) {
        if (memberIds != null && memberIds.size() > MAX_GROUP_MEMBERS) {
            throw new ApiException("Group cannot exceed " + MAX_GROUP_MEMBERS + " members", HttpStatus.BAD_REQUEST);
        }

        UUID channelId = UUID.randomUUID();
        Instant now = Instant.now();

        Channel channel = Channel.builder()
                .channelId(channelId)
                .channelType(channelType)
                .name(name)
                .createdBy(creatorId)
                .createdAt(now)
                .build();

        channelRepository.save(channel);

        Set<UUID> allMembers = new LinkedHashSet<>();
        allMembers.add(creatorId);
        if (memberIds != null) {
            allMembers.addAll(memberIds);
        }

        for (UUID memberId : allMembers) {
            addMemberInternal(channelId, memberId, channelType, name,
                    memberId.equals(creatorId) ? "OWNER" : "MEMBER", now);
        }

        log.info("Channel created: channelId={}, type={}, name={}, members={}",
                channelId, channelType, name, allMembers.size());

        return toDto(channel);
    }

    public List<ChannelDto> getUserChannels(UUID userId) {
        List<UserChannel> userChannels = userChannelRepository.findByUserId(userId);

        return userChannels.stream()
                .map(uc -> ChannelDto.builder()
                        .channelId(uc.getChannelId())
                        .channelType(uc.getChannelType())
                        .name(uc.getChannelName())
                        .build())
                .toList();
    }

    public Map<String, Object> getChannel(UUID channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ApiException("Channel not found", HttpStatus.NOT_FOUND));

        List<ChannelMember> members = channelMemberRepository.findByChannelId(channelId);

        List<Map<String, Object>> memberList = members.stream()
                .map(m -> {
                    Map<String, Object> memberMap = new HashMap<>();
                    memberMap.put("userId", m.getKey().getUserId());
                    memberMap.put("role", m.getRole());
                    memberMap.put("joinedAt", m.getJoinedAt());
                    return memberMap;
                })
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("channel", toDto(channel));
        result.put("members", memberList);
        return result;
    }

    public void addMembers(UUID channelId, List<UUID> userIds) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ApiException("Channel not found", HttpStatus.NOT_FOUND));

        long currentCount = channelMemberRepository.countByChannelId(channelId);
        if (currentCount + userIds.size() > MAX_GROUP_MEMBERS) {
            throw new ApiException("Adding these members would exceed the limit of " + MAX_GROUP_MEMBERS, HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        for (UUID userId : userIds) {
            addMemberInternal(channelId, userId, channel.getChannelType(), channel.getName(), "MEMBER", now);
        }

        log.info("Added {} members to channel {}", userIds.size(), channelId);
    }

    public void removeMember(UUID channelId, UUID userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ApiException("Channel not found", HttpStatus.NOT_FOUND));

        ChannelMemberKey memberKey = ChannelMemberKey.builder()
                .channelId(channelId)
                .userId(userId)
                .build();

        channelMemberRepository.deleteById(memberKey);

        List<UserChannel> userChannels = userChannelRepository.findByUserId(userId);
        userChannels.stream()
                .filter(uc -> uc.getChannelId().equals(channelId))
                .findFirst()
                .ifPresent(uc -> userChannelRepository.delete(uc));

        log.info("Removed member {} from channel {}", userId, channelId);
    }

    private void addMemberInternal(UUID channelId, UUID userId, String channelType, String channelName,
                                   String role, Instant joinedAt) {
        ChannelMember member = ChannelMember.builder()
                .key(ChannelMemberKey.builder()
                        .channelId(channelId)
                        .userId(userId)
                        .build())
                .role(role)
                .joinedAt(joinedAt)
                .build();

        UserChannel userChannel = UserChannel.builder()
                .key(UserChannelKey.builder()
                        .userId(userId)
                        .joinedAt(joinedAt)
                        .build())
                .channelId(channelId)
                .channelType(channelType)
                .channelName(channelName)
                .build();

        channelMemberRepository.save(member);
        userChannelRepository.save(userChannel);
    }

    private ChannelDto toDto(Channel channel) {
        return ChannelDto.builder()
                .channelId(channel.getChannelId())
                .channelType(channel.getChannelType())
                .name(channel.getName())
                .createdBy(channel.getCreatedBy())
                .createdAt(channel.getCreatedAt())
                .build();
    }
}
