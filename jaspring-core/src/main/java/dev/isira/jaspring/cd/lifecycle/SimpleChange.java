package dev.isira.jaspring.cd.lifecycle;

public record SimpleChange(Object previousValue, Object currentValue, boolean firstChange) {

    public boolean isFirstChange() {
        return firstChange;
    }
}
