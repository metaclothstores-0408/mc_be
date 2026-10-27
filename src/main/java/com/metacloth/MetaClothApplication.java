package com.metacloth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class MetaClothApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(MetaClothApplication.class, args);
    }

    private static void loadDotEnv() {
        try {
            Path[] candidatePaths = {
                    Path.of(".env"),
                    Path.of("..", ".env"),
                    Path.of("mc_be", ".env"),
                    Path.of("d:", "Tshirt_Business", "WebAPP", "Github", "mc_be", ".env")
            };
            for (Path path : candidatePaths) {
                if (Files.exists(path)) {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            String key = line.substring(0, eq).trim();
                            String val = line.substring(eq + 1).trim();
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, val);
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception ignored) {}
    }
}