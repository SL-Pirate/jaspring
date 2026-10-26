package dev.isira.jaspring.template.ast;

import java.util.List;

import dev.isira.jaspring.template.parse.TemplateError;

public record ParsedTemplate(List<TemplateNode> nodes, List<String> ngContentSelectors, String url,
        List<TemplateError> warnings) {}
