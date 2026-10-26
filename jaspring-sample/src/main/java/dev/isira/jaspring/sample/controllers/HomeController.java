package dev.isira.jaspring.sample.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import dev.isira.jaspring.boot.JaspringView;
import dev.isira.jaspring.sample.components.HomeComponent;

@Controller
public class HomeController {

    @GetMapping("/")
    public JaspringView home() {
        return JaspringView.of(HomeComponent.class).input("title", "Jaspring sample");
    }
}
