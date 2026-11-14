package com.omnitrade.erp.social.repository;

import com.omnitrade.erp.social.model.Influencer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InfluencerRepository extends JpaRepository<Influencer, Long> {
    Optional<Influencer> findByPlatformId(String platformId);
    
    List<Influencer> findByPlatform(Influencer.Platform platform);
    
    List<Influencer> findByStatus(Influencer.InfluencerStatus status);
    
    List<Influencer> findByLevel(Influencer.InfluencerLevel level);
    
    @Query("SELECT i FROM Influencer i WHERE i.followerCount >= :minFollowers ORDER BY i.followerCount DESC")
    List<Influencer> findTopInfluencersByFollowers(@Param("minFollowers") Integer minFollowers);
    
    @Query("SELECT i FROM Influencer i WHERE i.categories LIKE %:category%")
    List<Influencer> findByCategory(@Param("category") String category);
}