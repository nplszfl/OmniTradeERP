package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.dto.InfluencerDTO;
import com.omnitrade.erp.social.model.Influencer;
import com.omnitrade.erp.social.repository.InfluencerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InfluencerService {
    
    private final InfluencerRepository repository;
    
    public List<InfluencerDTO> findAll() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public InfluencerDTO findById(Long id) {
        return repository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new BizException("达人不存在: " + id));
    }
    
    public List<InfluencerDTO> findByPlatform(String platform) {
        return repository.findByPlatform(Influencer.Platform.valueOf(platform)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<InfluencerDTO> findByStatus(String status) {
        return repository.findByStatus(Influencer.InfluencerStatus.valueOf(status)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<InfluencerDTO> findByLevel(String level) {
        return repository.findByLevel(Influencer.InfluencerLevel.valueOf(level)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<InfluencerDTO> findTopInfluencers(Integer minFollowers) {
        return repository.findTopInfluencersByFollowers(minFollowers).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<InfluencerDTO> findByCategory(String category) {
        return repository.findByCategory(category).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public InfluencerDTO create(InfluencerDTO dto) {
        Influencer entity = toEntity(dto);
        entity.setStatus(Influencer.InfluencerStatus.ACTIVE);
        Influencer saved = repository.save(entity);
        return toDTO(saved);
    }
    
    @Transactional
    public InfluencerDTO update(Long id, InfluencerDTO dto) {
        Influencer entity = repository.findById(id)
                .orElseThrow(() -> new BizException("达人不存在: " + id));
        
        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getPlatformId() != null) entity.setPlatformId(dto.getPlatformId());
        if (dto.getPlatform() != null) entity.setPlatform(Influencer.Platform.valueOf(dto.getPlatform()));
        if (dto.getAvatarUrl() != null) entity.setAvatarUrl(dto.getAvatarUrl());
        if (dto.getFollowerCount() != null) entity.setFollowerCount(dto.getFollowerCount());
        if (dto.getFollowingCount() != null) entity.setFollowingCount(dto.getFollowingCount());
        if (dto.getTotalViews() != null) entity.setTotalViews(dto.getTotalViews());
        if (dto.getTotalLikes() != null) entity.setTotalLikes(dto.getTotalLikes());
        if (dto.getLevel() != null) entity.setLevel(Influencer.InfluencerLevel.valueOf(dto.getLevel()));
        if (dto.getStatus() != null) entity.setStatus(Influencer.InfluencerStatus.valueOf(dto.getStatus()));
        if (dto.getContactEmail() != null) entity.setContactEmail(dto.getContactEmail());
        if (dto.getContactPhone() != null) entity.setContactPhone(dto.getContactPhone());
        if (dto.getCooperationPrice() != null) entity.setCooperationPrice(dto.getCooperationPrice());
        if (dto.getCategories() != null) entity.setCategories(dto.getCategories());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
    
    private InfluencerDTO toDTO(Influencer entity) {
        return InfluencerDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .platformId(entity.getPlatformId())
                .platform(entity.getPlatform().name())
                .avatarUrl(entity.getAvatarUrl())
                .followerCount(entity.getFollowerCount())
                .followingCount(entity.getFollowingCount())
                .totalViews(entity.getTotalViews())
                .totalLikes(entity.getTotalLikes())
                .level(entity.getLevel() != null ? entity.getLevel().name() : null)
                .status(entity.getStatus().name())
                .contactEmail(entity.getContactEmail())
                .contactPhone(entity.getContactPhone())
                .cooperationPrice(entity.getCooperationPrice())
                .categories(entity.getCategories())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
    
    private Influencer toEntity(InfluencerDTO dto) {
        return Influencer.builder()
                .name(dto.getName())
                .platformId(dto.getPlatformId())
                .platform(Influencer.Platform.valueOf(dto.getPlatform()))
                .avatarUrl(dto.getAvatarUrl())
                .followerCount(dto.getFollowerCount())
                .followingCount(dto.getFollowingCount())
                .totalViews(dto.getTotalViews())
                .totalLikes(dto.getTotalLikes())
                .level(dto.getLevel() != null ? Influencer.InfluencerLevel.valueOf(dto.getLevel()) : null)
                .contactEmail(dto.getContactEmail())
                .contactPhone(dto.getContactPhone())
                .cooperationPrice(dto.getCooperationPrice())
                .categories(dto.getCategories())
                .description(dto.getDescription())
                .build();
    }
}