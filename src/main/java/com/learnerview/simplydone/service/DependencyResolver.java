package com.learnerview.simplydone.service;

import com.learnerview.simplydone.dto.WorkflowRequest;
import com.learnerview.simplydone.exception.CyclicDependencyException;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * DAG dependency resolver using Kahn's Algorithm (BFS topological sort).
 *
 * Given a list of jobs with dependsOn edges, returns execution levels:
 *   Level 0: jobs with no dependencies (roots)
 *   Level 1: jobs whose dependencies are all in level 0
 *   ...etc
 *
 * Cycle detection: if BFS visits fewer nodes than total → cycle exists.
 *
 * Time:  O(V + E) where V = jobs, E = dependency edges
 * Space: O(V + E)
 */
@Service
public class DependencyResolver {

    /**
     * Validate the DAG and return execution levels (jobs that can run in parallel per level).
     */
    public List<List<String>> resolve(List<WorkflowRequest.WorkflowJob> jobs) {
        // Build adjacency list and in-degree map
        Map<String, List<String>> adjList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Set<String> allIds = new HashSet<>();

        for (WorkflowRequest.WorkflowJob job : jobs) {
            String id = job.getId();
            allIds.add(id);
            adjList.putIfAbsent(id, new ArrayList<>());
            inDegree.putIfAbsent(id, 0);
        }

        // Build edges: dependency → dependent
        for (WorkflowRequest.WorkflowJob job : jobs) {
            if (job.getDependsOn() != null) {
                for (String dep : job.getDependsOn()) {
                    if (!allIds.contains(dep)) {
                        throw new IllegalArgumentException("Unknown dependency: " + dep);
                    }
                    adjList.get(dep).add(job.getId());
                    inDegree.merge(job.getId(), 1, Integer::sum);
                }
            }
        }

        // Kahn's BFS: start from nodes with in-degree 0
        Queue<String> queue = new LinkedList<>();
        for (String id : allIds) {
            if (inDegree.getOrDefault(id, 0) == 0) {
                queue.add(id);
            }
        }

        List<List<String>> levels = new ArrayList<>();
        int visited = 0;

        while (!queue.isEmpty()) {
            List<String> currentLevel = new ArrayList<>(queue);
            queue.clear();

            for (String id : currentLevel) {
                visited++;
                for (String neighbor : adjList.getOrDefault(id, List.of())) {
                    int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                    if (newDegree == 0) {
                        queue.add(neighbor);
                    }
                }
            }

            levels.add(currentLevel);
        }

        // Cycle detection: if we didn't visit all nodes, there's a cycle
        if (visited != allIds.size()) {
            throw new CyclicDependencyException(
                    "Could not resolve all " + allIds.size() + " jobs (visited " + visited + ")");
        }

        return levels;
    }
}
