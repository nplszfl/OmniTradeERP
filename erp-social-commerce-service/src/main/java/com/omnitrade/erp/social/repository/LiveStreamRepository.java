package com.omnitrade.erp.social.repository;

import com.omnitrade.erp.social.model.LiveStream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LiveStreamRepository extends JpaRepository<LiveStream, Long> {
    Optional<LiveStream> findByStreamId(String streamId);
    
    List<LiveStream> findByPlatform(LiveStream.Platform platform);
    
    List<LiveStream> findByStatus(LiveStream.StreamStatus status);
    
    List<LiveStream> findByInfluencerId(Long influencerId);
    
    @Query("SELECT l FROM LiveStream l WHERE l.scheduledStartTime BETWEEN :startTime AND :endTime")
    List<LiveStream> findByScheduledTimeBetween(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);
    
    @Query("SELECT l FROM LiveStream l WHERE l.status = 'LIVE'")
    List<LiveStream> findCurrentLiveStreams();
    
    @Query("SELECT l FROM LiveStream l WHERE l.actualStartTime IS NOT NULL ORDER BY l.actualStartTime DESC")
    List<LiveStream> findRecentLiveStreams();
}