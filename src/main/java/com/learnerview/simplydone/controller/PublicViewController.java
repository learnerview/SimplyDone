package com.learnerview.simplydone.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for public pages accessible without API key authentication.
 */
@Controller
public class PublicViewController {

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }
}
