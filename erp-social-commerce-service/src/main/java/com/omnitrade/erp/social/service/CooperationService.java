package com.omnitrade.erp.social.service;

import com.crossborder.erp.common.exception.BizException;
import com.omnitrade.erp.social.dto.CooperationDTO;
import com.omnitrade.erp.social.model.Cooperation;
import com.omnitrade.erp.social.model.Influencer;
import com.omnitrade.erp.social.repository.CooperationRepository;
import com.omnitrade.erp.social.repository.InfluencerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CooperationService {
    
    private final CooperationRepository repository;
    private final InfluencerRepository influencerRepository;
    
    public List<CooperationDTO> findAll() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public CooperationDTO findById(Long id) {
        return repository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new BizException("合作不存在: " + id));
    }
    
    public List<CooperationDTO> findByInfluencerId(Long influencerId) {
        return repository.findByInfluencerId(influencerId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<CooperationDTO> findByProductId(String productId) {
        return repository.findByProductId(productId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<CooperationDTO> findByType(String type) {
        return repository.findByType(Cooperation.CooperationType.valueOf(type)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<CooperationDTO> findByStatus(String status) {
        return repository.findByStatus(Cooperation.CooperationStatus.valueOf(status)).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<CooperationDTO> findCompleted() {
        return repository.findCompletedCooperations().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<CooperationDTO> findUpcoming() {
        return repository.findUpcomingCooperations().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public List<CooperationDTO> findByDateRange(String startDate, String endDate) {
        LocalDateTime start = LocalDateTime.parse(startDate);
        LocalDateTime end = LocalDateTime.parse(endDate);
        return repository.findByDateRange(start, end).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public CooperationDTO create(CooperationDTO dto) {
        Cooperation entity = toEntity(dto);
        Influencer influencer = influencerRepository.findById(dto.getInfluencerId())
                .orElseThrow(() -> new BizException("达人不存在: " + dto.getInfluencerId()));
        entity.setInfluencer(influencer);
        entity.setStatus(Cooperation.CooperationStatus.DRAFT);
        Cooperation saved = repository.save(entity);
        return toDTO(saved);
    }
    
    @Transactional
    public CooperationDTO update(Long id, CooperationDTO dto) {
        Cooperation entity = repository.findById(id)
                .orElseThrow(() -> new BizException("合作不存在: " + id));
        
        if (dto.getProductId() != null) entity.setProductId(dto.getProductId());
        if (dto.getProductName() != null) entity.setProductName(dto.getProductName());
        if (dto.getType() != null) entity.setType(Cooperation.CooperationType.valueOf(dto.getType()));
        if (dto.getStatus() != null) entity.setStatus(Cooperation.CooperationStatus.valueOf(dto.getStatus()));
        if (dto.getPlannedStartDate() != null) {
            entity.setPlannedStartDate(LocalDateTime.parse(dto.getPlannedStartDate()));
        }
        if (dto.getPlannedEndDate() != null) {
            entity.setPlannedEndDate(LocalDateTime.parse(dto.getPlannedEndDate()));
        }
        if (dto.getActualStartDate() != null) {
            entity.setActualStartDate(LocalDateTime.parse(dto.getActualStartDate()));
        }
        if (dto.getActualEndDate() != null) {
            entity.setActualEndDate(LocalDateTime.parse(dto.getActualEndDate()));
        }
        if (dto.getAgreedPrice() != null) entity.setAgreedPrice(dto.getAgreedPrice());
        if (dto.getActualCost() != null) entity.setActualCost(dto.getActualCost());
        if (dto.getCommissionRate() != null) entity.setCommissionRate(dto.getCommissionRate());
        if (dto.getExpectedViews() != null) entity.setExpectedViews(dto.getExpectedViews());
        if (dto.getActualViews() != null) entity.setActualViews(dto.getActualViews());
        if (dto.getExpectedSales() != null) entity.setExpectedSales(dto.getExpectedSales());
        if (dto.getActualSales() != null) entity.setActualSales(dto.getActualSales());
        if (dto.getExpectedGmv() != null) entity.setExpectedGmv(dto.getExpectedGmv());
        if (dto.getActualGmv() != null) entity.setActualGmv(dto.getActualGmv());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getRemark() != null) entity.setRemark(dto.getRemark());
        
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public CooperationDTO confirm(Long id) {
        Cooperation entity = repository.findById(id)
                .orElseThrow(() -> new BizException("合作不存在: " + id));
        entity.setStatus(Cooperation.CooperationStatus.CONFIRMED);
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public CooperationDTO start(Long id) {
        Cooperation entity = repository.findById(id)
                .orElseThrow(() -> new BizException("合作不存在: " + id));
        entity.setStatus(Cooperation.CooperationStatus.IN_PROGRESS);
        entity.setActualStartDate(LocalDateTime.now());
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public CooperationDTO complete(Long id, CooperationDTO dto) {
        Cooperation entity = repository.findById(id)
                .orElseThrow(() -> new BizException("合作不存在: " + id));
        entity.setStatus(Cooperation.CooperationStatus.COMPLETED);
        entity.setActualEndDate(LocalDateTime.now());
        
        if (dto != null) {
            if (dto.getActualViews() != null) entity.setActualViews(dto.getActualViews());
            if (dto.getActualSales() != null) entity.setActualSales(dto.getActualSales());
            if (dto.getActualGmv() != null) entity.setActualGmv(dto.getActualGmv());
            if (dto.getActualCost() != null) entity.setActualCost(dto.getActualCost());
            if (dto.getContent() != null) entity.setContent(dto.getContent());
        }
        
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public CooperationDTO cancel(Long id) {
        Cooperation entity = repository.findById(id)
                .orElseThrow(() -> new BizException("合作不存在: " + id));
        entity.setStatus(Cooperation.CooperationStatus.CANCELLED);
        return toDTO(repository.save(entity));
    }
    
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
    
    private CooperationDTO toDTO(Cooperation entity) {
        return CooperationDTO.builder()
                .id(entity.getId())
                .influencerId(entity.getInfluencer().getId())
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .type(entity.getType().name())
                .status(entity.getStatus().name())
                .plannedStartDate(entity.getPlannedStartDate() != null ? entity.getPlannedStartDate().toString() : null)
                .plannedEndDate(entity.getPlannedEndDate() != null ? entity.getPlannedEndDate().toString() : null)
                .actualStartDate(entity.getActualStartDate() != null ? entity.getActualStartDate().toString() : null)
                .actualEndDate(entity.getActualEndDate() != null ? entity.getActualEndDate().toString() : null)
                .agreedPrice(entity.getAgreedPrice())
                .actualCost(entity.getActualCost())
                .commissionRate(entity.getCommissionRate())
                .expectedViews(entity.getExpectedViews())
                .actualViews(entity.getActualViews())
                .expectedSales(entity.getExpectedSales())
                .actualSales(entity.getActualSales())
                .expectedGmv(entity.getExpectedGmv())
                .actualGmv(entity.getActualGmv())
                .content(entity.getContent())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
    
    private Cooperation toEntity(CooperationDTO dto) {
        return Cooperation.builder()
                .productId(dto.getProductId())
                .productName(dto.getProductName())
                .type(Cooperation.CooperationType.valueOf(dto.getType()))
                .plannedStartDate(dto.getPlannedStartDate() != null ? LocalDateTime.parse(dto.getPlannedStartDate()) : null)
                .plannedEndDate(dto.getPlannedEndDate() != null ? LocalDateTime.parse(dto.getPlannedEndDate()) : null)
                .actualStartDate(dto.getActualStartDate() != null ? LocalDateTime.parse(dto.getActualStartDate()) : null)
                .actualEndDate(dto.getActualEndDate() != null ? LocalDateTime.parse(dto.getActualEndDate()) : null)
                .agreedPrice(dto.getAgreedPrice())
                .actualCost(dto.getActualCost())
                .commissionRate(dto.getCommissionRate())
                .expectedViews(dto.getExpectedViews())
                .actualViews(dto.getActualViews())
                .expectedSales(dto.getExpectedSales())
                .actualSales(dto.getActualSales())
                .expectedGmv(dto.getExpectedGmv())
                .actualGmv(dto.getActualGmv())
                .content(dto.getContent())
                .remark(dto.getRemark())
                .build();
    }
}