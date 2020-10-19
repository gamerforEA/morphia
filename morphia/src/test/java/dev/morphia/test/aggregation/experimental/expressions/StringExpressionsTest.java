package dev.morphia.test.aggregation.experimental.expressions;

import dev.morphia.aggregation.experimental.expressions.StringExpressions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

import static dev.morphia.aggregation.experimental.expressions.Expressions.value;
import static dev.morphia.aggregation.experimental.expressions.StringExpressions.*;
import static org.bson.Document.parse;

public class StringExpressionsTest extends ExpressionsTestBase {

    @Test
    public void testConcat() {
        assertAndCheckDocShape("{ $concat: [ 'item', ' - ', 'description' ] }", concat(value("item"), value(" - "), value("description")),
            "item - description");
    }

    @Test
    public void testIndexOfBytes() {
        assertAndCheckDocShape("{ $indexOfBytes: [ 'item', 'foo' ] }", indexOfBytes(value("item"), value("foo")), -1);
        assertAndCheckDocShape("{ $indexOfBytes: [ 'winter wonderland', 'winter' ] }",
            indexOfBytes(value("winter wonderland"), value("winter"))
                .start(4), -1);
        assertAndCheckDocShape("{ $indexOfBytes: [ 'winter wonderland', 'winter' ] }",
            indexOfBytes(value("winter wonderland"), value("winter"))
                .end(5), -1);
    }

    @Test
    public void testIndexOfCP() {
        assertAndCheckDocShape("{ $indexOfCP: [ 'item', 'foo' ] }", indexOfCP(value("item"), value("foo")), -1);
        assertAndCheckDocShape("{ $indexOfCP: [ 'winter wonderland', 'winter' ] }",
            indexOfCP(value("winter wonderland"), value("winter"))
                .start(4), -1);
        assertAndCheckDocShape("{ $indexOfCP: [ 'winter wonderland', 'winter' ] }",
            indexOfCP(value("winter wonderland"), value("winter"))
                .end(5), -1);
    }

    @Test
    public void testLtrim() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{ $ltrim: { input: '    winter wonderland' } }", ltrim(value("    winter wonderland")),
            "winter wonderland");
        assertAndCheckDocShape("{ $ltrim: { input: 'winter wonderland' } }", ltrim(value("winter wonderland"))
                                                                   .chars(value("winter")),
            " wonderland");
    }

    @Test
    public void testRegexFind() {
        checkMinServerVersion(4.2);
        assertAndCheckDocShape("{ $regexFind: { input: 'winter wonderland', regex: /inter/ } }", regexFind(value("winter wonderland"))
                                                                                       .pattern("inter"),
            parse("{match: 'inter', idx:1, captures:[]}"));
        assertAndCheckDocShape("{ $regexFind: { input: 'winter wonderland', regex: /inter/ } }", regexFind(value("winter wonderland"))
                                                                                       .pattern(Pattern.compile("inter")),
            parse("{match: 'inter', idx:1, captures:[]}"));
        assertAndCheckDocShape("{ $regexFind: { input: 'winter wonderland', regex: /splinter/ } }", regexFind(value("winter wonderland"))
                                                                                          .pattern("splinter"), null);
    }

    @Test
    public void testRegexFindAll() {
        checkMinServerVersion(4.2);
        assertAndCheckDocShape("{ $regexFindAll: { input: 'winter wonderland', regex: /inter/ } }",
            regexFindAll(value("winter wonderland")).pattern("inter"),
            Collections.singletonList(parse("{match: 'inter', idx:1, captures:[]}")));
        assertAndCheckDocShape("{ $regexFindAll: { input: 'winter wonderland', regex: /inter/ } }",
            regexFindAll(value("winter wonderland")).pattern(Pattern.compile("inter")),
            Collections.singletonList(parse("{match: 'inter', idx:1, captures:[]}")));
        assertAndCheckDocShape("{ $regexFindAll: { input: 'winter wonderland', regex: /splinter/ } }",
            regexFindAll(value("winter wonderland")).pattern("splinter"), Collections.emptyList());
    }

    @Test
    public void testRegexMatch() {
        checkMinServerVersion(4.2);
        assertAndCheckDocShape("{ $regexMatch: { input: 'winter wonderland', regex: /inter/ } }",
            regexMatch(value("winter wonderland")).pattern("inter"), true);
        assertAndCheckDocShape("{ $regexMatch: { input: 'winter wonderland', regex: /inter/ } }",
            regexMatch(value("winter wonderland")).pattern(Pattern.compile("inter")), true);
        assertAndCheckDocShape("{ $regexMatch: { input: 'winter wonderland', regex: /splinter/ } }",
            regexMatch(value("winter wonderland")).pattern("splinter"), false);
    }

    @Test
    public void testRtrim() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{ $rtrim: { input: 'winter wonderland    ' } }", rtrim(value("winter wonderland    ")),
            "winter wonderland");
        assertAndCheckDocShape("{ $rtrim: { input: 'winter wonderland' } }", rtrim(value("winter wonderland"))
                                                                   .chars(value("land")),
            "winter wonder");
    }

    @Test
    public void testSplit() {
        assertAndCheckDocShape("{ $split: [ 'June-15-2013', '-' ] }", split(value("June-15-2013"), value("-")),
            Arrays.asList("June", "15", "2013"));
    }

    @Test
    public void testStrLenBytes() {
        assertAndCheckDocShape("{ $strLenBytes: 'abcde' }", strLenBytes(value("abcde")), 5);
    }

    @Test
    public void testStrLenCP() {
        assertAndCheckDocShape("{ $strLenCP: 'abcde' }", strLenCP(value("abcde")), 5);
    }

    @Test
    public void testStrcasecmp() {
        assertAndCheckDocShape("{ $strcasecmp: [ 'abcde', 'ABCDEF' ] }", strcasecmp(value("abcde"), value("ABCDEF")), -1);
    }

    @Test
    public void testSubstrBytes() {
        assertAndCheckDocShape("{ $substrBytes: [ 'winter wonderland', 3, 5 ] }", substrBytes(value("winter wonderland"), 3, 5),
            "ter w");
    }

    @Test
    public void testSubstrCP() {
        assertAndCheckDocShape("{ $substrCP: [ 'winter wonderland', 3, 5 ] }", substrCP(value("winter wonderland"), 3, 5),
            "ter w");
    }

    @Test
    public void testToLower() {
        assertAndCheckDocShape("{ $toLower: 'HELLO' }", toLower(value("HELLO")), "hello");
    }

    @Test
    public void testToString() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{ $toString: 12345 }", StringExpressions.toString(value(12345)), "12345");
    }

    @Test
    public void testToUpper() {
        assertAndCheckDocShape("{ $toUpper: 'hello' }", toUpper(value("hello")), "HELLO");
    }

    @Test
    public void testTrim() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{ $trim: { input: '   books   ' } }", trim(value("   books   ")), "books");
        assertAndCheckDocShape("{ $trim: { input: '===books===' } }", trim(value("===books===")).chars(value("===")), "books");
    }
}