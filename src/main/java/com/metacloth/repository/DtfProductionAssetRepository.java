package com.metacloth.repository;

import com.metacloth.domain.entity.DtfProductionAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DtfProductionAssetRepository extends JpaRepository<DtfProductionAsset, Long> {
    Optional<DtfProductionAsset> findByOrderItemId(Long orderItemId);
    List<DtfProductionAsset> findByOrderItemOrderId(Long orderId);
}
