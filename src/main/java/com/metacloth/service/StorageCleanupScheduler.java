package com.metacloth.service;

import com.metacloth.domain.entity.DtfDesignCollection;
import com.metacloth.domain.entity.DtfProductionAsset;
import com.metacloth.repository.DtfDesignCollectionRepository;
import com.metacloth.repository.DtfProductionAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Automated Lifecycle Cleanup Scheduler.
 * Protects Cloudflare R2 10 GB Free Tier by automatically purging:
 * 1. Saved Collections older than 14 days (or 28 days if extended via cart).
 * 2. Production 300 DPI print files older than 30 days post-order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageCleanupScheduler {

    private final DtfDesignCollectionRepository collections;
    private final DtfProductionAssetRepository productionAssets;
    private final R2StorageService r2Storage;

    /**
     * Runs daily at 02:00 AM server time.
     */
    @Scheduled(cron = "${metacloth.storage.cleanup-cron:0 0 2 * * ?}")
    @Transactional
    public CleanupReport runScheduledCleanup() {
        log.info("Starting automated R2 storage lifecycle cleanup job...");
        OffsetDateTime now = OffsetDateTime.now();

        int purgedCollections = 0;
        long bytesFreedCollections = 0;

        List<DtfDesignCollection> expiredCollections = collections.findExpiredCollections(now);
        for (DtfDesignCollection c : expiredCollections) {
            try {
                if (c.getR2ObjectKey() != null) {
                    r2Storage.deleteObject(c.getR2ObjectKey());
                }
                if (c.getR2PreviewKey() != null) {
                    r2Storage.deleteObject(c.getR2PreviewKey());
                }
                long size = c.getFileSizeBytes() != null ? c.getFileSizeBytes() : 0L;
                bytesFreedCollections += size;
                collections.delete(c);
                purgedCollections++;
            } catch (Exception e) {
                log.error("Failed to purge expired collection #{}: {}", c.getId(), e.getMessage());
            }
        }

        int purgedAssets = 0;
        long bytesFreedAssets = 0;

        List<DtfProductionAsset> expiredAssets = productionAssets.findExpiredAssets(now);
        for (DtfProductionAsset a : expiredAssets) {
            try {
                if (a.getR2ObjectKey() != null) {
                    r2Storage.deleteObject(a.getR2ObjectKey());
                }
                long size = a.getFileSizeBytes() != null ? a.getFileSizeBytes() : 0L;
                bytesFreedAssets += size;
                a.setStatus("ARCHIVED_PURGED");
                productionAssets.save(a);
                purgedAssets++;
            } catch (Exception e) {
                log.error("Failed to purge expired production asset #{}: {}", a.getId(), e.getMessage());
            }
        }

        long totalFreedBytes = bytesFreedCollections + bytesFreedAssets;
        double totalFreedMb = Math.round((totalFreedBytes / (1024.0 * 1024.0)) * 100.0) / 100.0;

        log.info("Cleanup job finished. Purged {} expired collections ({} bytes) and {} production assets ({} bytes). Total freed: {} MB",
                purgedCollections, bytesFreedCollections, purgedAssets, bytesFreedAssets, totalFreedMb);

        return new CleanupReport(purgedCollections, purgedAssets, totalFreedBytes, totalFreedMb, now);
    }

    public record CleanupReport(
            int purgedCollections,
            int purgedProductionAssets,
            long bytesFreed,
            double mbFreed,
            OffsetDateTime executedAt
    ) {}
}
