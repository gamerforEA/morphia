package dev.morphia.test.aggregation.experimental.expressions;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static dev.morphia.aggregation.experimental.expressions.Expressions.value;
import static dev.morphia.aggregation.experimental.expressions.SetExpressions.*;
import static java.util.Arrays.asList;

public class SetExpressionsTest extends ExpressionsTestBase {

    @Test
    public void testAllElementsTrue() {
        assertAndCheckDocShape("{ $allElementsTrue: [ [ true, 1, 'someString' ] ] }",
            allElementsTrue(value(Arrays.asList(value(true), value(1), value("someString")))), true);
        assertAndCheckDocShape("{ $allElementsTrue: [ [ [ false ] ] ] }",
            allElementsTrue(value(Collections.singletonList(Collections.singletonList(false)))), true);
        assertAndCheckDocShape("{ $allElementsTrue: [ [ ] ] }",
            allElementsTrue(value(Collections.emptyList())), true);
        assertAndCheckDocShape("{ $allElementsTrue: [ [ null, false, 0 ] ] }",
            allElementsTrue(value(asList(null, false, 0))), false);
    }


    @Test
    public void testAnyElementTrue() {
        assertAndCheckDocShape("{ $anyElementTrue: [ [ true, false ] ] }", anyElementTrue(value(asList(true, false))), true);
        assertAndCheckDocShape("{ $anyElementTrue: [ [ [ false ] ] ] }", anyElementTrue(value(asList(asList(false)))), true);
        assertAndCheckDocShape("{ $anyElementTrue: [ [ null, false, 0 ] ] }",
            anyElementTrue(value(asList(null, false, 0))), false);
        assertAndCheckDocShape("{ $anyElementTrue: [ [ ] ] }",
            anyElementTrue(value(Collections.emptyList())), false);
    }

    @Test
    public void testSetDifference() {
        assertAndCheckDocShape("{ $setDifference: [ [ 'a', 'b', 'a' ], [ 'b', 'a' ] ] }",
            setDifference(value(asList("a", "b", "a")), value(asList("b", "a"))), asList());

        assertAndCheckDocShape("{ $setDifference: [ [ 'a', 'b' ], [ [ 'a', 'b' ] ] ] }",
            setDifference(value(asList("a", "b")), value(asList(asList("a", "b")))), asList("a", "b"));
    }

    @Test
    public void testSetEquals() {
        assertAndCheckDocShape("{ $setEquals: [ [ 'a', 'b', 'a' ], [ 'b', 'a' ] ] }",
            setEquals(value(asList("a", "b", "a")), value(asList("b", "a"))), true);

        assertAndCheckDocShape("{ $setEquals: [ [ 'a', 'b' ], [ [ 'a', 'b' ] ] ] }",
            setEquals(value(asList("a", "b")), value(asList(asList("a", "b")))), false);
    }

    @Test
    public void testSetIntersection() {
        assertAndCheckDocShape("{ $setIntersection: [ [ 'a', 'b', 'a' ], [ 'b', 'a' ] ] }",
            setIntersection(value(asList("a", "b", "a")), value(asList("b", "a"))), asList("a", "b"));

        assertAndCheckDocShape("{ $setIntersection: [ [ 'a', 'b' ], [ [ 'a', 'b' ] ] ] }",
            setIntersection(value(asList("a", "b")), value(asList(asList("a", "b")))), asList());
    }

    @Test
    public void testSetIsSubset() {
        assertAndCheckDocShape("{ $setIsSubset: [ [ 'a', 'b', 'a' ], [ 'b', 'a' ] ] }",
            setIsSubset(value(asList("a", "b", "a")), value(asList("b", "a"))), true);

        assertAndCheckDocShape("{ $setIsSubset: [ [ 'a', 'b' ], [ [ 'a', 'b' ] ] ] }",
            setIsSubset(value(asList("a", "b")), value(asList(asList("a", "b")))), false);
    }

    @Test
    public void testSetUnion() {
        assertAndCheckDocShape("{ $setUnion: [ [ 'a', 'b', 'a' ], [ 'b', 'a' ] ] }",
            setUnion(value(asList("a", "b", "a")), value(asList("b", "a"))), asList("a", "b"));

        assertAndCheckDocShape("{ $setUnion: [ [ 'a', 'b' ], [ [ 'a', 'b' ] ] ] }",
            setUnion(value(asList("a", "b")), value(asList(asList("a", "b")))), asList("a", "b", asList("a", "b")));
    }
}
