package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Stream;

/**
 * Strategy for executing data processing jobs.
 * Handles actual CSV processing, data transformation, and analytics using Java NIO.
 */
@Component
@Slf4j
public class DataProcessingJobStrategy implements JobExecutionStrategy {

    @Override
    public JobType getSupportedJobType() {
        return JobType.DATA_PROCESS;
    }

    @Override
    public void execute(Job job) throws Exception {
        log.info("Executing data processing job: {} (ID: {})", job.getMessage(), job.getId());
        
        validateJob(job);
        
        Map<String, Object> params = job.getParameters();
        String operation = (String) params.get("operation");
        String inputFile = (String) params.get("inputFile");
        String outputFile = (String) params.get("outputFile");
        
        try {
            switch (operation.toUpperCase()) {
                case "TRANSFORM":
                case "CSV_TRANSFORM":
                    transformCSV(inputFile, outputFile, params);
                    break;
                case "AGGREGATE":
                case "DATA_AGGREGATION":
                    aggregateData(inputFile, outputFile, params);
                    break;
                case "VALIDATE":
                case "DATA_VALIDATION":
                    validateData(inputFile, params);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported data processing operation: " + operation);
            }
            
            log.info("Data processing completed successfully for job: {}", job.getId());
            
        } catch (Exception e) {
            log.error("Data processing failed for job {}: {}", job.getId(), e.getMessage());
            throw new Exception("Data processing failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void validateJob(Job job) throws IllegalArgumentException {
        if (job.getParameters() == null) {
            throw new IllegalArgumentException("Data processing job requires parameters");
        }
        
        Map<String, Object> params = job.getParameters();
        String operation = (String) params.get("operation");
        String inputFile = (String) params.get("inputFile");
        
        if (operation == null || operation.trim().isEmpty()) {
            throw new IllegalArgumentException("Data processing 'operation' is required");
        }
        if (inputFile == null || inputFile.trim().isEmpty()) {
            throw new IllegalArgumentException("Data processing 'inputFile' is required");
        }
        
        // Block path traversal attempts (e.g. ../../etc/passwd)
        Path path = Paths.get(inputFile);
        if (!path.normalize().toString().equals(path.toString())) {
            throw new SecurityException("Path traversal attempt detected: " + inputFile);
        }
        
        // Check if input file exists
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFile);
        }
        
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Input file is not readable: " + inputFile);
        }
    }
    
    @Override
    public long estimateExecutionTime(Job job) {
        String inputFile = (String) job.getParameters().get("inputFile");
        try {
            long size = Files.size(Paths.get(inputFile));
            // Estimate 1 second per MB of data
            return Math.max(5, size / (1024 * 1024));
        } catch (IOException e) {
            return 30; // Fallback estimate
        }
    }
    
    private void transformCSV(String inputFile, String outputFile, Map<String, Object> params) throws Exception {
        log.debug("Transforming CSV file: {} -> {}", inputFile, outputFile);
        
        Path inputPath = Paths.get(inputFile);
        Path outputPath = Paths.get(outputFile);
        
        List<String> transformations = (List<String>) params.getOrDefault("transformations", Collections.emptyList());
        
        try (BufferedReader reader = Files.newBufferedReader(inputPath);
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            
            String header = reader.readLine();
            if (header != null) {
                writer.write(header);
                writer.newLine();
            }
            
            // Stream-based processing for memory efficiency
            reader.lines().forEach(line -> {
                try {
                    String transformedLine = applyTransformations(line, transformations);
                    writer.write(transformedLine);
                    writer.newLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
    
    private void aggregateData(String inputFile, String outputFile, Map<String, Object> params) throws Exception {
        log.debug("Aggregating data from file: {}", inputFile);
        
        String groupByColumn = (String) params.get("groupBy");
        String aggregateColumn = (String) params.get("aggregate");
        String aggregateFunction = (String) params.getOrDefault("function", "SUM");
        
        Path inputPath = Paths.get(inputFile);
        
        try (Stream<String> lines = Files.lines(inputPath)) {
            // Read header to find indices
            String header = Files.lines(inputPath).findFirst().orElseThrow(() -> new IOException("Empty file"));
            String[] headers = header.split(",");
            
            int groupByIndex = findColumnIndex(headers, groupByColumn);
            int aggregateIndex = findColumnIndex(headers, aggregateColumn);
            
            if (groupByIndex == -1 || aggregateIndex == -1) {
                throw new IllegalArgumentException("Column not found in CSV header");
            }
            
            Map<String, DoubleAdder> aggregationMap = new ConcurrentHashMap<>();
            Map<String, AtomicInteger> countMap = new ConcurrentHashMap<>();
            
            // Skip header and process
            lines.skip(1).parallel().forEach(line -> {
                String[] values = line.split(",");
                if (values.length > Math.max(groupByIndex, aggregateIndex)) {
                    String key = values[groupByIndex];
                    try {
                        double value = Double.parseDouble(values[aggregateIndex]);
                        
                        aggregationMap.computeIfAbsent(key, k -> new DoubleAdder()).add(value);
                        if ("AVG".equalsIgnoreCase(aggregateFunction)) {
                            countMap.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                        }
                    } catch (NumberFormatException ignored) {
                        // Skip invalid numbers
                    }
                }
            });
            
            // Write results
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile))) {
                writer.write(groupByColumn + "," + aggregateFunction + "_" + aggregateColumn);
                writer.newLine();
                
                for (Map.Entry<String, DoubleAdder> entry : aggregationMap.entrySet()) {
                    double finalValue = entry.getValue().sum();
                    if ("AVG".equalsIgnoreCase(aggregateFunction)) {
                        int count = countMap.getOrDefault(entry.getKey(), new AtomicInteger(1)).get();
                        finalValue = finalValue / count;
                    }
                    writer.write(entry.getKey() + "," + finalValue);
                    writer.newLine();
                }
            }
        }
    }
    
    private void validateData(String inputFile, Map<String, Object> params) throws Exception {
        log.debug("Validating data file: {}", inputFile);
        
        List<String> requiredColumns = (List<String>) params.getOrDefault("requiredColumns", Collections.emptyList());
        
        Path inputPath = Paths.get(inputFile);
        if (!Files.exists(inputPath)) {
            throw new FileNotFoundException("Input file not found: " + inputFile);
        }
        
        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            String header = reader.readLine();
            if (header == null) throw new IOException("File is empty");
            
            String[] headers = header.split(",");
            List<String> missingColumns = new ArrayList<>();
            
            for (String col : requiredColumns) {
                if (findColumnIndex(headers, col) == -1) {
                    missingColumns.add(col);
                }
            }
            
            if (!missingColumns.isEmpty()) {
                throw new IllegalArgumentException("Missing required columns: " + String.join(", ", missingColumns));
            }
            
            // Basic efficient validation
            long invalidRows = reader.lines().parallel()
                .filter(line -> line.split(",").length != headers.length)
                .count();
                
            if (invalidRows > 0) {
                 log.warn("Found {} rows with column count mismatch", invalidRows);
            }
        }
    }
    
    private String applyTransformations(String line, List<String> transformations) {
        String result = line;
        for (String trans : transformations) {
            switch (trans.toUpperCase()) {
                case "UPPERCASE": result = result.toUpperCase(); break;
                case "LOWERCASE": result = result.toLowerCase(); break;
                case "TRIM": result = result.trim(); break;
            }
        }
        return result;
    }
    
    private int findColumnIndex(String[] headers, String columnName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(columnName.trim())) {
                return i;
            }
        }
        return -1;
    }
}
