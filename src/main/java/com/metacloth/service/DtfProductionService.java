package com.metacloth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metacloth.api.dto.DtfDtos.DtfDownloadRequest;
import com.metacloth.api.dto.DtfDtos.DtfPrintParams;
import com.metacloth.config.AppProperties;
import com.metacloth.domain.entity.DtfProductionAsset;
import com.metacloth.domain.entity.OrderItem;
import com.metacloth.repository.DtfProductionAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DtfProductionService {

    private final DtfProductionAssetRepository assets;
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final R2StorageService r2Storage;

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
    private static final int DPI = 300;
    private static final int MAX_PX = 9000;
    private static final double SHEET_A4_W = 8.3, SHEET_A4_H = 11.7;
    private static final double SHEET_A3_W = 11.7, SHEET_A3_H = 16.5;

    @Async
    public void processOrderAsync(Long orderId, List<OrderItem> items) {
        for (OrderItem item : items) {
            try {
                processSingleItem(item);
            } catch (Exception e) {
                log.error("DTF failed item {}: {}", item.getId(), e.getMessage(), e);
            }
        }
        log.info("DTF pipeline finished order {}", orderId);
    }

    private void processSingleItem(OrderItem item) throws Exception {
        if (item.getPrintConfigJson() == null || item.getPrintConfigJson().isBlank()) return;
        JsonNode root;
        try {
            root = objectMapper.readTree(item.getPrintConfigJson());
        } catch (Exception e) {
            log.warn("DTF invalid JSON item {}", item.getId());
            return;
        }
        JsonNode dtf = root.get("dtf_print_parameters");
        if (dtf == null || dtf.isNull()) {
            log.info("DTF skip item {} no params", item.getId());
            return;
        }

        String stripHex = pickText(dtf, new String[]{"strip_color_hex", "stripColorHex"}, "#ffffff");
        int sens = pickInt(dtf, new String[]{"strip_sensitivity", "stripSensitivity"}, 30);
        int halftone = pickInt(dtf, new String[]{"halftone_density", "halftoneDensity"}, 0);
        double printScale = pickDouble(dtf, new String[]{"print_scale", "printScale"}, 1.0);
        if (printScale < 0.4) printScale = 0.4;
        if (printScale > 4.0) printScale = 4.0;

        String sheetPreset = pickText(dtf, new String[]{"selected_sheet_preset", "selectedSheetPreset"}, "A3");
        if (!"A4".equalsIgnoreCase(sheetPreset) && !"A3".equalsIgnoreCase(sheetPreset)) sheetPreset = "A3";

        Double printWidthInches = pickDoubleOrNull(dtf, new String[]{"print_width_inches", "printWidthInches"});
        if (printWidthInches == null || printWidthInches <= 0) {
            double sheetW = "A4".equalsIgnoreCase(sheetPreset) ? SHEET_A4_W : SHEET_A3_W;
            printWidthInches = Math.round(sheetW * (printScale / 2.0) * 100.0) / 100.0;
        }

        Double sheetW = pickDoubleOrNull(dtf, new String[]{"sheet_width_inches", "sheetWidthInches"});
        Double sheetH = pickDoubleOrNull(dtf, new String[]{"sheet_height_inches", "sheetHeightInches"});
        if (sheetW == null) sheetW = "A4".equalsIgnoreCase(sheetPreset) ? SHEET_A4_W : SHEET_A3_W;
        if (sheetH == null) sheetH = "A4".equalsIgnoreCase(sheetPreset) ? SHEET_A4_H : SHEET_A3_H;

        String originalUrl = null;
        if (dtf.has("original_asset_url") && !dtf.get("original_asset_url").isNull()) originalUrl = dtf.get("original_asset_url").asText();
        else if (dtf.has("originalAssetUrl") && !dtf.get("originalAssetUrl").isNull()) originalUrl = dtf.get("originalAssetUrl").asText();

        if ((originalUrl == null || originalUrl.isBlank() || originalUrl.toLowerCase().startsWith("blob:")) && dtf.has("preview_data_url") && !dtf.get("preview_data_url").isNull()) {
            String cand = dtf.get("preview_data_url").asText();
            if (cand != null && !cand.isBlank() && !cand.toLowerCase().startsWith("blob:")) originalUrl = cand;
        }
        if ((originalUrl == null || originalUrl.isBlank() || originalUrl.toLowerCase().startsWith("blob:")) && dtf.has("previewDataUrl") && !dtf.get("previewDataUrl").isNull()) {
            String cand2 = dtf.get("previewDataUrl").asText();
            if (cand2 != null && !cand2.isBlank() && !cand2.toLowerCase().startsWith("blob:")) originalUrl = cand2;
        }
        if ((originalUrl == null || originalUrl.isBlank() || originalUrl.toLowerCase().startsWith("blob:")) && item.getPreviewRenderUrl() != null && !item.getPreviewRenderUrl().toLowerCase().startsWith("blob:")) {
            originalUrl = item.getPreviewRenderUrl();
        }
        if (originalUrl == null || originalUrl.isBlank()) {
            log.warn("DTF no source {}", item.getId());
            return;
        }

        DtfProductionAsset asset = DtfProductionAsset.builder()
                .orderItem(item)
                .originalAssetUrl(originalUrl)
                .stripColorHex(stripHex)
                .stripSensitivity(sens)
                .halftoneDensity(halftone)
                .printScale(printScale)
                .selectedSheetPreset(sheetPreset.toUpperCase())
                .printWidthInches(printWidthInches)
                .sheetWidthInches(sheetW)
                .sheetHeightInches(sheetH)
                .status("PROCESSING")
                .build();
        assets.save(asset);

        try {
            BufferedImage src = fetchImage(originalUrl);
            if (src == null) throw new IOException("decode failed " + originalUrl);

            BufferedImage processed;
            if (printWidthInches != null && printWidthInches > 0) {
                processed = processImageAtWidth(src, stripHex, sens, halftone, printWidthInches);
            } else {
                processed = processImage(src, stripHex, sens, halftone, printScale);
            }

            // Encode PNG bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(processed, "png", baos);
            byte[] pngBytes = baos.toByteArray();

            // Store in Cloudflare R2
            Long orderId = (item.getOrder() != null) ? item.getOrder().getId() : 0L;
            String objectKey = String.format("orders/%d/items/%d/production_dtf_300dpi_%d.png", orderId, item.getId(), System.currentTimeMillis());
            String r2Url = r2Storage.uploadBytes(objectKey, pngBytes, "image/png");

            asset.setR2ObjectKey(objectKey);
            asset.setR2ImageUrl(r2Url);
            asset.setFileSizeBytes((long) pngBytes.length);
            asset.setExpiresAt(OffsetDateTime.now().plusDays(30)); // 30-day retention for production files

            // Optional local fallback backup if outputDir exists
            try {
                String dirStr = (props.dtf() != null && props.dtf().outputDir() != null) ? props.dtf().outputDir() : "./storage/dtf";
                Path dir = Path.of(dirStr);
                Files.createDirectories(dir);
                String name = String.format("dtf_%d_%d.png", item.getId(), System.currentTimeMillis());
                Path out = dir.resolve(name);
                Files.write(out, pngBytes);
                asset.setProcessedFilePath(out.toAbsolutePath().toString());
            } catch (Exception localErr) {
                log.debug("Local disk backup skipped: {}", localErr.getMessage());
            }

            asset.setStatus("READY");
            assets.save(asset);
            log.info("DTF READY item {} -> R2 key: {} ({} bytes)", item.getId(), objectKey, pngBytes.length);
        } catch (Exception e) {
            asset.setStatus("FAILED");
            asset.setErrorMessage(e.getMessage() != null ? e.getMessage().substring(0, Math.min(900, e.getMessage().length())) : "unknown");
            assets.save(asset);
            throw e;
        }
    }

    public byte[] renderForDownload(DtfDownloadRequest req) throws Exception {
        String url = req.original_asset_url();
        DtfPrintParams p = req.dtf_print_parameters();
        if ((url == null || url.isBlank() || url.toLowerCase().startsWith("blob:")) && p != null) {
            if (p.original_asset_url() != null && !p.original_asset_url().isBlank() && !p.original_asset_url().toLowerCase().startsWith("blob:")) {
                url = p.original_asset_url();
            } else if (p.preview_data_url() != null && !p.preview_data_url().isBlank() && !p.preview_data_url().toLowerCase().startsWith("blob:")) {
                url = p.preview_data_url();
            }
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("No source image provided for download render");
        }

        BufferedImage src = fetchImage(url);
        if (src == null) throw new IOException("Failed to decode source image: " + url);

        String hex = (p != null && p.strip_color_hex() != null) ? p.strip_color_hex() : "#ffffff";
        int sens = (p != null && p.strip_sensitivity() != null) ? p.strip_sensitivity() : 30;
        int half = (p != null && p.halftone_density() != null) ? p.halftone_density() : 0;

        Double widthInches = (p != null) ? p.print_width_inches() : null;
        if (widthInches == null || widthInches <= 0) {
            double scale = (p != null && p.print_scale() != null) ? p.print_scale() : 1.0;
            String sheet = (p != null && p.selected_sheet_preset() != null) ? p.selected_sheet_preset() : "A3";
            double sheetW = "A4".equalsIgnoreCase(sheet) ? SHEET_A4_W : SHEET_A3_W;
            widthInches = Math.round(sheetW * (scale / 2.0) * 100.0) / 100.0;
        }

        BufferedImage processed = processImageAtWidth(src, hex, sens, half, widthInches);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(processed, "png", baos);
        return baos.toByteArray();
    }

    private BufferedImage processImageAtWidth(BufferedImage src, String hex, int sens, int halftoneDensity, double printWidthInches) {
        int origW = src.getWidth(), origH = src.getHeight();
        if (origW <= 0 || origH <= 0) throw new IllegalArgumentException("Invalid source dimensions");
        double aspect = (double) origH / (double) origW;

        int targetW = (int) Math.round(printWidthInches * DPI);
        if (targetW < 100) targetW = 100;
        if (targetW > MAX_PX) targetW = MAX_PX;
        int targetH = (int) Math.round(targetW * aspect);
        if (targetH < 100) targetH = 100;
        if (targetH > MAX_PX) targetH = MAX_PX;

        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(src, 0, 0, targetW, targetH, null);
        g2.dispose();

        BufferedImage img = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(scaled, 0, 0, null);

        int[] target = hexToRgb(hex);
        double maxDist = 441.67295;
        double tol = (Math.max(0, Math.min(100, sens)) / 100.0) * maxDist;
        double feather = maxDist * 0.04;
        double blend = Math.max(0, Math.min(100, halftoneDensity)) / 100.0;

        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
                if (target != null) {
                    double dist = colorDistance(r, g, b, target[0], target[1], target[2]);
                    if (dist <= tol) {
                        img.setRGB(x, y, 0x00000000);
                        continue;
                    }
                    if (dist < tol + feather && tol > 0) {
                        double fade = (dist - tol) / feather;
                        if (fade < 0) fade = 0;
                        if (fade > 1) fade = 1;
                        int na = (int) Math.round(a * fade);
                        img.setRGB(x, y, (na << 24) | (r << 16) | (g << 8) | b);
                        if (fade < 0.9) continue;
                        a = na;
                    }
                }
                if (halftoneDensity > 0 && a > 20) {
                    double gray = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                    int bv = BAYER[(y % 4) * 4 + (x % 4)];
                    double thr = bv / 16.0 * (0.55 + blend * 0.45) + (1 - blend) * 0.15;
                    if (gray > 0.35 && gray < 0.92 && gray < thr + blend * 0.15) {
                        double hole = blend * (gray > 0.6 ? 1.0 : 0.6);
                        int na = (int) Math.round(a * (1 - hole * 0.85));
                        img.setRGB(x, y, (na << 24) | (r << 16) | (g << 8) | b);
                    }
                }
            }
        }
        return img;
    }

    private BufferedImage processImage(BufferedImage src, String hex, int sens, int halftoneDensity, double printScale) {
        int sw = src.getWidth(), sh = src.getHeight();
        int w = (int) Math.round(sw * Math.max(0.4, Math.min(4.0, printScale)));
        int h = (int) Math.round(sh * Math.max(0.4, Math.min(4.0, printScale)));
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.getGraphics().drawImage(scaled, 0, 0, null);

        int[] target = hexToRgb(hex);
        double maxDist = 441.67295;
        double tol = (Math.max(0, Math.min(100, sens)) / 100.0) * maxDist;
        double feather = maxDist * 0.04;
        double blend = Math.max(0, Math.min(100, halftoneDensity)) / 100.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
                if (target != null) {
                    double dist = colorDistance(r, g, b, target[0], target[1], target[2]);
                    if (dist <= tol) {
                        img.setRGB(x, y, 0x00000000);
                        continue;
                    }
                    if (dist < tol + feather && tol > 0) {
                        double fade = (dist - tol) / feather;
                        if (fade < 0) fade = 0;
                        if (fade > 1) fade = 1;
                        int na = (int) Math.round(a * fade);
                        img.setRGB(x, y, (na << 24) | (r << 16) | (g << 8) | b);
                        if (fade < 0.9) continue;
                        a = na;
                    }
                }
                if (halftoneDensity > 0 && a > 20) {
                    double gray = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                    int bv = BAYER[(y % 4) * 4 + (x % 4)];
                    double thr = bv / 16.0 * (0.55 + blend * 0.45) + (1 - blend) * 0.15;
                    if (gray > 0.35 && gray < 0.92 && gray < thr + blend * 0.15) {
                        double hole = blend * (gray > 0.6 ? 1.0 : 0.6);
                        int na = (int) Math.round(a * (1 - hole * 0.85));
                        img.setRGB(x, y, (na << 24) | (r << 16) | (g << 8) | b);
                    }
                }
            }
        }
        return img;
    }

    private int[] hexToRgb(String hex) {
        if (hex == null) return null;
        String h = hex.trim().replaceFirst("^#", "");
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        if (!h.matches("^[0-9a-fA-F]{6}$")) return null;
        return new int[]{Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16), Integer.parseInt(h.substring(4, 6), 16)};
    }

    private double colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        int dr = r1 - r2, dg = g1 - g2, db = b1 - b2;
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private BufferedImage fetchImage(String url) throws Exception {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Empty source URL");
        String low = url.toLowerCase();
        if (low.startsWith("blob:")) {
            throw new IllegalArgumentException("Blob URL is local to the browser and cannot be fetched by the server.");
        }
        if (url.startsWith("data:")) {
            int c = url.indexOf(',');
            if (c < 0) throw new IOException("Invalid data URL");
            String b64 = url.substring(c + 1).trim();
            byte[] bytes = Base64.getDecoder().decode(b64.replaceAll("\\s+", ""));
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
                return ImageIO.read(bis);
            }
        }

        // Direct R2 object key (e.g. users/1/collections/...)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            byte[] r2Bytes = r2Storage.getObjectBytes(url);
            if (r2Bytes.length > 0) {
                try (ByteArrayInputStream bis = new ByteArrayInputStream(r2Bytes)) {
                    return ImageIO.read(bis);
                }
            }
        }

        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Invalid URL scheme: " + scheme);
        }

        URL u = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "MetaCloth-DTF-Renderer/1.0");

        try (InputStream in = conn.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("ImageIO could not decode image from URL");
            return img;
        }
    }

    private String pickText(JsonNode node, String[] keys, String def) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) return node.get(k).asText();
        }
        return def;
    }

    private int pickInt(JsonNode node, String[] keys, int def) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) return node.get(k).asInt();
        }
        return def;
    }

    private double pickDouble(JsonNode node, String[] keys, double def) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) return node.get(k).asDouble();
        }
        return def;
    }

    private Double pickDoubleOrNull(JsonNode node, String[] keys) {
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) return node.get(k).asDouble();
        }
        return null;
    }
}
