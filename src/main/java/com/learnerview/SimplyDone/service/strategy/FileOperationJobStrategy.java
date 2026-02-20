package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Strategy for executing file operation jobs.
 * Supports: COPY, MOVE, DELETE, ZIP, UNZIP, COMPRESS operations.
 */
@Component
@Slf4j
public class FileOperationJobStrategy implements JobExecutionStrategy {

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB limit for safety

    @Override
    public JobType getSupportedJobType() {
        return JobType.FILE_OPERATION;
    }

    @Override
    public void execute(Job job) throws Exception {
        log.info("Executing file operation job: {} (ID: {})", job.getMessage(), job.getId());

        validateJob(job);

        Map<String, Object> params = job.getParameters();
        String operation = ((String) params.get("operation")).toUpperCase();
        String sourcePath = (String) params.get("source");
        // Support both "target" and "destination" for backwards compatibility
        String targetPath = (String) params.getOrDefault("target", params.get("destination"));

        try {
            switch (operation) {
                case "COPY":
                    copyFile(sourcePath, targetPath);
                    break;
                case "MOVE":
                    moveFile(sourcePath, targetPath);
                    break;
                case "DELETE":
                    deleteFile(sourcePath);
                    break;
                case "ZIP":
                    zipFiles(sourcePath, targetPath);
                    break;
                case "UNZIP":
                    unzipFiles(sourcePath, targetPath);
                    break;
                case "CREATE_DIRECTORY":
                    createDirectory(targetPath != null ? targetPath : sourcePath);
                    break;
                case "LIST":
                    listFiles(sourcePath);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported file operation: " + operation);
            }

            log.info("File operation {} completed successfully for job: {}", operation, job.getId());

        } catch (Exception e) {
            log.error("File operation failed for job {}: {}", job.getId(), e.getMessage());
            throw new Exception("File operation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateJob(Job job) throws IllegalArgumentException {
        if (job.getParameters() == null) {
            throw new IllegalArgumentException("File operation job requires parameters");
        }

        Map<String, Object> params = job.getParameters();
        String operation = (String) params.get("operation");
        String source = (String) params.get("source");

        if (operation == null || operation.trim().isEmpty()) {
            throw new IllegalArgumentException("File operation 'operation' is required");
        }

        // DELETE and CREATE_DIRECTORY operations don't need source for some cases
        if (!operation.equalsIgnoreCase("CREATE_DIRECTORY") &&
            (source == null || source.trim().isEmpty())) {
            throw new IllegalArgumentException("File operation 'source' is required");
        }

        // Security: Prevent path traversal attacks
        if (source != null) {
            validatePath(source);
        }

        // Support both "target" and "destination" parameter names
        String target = (String) params.getOrDefault("target", params.get("destination"));
        if (target != null) {
            validatePath(target);
        }
        
        // For operations that need source file, verify it exists.
        // DELETE is intentionally exempt: deleting a non-existent file is a no-op, not an error.
        if (source != null
                && !operation.equalsIgnoreCase("CREATE_DIRECTORY")
                && !operation.equalsIgnoreCase("DELETE")) {
            Path sourcePath = Paths.get(source);
            if (!Files.exists(sourcePath)) {
                throw new IllegalArgumentException("Source file does not exist: " + source);
            }
        }
    }

    @Override
    public long estimateExecutionTime(Job job) {
        String operation = (String) job.getParameters().get("operation");
        String source = (String) job.getParameters().get("source");

        try {
            if (source != null && Files.exists(Paths.get(source))) {
                long size = Files.size(Paths.get(source));

                // Different operations have different time complexity
                switch (operation.toUpperCase()) {
                    case "ZIP":
                    case "UNZIP":
                        return Math.max(10, size / (1024 * 1024) * 2); // 2 seconds per MB
                    case "COPY":
                    case "MOVE":
                        return Math.max(5, size / (1024 * 1024)); // 1 second per MB
                    case "DELETE":
                        return 5;
                    default:
                        return 10;
                }
            }
        } catch (IOException e) {
            log.warn("Could not estimate file size for {}: {}", source, e.getMessage());
        }

        return 15; // Default estimate
    }

    private void copyFile(String source, String target) throws IOException {
        Path sourcePath = Paths.get(source);
        Path targetPath = Paths.get(target);

        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Source file not found: " + source);
        }

        if (Files.isDirectory(sourcePath)) {
            copyDirectory(sourcePath, targetPath);
        } else {
            // Check file size
            long size = Files.size(sourcePath);
            if (size > MAX_FILE_SIZE) {
                throw new IOException("File too large: " + size + " bytes (max: " + MAX_FILE_SIZE + ")");
            }

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied file from {} to {}", source, target);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)),
                          StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void moveFile(String source, String target) throws IOException {
        Path sourcePath = Paths.get(source);
        Path targetPath = Paths.get(target);

        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Source file not found: " + source);
        }

        Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Moved file from {} to {}", source, target);
    }

    private void deleteFile(String source) throws IOException {
        Path sourcePath = Paths.get(source);

        if (!Files.exists(sourcePath)) {
            log.warn("File to delete not found: {}", source);
            return; // Idempotent - already deleted
        }

        if (Files.isDirectory(sourcePath)) {
            deleteDirectory(sourcePath);
        } else {
            Files.delete(sourcePath);
            log.debug("Deleted file: {}", source);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
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

    private void zipFiles(String source, String target) throws IOException {
        Path sourcePath = Paths.get(source);
        Path targetPath = Paths.get(target);

        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Source path not found: " + source);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            if (Files.isDirectory(sourcePath)) {
                zipDirectory(sourcePath, sourcePath, zos);
            } else {
                zipFile(sourcePath, sourcePath.getFileName().toString(), zos);
            }
        }

        log.debug("Created zip archive: {}", target);
    }

    private void zipDirectory(Path rootPath, Path sourceDir, ZipOutputStream zos) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(path -> !Files.isDirectory(path))
                 .forEach(path -> {
                     try {
                         Path relativePath = rootPath.relativize(path);
                         zipFile(path, relativePath.toString(), zos);
                     } catch (IOException e) {
                         throw new UncheckedIOException(e);
                     }
                 });
        }
    }

    private void zipFile(Path file, String entryName, ZipOutputStream zos) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entryName.replace("\\", "/"));
        zos.putNextEntry(zipEntry);

        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void unzipFiles(String source, String target) throws IOException {
        Path sourcePath = Paths.get(source);
        Path targetPath = Paths.get(target);

        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Zip file not found: " + source);
        }

        Files.createDirectories(targetPath);

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(source))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetPath.resolve(entry.getName());

                // Security: Prevent zip slip vulnerability
                if (!entryPath.normalize().startsWith(targetPath.normalize())) {
                    throw new IOException("Zip entry is outside target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }

        log.debug("Unzipped archive to: {}", target);
    }

    private void createDirectory(String target) throws IOException {
        Path targetPath = Paths.get(target);
        Files.createDirectories(targetPath);
        log.debug("Created directory: {}", target);
    }

    private void listFiles(String source) throws IOException {
        Path sourcePath = Paths.get(source);

        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Source path not found: " + source);
        }

        if (!Files.isDirectory(sourcePath)) {
            log.info("File: {} (size: {} bytes)", source, Files.size(sourcePath));
            return;
        }

        try (Stream<Path> paths = Files.list(sourcePath)) {
            paths.forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        log.info("Directory: {}", path.getFileName());
                    } else {
                        log.info("File: {} (size: {} bytes)",
                                path.getFileName(), Files.size(path));
                    }
                } catch (IOException e) {
                    log.warn("Could not get info for: {}", path);
                }
            });
        }
    }

    private void validatePath(String path) {
        Path p = Paths.get(path);

        // Block path traversal attempts (e.g. ../../etc/passwd)
        if (!p.normalize().toString().equals(p.toString())) {
            throw new SecurityException("Path traversal attempt detected: " + path);
        }

        // Normalise separators so the blocklist works on both Windows and Unix
        String normalized = p.normalize().toString().toLowerCase().replace('\\', '/');
        List<String> blockedPaths = Arrays.asList(
            "/system", "/windows", "/etc", "/root", "/usr/bin", "/usr/sbin",
            "/proc", "/sys", "c:/windows", "c:/program files", "c:/users/administrator"
        );

        for (String blocked : blockedPaths) {
            if (normalized.startsWith(blocked)) {
                throw new SecurityException("Access to system directory is forbidden: " + path);
            }
        }
    }
}
