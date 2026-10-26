package dev.isira.jaspring.di.fixture;

import dev.isira.jaspring.component.Pipe;
import dev.isira.jaspring.pipe.PipeTransform;

@Pipe(name = "upper")
public class UpperPipe implements PipeTransform {

    @Override
    public Object transform(Object value, Object... args) {
        return String.valueOf(value).toUpperCase();
    }
}
