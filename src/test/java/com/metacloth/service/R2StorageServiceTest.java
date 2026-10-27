package com.metacloth.service;

import com.metacloth.config.AppProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class R2StorageServiceTest {

    @Test
    public void testR2ClientInitialization() {
        String endpoint = System.getenv("R2_ENDPOINT");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://33bbf9ac240c347e9a1296d26a2a6b4d.r2.cloudflarestorage.com";
        }
        String accessKey = System.getenv("R2_ACCESS_KEY_ID");
        String secretKey = System.getenv("R2_SECRET_ACCESS_KEY");
        String bucket = System.getenv("R2_BUCKET_NAME");
        if (bucket == null || bucket.isBlank()) bucket = "shade-atelier-production";

        AppProperties.R2 r2 = new AppProperties.R2(endpoint, accessKey != null ? accessKey : "", secretKey != null ? secretKey : "", bucket, "");
        AppProperties props = new AppProperties(null, null, null, null, null, r2);
        R2StorageService service = new R2StorageService(props);
        service.init();

        if (accessKey != null && !accessKey.isBlank()) {
            assertTrue(service.isConfigured());
            var metrics = service.getBucketMetrics();
            System.out.println("=== R2 LIVE OBJECTS IN BUCKET: " + service.getBucketName() + " ===");
            System.out.println("Total Object Count: " + metrics.totalObjects());
            System.out.println("Total Size Bytes: " + metrics.totalBytes() + " (" + (metrics.totalBytes() / (1024.0 * 1024.0)) + " MB)");
            var list = service.listAllObjectKeys();
            for (String key : list) {
                System.out.println("  -> OBJECT IN R2: " + key);
            }
        }
        service.destroy();
    }
}