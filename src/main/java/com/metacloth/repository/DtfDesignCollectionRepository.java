package com.metacloth.repository;

import com.metacloth.domain.entity.DtfDesignCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface DtfDesignCollectionRepository extends JpaRepository<DtfDesignCollection, Long> {

    List<DtfDesignCollection> findByUserIdOrderByUpdatedAtDesc(Long userId);

    List<DtfDesignCollection> findBySessionIdOrderByUpdatedAtDesc(String sessionId);

    @Query("SELECT d FROM DtfDesignCollection d WHERE (:userId IS NOT NULL AND d.user.id = :userId) OR (:userId IS NULL AND d.sessionId = :sessionId) ORDER BY d.updatedAt DESC")
    List<DtfDesignCollection> findForUserOrSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Query("SELECT COALESCE(MAX(d.versionNumber), 0) FROM DtfDesignCollection d WHERE d.originalFilename = :filename AND d.user.id = :userId")
    int findLatestVersionForUser(@Param("filename") String filename, @Param("userId") Long userId);

    @Query("SELECT COALESCE(MAX(d.versionNumber), 0) FROM DtfDesignCollection d WHERE d.originalFilename = :filename AND d.sessionId = :sessionId")
    int findLatestVersionForSession(@Param("filename") String filename, @Param("sessionId") String sessionId);

    @Query("SELECT COALESCE(MAX(d.versionNumber), 0) FROM DtfDesignCollection d WHERE d.originalFilename = :filename AND ((:userId IS NOT NULL AND d.user.id = :userId) OR (:userId IS NULL AND d.sessionId = :sessionId))")
    int findLatestVersionNumber(@Param("filename") String filename, @Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Query("SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM DtfDesignCollection d WHERE d.user.id = :userId")
    long calculateUserStorageBytes(@Param("userId") Long userId);

    @Query("SELECT d FROM DtfDesignCollection d WHERE d.expiresAt IS NOT NULL AND d.expiresAt <= :now")
    List<DtfDesignCollection> findExpiredCollections(@Param("now") OffsetDateTime now);

    @Query("SELECT d.user.id, d.user.email, SUM(d.fileSizeBytes), COUNT(d) FROM DtfDesignCollection d WHERE d.user IS NOT NULL GROUP BY d.user.id, d.user.email ORDER BY SUM(d.fileSizeBytes) DESC")
    List<Object[]> getUserStorageBreakdown();
}
