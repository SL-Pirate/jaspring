package dev.isira.jaspring.sample.services.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import dev.isira.jaspring.sample.services.UserDirectory;

@Service
public class SlowUserDirectory implements UserDirectory {

    @Override
    public List<String> findAll() throws InterruptedException {
        TimeUnit.SECONDS.sleep(2);
        return List.of("Ada", "Grace", "Linus");
    }
}
