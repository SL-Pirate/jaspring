package dev.isira.jaspring.template.parse;

/// One shared compiler runtime for the parser tests; loading the bundle costs about a second.
final class ParserFixture {

    static final AngularCompilerRuntime RUNTIME = AngularCompilerRuntime.load();
    static final GraalAngularTemplateParser PARSER = new GraalAngularTemplateParser(RUNTIME);

    private ParserFixture() {}
}
