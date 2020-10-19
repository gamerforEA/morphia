package dev.morphia.test.aggregation.experimental.expressions;

import dev.morphia.aggregation.experimental.expressions.impls.ConvertType;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static dev.morphia.aggregation.experimental.expressions.Expressions.value;
import static dev.morphia.aggregation.experimental.expressions.TypeExpressions.*;

public class TypeExpressionsTest extends ExpressionsTestBase {

    @Test
    public void testConvert() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{$convert: {input: true, to: \"bool\"}}", convert(value(true), ConvertType.BOOLEAN), true);
    }

    @Test
    public void testToBool() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{$toBool: 'true' }", toBool(value("true")), true);
    }

    @Test
    public void testToDate() {
        checkMinServerVersion(4.0);
        Date date = new Date(LocalDateTime.of(2018, 3, 3, 0, 0)
                                          .toEpochSecond(ZoneOffset.UTC) * 1000);
        assertAndCheckDocShape("{$toDate: '2018-03-03' }", toDate(value("2018-03-03")), date);
    }

    @Test
    public void testToDecimal() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{$toDecimal: true }", toDecimal(value(true)), new Decimal128(1));
    }

    @Test
    public void testToDouble() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{$toDouble: true }", toDouble(value(true)), 1.0);
    }

    @Test
    public void testToInt() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{$toInt: true }", toInt(value(true)), 1);
    }

    @Test
    public void testToLong() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{$toLong: true }", toLong(value(true)), 1L);
    }

    @Test
    public void testToObjectId() {
        checkMinServerVersion(4.0);
        assertAndCheckDocShape("{$toObjectId: '5ab9cbfa31c2ab715d42129e'}", toObjectId(value("5ab9cbfa31c2ab715d42129e")),
            new ObjectId("5ab9cbfa31c2ab715d42129e"));
    }

    @Test
    public void testType() {
        assertAndCheckDocShape("{ $type: \"a\" }", type(value("a")), "string");
    }
}
