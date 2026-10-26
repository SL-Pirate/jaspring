package dev.isira.jaspring.di.fixture;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("jaspring-page")
public class PageCounter {

    private int count;

    public int next() {
        return ++count;
    }
}
