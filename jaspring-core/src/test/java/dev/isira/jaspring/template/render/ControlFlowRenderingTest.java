package dev.isira.jaspring.template.render;

import static dev.isira.jaspring.template.render.RenderFixture.render;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.isira.jaspring.template.eval.EvaluationException;

class ControlFlowRenderingTest {

    public record User(long id, String name) {}

    public static final class Model {
        public boolean show = true;
        public boolean other;
        public User user = new User(7, "Ann");
        public User nobody;
        public List<String> items = List.of("a", "b", "c");
        public List<String> none = List.of();
        public List<User> users = List.of(new User(1, "x"), new User(2, "y"));
        public long count;
        public String gender = "female";
        public int selected = 2;
        public Map<String, Object> ctx = Map.of("$implicit", "v", "extra", 1);

        public long byId(long index, User user) {
            return user.id();
        }
    }

    private final Model model = new Model();

    @Test
    void ifBlockRendersTheFirstTruthyBranch() {
        final var template = "@if (show) {A} @else if (other) {B} @else {C}";
        assertEquals("A", render(template, model));
        model.show = false;
        assertEquals("C", render(template, model));
        model.other = true;
        assertEquals("B", render(template, model));
    }

    @Test
    void ifBlockAliasIsAvailableInTheBranch() {
        assertEquals("Ann", render("@if (user; as u) {{{ u.name }}} @else {none}", model));
        assertEquals("none", render("@if (nobody; as u) {{{ u.name }}} @else {none}", model));
    }

    @Test
    void forBlockExposesImplicitVariablesAndAliases() {
        assertEquals("<li>0:a,</li><li>1:b,</li><li>2:c.</li>",
                render("@for (item of items; track item; let i = $index, isLast = $last) {<li>{{ i }}:{{ item }}{{ isLast ? '.' : ',' }}</li>}", model));
        assertEquals("a3true b3false c3false",
                render("@for (item of items; track $index) {{{ item }}{{ $count }}{{ $first }} }", model).trim());
        assertEquals("evenoddeven", render("@for (item of items; track $index) {{{ $even ? 'even' : 'odd' }}}", model));
    }

    @Test
    void forBlockEmptyRendersWhenThereAreNoItems() {
        assertEquals("none", render("@for (item of none; track item) {{{ item }}} @empty {none}", model));
        assertEquals("", render("@for (item of nobody; track item) {{{ item }}}", model));
    }

    @Test
    void switchBlockMatchesStrictlyAndFallsBackToDefault() {
        final var template = "@switch (selected) { @case (1) {one} @case (2) @case (3) {two-three} @default {other} }";
        assertEquals("two-three", render(template, model));
        model.selected = 9;
        assertEquals("other", render(template, model));
        assertEquals("", render("@switch ('2') { @case (2) {num} }", model));
    }

    @Test
    void deferBlockRendersThePlaceholderUnlessHydrated() {
        assertEquals("ph", render("@defer {main} @placeholder {ph}", model));
        assertEquals("", render("@defer {main}", model));
        assertEquals("main", render("@defer (hydrate on idle) {main} @placeholder {ph}", model));
    }

    @Test
    void icuPluralAndSelect() {
        final var plural = "{count, plural, =0 {none} =1 {one item} other {{{ count }} items}}";
        assertEquals("none", render(plural, model));
        model.count = 1;
        assertEquals("one item", render(plural, model));
        model.count = 5;
        assertEquals("5 items", render(plural, model));
        assertEquals("she", render("{gender, select, male {he} female {she} other {they}}", model));
        assertEquals("<b>Ann</b> x", render("{count, plural, other {<b>{{ user.name }}</b> x}}", model));
    }

    @Test
    void ngIfWithThenAndElseTemplates() {
        final var template = "<p *ngIf=\"show; else no\">yes</p><ng-template #no>no</ng-template>";
        assertEquals("<p>yes</p>", render(template, model));
        model.show = false;
        assertEquals("no", render(template, model));
        final var thenElse = "<ng-container *ngIf=\"show; then t; else e\"></ng-container><ng-template #t>T</ng-template><ng-template #e>E</ng-template>";
        assertEquals("E", render(thenElse, model));
        model.show = true;
        assertEquals("T", render(thenElse, model));
        assertEquals("<span>Ann</span>", render("<span *ngIf=\"user as u\">{{ u.name }}</span>", model));
        assertEquals("plain", render("<ng-template [ngIf]=\"show\">plain</ng-template>", model));
    }

    @Test
    void ngForWithContextAndTrackBy() {
        assertEquals("<li>0 x first</li><li>1 y last</li>",
                render("<li *ngFor=\"let u of users; let i = index; let f = first; let l = last; trackBy: byId\">{{ i }} {{ u.name }} {{ f ? 'first' : l ? 'last' : '' }}</li>", model));
        assertEquals("a,b,c,", render("<ng-container *ngFor=\"let s of items\">{{ s }},</ng-container>", model));
    }

    @Test
    void ngSwitchDirectivesMatchCasesAndDefaults() {
        final var template = "<div [ngSwitch]=\"selected\"><p *ngSwitchCase=\"1\">one</p><p *ngSwitchCase=\"2\">two</p><p *ngSwitchDefault>other</p></div>";
        assertEquals("<div><p>two</p></div>", render(template, model));
        model.selected = 5;
        assertEquals("<div><p>other</p></div>", render(template, model));
    }

    @Test
    void ngTemplateOutletRendersInTheDeclaringScopeWithContext() {
        assertEquals("v1Ann", render("<ng-template #tpl let-val let-e=\"extra\">{{ val }}{{ e }}{{ user.name }}</ng-template><ng-container *ngTemplateOutlet=\"tpl; context: ctx\"></ng-container>", model));
        assertEquals("<div>v1</div>", render("<ng-template #tpl let-val let-e=\"extra\">{{ val }}{{ e }}</ng-template><div [ngTemplateOutlet]=\"tpl\" [ngTemplateOutletContext]=\"ctx\"></div>", model));
        assertEquals("", render("<ng-template #tpl>hidden</ng-template><ng-container *ngTemplateOutlet=\"nobody\"></ng-container>", model));
    }

    @Test
    void plainNgTemplateRendersNothing() {
        assertEquals("ab", render("a<ng-template>hidden</ng-template>b", model));
    }

    @Test
    void referencesInsideBlocksDoNotLeakOut() {
        assertEquals("<i>in</i>", render("@if (show) {<i #x>in</i>}{{ x }}", model));
    }

    @Test
    void iteratingANonCollectionIsAnError() {
        model.ctx = null;
        assertThrows(EvaluationException.class, () -> render("@for (c of user.name; track c) {}", model));
    }
}
