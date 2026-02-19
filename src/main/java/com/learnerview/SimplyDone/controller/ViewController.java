package com.learnerview.SimplyDone.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving the main frontend templates.
 *
 * Can be disabled by setting simplydone.scheduler.api.view-endpoints=false
 */
@Controller
@ConditionalOnProperty(
    prefix = "simplydone.scheduler.api",
    name = "view-endpoints",
    havingValue = "true",
    matchIfMissing = false // Disabled by default - views are optional
)
public class ViewController {

    @GetMapping("/")
    public String dashboard() {
        return "index";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/jobs")
    public String jobs() {
        return "jobs";
    }

    @GetMapping("/job-status")
    public String jobStatus() {
        return "job-status";
    }

    @GetMapping("/email-send")
    public String emailSend() {
        return "email-send";
    }

    @GetMapping("/data-process")
    public String dataProcess() {
        return "data-process";
    }

    @GetMapping("/api-call")
    public String apiCall() {
        return "api-call";
    }

    @GetMapping("/file-operation")
    public String fileOperation() {
        return "file-operation";
    }

    @GetMapping("/notification")
    public String notification() {
        return "notification";
    }

    @GetMapping("/report-generation")
    public String reportGeneration() {
        return "report-generation";
    }

    @GetMapping("/cleanup")
    public String cleanup() {
        return "cleanup";
    }

    @GetMapping("/assets")
    public String assets() {
        return "assets";
    }

    @GetMapping("/system-health")
    public String systemHealth() {
        return "system-health";
    }

    @GetMapping("/dlq")
    public String dlq() {
        return "dlq";
    }
}
