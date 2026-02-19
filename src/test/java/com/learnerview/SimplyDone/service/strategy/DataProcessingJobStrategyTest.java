package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DataProcessingJobStrategy Tests")
class DataProcessingJobStrategyTest {

    private DataProcessingJobStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DataProcessingJobStrategy();
    }

    private Job buildJob(Map<String, Object> params) {
        return Job.builder()
                .id("j-1")
                .jobType(JobType.DATA_PROCESS)
                .userId("user-1")
                .priority(JobPriority.LOW)
                .message("process data")
                .executeAt(Instant.now())
                .parameters(params)
                .build();
    }

    @Test
    @DisplayName("getSupportedJobType returns DATA_PROCESS")
    void getSupportedJobType_returnsDataProcess() {
        assertThat(strategy.getSupportedJobType()).isEqualTo(JobType.DATA_PROCESS);
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
        Job job = buildJob(Map.of("inputFile", "/tmp/data.csv"));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws when inputFile is missing")
    void validateJob_missingInputFile_throws() {
        Job job = buildJob(Map.of("operation", "TRANSFORM"));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws SecurityException on path traversal")
    void validateJob_pathTraversal_throwsSecurityException(@TempDir Path tempDir) {
        Job job = buildJob(Map.of(
                "operation", "TRANSFORM",
                "inputFile", tempDir.toString() + "/../../etc/secret.csv",
                "outputFile", tempDir.resolve("out.csv").toString()
        ));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("execute TRANSFORM writes output file with UPPERCASE transformation")
    void execute_transform_uppercase(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("data.csv");
        Path output = tempDir.resolve("output.csv");
        Files.writeString(input, "name,city\nalice,london\nbob,paris\n");

        Job job = buildJob(Map.of(
                "operation", "TRANSFORM",
                "inputFile", input.toString(),
                "outputFile", output.toString(),
                "transformations", List.of("UPPERCASE")
        ));

        strategy.execute(job);

        assertThat(Files.exists(output)).isTrue();
        String result = Files.readString(output);
        assertThat(result).contains("ALICE");
        assertThat(result).contains("LONDON");
    }

    @Test
    @DisplayName("execute VALIDATE passes for file with required columns present")
    void execute_validate_presentColumns(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("data.csv");
        Files.writeString(input, "name,age,city\nalice,30,london\n");

        Job job = buildJob(Map.of(
                "operation", "VALIDATE",
                "inputFile", input.toString(),
                "requiredColumns", List.of("name", "age")
        ));

        assertThatNoException().isThrownBy(() -> strategy.execute(job));
    }

    @Test
    @DisplayName("execute VALIDATE throws when required column is missing from file")
    void execute_validate_missingColumn_throws(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("data.csv");
        Files.writeString(input, "name,city\nalice,london\n");

        Job job = buildJob(Map.of(
                "operation", "VALIDATE",
                "inputFile", input.toString(),
                "requiredColumns", List.of("name", "salary")
        ));

        assertThatThrownBy(() -> strategy.execute(job))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("execute throws for unsupported operation")
    void execute_unknownOperation_throws(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("data.csv");
        Files.writeString(input, "name\nalice\n");

        Job job = buildJob(Map.of(
                "operation", "UNKNOWN_OP",
                "inputFile", input.toString()
        ));

        assertThatThrownBy(() -> strategy.execute(job))
                .isInstanceOf(Exception.class);
    }
}
