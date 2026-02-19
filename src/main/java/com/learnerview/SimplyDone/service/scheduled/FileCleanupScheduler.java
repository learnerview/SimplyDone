package com.learnerview.SimplyDone.service.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled task to automatically clean up uploaded files after 10 minutes.
 */
@Component
@Slf4j
public class FileCleanupScheduler {

    @Value("${simplydone.upload.directory:${java.io.tmpdir}/simplydone-uploads}")
    private String uploadDirectory;

    @Value("${simplydone.upload.retention-minutes:10}")
    private int retentionMinutes;

    /**
     * Run cleanup every minute to delete files older than retention period.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000) // Run every 60 seconds
    public void cleanupOldFiles() {
        log.debug("Running file cleanup task");

        Path uploadPath = Paths.get(uploadDirectory);
        
        // Create directory if it doesn't exist
        if (!Files.exists(uploadPath)) {
            try {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath);
            } catch (IOException e) {
                log.error("Failed to create upload directory: {}", e.getMessage());
                return;
            }
        }

        AtomicInteger deletedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        Instant cutoffTime = Instant.now().minus(retentionMinutes, ChronoUnit.MINUTES);

        try {
            Files.walkFileTree(uploadPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Instant fileTime = attrs.lastModifiedTime().toInstant();
                        
                        // Delete if file is older than retention period
                        if (fileTime.isBefore(cutoffTime)) {
                            Files.delete(file);
                            deletedCount.incrementAndGet();
                            log.info("Deleted old uploaded file: {} (age: {} minutes)", 
                                file.getFileName(), 
                                ChronoUnit.MINUTES.between(fileTime, Instant.now()));
                        }
                    } catch (IOException e) {
                        errorCount.incrementAndGet();
                        log.error("Failed to delete file {}: {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.error("Failed to access file {}: {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });

            if (deletedCount.get() > 0) {
                log.info("File cleanup completed: {} files deleted, {} errors", 
                    deletedCount.get(), errorCount.get());
            }

        } catch (IOException e) {
            log.error("Error during file cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Get current cleanup statistics.
     *
     * @return Map with cleanup stats
     */
    public java.util.Map<String, Object> getCleanupStats() {
        Path uploadPath = Paths.get(uploadDirectory);
        
        try {
            if (!Files.exists(uploadPath)) {
                return java.util.Map.of(
                    "uploadDirectory", uploadDirectory,
                    "retentionMinutes", retentionMinutes,
                    "currentFileCount", 0
                );
            }

            long fileCount = Files.list(uploadPath)
                .filter(Files::isRegularFile)
                .count();

            return java.util.Map.of(
                "uploadDirectory", uploadDirectory,
                "retentionMinutes", retentionMinutes,
                "currentFileCount", fileCount
            );

        } catch (IOException e) {
            log.error("Error getting cleanup stats: {}", e.getMessage());
            return java.util.Map.of(
                "uploadDirectory", uploadDirectory,
                "retentionMinutes", retentionMinutes,
                "error", e.getMessage()
            );
        }
    }
}
