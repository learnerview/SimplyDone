package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileOperationJobStrategy Tests")
class FileOperationJobStrategyTest {

    private FileOperationJobStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FileOperationJobStrategy();
    }

    private Job buildJob(Map<String, Object> params) {
        return Job.builder()
                .id("j-1")
                .jobType(JobType.FILE_OPERATION)
                .userId("user-1")
                .priority(JobPriority.LOW)
                .message("file op")
                .executeAt(Instant.now())
                .parameters(params)
                .build();
    }

    @Test
    @DisplayName("getSupportedJobType returns FILE_OPERATION")
    void getSupportedJobType_returnsFileOperation() {
        assertThat(strategy.getSupportedJobType()).isEqualTo(JobType.FILE_OPERATION);
    }

    @Test
    @DisplayName("validateJob throws when parameters are null")
    void validateJob_nullParams_throws() {
        assertThatThrownBy(() -> strategy.validateJob(buildJob(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws when operation is missing")
    void validateJob_missingOperation_throws() {
        Job job = buildJob(Map.of("source", "/tmp/file.txt"));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws when source is missing for COPY")
    void validateJob_missingSourceForCopy_throws() {
        Job job = buildJob(Map.of("operation", "COPY", "target", "/tmp/dest.txt"));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws SecurityException on path traversal")
    void validateJob_pathTraversal_throwsSecurityException(@TempDir Path tempDir) {
        Job job = buildJob(Map.of(
                "operation", "DELETE",
                "source", tempDir.toString() + "/../../etc/passwd"
        ));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("validateJob throws SecurityException for blocked system directories")
    void validateJob_systemDirectory_throwsSecurityException() {
        Job job = buildJob(Map.of(
                "operation", "DELETE",
                "source", "/etc/passwd"
        ));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("execute CREATE_DIRECTORY creates the directory")
    void execute_createDirectory_createsDir(@TempDir Path tempDir) throws Exception {
        Path newDir = tempDir.resolve("new-directory");
        Job job = buildJob(Map.of(
                "operation", "CREATE_DIRECTORY",
                "target", newDir.toString()
        ));

        strategy.execute(job);

        assertThat(Files.isDirectory(newDir)).isTrue();
    }

    @Test
    @DisplayName("execute COPY copies source file to target")
    void execute_copy_copiesFile(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("source.txt");
        Path target = tempDir.resolve("target.txt");
        Files.writeString(source, "hello world");

        Job job = buildJob(Map.of(
                "operation", "COPY",
                "source", source.toString(),
                "target", target.toString()
        ));

        strategy.execute(job);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("hello world");
    }

    @Test
    @DisplayName("execute DELETE removes the file")
    void execute_delete_removesFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("to-delete.txt");
        Files.writeString(file, "delete me");

        Job job = buildJob(Map.of(
                "operation", "DELETE",
                "source", file.toString()
        ));

        strategy.execute(job);

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    @DisplayName("execute DELETE on non-existent file does not throw")
    void execute_deleteNonExistent_doesNotThrow(@TempDir Path tempDir) {
        Job job = buildJob(Map.of(
                "operation", "DELETE",
                "source", tempDir.resolve("ghost.txt").toString()
        ));

        assertThatNoException().isThrownBy(() -> strategy.execute(job));
    }

    @Test
    @DisplayName("execute MOVE moves file to target location")
    void execute_move_movesFile(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("move-me.txt");
        Path target = tempDir.resolve("moved.txt");
        Files.writeString(source, "content");

        Job job = buildJob(Map.of(
                "operation", "MOVE",
                "source", source.toString(),
                "target", target.toString()
        ));

        strategy.execute(job);

        assertThat(Files.exists(source)).isFalse();
        assertThat(Files.exists(target)).isTrue();
    }

    @Test
    @DisplayName("execute ZIP creates a zip archive")
    void execute_zip_createsArchive(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("to-zip");
        Files.createDirectory(sourceDir);
        Files.writeString(sourceDir.resolve("file.txt"), "zip me");
        Path zipFile = tempDir.resolve("archive.zip");

        Job job = buildJob(Map.of(
                "operation", "ZIP",
                "source", sourceDir.toString(),
                "target", zipFile.toString()
        ));

        strategy.execute(job);

        assertThat(Files.exists(zipFile)).isTrue();
        assertThat(Files.size(zipFile)).isGreaterThan(0);
    }
}
