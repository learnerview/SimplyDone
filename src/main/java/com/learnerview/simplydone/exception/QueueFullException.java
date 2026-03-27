package com.learnerview.simplydone.exception;

public class QueueFullException extends RuntimeException {
    public QueueFullException(long maxDepth) {
        super("Queue is full (max depth: " + maxDepth + "). Try again later.");
    }
}
