package com.learnerview.simplydone.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("api")
public class ViewController {

    @GetMapping("/")
    public String dashboard() { return "index"; }

    @GetMapping("/jobs")
    public String jobs() { return "jobs"; }

    @GetMapping("/admin")
    public String admin() { return "admin"; }

    @GetMapping("/dlq")
    public String dlq() { return "dlq"; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/error")
    public String error() { return "error"; }
}
