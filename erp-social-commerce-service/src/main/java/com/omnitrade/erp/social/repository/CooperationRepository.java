package com.omnitrade.erp.social.repository;

import com.omnitrade.erp.social.model.Cooperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CooperationRepository extends JpaRepository<Cooperation, Long> {
    
    List<Cooperation> findByInfluencerId(Long influencerId);
    
    List<Cooperation> findByProductId(String productId);
    
    List<Cooperation> findByType(Cooperation.CooperationType type);
    
    List<Cooperation> findByStatus(Cooperation.CooperationStatus status);
    
    @Query("SELECT c FROM Cooperation c WHERE c.influencer.id = :influencerId AND c.status = :status")
    List<Cooperation> findByInfluencerIdAndStatus(@Param("influencerId") Long influencerId,
                                                @Param("status") Cooperation.CooperationStatus status);
    
    @Query("SELECT c FROM Cooperation c WHERE c.plannedStartDate <= :endDate AND c.plannedEndDate >= :startDate")
    List<Cooperation> findByDateRange(@Param("startDate") java.time.LocalDateTime startDate,
                                        @Param("endDate") java.time.LocalDateTime endDate);
    
    @Query("SELECT c FROM Cooperation c WHERE c.status = 'COMPLETED' ORDER BY c.actualEndDate DESC")
    List<Cooperation> findCompletedCooperations();
    
    @Query("SELECT c FROM Cooperation c WHERE c.status IN ('PENDING', 'CONFIRMED') ORDER BY c.plannedStartDate ASC")
    List<Cooperation> findUpcomingCooperations();
}