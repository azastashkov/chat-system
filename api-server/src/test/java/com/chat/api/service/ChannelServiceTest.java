package com.chat.api.service;

import com.chat.api.exception.ApiException;
import com.chat.api.model.Channel;
import com.chat.api.model.ChannelMember;
import com.chat.api.model.UserChannel;
import com.chat.api.model.UserChannelKey;
import com.chat.api.repository.ChannelMemberRepository;
import com.chat.api.repository.ChannelRepository;
import com.chat.api.repository.UserChannelRepository;
import com.chat.api.repository.UserRepository;
import com.chat.common.dto.ChannelDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private UserChannelRepository userChannelRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChannelService channelService;

    @Test
    void createChannel_shouldCreateChannelWithMembers() {
        UUID creatorId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        List<UUID> memberIds = List.of(memberId);

        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(channelMemberRepository.save(any(ChannelMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userChannelRepository.save(any(UserChannel.class))).thenAnswer(inv -> inv.getArgument(0));

        ChannelDto result = channelService.createChannel("GROUP", "Test Channel", memberIds, creatorId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Channel");
        assertThat(result.getChannelType()).isEqualTo("GROUP");
        assertThat(result.getCreatedBy()).isEqualTo(creatorId);

        verify(channelRepository).save(any(Channel.class));
        // 2 members: creator + 1 member
        verify(channelMemberRepository, times(2)).save(any(ChannelMember.class));
        verify(userChannelRepository, times(2)).save(any(UserChannel.class));
    }

    @Test
    void createChannel_shouldSetOwnerRoleForCreator() {
        UUID creatorId = UUID.randomUUID();

        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(channelMemberRepository.save(any(ChannelMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userChannelRepository.save(any(UserChannel.class))).thenAnswer(inv -> inv.getArgument(0));

        channelService.createChannel("GROUP", "Test", List.of(), creatorId);

        ArgumentCaptor<ChannelMember> captor = ArgumentCaptor.forClass(ChannelMember.class);
        verify(channelMemberRepository).save(captor.capture());

        ChannelMember savedMember = captor.getValue();
        assertThat(savedMember.getRole()).isEqualTo("OWNER");
        assertThat(savedMember.getKey().getUserId()).isEqualTo(creatorId);
    }

    @Test
    void createChannel_shouldRejectExceedingMaxMembers() {
        UUID creatorId = UUID.randomUUID();
        List<UUID> memberIds = IntStream.range(0, 101)
                .mapToObj(i -> UUID.randomUUID())
                .toList();

        assertThatThrownBy(() -> channelService.createChannel("GROUP", "Big Group", memberIds, creatorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot exceed 100");
    }

    @Test
    void addMembers_shouldRejectWhenExceedingLimit() {
        UUID channelId = UUID.randomUUID();
        Channel channel = Channel.builder()
                .channelId(channelId)
                .channelType("GROUP")
                .name("Test")
                .build();

        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
        when(channelMemberRepository.countByChannelId(channelId)).thenReturn(95L);

        List<UUID> newMembers = IntStream.range(0, 10)
                .mapToObj(i -> UUID.randomUUID())
                .toList();

        assertThatThrownBy(() -> channelService.addMembers(channelId, newMembers))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exceed the limit");
    }

    @Test
    void getUserChannels_shouldReturnUserChannels() {
        UUID userId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        UserChannel userChannel = UserChannel.builder()
                .key(UserChannelKey.builder().userId(userId).build())
                .channelId(channelId)
                .channelType("GROUP")
                .channelName("Test Channel")
                .build();

        when(userChannelRepository.findByUserId(userId)).thenReturn(List.of(userChannel));

        List<ChannelDto> result = channelService.getUserChannels(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getChannelId()).isEqualTo(channelId);
        assertThat(result.get(0).getName()).isEqualTo("Test Channel");
    }
}
