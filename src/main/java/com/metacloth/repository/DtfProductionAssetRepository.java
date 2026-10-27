package com.metacloth.repository;

import com.metacloth.domain.entity.DtfProductionAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DtfProductionAssetRepository extends JpaRepository<DtfProductionAsset, Long> {
    Optional<DtfProductionAsset> findByOrderItemId(Long orderItemId);
    List<DtfProductionAsset> findByOrderItemOrderId(Long orderId);

    @Query("SELECT a FROM DtfProductionAsset a WHERE a.expiresAt IS NOT NULL AND a.expiresAt <= :now AND a.status != 'ARCHIVED_PURGED'")
    List<DtfProductionAsset> findExpiredAssets(@Param("now") OffsetDateTime now);
}
