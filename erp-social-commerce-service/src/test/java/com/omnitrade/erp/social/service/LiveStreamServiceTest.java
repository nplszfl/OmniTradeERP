package com.omnitrade.erp.social.service;

import com.omnitrade.erp.social.dto.LiveStreamDTO;
import com.omnitrade.erp.social.model.Influencer;
import com.omnitrade.erp.social.model.LiveStream;
import com.omnitrade.erp.social.repository.InfluencerRepository;
import com.omnitrade.erp.social.repository.LiveStreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("直播服务测试")
class LiveStreamServiceTest {

    @Mock
    private LiveStreamRepository repository;

    @Mock
    private InfluencerRepository influencerRepository;

    @InjectMocks
    private LiveStreamService service;

    private LiveStream testStream;
    private LiveStreamDTO testDTO;
    private Influencer testInfluencer;

    @BeforeEach
    void setUp() {
        testInfluencer = Influencer.builder()
                .id(1L)
                .name("测试达人")
                .platform(Influencer.Platform.TIKTOK)
                .build();

        testStream = LiveStream.builder()
                .id(1L)
                .streamId("stream123")
                .title("测试直播")
                .platform(LiveStream.Platform.TIKTOK)
                .influencer(testInfluencer)
                .status(LiveStream.StreamStatus.SCHEDULED)
                .scheduledStartTime(LocalDateTime.now().plusHours(1))
                .viewerCount(1000)
                .peakViewers(5000)
                .likeCount(10000)
                .shareCount(500)
                .commentCount(2000)
                .newFollowers(100)
                .totalSales(BigDecimal.valueOf(5000))
                .totalOrders(50)
                .totalGmv(BigDecimal.valueOf(10000))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testDTO = LiveStreamDTO.builder()
                .id(1L)
                .streamId("stream123")
                .title("测试直播")
                .platform("TIKTOK")
                .influencerId(1L)
                .status("SCHEDULED")
                .scheduledStartTime(LocalDateTime.now().plusHours(1).toString())
                .viewerCount(1000)
                .peakViewers(5000)
                .likeCount(10000)
                .shareCount(500)
                .commentCount(2000)
                .newFollowers(100)
                .totalSales(BigDecimal.valueOf(5000))
                .totalOrders(50)
                .totalGmv(BigDecimal.valueOf(10000))
                .build();
    }

    @Test
    @DisplayName("获取所有直播")
    void testFindAll() {
        when(repository.findAll()).thenReturn(Arrays.asList(testStream));

        var result = service.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("测试直播", result.get(0).getTitle());
    }

    @Test
    @DisplayName("根据ID获取直播")
    void testFindById() {
        when(repository.findById(1L)).thenReturn(Optional.of(testStream));

        var result = service.findById(1L);

        assertNotNull(result);
        assertEquals("stream123", result.getStreamId());
    }

    @Test
    @DisplayName("根据StreamId获取直播")
    void testFindByStreamId() {
        when(repository.findByStreamId("stream123")).thenReturn(Optional.of(testStream));

        var result = service.findByStreamId("stream123");

        assertNotNull(result);
        assertEquals("测试直播", result.getTitle());
    }

    @Test
    @DisplayName("根据平台获取直播列表")
    void testFindByPlatform() {
        when(repository.findByPlatform(LiveStream.Platform.TIKTOK))
                .thenReturn(Arrays.asList(testStream));

        var result = service.findByPlatform("TIKTOK");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("根据状态获取直播列表")
    void testFindByStatus() {
        when(repository.findByStatus(LiveStream.StreamStatus.SCHEDULED))
                .thenReturn(Arrays.asList(testStream));

        var result = service.findByStatus("SCHEDULED");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("根据达人ID获取直播列表")
    void testFindByInfluencerId() {
        when(repository.findByInfluencerId(1L)).thenReturn(Arrays.asList(testStream));

        var result = service.findByInfluencerId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("获取当前直播")
    void testFindCurrentLive() {
        when(repository.findCurrentLiveStreams()).thenReturn(Arrays.asList(testStream));

        var result = service.findCurrentLive();

        assertNotNull(result);
    }

    @Test
    @DisplayName("获取最近直播")
    void testFindRecent() {
        when(repository.findRecentLiveStreams()).thenReturn(Arrays.asList(testStream));

        var result = service.findRecent();

        assertNotNull(result);
    }

    @Test
    @DisplayName("创建直播")
    void testCreate() {
        when(influencerRepository.findById(1L)).thenReturn(Optional.of(testInfluencer));
        when(repository.save(any(LiveStream.class))).thenReturn(testStream);

        var result = service.create(testDTO);

        assertNotNull(result);
        assertEquals("SCHEDULED", result.getStatus());
    }

    @Test
    @DisplayName("开始直播")
    void testStartLive() {
        when(repository.findById(1L)).thenReturn(Optional.of(testStream));
        when(repository.save(any(LiveStream.class))).thenReturn(testStream);

        var result = service.startLive(1L);

        assertNotNull(result);
        assertEquals("LIVE", result.getStatus());
    }

    @Test
    @DisplayName("结束直播")
    void testEndLive() {
        testStream.setActualStartTime(LocalDateTime.now().minusHours(1));
        when(repository.findById(1L)).thenReturn(Optional.of(testStream));
        when(repository.save(any(LiveStream.class))).thenReturn(testStream);

        LiveStreamDTO endDTO = LiveStreamDTO.builder()
                .viewerCount(5000)
                .peakViewers(10000)
                .totalSales(BigDecimal.valueOf(10000))
                .totalOrders(100)
                .totalGmv(BigDecimal.valueOf(20000))
                .build();

        var result = service.endLive(1L, endDTO);

        assertNotNull(result);
        assertEquals("ENDED", result.getStatus());
    }

    @Test
    @DisplayName("更新直播信息")
    void testUpdate() {
        when(repository.findById(1L)).thenReturn(Optional.of(testStream));
        when(repository.save(any(LiveStream.class))).thenReturn(testStream);

        LiveStreamDTO updateDTO = LiveStreamDTO.builder()
                .title("新的直播标题")
                .build();

        var result = service.update(1L, updateDTO);

        assertNotNull(result);
    }

    @Test
    @DisplayName("删除直播")
    void testDelete() {
        doNothing().when(repository).deleteById(1L);

        service.delete(1L);

        verify(repository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("获取日期范围内的直播")
    void testFindByDateRange() {
        String startTime = LocalDateTime.now().minusDays(1).toString();
        String endTime = LocalDateTime.now().plusDays(1).toString();
        when(repository.findByScheduledTimeBetween(any(), any()))
                .thenReturn(Arrays.asList(testStream));

        var result = service.findByDateRange(startTime, endTime);

        assertNotNull(result);
    }
}