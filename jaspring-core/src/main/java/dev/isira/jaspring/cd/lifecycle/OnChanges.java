package dev.isira.jaspring.cd.lifecycle;

/// Called before `ngOnInit` and whenever one or more bound inputs changed.
public interface OnChanges {

    void ngOnChanges(SimpleChanges changes);
}
