package dev.isira.jaspring.boot.testapp.services.impl;

import org.springframework.stereotype.Service;

import dev.isira.jaspring.boot.testapp.services.Greeter;

@Service
public class PoliteGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
