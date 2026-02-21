package com.learnerview.SimplyDone.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
    public String dashboard(Model model) {
        model.addAttribute("currentPage", "dashboard");
        return "index";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("currentPage", "admin");
        return "admin";
    }

    @GetMapping("/jobs")
    public String jobs(Model model) {
        model.addAttribute("currentPage", "jobs");
        return "jobs";
    }

    @GetMapping("/job-status")
    public String jobStatus(Model model) {
        model.addAttribute("currentPage", "jobs");
        return "job-status";
    }

    @GetMapping("/email-send")
    public String emailSend(Model model) {
        model.addAttribute("currentPage", "email");
        return "email-send";
    }

    @GetMapping("/data-process")
    public String dataProcess(Model model) {
        model.addAttribute("currentPage", "data");
        return "data-process";
    }

    @GetMapping("/api-call")
    public String apiCall(Model model) {
        model.addAttribute("currentPage", "api");
        return "api-call";
    }

    @GetMapping("/file-operation")
    public String fileOperation(Model model) {
        model.addAttribute("currentPage", "file");
        return "file-operation";
    }

    @GetMapping("/notification")
    public String notification(Model model) {
        model.addAttribute("currentPage", "notification");
        return "notification";
    }

    @GetMapping("/report-generation")
    public String reportGeneration(Model model) {
        model.addAttribute("currentPage", "report");
        return "report-generation";
    }

    @GetMapping("/cleanup")
    public String cleanup(Model model) {
        model.addAttribute("currentPage", "cleanup");
        return "cleanup";
    }

    @GetMapping("/assets")
    public String assets(Model model) {
        model.addAttribute("currentPage", "assets");
        return "assets";
    }

    @GetMapping("/system-health")
    public String systemHealth(Model model) {
        model.addAttribute("currentPage", "health");
        return "system-health";
    }

    @GetMapping("/dlq")
    public String dlq(Model model) {
        model.addAttribute("currentPage", "dlq");
        return "dlq";
    }

    @GetMapping("/executed-jobs")
    public String executedJobs(Model model) {
        model.addAttribute("currentPage", "executed-jobs");
        return "executed-jobs";
    }

    @GetMapping("/rate-limits")
    public String rateLimits(Model model) {
        model.addAttribute("currentPage", "rate-limits");
        return "rate-limits";
    }
}
