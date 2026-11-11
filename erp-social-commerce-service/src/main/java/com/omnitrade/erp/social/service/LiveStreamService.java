package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.dto.LiveStreamDTO;
import com.omnitrade.erp.social.model.Influencer;
import com.omnitrade.erp.social.model.LiveStream;
import com.omnitrade.erp.social.repository.InfluencerRepository;
import com.omnitrade.erp.social.repository.LiveStreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LiveStreamService {
    
    private final LiveStreamRepository repository;
    private final InfluencerRepository influencerRepository;
    
    public List<LiveStreamDTO> findAll() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public LiveStreamDTO findById(Long id) {
        return repository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new BizException("直播不存在: " + id));
    }
    
    public LiveStreamDTO findByStreamId(String streamId) {
        return repository.findByStreamId(streamId)
                .map(this::toDTO)
                .orElseThrow(() -> new BizException("直播不存在: " + streamId));
    }
    
    public List<LiveStreamDTO> findByPlatform(String platform) {
        return repository.findByPlatform(LiveStream.Platform.valueOf(platform)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<LiveStreamDTO> findByStatus(String status) {
        return repository.findByStatus(LiveStream.StreamStatus.valueOf(status)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<LiveStreamDTO> findByInfluencerId(Long influencerId) {
        return repository.findByInfluencerId(influencerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<LiveStreamDTO> findByDateRange(String startTime, String endTime) {
        LocalDateTime start = LocalDateTime.parse(startTime);
        LocalDateTime end = LocalDateTime.parse(endTime);
        return repository.findByScheduledTimeBetween(start, end).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<LiveStreamDTO> findCurrentLive() {
        return repository.findCurrentLiveStreams().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<LiveStreamDTO> findRecent() {
        return repository.findRecentLiveStreams().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public LiveStreamDTO create(LiveStreamDTO dto) {
        LiveStream entity = toEntity(dto);
        if (dto.getInfluencerId() != null) {
            Influencer influencer = influencerRepository.findById(dto.getInfluencerId())
                    .orElseThrow(() -> new BizException("达人不存在: " + dto.getInfluencerId()));
            entity.setInfluencer(influencer);
        }
        entity.setStatus(LiveStream.StreamStatus.SCHEDULED);
        LiveStream saved = repository.save(entity);
        return toDTO(saved);
    }
    
    @Transactional
    public LiveStreamDTO update(Long id, LiveStreamDTO dto) {
        LiveStream entity = repository.findById(id)
                .orElseThrow(() -> new BizException("直播不存在: " + id));
        
        if (dto.getStreamId() != null) entity.setStreamId(dto.getStreamId());
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getPlatform() != null) entity.setPlatform(LiveStream.Platform.valueOf(dto.getPlatform()));
        if (dto.getInfluencerId() != null) {
            Influencer influencer = influencerRepository.findById(dto.getInfluencerId())
                    .orElseThrow(() -> new BizException("达人不存在: " + dto.getInfluencerId()));
            entity.setInfluencer(influencer);
        }
        if (dto.getStatus() != null) entity.setStatus(LiveStream.StreamStatus.valueOf(dto.getStatus()));
        if (dto.getScheduledStartTime() != null) {
            entity.setScheduledStartTime(LocalDateTime.parse(dto.getScheduledStartTime()));
        }
        if (dto.getActualStartTime() != null) {
            entity.setActualStartTime(LocalDateTime.parse(dto.getActualStartTime()));
        }
        if (dto.getEndTime() != null) entity.setEndTime(LocalDateTime.parse(dto.getEndTime()));
        if (dto.getDuration() != null) entity.setDuration(dto.getDuration());
        if (dto.getViewerCount() != null) entity.setViewerCount(dto.getViewerCount());
        if (dto.getPeakViewers() != null) entity.setPeakViewers(dto.getPeakViewers());
        if (dto.getLikeCount() != null) entity.setLikeCount(dto.getLikeCount());
        if (dto.getShareCount() != null) entity.setShareCount(dto.getShareCount());
        if (dto.getCommentCount() != null) entity.setCommentCount(dto.getCommentCount());
        if (dto.getNewFollowers() != null) entity.setNewFollowers(dto.getNewFollowers());
        if (dto.getTotalSales() != null) entity.setTotalSales(dto.getTotalSales());
        if (dto.getTotalOrders() != null) entity.setTotalOrders(dto.getTotalOrders());
        if (dto.getTotalGmv() != null) entity.setTotalGmv(dto.getTotalGmv());
        if (dto.getCoverImageUrl() != null) entity.setCoverImageUrl(dto.getCoverImageUrl());
        if (dto.getProducts() != null) entity.setProducts(dto.getProducts());
        
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public LiveStreamDTO startLive(Long id) {
        LiveStream entity = repository.findById(id)
                .orElseThrow(() -> new BizException("直播不存在: " + id));
        entity.setStatus(LiveStream.StreamStatus.LIVE);
        entity.setActualStartTime(LocalDateTime.now());
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public LiveStreamDTO endLive(Long id, LiveStreamDTO dto) {
        LiveStream entity = repository.findById(id)
                .orElseThrow(() -> new BizException("直播不存在: " + id));
        entity.setStatus(LiveStream.StreamStatus.ENDED);
        entity.setEndTime(LocalDateTime.now());
        if (entity.getActualStartTime() != null) {
            entity.setDuration((int) java.time.Duration.between(entity.getActualStartTime(), entity.getEndTime()).toMinutes());
        }
        if (dto != null) {
            if (dto.getViewerCount() != null) entity.setViewerCount(dto.getViewerCount());
            if (dto.getPeakViewers() != null) entity.setPeakViewers(dto.getPeakViewers());
            if (dto.getLikeCount() != null) entity.setLikeCount(dto.getLikeCount());
            if (dto.getShareCount() != null) entity.setShareCount(dto.getShareCount());
            if (dto.getCommentCount() != null) entity.setCommentCount(dto.getCommentCount());
            if (dto.getNewFollowers() != null) entity.setNewFollowers(dto.getNewFollowers());
            if (dto.getTotalSales() != null) entity.setTotalSales(dto.getTotalSales());
            if (dto.getTotalOrders() != null) entity.setTotalOrders(dto.getTotalOrders());
            if (dto.getTotalGmv() != null) entity.setTotalGmv(dto.getTotalGmv());
        }
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
    
    // Package-private for use by LiveStreamMonitorService
    LiveStreamDTO toDTO(LiveStream entity) {
        return LiveStreamDTO.builder()
                .id(entity.getId())
                .streamId(entity.getStreamId())
                .title(entity.getTitle())
                .platform(entity.getPlatform().name())
                .shopId(entity.getShopId())
                .influencerId(entity.getInfluencer() != null ? entity.getInfluencer().getId() : null)
                .status(entity.getStatus().name())
                .scheduledStartTime(entity.getScheduledStartTime() != null ? entity.getScheduledStartTime().toString() : null)
                .actualStartTime(entity.getActualStartTime() != null ? entity.getActualStartTime().toString() : null)
                .endTime(entity.getEndTime() != null ? entity.getEndTime().toString() : null)
                .duration(entity.getDuration())
                .viewerCount(entity.getViewerCount())
                .peakViewers(entity.getPeakViewers())
                .likeCount(entity.getLikeCount())
                .shareCount(entity.getShareCount())
                .commentCount(entity.getCommentCount())
                .newFollowers(entity.getNewFollowers())
                .totalSales(entity.getTotalSales())
                .totalOrders(entity.getTotalOrders())
                .totalGmv(entity.getTotalGmv())
                .coverImageUrl(entity.getCoverImageUrl())
                .products(entity.getProducts())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
    
    private LiveStream toEntity(LiveStreamDTO dto) {
        return LiveStream.builder()
                .streamId(dto.getStreamId())
                .title(dto.getTitle())
                .platform(LiveStream.Platform.valueOf(dto.getPlatform()))
                .scheduledStartTime(dto.getScheduledStartTime() != null ? LocalDateTime.parse(dto.getScheduledStartTime()) : null)
                .actualStartTime(dto.getActualStartTime() != null ? LocalDateTime.parse(dto.getActualStartTime()) : null)
                .endTime(dto.getEndTime() != null ? LocalDateTime.parse(dto.getEndTime()) : null)
                .duration(dto.getDuration())
                .viewerCount(dto.getViewerCount())
                .peakViewers(dto.getPeakViewers())
                .likeCount(dto.getLikeCount())
                .shareCount(dto.getShareCount())
                .commentCount(dto.getCommentCount())
                .newFollowers(dto.getNewFollowers())
                .totalSales(dto.getTotalSales())
                .totalOrders(dto.getTotalOrders())
                .totalGmv(dto.getTotalGmv())
                .coverImageUrl(dto.getCoverImageUrl())
                .products(dto.getProducts())
                .build();
    }
}