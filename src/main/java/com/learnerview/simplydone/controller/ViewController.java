package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.dto.HandlerInfoResponse;
import com.learnerview.simplydone.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@Profile("api")
@RequiredArgsConstructor
public class ViewController {

    private final AdminService adminService;

    private List<HandlerInfoResponse> handlerInfo() {
        return List.of(HandlerInfoResponse.builder()
                .jobType("external")
                .description("Generic external HTTP execution")
                .handlerClass("ExternalHttpExecutor")
                .build());
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("stats", adminService.getStats());
        model.addAttribute("recentJobs", adminService.getRecentJobs());
        model.addAttribute("handlers", handlerInfo());
        return "index";
    }

    @GetMapping("/jobs")
    public String jobs(Model model) {
        model.addAttribute("handlers", handlerInfo());
        model.addAttribute("recentJobs", adminService.getRecentJobs());
        return "jobs";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("stats", adminService.getStats());
        model.addAttribute("handlers", handlerInfo());
        return "admin";
    }

    @GetMapping("/dlq")
    public String dlq(Model model) {
        model.addAttribute("dlqJobs", adminService.getDlqJobs());
        return "dlq";
    }
}
