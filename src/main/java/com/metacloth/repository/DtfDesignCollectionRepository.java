package com.metacloth.repository;

import com.metacloth.domain.entity.DtfDesignCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DtfDesignCollectionRepository extends JpaRepository<DtfDesignCollection, Long> {

    List<DtfDesignCollection> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<DtfDesignCollection> findBySessionIdOrderByUpdatedAtDesc(String sessionId);

    @Query("SELECT d FROM DtfDesignCollection d WHERE (:userId IS NOT NULL AND d.user.id = :userId) OR (:userId IS NULL AND d.sessionId = :sessionId) ORDER BY d.updatedAt DESC")
    List<DtfDesignCollection> findForUserOrSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Query("SELECT COALESCE(MAX(d.versionNumber), 0) FROM DtfDesignCollection d WHERE d.originalFilename = :filename AND ((:userId IS NOT NULL AND d.user.id = :userId) OR (:userId IS NULL AND d.sessionId = :sessionId))")
    int findLatestVersionNumber(@Param("filename") String filename, @Param("userId") Long userId, @Param("sessionId") String sessionId);
}

