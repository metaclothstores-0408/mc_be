package com.metacloth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

@Slf4j
@Service
public class ImageEnhancerService {

    private final String binaryName = System.getProperty("os.name").toLowerCase().contains("win")
            ? "realesrgan-ncnn-vulkan.exe"
            : "realesrgan-ncnn-vulkan";

    private final Path toolDir = resolveToolDir();
    private final File binaryExecutable = toolDir.resolve(binaryName).toFile();
    private final File modelsDir = toolDir.resolve("models").toFile();

    private Path resolveToolDir() {
        Path p1 = Paths.get("tools/realesrgan").toAbsolutePath();
        if (Files.exists(p1.resolve(binaryName))) {
            return p1;
        }
        Path p2 = Paths.get(System.getProperty("user.dir", ""), "tools/realesrgan").toAbsolutePath();
        if (Files.exists(p2.resolve(binaryName))) {
            return p2;
        }
        Path p3 = Paths.get("C:/Users/AVITA/Downloads/realesrgan-ncnn-vulkan-20220424-windows").toAbsolutePath();
        if (Files.exists(p3.resolve(binaryName))) {
            return p3;
        }
        return p1;
    }

    public byte[] processDtfPrint(
            MultipartFile file, 
            boolean enableEnhance, 
            boolean removeBackground, 
            boolean isPhoto) throws Exception {

        String runId = UUID.randomUUID().toString();
        Path tempDir = Files.createTempDirectory("dtf_job_" + runId);

        File rawInput = tempDir.resolve("input.png").toFile();
        File noBgFile = tempDir.resolve("nobg.png").toFile();
        File upscaledFile = tempDir.resolve("upscaled.png").toFile();

        try {
            file.transferTo(rawInput);

            File finalImageFile;

            // Scenario 1: User wants AI Enhancement (4x Super-Resolution)
            if (enableEnhance) {
                if (!binaryExecutable.exists()) {
                    throw new IllegalStateException("Real-ESRGAN binary not found at: " + binaryExecutable.getAbsolutePath());
                }

                File stepInput = rawInput;
                if (removeBackground) {
                    try {
                        runCliCommand("rembg", "i", rawInput.getAbsolutePath(), noBgFile.getAbsolutePath());
                        stepInput = noBgFile;
                    } catch (Exception e) {
                        log.warn("rembg execution failed: {}", e.getMessage());
                        throw new RuntimeException("Background removal tool 'rembg' is not found in system PATH. " +
                                "Please uncheck 'Remove Background' or install rembg via 'pip install rembg'. Error: " + e.getMessage(), e);
                    }
                }

                String model = isPhoto ? "realesrgan-x4plus" : "realesrgan-x4plus-anime";
                log.info("Running Real-ESRGAN with model={} on file={}", model, stepInput.getName());

                runCliCommand(
                    binaryExecutable.getAbsolutePath(),
                    "-i", stepInput.getAbsolutePath(),
                    "-o", upscaledFile.getAbsolutePath(),
                    "-s", "4",
                    "-m", modelsDir.getAbsolutePath(),
                    "-n", model,
                    "-f", "png",
                    "-t", "0"
                );

                finalImageFile = upscaledFile;
            } 
            // Scenario 2: User keeps original image as-is (bypassing Real-ESRGAN)
            else {
                log.info("Enhancement disabled: passing image straight through to 300 DPI metadata tagger");
                if (removeBackground) {
                    try {
                        runCliCommand("rembg", "i", rawInput.getAbsolutePath(), noBgFile.getAbsolutePath());
                        finalImageFile = noBgFile;
                    } catch (Exception e) {
                        log.warn("rembg execution failed: {}", e.getMessage());
                        throw new RuntimeException("Background removal tool 'rembg' is not found in system PATH. " +
                                "Please uncheck 'Remove Background' or install rembg via 'pip install rembg'. Error: " + e.getMessage(), e);
                    }
                } else {
                    finalImageFile = rawInput;
                }
            }

            // Always ensure the output has 300 DPI physical metadata for the DTF printer
            BufferedImage resultImage = ImageIO.read(finalImageFile);
            if (resultImage == null) {
                throw new IllegalStateException("Failed to read processed image: " + finalImageFile.getAbsolutePath());
            }
            return write300DpiMetadata(resultImage);

        } finally {
            try {
                Files.walk(tempDir)
                     .sorted((a, b) -> b.compareTo(a))
                     .map(Path::toFile)
                     .forEach(File::delete);
            } catch (Exception e) {
                log.warn("Failed to clean up temp dir {}: {}", tempDir, e.getMessage());
            }
        }
    }

    private void runCliCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorMsg = new String(process.getInputStream().readAllBytes());
            throw new RuntimeException("CLI error (Exit " + exitCode + "): " + errorMsg);
        }
    }

    private byte[] write300DpiMetadata(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) throw new IllegalStateException("No PNG writer found");

        ImageWriter writer = writers.next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        IIOMetadata metadata = writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(image), writeParam);

        double dotsPerMeter = 300.0 / 0.0254;
        IIOMetadataNode horiz = new IIOMetadataNode("HorizontalPixelSize");
        horiz.setAttribute("value", Double.toString(1000.0 / dotsPerMeter));

        IIOMetadataNode vert = new IIOMetadataNode("VerticalPixelSize");
        vert.setAttribute("value", Double.toString(1000.0 / dotsPerMeter));

        IIOMetadataNode dim = new IIOMetadataNode("Dimension");
        dim.appendChild(horiz);
        dim.appendChild(vert);

        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_1.0");
        root.appendChild(dim);
        metadata.mergeTree("javax_imageio_1.0", root);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(metadata, new IIOImage(image, null, metadata), writeParam);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
