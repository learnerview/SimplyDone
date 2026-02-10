package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.handler.JobHandlerRegistry;
import com.learnerview.simplydone.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ViewController {

    private final AdminService adminService;
    private final JobHandlerRegistry registry;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("stats", adminService.getStats());
        model.addAttribute("recentJobs", adminService.getRecentJobs());
        model.addAttribute("handlers", registry.getHandlerInfo());
        return "index";
    }

    @GetMapping("/jobs")
    public String jobs(Model model) {
        model.addAttribute("handlers", registry.getHandlerInfo());
        model.addAttribute("recentJobs", adminService.getRecentJobs());
        return "jobs";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("stats", adminService.getStats());
        model.addAttribute("handlers", registry.getHandlerInfo());
        return "admin";
    }

    @GetMapping("/dlq")
    public String dlq(Model model) {
        model.addAttribute("dlqJobs", adminService.getDlqJobs());
        return "dlq";
    }

    @GetMapping("/source")
    public String source() {
        return "source";
    }
}
