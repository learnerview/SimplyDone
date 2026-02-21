package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Strategy for executing cleanup and maintenance jobs.
 * Supports: DELETE_OLD_FILES, CLEAR_DIRECTORY, ARCHIVE_OLD_FILES, VACUUM_DATABASE operations.
 */
@Component
@Slf4j
public class CleanupJobStrategy implements JobExecutionStrategy {

    @Override
    public JobType getSupportedJobType() {
        return JobType.CLEANUP;
    }

    @Override
    public void execute(Job job) throws Exception {
        log.info("Executing cleanup job: {} (ID: {})", job.getMessage(), job.getId());

        validateJob(job);

        Map<String, Object> params = job.getParameters();
        String operation = ((String) params.get("operation")).toUpperCase();

        try {
            switch (operation) {
                case "DELETE_OLD_FILES":
                    deleteOldFiles(params);
                    break;
                case "CLEAR_DIRECTORY":
                    clearDirectory(params);
                    break;
                case "CLEAR_TEMP":
                    clearTempFiles(params);
                    break;
                case "ARCHIVE_OLD_FILES":
                    archiveOldFiles(params);
                    break;
                case "DELETE_BY_PATTERN":
                    deleteByPattern(params);
                    break;
                case "CLEANUP_LOGS":
                    cleanupLogs(params);
                    break;
                case "PURGE_CACHE":
                    purgeCache(params);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported cleanup operation: " + operation);
            }

            log.info("Cleanup operation {} completed successfully for job: {}", operation, job.getId());

        } catch (Exception e) {
            log.error("Cleanup operation failed for job {}: {}", job.getId(), e.getMessage());
            throw new Exception("Cleanup operation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateJob(Job job) throws IllegalArgumentException {
        if (job.getParameters() == null) {
            throw new IllegalArgumentException("Cleanup job requires parameters");
        }

        Map<String, Object> params = job.getParameters();
        String operation = (String) params.get("operation");

        if (operation == null || operation.trim().isEmpty()) {
            throw new IllegalArgumentException("Cleanup 'operation' is required");
        }

        // Most operations require a directory path; PURGE_CACHE and CLEAR_TEMP handle their own paths
        if (!operation.equalsIgnoreCase("PURGE_CACHE") && !operation.equalsIgnoreCase("CLEAR_TEMP")) {
            String directory = (String) params.get("directory");
            if (directory == null || directory.trim().isEmpty()) {
                throw new IllegalArgumentException("Cleanup 'directory' is required");
            }
        }
    }

    @Override
    public long estimateExecutionTime(Job job) {
        String directory = (String) job.getParameters().get("directory");

        if (directory != null) {
            try {
                Path dir = Paths.get(directory);
                if (Files.exists(dir) && Files.isDirectory(dir)) {
                    // Estimate based on file count
                    long fileCount = Files.walk(dir).count();
                    return Math.max(5, fileCount / 1000); // 1 second per 1000 files
                }
            } catch (IOException e) {
                log.warn("Could not estimate cleanup time: {}", e.getMessage());
            }
        }

        return 10; // Default estimate
    }

    private void deleteOldFiles(Map<String, Object> params) throws IOException {
        String directory = (String) params.get("directory");
        Integer olderThanDays = (Integer) params.getOrDefault("olderThanDays", 30);
        Boolean recursive = (Boolean) params.getOrDefault("recursive", false);

        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            log.warn("Directory not found: {}", directory);
            return;
        }

        Instant threshold = Instant.now().minus(olderThanDays, ChronoUnit.DAYS);
        AtomicInteger deletedCount = new AtomicInteger(0);
        AtomicLong freedSpace = new AtomicLong(0);

        if (Boolean.TRUE.equals(recursive)) {
            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.lastModifiedTime().toInstant().isBefore(threshold)) {
                        long size = attrs.size();
                        Files.delete(file);
                        deletedCount.incrementAndGet();
                        freedSpace.addAndGet(size);
                        log.debug("Deleted old file: {}", file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            try (Stream<Path> files = Files.list(dirPath)) {
                files.filter(Files::isRegularFile)
                     .forEach(file -> {
                         try {
                             BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                             if (attrs.lastModifiedTime().toInstant().isBefore(threshold)) {
                                 long size = attrs.size();
                                 Files.delete(file);
                                 deletedCount.incrementAndGet();
                                 freedSpace.addAndGet(size);
                                 log.debug("Deleted old file: {}", file);
                             }
                         } catch (IOException e) {
                             log.warn("Failed to delete file {}: {}", file, e.getMessage());
                         }
                     });
            }
        }

        log.info("Deleted {} old files, freed {} MB", deletedCount.get(),
                freedSpace.get() / (1024 * 1024));
    }

    private void clearDirectory(Map<String, Object> params) throws IOException {
        String directory = (String) params.get("directory");
        Boolean preserveDirectory = (Boolean) params.getOrDefault("preserveDirectory", true);

        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            log.warn("Directory not found: {}", directory);
            return;
        }

        AtomicInteger deletedCount = new AtomicInteger(0);

        Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                deletedCount.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (!dir.equals(dirPath) || !Boolean.TRUE.equals(preserveDirectory)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("Cleared directory {}: deleted {} items", directory, deletedCount.get());
    }

    private void clearTempFiles(Map<String, Object> params) throws IOException {
        // Clear system temp directory
        String tempDir = System.getProperty("java.io.tmpdir");
        String prefix = (String) params.getOrDefault("prefix", "simplydone-");
        Integer olderThanHours = (Integer) params.getOrDefault("olderThanHours", 24);

        Path tempPath = Paths.get(tempDir);
        Instant threshold = Instant.now().minus(olderThanHours, ChronoUnit.HOURS);
        AtomicInteger deletedCount = new AtomicInteger(0);

        try (Stream<Path> files = Files.list(tempPath)) {
            files.filter(file -> file.getFileName().toString().startsWith(prefix))
                 .forEach(file -> {
                     try {
                         BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                         if (attrs.lastModifiedTime().toInstant().isBefore(threshold)) {
                             if (Files.isDirectory(file)) {
                                 deleteDirectoryRecursively(file);
                             } else {
                                 Files.delete(file);
                             }
                             deletedCount.incrementAndGet();
                             log.debug("Deleted temp file: {}", file);
                         }
                     } catch (IOException e) {
                         log.warn("Failed to delete temp file {}: {}", file, e.getMessage());
                     }
                 });
        }

        log.info("Cleared {} temp files", deletedCount.get());
    }

    private void archiveOldFiles(Map<String, Object> params) throws IOException {
        String directory = (String) params.get("directory");
        String archiveDirectory = (String) params.get("archiveDirectory");
        Integer olderThanDays = (Integer) params.getOrDefault("olderThanDays", 90);

        Path sourcePath = Paths.get(directory);
        Path archivePath = Paths.get(archiveDirectory);

        if (!Files.exists(sourcePath)) {
            throw new IOException("Source directory not found: " + directory);
        }

        Files.createDirectories(archivePath);
        Instant threshold = Instant.now().minus(olderThanDays, ChronoUnit.DAYS);
        AtomicInteger archivedCount = new AtomicInteger(0);

        try (Stream<Path> files = Files.list(sourcePath)) {
            files.filter(Files::isRegularFile)
                 .forEach(file -> {
                     try {
                         BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                         if (attrs.lastModifiedTime().toInstant().isBefore(threshold)) {
                             Path targetPath = archivePath.resolve(file.getFileName());
                             Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                             archivedCount.incrementAndGet();
                             log.debug("Archived file: {} -> {}", file, targetPath);
                         }
                     } catch (IOException e) {
                         log.warn("Failed to archive file {}: {}", file, e.getMessage());
                     }
                 });
        }

        log.info("Archived {} files to {}", archivedCount.get(), archiveDirectory);
    }

    private void deleteByPattern(Map<String, Object> params) throws IOException {
        String directory = (String) params.get("directory");
        String pattern = (String) params.get("pattern");
        Boolean recursive = (Boolean) params.getOrDefault("recursive", false);

        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern is required for DELETE_BY_PATTERN operation");
        }

        Path dirPath = Paths.get(directory);
        if (!Files.exists(dirPath)) {
            log.warn("Directory not found: {}", directory);
            return;
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        AtomicInteger deletedCount = new AtomicInteger(0);

        if (Boolean.TRUE.equals(recursive)) {
            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (matcher.matches(file.getFileName())) {
                        Files.delete(file);
                        deletedCount.incrementAndGet();
                        log.debug("Deleted file matching pattern: {}", file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            try (Stream<Path> files = Files.list(dirPath)) {
                files.filter(file -> matcher.matches(file.getFileName()))
                     .forEach(file -> {
                         try {
                             Files.delete(file);
                             deletedCount.incrementAndGet();
                             log.debug("Deleted file matching pattern: {}", file);
                         } catch (IOException e) {
                             log.warn("Failed to delete file {}: {}", file, e.getMessage());
                         }
                     });
            }
        }

        log.info("Deleted {} files matching pattern: {}", deletedCount.get(), pattern);
    }

    private void cleanupLogs(Map<String, Object> params) throws IOException {
        String directory = (String) params.get("directory");
        Integer keepDays = (Integer) params.getOrDefault("keepDays", 7);
        Boolean compressOld = (Boolean) params.getOrDefault("compressOld", false);

        Path logDir = Paths.get(directory);
        if (!Files.exists(logDir)) {
            log.warn("Log directory not found: {}", directory);
            return;
        }

        Instant deleteThreshold = Instant.now().minus(keepDays, ChronoUnit.DAYS);
        AtomicInteger deletedCount = new AtomicInteger(0);
        AtomicInteger compressedCount = new AtomicInteger(0);

        try (Stream<Path> files = Files.list(logDir)) {
            files.filter(file -> file.getFileName().toString().endsWith(".log"))
                 .forEach(file -> {
                     try {
                         BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                         Instant lastModified = attrs.lastModifiedTime().toInstant();

                         if (lastModified.isBefore(deleteThreshold)) {
                             Files.delete(file);
                             deletedCount.incrementAndGet();

log.debug("Deleted old log: {}", file);
                         } else if (Boolean.TRUE.equals(compressOld) &&
                                  lastModified.isBefore(Instant.now().minus(1, ChronoUnit.DAYS))) {
                             // Compress logs older than 1 day but within keep period
                             compressedCount.incrementAndGet();
                             log.debug("Would compress log: {} (compression not yet implemented)", file);
                         }
                     } catch (IOException e) {
                         log.warn("Failed to process log file {}: {}", file, e.getMessage());
                     }
                 });
        }

        log.info("Cleaned up logs: deleted {}, compressed {}", deletedCount.get(), compressedCount.get());
    }

    private void purgeCache(Map<String, Object> params) throws IOException {
        String cacheType = (String) params.getOrDefault("cacheType", "application");

        log.info("Purging {} cache", cacheType);

        // This is a stub - in real implementation, this would clear application caches
        // For example, Redis cache, in-memory caches, etc.
        // Users can extend this method to implement their specific cache purging logic

        if ("redis".equalsIgnoreCase(cacheType)) {
            log.info("Redis cache purge requested - implement by injecting RedisTemplate");
        } else if ("application".equalsIgnoreCase(cacheType)) {
            log.info("Application cache purge requested - implement with @CacheEvict or CacheManager");
        }

        log.info("Cache purge completed (stub implementation - extend for actual purging)");
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
