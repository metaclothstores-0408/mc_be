package com.metacloth.service;

import com.metacloth.config.AppProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class R2StorageService {

    private final AppProperties properties;

    public static final long USER_STORAGE_QUOTA_BYTES = 60L * 1024L * 1024L; // 60 MB per user
    public static final long R2_FREE_TIER_BYTES = 10L * 1024L * 1024L * 1024L; // 10 GB free tier

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private String bucketName;
    private String publicBaseUrl;
    private boolean isConfigured = false;

    @PostConstruct
    public void init() {
        try {
            String endpoint = properties.r2() != null ? properties.r2().endpoint() : null;
            String accessKeyId = properties.r2() != null ? properties.r2().accessKeyId() : null;
            String secretAccessKey = properties.r2() != null ? properties.r2().secretAccessKey() : null;
            String bName = properties.r2() != null ? properties.r2().bucketName() : null;
            String pubBase = properties.r2() != null ? properties.r2().publicBaseUrl() : null;

            // Direct check of system properties / env
            if (accessKeyId == null || accessKeyId.isBlank()) {
                accessKeyId = System.getProperty("R2_ACCESS_KEY_ID", System.getenv("R2_ACCESS_KEY_ID"));
            }
            if (secretAccessKey == null || secretAccessKey.isBlank()) {
                secretAccessKey = System.getProperty("R2_SECRET_ACCESS_KEY", System.getenv("R2_SECRET_ACCESS_KEY"));
            }
            if (endpoint == null || endpoint.isBlank()) {
                endpoint = System.getProperty("R2_ENDPOINT", System.getenv("R2_ENDPOINT"));
            }
            if (bName == null || bName.isBlank()) {
                bName = System.getProperty("R2_BUCKET_NAME", System.getenv("R2_BUCKET_NAME"));
            }

            // Fallback: direct read from untracked .env file
            if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank()) {
                Map<String, String> dotEnv = readDotEnvFile();
                if (accessKeyId == null || accessKeyId.isBlank()) accessKeyId = dotEnv.get("R2_ACCESS_KEY_ID");
                if (secretAccessKey == null || secretAccessKey.isBlank()) secretAccessKey = dotEnv.get("R2_SECRET_ACCESS_KEY");
                if (endpoint == null || endpoint.isBlank()) endpoint = dotEnv.get("R2_ENDPOINT");
                if (bName == null || bName.isBlank()) bName = dotEnv.get("R2_BUCKET_NAME");
                if (pubBase == null || pubBase.isBlank()) pubBase = dotEnv.get("R2_PUBLIC_BASE_URL");
            }

            if (accessKeyId == null || accessKeyId.isBlank() || secretAccessKey == null || secretAccessKey.isBlank()) {
                log.error("Cloudflare R2 credentials NOT found in properties, env, or .env file! Uploads to R2 will fail.");
                return;
            }

            this.bucketName = (bName != null && !bName.isBlank()) ? bName.trim() : "shade-atelier-production";
            this.publicBaseUrl = pubBase;

            if (endpoint == null || endpoint.isBlank()) {
                endpoint = "https://33bbf9ac240c347e9a1296d26a2a6b4d.r2.cloudflarestorage.com";
            }

            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim());
            StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

            S3Configuration serviceConfiguration = S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .chunkedEncodingEnabled(false)
                    .build();

            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint.trim()))
                    .credentialsProvider(credentialsProvider)
                    .region(Region.of("auto"))
                    .serviceConfiguration(serviceConfiguration)
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .build();

            this.s3Presigner = S3Presigner.builder()
                    .endpointOverride(URI.create(endpoint.trim()))
                    .credentialsProvider(credentialsProvider)
                    .region(Region.of("auto"))
                    .serviceConfiguration(serviceConfiguration)
                    .build();

            this.isConfigured = true;
            log.info("Successfully connected Cloudflare R2 S3 client for bucket: {}", bucketName);
        } catch (Exception e) {
            log.error("Failed to initialize Cloudflare R2 storage client: {}", e.getMessage(), e);
        }
    }

    private Map<String, String> readDotEnvFile() {
        Map<String, String> map = new HashMap<>();
        try {
            Path[] candidates = {
                    Path.of(".env"),
                    Path.of("..", ".env"),
                    Path.of("mc_be", ".env"),
                    Path.of("d:", "Tshirt_Business", "WebAPP", "Github", "mc_be", ".env")
            };
            for (Path p : candidates) {
                if (Files.exists(p)) {
                    for (String line : Files.readAllLines(p)) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    @PreDestroy
    public void destroy() {
        if (s3Client != null) {
            try { s3Client.close(); } catch (Exception ignored) {}
        }
        if (s3Presigner != null) {
            try { s3Presigner.close(); } catch (Exception ignored) {}
        }
    }

    public boolean isConfigured() {
        return isConfigured;
    }

    public String getBucketName() {
        return bucketName;
    }

    /**
     * Uploads binary bytes to Cloudflare R2 and returns a download/presigned URL.
     */
    public String uploadBytes(String objectKey, byte[] bytes, String contentType) {
        if (!isConfigured) {
            throw new IllegalStateException("Cloudflare R2 storage client is not configured. Verify R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY in .env");
        }

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(contentType != null ? contentType : "image/png")
                    .contentLength((long) bytes.length)
                    .build();

            s3Client.putObject(putReq, RequestBody.fromBytes(bytes));
            log.info("Uploaded {} bytes to R2 at key: {}", bytes.length, objectKey);
            return getFileUrl(objectKey);
        } catch (Exception e) {
            log.error("Failed to upload object to R2 (key={}): {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("Failed to store image in R2: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves accessible URL for the object.
     */
    public String getFileUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return null;
        if (!isConfigured) return "/api/v1/storage/file?key=" + objectKey;

        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            String base = publicBaseUrl.trim();
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + "/" + objectKey;
        }

        // Generate 7-day presigned GET URL
        return generatePresignedUrl(objectKey, Duration.ofDays(7));
    }

    public String generatePresignedUrl(String objectKey, Duration duration) {
        if (!isConfigured || s3Presigner == null) {
            return "/api/v1/storage/file?key=" + objectKey;
        }
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(getReq)
                    .build();

            return s3Presigner.presignGetObject(presignReq).url().toString();
        } catch (Exception e) {
            log.error("Failed to presign URL for {}: {}", objectKey, e.getMessage());
            return "/api/v1/storage/file?key=" + objectKey;
        }
    }

    public byte[] getObjectBytes(String objectKey) {
        if (!isConfigured) {
            return new byte[0];
        }
        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            return s3Client.getObjectAsBytes(getReq).asByteArray();
        } catch (Exception e) {
            log.error("Failed to fetch object bytes from R2 (key={}): {}", objectKey, e.getMessage());
            return new byte[0];
        }
    }

    public boolean deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || !isConfigured) return false;
        try {
            DeleteObjectRequest delReq = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(delReq);
            log.info("Deleted object from R2: {}", objectKey);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete object from R2 (key={}): {}", objectKey, e.getMessage());
            return false;
        }
    }

    public BucketMetrics getBucketMetrics() {
        if (!isConfigured) {
            return new BucketMetrics(0L, 0L, 0L, 0L, 0L, 0, Map.of());
        }

        long totalBytes = 0;
        int objectCount = 0;
        long collectionsBytes = 0;
        long productionBytes = 0;
        long curatedBytes = 0;
        long otherBytes = 0;
        Map<String, Long> categoryCount = new HashMap<>();

        try {
            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1000)
                    .build();

            ListObjectsV2Response res;
            do {
                res = s3Client.listObjectsV2(listReq);
                for (S3Object s3Object : res.contents()) {
                    long size = s3Object.size();
                    totalBytes += size;
                    objectCount++;

                    String key = s3Object.key();
                    if (key.contains("/collections/")) {
                        collectionsBytes += size;
                        categoryCount.merge("collections", 1L, Long::sum);
                    } else if (key.contains("/orders/") || key.contains("production_print")) {
                        productionBytes += size;
                        categoryCount.merge("production", 1L, Long::sum);
                    } else if (key.startsWith("curated/")) {
                        curatedBytes += size;
                        categoryCount.merge("curated", 1L, Long::sum);
                    } else {
                        otherBytes += size;
                        categoryCount.merge("other", 1L, Long::sum);
                    }
                }
                listReq = listReq.toBuilder().continuationToken(res.nextContinuationToken()).build();
            } while (res.isTruncated());

        } catch (Exception e) {
            log.error("Failed to list bucket objects for metrics: {}", e.getMessage());
        }

        return new BucketMetrics(
                totalBytes,
                collectionsBytes,
                productionBytes,
                curatedBytes,
                otherBytes,
                objectCount,
                categoryCount
        );
    }

    public java.util.List<String> listAllObjectKeys() {
        if (!isConfigured || s3Client == null) return java.util.List.of();
        java.util.List<String> keys = new java.util.ArrayList<>();
        try {
            ListObjectsV2Request listReq = ListObjectsV2Request.builder().bucket(bucketName).maxKeys(100).build();
            ListObjectsV2Response res = s3Client.listObjectsV2(listReq);
            for (S3Object obj : res.contents()) {
                keys.add(obj.key() + " (" + obj.size() + " bytes)");
            }
        } catch (Exception e) {
            log.error("Failed to list keys: {}", e.getMessage());
        }
        return keys;
    }

    public record BucketMetrics(
            long totalBytes,
            long collectionsBytes,
            long productionBytes,
            long curatedBytes,
            long otherBytes,
            int totalObjects,
            Map<String, Long> categoryCount
    ) {}
}