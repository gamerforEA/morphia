/*
 * Copyright (c) 2008 - 2013 MongoDB, Inc. <http://mongodb.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.morphia.test.aggregation.experimental;

import com.mongodb.ReadConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.MergeOptions.WhenMatched;
import com.mongodb.client.model.MergeOptions.WhenNotMatched;
import dev.morphia.aggregation.experimental.Aggregation;
import dev.morphia.aggregation.experimental.AggregationOptions;
import dev.morphia.aggregation.experimental.expressions.Expressions;
import dev.morphia.aggregation.experimental.stages.AddFields;
import dev.morphia.aggregation.experimental.stages.AutoBucket;
import dev.morphia.aggregation.experimental.stages.Bucket;
import dev.morphia.aggregation.experimental.stages.CollectionStats;
import dev.morphia.aggregation.experimental.stages.Facet;
import dev.morphia.aggregation.experimental.stages.GraphLookup;
import dev.morphia.aggregation.experimental.stages.Group;
import dev.morphia.aggregation.experimental.stages.Match;
import dev.morphia.aggregation.experimental.stages.Merge;
import dev.morphia.aggregation.experimental.stages.Out;
import dev.morphia.aggregation.experimental.stages.Projection;
import dev.morphia.aggregation.experimental.stages.Redact;
import dev.morphia.aggregation.experimental.stages.ReplaceRoot;
import dev.morphia.aggregation.experimental.stages.SortByCount;
import dev.morphia.aggregation.experimental.stages.Unset;
import dev.morphia.aggregation.experimental.stages.Unwind;
import dev.morphia.annotations.Embedded;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.query.Type;
import dev.morphia.query.internal.MorphiaCursor;
import dev.morphia.test.TestBase;
import dev.morphia.test.aggregation.experimental.model.Author;
import dev.morphia.test.aggregation.experimental.model.Book;
import dev.morphia.test.aggregation.experimental.model.Inventory;
import dev.morphia.test.aggregation.experimental.model.Order;
import dev.morphia.test.aggregation.experimental.model.Sales;
import dev.morphia.test.models.User;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static com.mongodb.client.model.CollationStrength.SECONDARY;
import static dev.morphia.aggregation.experimental.expressions.AccumulatorExpressions.push;
import static dev.morphia.aggregation.experimental.expressions.AccumulatorExpressions.sum;
import static dev.morphia.aggregation.experimental.expressions.ArrayExpressions.array;
import static dev.morphia.aggregation.experimental.expressions.ArrayExpressions.size;
import static dev.morphia.aggregation.experimental.expressions.ComparisonExpressions.gt;
import static dev.morphia.aggregation.experimental.expressions.ConditionalExpressions.condition;
import static dev.morphia.aggregation.experimental.expressions.ConditionalExpressions.ifNull;
import static dev.morphia.aggregation.experimental.expressions.Expressions.field;
import static dev.morphia.aggregation.experimental.expressions.Expressions.value;
import static dev.morphia.aggregation.experimental.expressions.MathExpressions.add;
import static dev.morphia.aggregation.experimental.expressions.ObjectExpressions.mergeObjects;
import static dev.morphia.aggregation.experimental.expressions.SetExpressions.setIntersection;
import static dev.morphia.aggregation.experimental.expressions.SystemVariables.DESCEND;
import static dev.morphia.aggregation.experimental.expressions.SystemVariables.PRUNE;
import static dev.morphia.aggregation.experimental.stages.Group.id;
import static dev.morphia.aggregation.experimental.stages.Lookup.from;
import static dev.morphia.aggregation.experimental.stages.ReplaceWith.with;
import static dev.morphia.aggregation.experimental.stages.Sort.on;
import static dev.morphia.query.experimental.filters.Filters.eq;
import static dev.morphia.query.experimental.filters.Filters.exists;
import static dev.morphia.query.experimental.filters.Filters.gt;
import static dev.morphia.query.experimental.filters.Filters.type;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.bson.Document.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("unused")
public class AggregationTest extends TestBase {

    @Test
    public void testAdd() {
        getMapper().map(Sales.class);
        List<Document> parse = asList(
            parse("{ '_id' : 1, 'item' : 'abc', 'price' : 10, 'fee' : 2, date: ISODate('2014-03-01T08:00:00Z') }"),
            parse("{ '_id' : 2, 'item' : 'jkl', 'price' : 20, 'fee' : 1, date: ISODate('2014-03-01T09:00:00Z') }"),
            parse("{ '_id' : 3, 'item' : 'xyz', 'price' : 5,  'fee' : 0, date: ISODate('2014-03-15T09:00:00Z') }"));
        getDatabase().getCollection("sales", Document.class)
                     .insertMany(parse);
        Aggregation<Sales> pipeline = getDs()
                                          .aggregate(Sales.class)
                                          .project(Projection.of()
                                                             .include("item")
                                                             .include("total",
                                                                 add(field("price"), field("fee"))));

        List<Document> list = pipeline.execute(Document.class).toList();
        List<Document> expected = asList(
            parse("{ '_id' : 1, 'item' : 'abc', 'total' : 12 }"),
            parse("{ '_id' : 2, 'item' : 'jkl', 'total' : 21 }"),
            parse("{ '_id' : 3, 'item' : 'xyz', 'total' : 5 } }"));
        for (int i = 1; i < 4; i++) {
            compare(i, expected, list);
        }
    }

    @Test
    public void testAddFields() {
        List<Document> list = java.util.Arrays.asList(parse("{ _id: 1, student: 'Maya', homework: [ 10, 5, 10 ],quiz: [ 10, 8 ],extraCredit: 0 }"),
            parse("{ _id: 2, student: 'Ryan', homework: [ 5, 6, 5 ],quiz: [ 8, 8 ],extraCredit: 8 }"));

        getDatabase().getCollection("scores").insertMany(list);

        List<Document> result = getDs().aggregate(Score.class)
                                       .addFields(AddFields.of()
                                                           .field("totalHomework", sum(field("homework")))
                                                           .field("totalQuiz", sum(field("quiz"))))
                                       .addFields(AddFields.of()
                                                           .field("totalScore", add(field("totalHomework"),
                                                               field("totalQuiz"), field("extraCredit"))))
                                       .execute(Document.class)
                                       .toList();

        list = java.util.Arrays.asList(parse("{ '_id' : 1, 'student' : 'Maya', 'homework' : [ 10, 5, 10 ],'quiz' : [ 10, 8 ],'extraCredit' : 0, 'totalHomework' : 25,"
                  + " 'totalQuiz' : 18, 'totalScore' : 43 }"),
            parse("{ '_id' : 2, 'student' : 'Ryan', 'homework' : [ 5, 6, 5 ],'quiz' : [ 8, 8 ],'extraCredit' : 8, 'totalHomework' : 16, "
                  + "'totalQuiz' : 16, 'totalScore' : 40 }"));

        assertEquals(list, result);
    }

    @Test
    public void testAutoBucket() {
        List<Document> list = java.util.Arrays.asList(parse("{'_id': 1, 'title': 'The Pillars of Society', 'artist': 'Grosz', 'year': 1926, 'price': NumberDecimal('199.99') }"),
            parse("{'_id': 2, 'title': 'Melancholy III', 'artist': 'Munch', 'year': 1902, 'price': NumberDecimal('280.00') }"),
            parse("{'_id': 3, 'title': 'Dancer', 'artist': 'Miro', 'year': 1925, 'price': NumberDecimal('76.04') }"),
            parse("{'_id': 4, 'title': 'The Great Wave off Kanagawa', 'artist': 'Hokusai', 'price': NumberDecimal('167.30') }"),
            parse("{'_id': 5, 'title': 'The Persistence of Memory', 'artist': 'Dali', 'year': 1931, 'price': NumberDecimal('483.00') }"),
            parse("{'_id': 6, 'title': 'Composition VII', 'artist': 'Kandinsky', 'year': 1913, 'price': NumberDecimal('385.00') }"),
            parse("{'_id': 7, 'title': 'The Scream', 'artist': 'Munch', 'year': 1893, 'price' : NumberDecimal('159.00')}"),
            parse("{'_id': 8, 'title': 'Blue Flower', 'artist': 'O\\'Keefe', 'year': 1918, 'price': NumberDecimal('118.42') }"));

        getDatabase().getCollection("artwork").insertMany(list);

        List<Document> results = getDs().aggregate(Artwork.class)
                                        .autoBucket(AutoBucket.of()
                                                              .groupBy(field("price"))
                                                              .buckets(4))
                                        .execute(Document.class)
                                        .toList();

        List<Document> documents = java.util.Arrays.asList(parse("{'_id': { 'min': NumberDecimal('76.04'), 'max': NumberDecimal('159.00') },'count': 2}"),
            parse("{'_id': { 'min': NumberDecimal('159.00'), 'max': NumberDecimal('199.99') },'count': 2 }"),
            parse("{'_id': { 'min': NumberDecimal('199.99'), 'max': NumberDecimal('385.00') },'count': 2 }"),
            parse("{'_id': { 'min': NumberDecimal('385.00'), 'max': NumberDecimal('483.00') },'count': 2 }"));

        assertEquals(documents, results);
    }

    @Test
    public void testBucket() {
        List<Document> list = java.util.Arrays.asList(parse("{'_id': 1, 'title': 'The Pillars of Society', 'artist': 'Grosz', 'year': 1926, 'price': NumberDecimal('199.99') }"),
            parse("{'_id': 2, 'title': 'Melancholy III', 'artist': 'Munch', 'year': 1902, 'price': NumberDecimal('280.00') }"),
            parse("{'_id': 3, 'title': 'Dancer', 'artist': 'Miro', 'year': 1925, 'price': NumberDecimal('76.04') }"),
            parse("{'_id': 4, 'title': 'The Great Wave off Kanagawa', 'artist': 'Hokusai', 'price': NumberDecimal('167.30') }"),
            parse("{'_id': 5, 'title': 'The Persistence of Memory', 'artist': 'Dali', 'year': 1931, 'price': NumberDecimal('483.00') }"),
            parse("{'_id': 6, 'title': 'Composition VII', 'artist': 'Kandinsky', 'year': 1913, 'price': NumberDecimal('385.00') }"),
            parse("{'_id': 7, 'title': 'The Scream', 'artist': 'Munch', 'year': 1893}"),
            parse("{'_id': 8, 'title': 'Blue Flower', 'artist': 'O\\'Keefe', 'year': 1918, 'price': NumberDecimal('118.42') }"));

        getDatabase().getCollection("artwork").insertMany(list);

        List<Document> results = getDs().aggregate(Artwork.class)
                                        .bucket(Bucket.of()
                                                      .groupBy(field("price"))
                                                      .boundaries(value(0), value(200), value(400))
                                                      .defaultValue("Other")
                                                      .outputField("count", sum(value(1)))
                                                      .outputField("titles", push().single(field("title"))))
                                        .execute(Document.class)
                                        .toList();

        List<Document> documents = java.util.Arrays.asList(parse("{'_id': 0, 'count': 4, 'titles': ['The Pillars of Society', 'Dancer', 'The Great Wave off Kanagawa', 'Blue Flower']}"),
            parse("{'_id': 200, 'count': 2, 'titles': ['Melancholy III', 'Composition VII']}"),
            parse("{'_id': 'Other', 'count': 2, 'titles': ['The Persistence of Memory', 'The Scream']}"));
        assertEquals(documents, results);
    }

    @Test
    public void testCollation() {
        getDs().save(asList(new User("john doe", LocalDate.now()), new User("John Doe", LocalDate.now())));

        Aggregation<User> pipeline = getDs()
                                         .aggregate(User.class)
                                         .match(eq("name", "john doe"));
        assertEquals(1, count(pipeline.execute(User.class)));

        assertEquals(2, count(pipeline.execute(User.class,
            new dev.morphia.aggregation.experimental.AggregationOptions()
                .collation(Collation.builder()
                                    .locale("en")
                                    .collationStrength(SECONDARY)
                                    .build()))));
    }

    @Test
    public void testCollectionStats() {
        getDs().save(new Author());
        Document stats = getDs().aggregate(Author.class)
                                .collStats(CollectionStats.with()
                                                          .histogram(true)
                                                          .scale(42)
                                                          .count(true))
                                .execute(Document.class)
                                .tryNext();
        assertNotNull(stats);
    }

    @Test
    public void testCount() {
        List<Document> list = java.util.Arrays.asList(parse("{ '_id' : 1, 'subject' : 'History', 'score' : 88 }"),
            parse("{ '_id' : 2, 'subject' : 'History', 'score' : 92 }"),
            parse("{ '_id' : 3, 'subject' : 'History', 'score' : 97 }"),
            parse("{ '_id' : 4, 'subject' : 'History', 'score' : 71 }"),
            parse("{ '_id' : 5, 'subject' : 'History', 'score' : 79 }"),
            parse("{ '_id' : 6, 'subject' : 'History', 'score' : 83 }"));

        getDatabase().getCollection("scores").insertMany(list);

        Document scores = getDs().aggregate(Score.class)
                                 .match(gt("score", 80))
                                 .count("passing_scores")
                                 .execute(Document.class)
                                 .next();
        assertEquals(parse("{ \"passing_scores\" : 4 }"), scores);
    }

    @Test
    public void testFacet() {
        List<Document> list = java.util.Arrays.asList(parse("{'_id': 1, 'title': 'The Pillars of Society', 'artist': 'Grosz', 'year': 1926, 'price': NumberDecimal('199.99'),"
                  + " 'tags': [ 'painting', 'satire', 'Expressionism', 'caricature' ] }"),
            parse("{'_id': 2, 'title': 'Melancholy III', 'artist': 'Munch', 'year': 1902, 'price': NumberDecimal('280.00'),"
                  + " 'tags': [ 'woodcut', 'Expressionism' ] }"),
            parse("{'_id': 3, 'title': 'Dancer', 'artist': 'Miro', 'year': 1925, 'price': NumberDecimal('76.04'),"
                  + " 'tags': [ 'oil', 'Surrealism', 'painting' ] }"),
            parse("{'_id': 4, 'title': 'The Great Wave off Kanagawa', 'artist': 'Hokusai', 'price': NumberDecimal('167.30'),"
                  + " 'tags': [ 'woodblock', 'ukiyo-e' ] }"),
            parse("{'_id': 5, 'title': 'The Persistence of Memory', 'artist': 'Dali', 'year': 1931, 'price': NumberDecimal('483.00'),"
                  + " 'tags': [ 'Surrealism', 'painting', 'oil' ] }"),
            parse("{'_id': 6, 'title': 'Composition VII', 'artist': 'Kandinsky', 'year': 1913, 'price': NumberDecimal('385.00'), "
                  + "'tags': [ 'oil', 'painting', 'abstract' ] }"),
            parse("{'_id': 7, 'title': 'The Scream', 'artist': 'Munch', 'year': 1893, 'tags': [ 'Expressionism', 'painting', 'oil' ] }"),
            parse("{'_id': 8, 'title': 'Blue Flower', 'artist': 'O\\'Keefe', 'year': 1918, 'price': NumberDecimal('118.42'),"
                  + " 'tags': [ 'abstract', 'painting' ] }"));

        getDatabase().getCollection("artwork").insertMany(list);

        Document result = getDs().aggregate(Artwork.class)
                                 .facet(Facet.of()
                                             .field("categorizedByTags",
                                                 Unwind.on("tags"),
                                                 SortByCount.on(field("tags")))
                                             .field("categorizedByPrice",
                                                 Match.on(exists("price")),
                                                 Bucket.of()
                                                       .groupBy(field("price"))
                                                       .boundaries(value(0), value(150), value(200), value(300), value(400))
                                                       .defaultValue("Other")
                                                       .outputField("count", sum(value(1)))
                                                       .outputField("titles", push().single(field("title"))))
                                             .field("categorizedByYears(Auto)", AutoBucket.of()
                                                                                          .groupBy(field("year"))
                                                                                          .buckets(4)))
                                 .execute(Document.class)
                                 .next();

        Document document = parse("{"
                                  + "    'categorizedByTags' : ["
                                  + "    { '_id' : 'painting', 'count' : 6 },"
                                  + "    { '_id' : 'oil', 'count' : 4 },"
                                  + "    { '_id' : 'Expressionism', 'count' : 3 },"
                                  + "    { '_id' : 'Surrealism', 'count' : 2 },"
                                  + "    { '_id' : 'abstract', 'count' : 2 },"
                                  + "    { '_id' : 'woodblock', 'count' : 1 },"
                                  + "    { '_id' : 'woodcut', 'count' : 1 },"
                                  + "    { '_id' : 'ukiyo-e', 'count' : 1 },"
                                  + "    { '_id' : 'satire', 'count' : 1 },"
                                  + "    { '_id' : 'caricature', 'count' : 1 }"
                                  + "    ],"
                                  + "    'categorizedByYears(Auto)' : ["
                                  + "    { '_id' : { 'min' : null, 'max' : 1902 }, 'count' : 2 },"
                                  + "    { '_id' : { 'min' : 1902, 'max' : 1918 }, 'count' : 2 },"
                                  + "    { '_id' : { 'min' : 1918, 'max' : 1926 }, 'count' : 2 },"
                                  + "    { '_id' : { 'min' : 1926, 'max' : 1931 }, 'count' : 2 }"
                                  + "    ],"
                                  + "    'categorizedByPrice' : ["
                                  + "    { '_id' : 0, 'count' : 2, 'titles' : ['Dancer', 'Blue Flower']},"
                                  + "    { '_id' : 150, 'count' : 2, 'titles' : ['The Pillars of Society', 'The Great Wave off Kanagawa']},"
                                  + "    { '_id' : 200, 'count' : 1, 'titles' : ['Melancholy III']},"
                                  + "    { '_id' : 300, 'count' : 1, 'titles' : ['Composition VII']},"
                                  + "    { '_id' : 'Other', 'count' : 1, 'titles' : ['The Persistence of Memory']}"
                                  + "    ],"
                                  + "}");

        assertDocumentEquals(document, result);
    }

    @Test
    public void testGraphLookup() {
        List<Document> list = java.util.Arrays.asList(parse("{ '_id' : 1, 'name' : 'Dev' }"),
            parse("{ '_id' : 2, 'name' : 'Eliot', 'reportsTo' : 'Dev' }"),
            parse("{ '_id' : 3, 'name' : 'Ron', 'reportsTo' : 'Eliot' }"),
            parse("{ '_id' : 4, 'name' : 'Andrew', 'reportsTo' : 'Eliot' }"),
            parse("{ '_id' : 5, 'name' : 'Asya', 'reportsTo' : 'Ron' }"),
            parse("{ '_id' : 6, 'name' : 'Dan', 'reportsTo' : 'Andrew' }"));

        getDatabase().getCollection("employees").insertMany(list);

        List<Document> actual = getDs().aggregate(Employee.class)
                                       .graphLookup(GraphLookup.from("employees")
                                                               .startWith(field("reportsTo"))
                                                               .connectFromField("reportsTo")
                                                               .connectToField("name")
                                                               .as("reportingHierarchy"))
                                       .execute(Document.class)
                                       .toList();

        List<Document> expected = java.util.Arrays.asList(parse("{'_id': 1, 'name': 'Dev', 'reportingHierarchy': []}"),
            parse("{'_id': 2, 'name': 'Eliot', 'reportsTo': 'Dev', 'reportingHierarchy': [{'_id': 1, 'name': 'Dev'}]}"),
            parse("{'_id': 3, 'name': 'Ron', 'reportsTo': 'Eliot', 'reportingHierarchy': [{'_id': 1, 'name': 'Dev'},{'_id': 2, 'name': "
                  + "'Eliot', 'reportsTo': 'Dev'}]}"),
            parse("{'_id': 4, 'name': 'Andrew', 'reportsTo': 'Eliot', 'reportingHierarchy': [{'_id': 1, 'name': 'Dev'},{'_id': 2, 'name': "
                  + "'Eliot', 'reportsTo': 'Dev'}]}"),
            parse("{'_id': 5, 'name': 'Asya', 'reportsTo': 'Ron', 'reportingHierarchy': [{'_id': 1, 'name': 'Dev'},{'_id': 2, 'name': "
                  + "'Eliot', 'reportsTo': 'Dev'},{'_id': 3, 'name': 'Ron', 'reportsTo': 'Eliot'}]}"),
            parse("{'_id': 6, 'name': 'Dan', 'reportsTo': 'Andrew', 'reportingHierarchy': [{'_id': 1, 'name': 'Dev'},{'_id': 2, 'name': "
                  + "'Eliot', 'reportsTo': 'Dev'},{'_id': 4, 'name': 'Andrew', 'reportsTo': 'Eliot'}]}"));

        assertDocumentEquals(expected, actual);
    }

    @Test
    public void testIndexStats() {
        getDs().getMapper().map(Author.class);
        getDs().ensureIndexes();
        Document stats = getDs().aggregate(Author.class)
                                .indexStats()
                                .match(eq("name", "books_1"))
                                .execute(Document.class)
                                .next();

        assertNotNull(stats);
    }

    @Test
    public void testLimit() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        List<Book> aggregate = getDs().aggregate(Book.class)
                                      .limit(2)
                                      .execute(Book.class)
                                      .toList();
        assertEquals(2, aggregate.size());
    }

    @Test
    public void testLookup() {
        // Test data pulled from https://docs.mongodb.com/v3.2/reference/operator/aggregation/lookup/
        getDs().save(asList(new Order(1, "abc", 12, 2),
            new Order(2, "jkl", 20, 1),
            new Order(3)));
        List<Inventory> inventories = asList(new Inventory(1, "abc", "product 1", 120),
            new Inventory(2, "def", "product 2", 80),
            new Inventory(3, "ijk", "product 3", 60),
            new Inventory(4, "jkl", "product 4", 70),
            new Inventory(5, null, "Incomplete"),
            new Inventory(6));
        getDs().save(inventories);

        List<Order> lookups = getDs().aggregate(Order.class)
                                     .lookup(from(Inventory.class)
                                                 .localField("item")
                                                 .foreignField("sku")
                                                 .as("inventoryDocs"))
                                     .sort(on().ascending("_id"))
                                     .execute(Order.class)
                                     .toList();
        assertEquals(inventories.get(0), lookups.get(0).getInventoryDocs().get(0));
        assertEquals(inventories.get(3), lookups.get(1).getInventoryDocs().get(0));
        assertEquals(inventories.get(4), lookups.get(2).getInventoryDocs().get(0));
        assertEquals(inventories.get(5), lookups.get(2).getInventoryDocs().get(1));
    }

/*
    @Test
    public void testLookupWithPipeline() {
        getDatabase().getCollection("orders").insertMany(List.of(
            parse("{ '_id' : 1, 'item' : 'almonds', 'price' : 12, 'ordered' : 2 }"),
            parse("{ '_id' : 2, 'item' : 'pecans', 'price' : 20, 'ordered' : 1 }"),
            parse("{ '_id' : 3, 'item' : 'cookies', 'price' : 10, 'ordered' : 60 }")));

        getDatabase().getCollection("warehouses").insertMany(List.of(
            parse("{ '_id' : 1, 'stock_item' : 'almonds', warehouse: 'A', 'instock' : 120 },"),
            parse("{ '_id' : 2, 'stock_item' : 'pecans', warehouse: 'A', 'instock' : 80 }"),
            parse("{ '_id' : 3, 'stock_item' : 'almonds', warehouse: 'B', 'instock' : 60 }"),
            parse("{ '_id' : 4, 'stock_item' : 'cookies', warehouse: 'B', 'instock' : 40 }"),
            parse("{ '_id' : 5, 'stock_item' : 'cookies', warehouse: 'A', 'instock' : 80 }")));

        getDs().aggregate("orders")
               .lookup(Lookup.from("warehouses")
                             .let("order_item", field("item"))
                             .let("order_qty", field("ordered"))
                             .pipeline(
                          Match.on(getDs().find()
                                       .expr())
                               ))
    }
*/

    @Test
    public void testMerge() {
        checkMinServerVersion(4.2);
        MongoCollection<Document> salaries = getDatabase().getCollection("salaries");

        salaries.insertMany(java.util.Arrays.asList(parse("{ '_id' : 1, employee: 'Ant', dept: 'A', salary: 100000, fiscal_year: 2017 }"),
            parse("{ '_id' : 2, employee: 'Bee', dept: 'A', salary: 120000, fiscal_year: 2017 }"),
            parse("{ '_id' : 3, employee: 'Cat', dept: 'Z', salary: 115000, fiscal_year: 2017 }"),
            parse("{ '_id' : 4, employee: 'Ant', dept: 'A', salary: 115000, fiscal_year: 2018 }"),
            parse("{ '_id' : 5, employee: 'Bee', dept: 'Z', salary: 145000, fiscal_year: 2018 }"),
            parse("{ '_id' : 6, employee: 'Cat', dept: 'Z', salary: 135000, fiscal_year: 2018 }"),
            parse("{ '_id' : 7, employee: 'Gecko', dept: 'A', salary: 100000, fiscal_year: 2018 }"),
            parse("{ '_id' : 8, employee: 'Ant', dept: 'A', salary: 125000, fiscal_year: 2019 }"),
            parse("{ '_id' : 9, employee: 'Bee', dept: 'Z', salary: 160000, fiscal_year: 2019 }"),
            parse("{ '_id' : 10, employee: 'Cat', dept: 'Z', salary: 150000, fiscal_year: 2019 }")));

        getDs().aggregate(Salary.class)
               .group(Group.of(id()
                                   .field("fiscal_year")
                                   .field("dept"))
                           .field("salaries", sum(field("salary"))))
               .merge(Merge.into("budgets")
                           .on("_id")
                           .whenMatched(WhenMatched.REPLACE)
                           .whenNotMatched(WhenNotMatched.INSERT));
        List<Document> actual = getDs().find("budgets", Document.class).iterator().toList();

        List<Document> expected = java.util.Arrays.asList(parse("{ '_id' : { 'fiscal_year' : 2017, 'dept' : 'A' }, 'salaries' : 220000 }"),
            parse("{ '_id' : { 'fiscal_year' : 2017, 'dept' : 'Z' }, 'salaries' : 115000 }"),
            parse("{ '_id' : { 'fiscal_year' : 2018, 'dept' : 'A' }, 'salaries' : 215000 }"),
            parse("{ '_id' : { 'fiscal_year' : 2018, 'dept' : 'Z' }, 'salaries' : 280000 }"),
            parse("{ '_id' : { 'fiscal_year' : 2019, 'dept' : 'A' }, 'salaries' : 125000 }"),
            parse("{ '_id' : { 'fiscal_year' : 2019, 'dept' : 'Z' }, 'salaries' : 310000 }"));

        assertDocumentEquals(expected, actual);
    }

    @Test
    public void testNullGroupId() {
        getDs().save(asList(new User("John", LocalDate.now()),
            new User("Paul", LocalDate.now()),
            new User("George", LocalDate.now()),
            new User("Ringo", LocalDate.now())));
        Aggregation<User> pipeline = getDs()
                                         .aggregate(User.class)
                                         .group(Group.of()
                                                     .field("count", sum(value(1))));

        assertEquals(Integer.valueOf(4), pipeline.execute(Document.class).tryNext().getInteger("count"));
    }

    @Test
    public void testOut() {
        getDs().save(asList(new Book("The Banquet", "Dante", 2),
            new Book("Divine Comedy", "Dante", 1),
            new Book("Eclogues", "Dante", 2),
            new Book("The Odyssey", "Homer", 10),
            new Book("Iliad", "Homer", 10)));

        AggregationOptions options = new AggregationOptions();
        getDs().aggregate(Book.class)
               .group(Group.of(id("author"))
                           .field("books", push()
                                               .single(field("title"))))
               .out(Out.to(Author.class));
        assertEquals(2, getMapper().getCollection(Author.class).countDocuments());

        getDs().aggregate(Book.class)
               .group(Group.of(id("author"))
                           .field("books", push()
                                               .single(field("title"))))
               .out(Out.to("different"));
        assertEquals(2, getDatabase().getCollection("different").countDocuments());
    }

    @Test
    public void testPlanCacheStats() {
        checkMinServerVersion(4.2);
        List<Document> list = java.util.Arrays.asList(parse("{ '_id' : 1, 'item' : 'abc', 'price' : NumberDecimal('12'), 'quantity' : 2, 'type': 'apparel' }"),
            parse("{ '_id' : 2, 'item' : 'jkl', 'price' : NumberDecimal('20'), 'quantity' : 1, 'type': 'electronics' }"),
            parse("{ '_id' : 3, 'item' : 'abc', 'price' : NumberDecimal('10'), 'quantity' : 5, 'type': 'apparel' }"),
            parse("{ '_id' : 4, 'item' : 'abc', 'price' : NumberDecimal('8'), 'quantity' : 10, 'type': 'apparel' }"),
            parse("{ '_id' : 5, 'item' : 'jkl', 'price' : NumberDecimal('15'), 'quantity' : 15, 'type': 'electronics' }"));

        MongoCollection<Document> orders = getDatabase().getCollection("orders");
        orders.insertMany(list);

        assertNotNull(orders.createIndex(new Document("item", 1)));
        assertNotNull(orders.createIndex(new Document("item", 1)
                                             .append("quantity", 1)));
        assertNotNull(orders.createIndex(new Document("item", 1)
                                             .append("price", 1),
            new IndexOptions()
                .partialFilterExpression(new Document("price", new Document("$gte", 10)))));
        assertNotNull(orders.createIndex(new Document("quantity", 1)));
        assertNotNull(orders.createIndex(new Document("quantity", 1)
                                             .append("type", 1)));

        orders.find(parse(" { item: 'abc', price: { $gte: NumberDecimal('10') } }"));
        orders.find(parse(" { item: 'abc', price: { $gte: NumberDecimal('5') } }"));
        orders.find(parse(" { quantity: { $gte: 20 } } "));
        orders.find(parse(" { quantity: { $gte: 5 }, type: 'apparel' } "));

        List<Document> stats = getDs().aggregate(Order.class)
                                      .planCacheStats()
                                      .execute(Document.class, new AggregationOptions()
                                                                   .readConcern(ReadConcern.LOCAL))
                                      .toList();

        assertNotNull(stats);
    }

    @Test
    public void testProjection() {
        Document doc = parse("{'_id' : 1, title: 'abc123', isbn: '0001122223334', author: { last: 'zzz', first: 'aaa' }, copies: 5,\n"
                        + "  lastModified: '2016-07-28'}");

        getDatabase().getCollection("books").insertOne(doc);
        Aggregation<Book> pipeline = getDs().aggregate(Book.class)
                                            .project(Projection.of()
                                                               .include("title")
                                                               .include("author"));
        MorphiaCursor<ProjectedBook> aggregate = pipeline.execute(ProjectedBook.class);
        assertEquals(new ProjectedBook(1, "abc123", "zzz", "aaa"), aggregate.next());

        pipeline = getDs().aggregate(Book.class)
                          .project(Projection.of()
                                             .suppressId()
                                             .include("title")
                                             .include("author"));
        aggregate = pipeline.execute(ProjectedBook.class);

        assertEquals(new ProjectedBook(null, "abc123", "zzz", "aaa"), aggregate.next());

        pipeline = getDs().aggregate(Book.class)
                          .project(Projection.of()
                                             .exclude("lastModified"));
        final MorphiaCursor<Document> docAgg = pipeline.execute(Document.class);

        doc = parse("{'_id' : 1, title: 'abc123', isbn: '0001122223334', author: { last: 'zzz', first: 'aaa' }, copies: 5}");
        assertEquals(doc, docAgg.next());
    }

    @Test
    public void testRedact() {
        Document document = parse(
            "{ _id: 1, title: '123 Department Report', tags: [ 'G', 'STLW' ],year: 2014, subsections: [{ subtitle: 'Section 1: Overview',"
            + " tags: [ 'SI', 'G' ],content:  'Section 1: This is the content of section 1.' },{ subtitle: 'Section 2: Analysis', tags: "
            + "[ 'STLW' ], content: 'Section 2: This is the content of section 2.' },{ subtitle: 'Section 3: Budgeting', tags: [ 'TK' ],"
            + "content: { text: 'Section 3: This is the content of section3.', tags: [ 'HCS' ]} }]}");

        getDatabase().getCollection("forecasts").insertOne(document);

        Document actual = getDs().aggregate("forecasts")
                                 .match(eq("year", 2014))
                                 .redact(Redact.on(
                                     condition(
                                         gt(size(setIntersection(field("tags"), array(value("STLW"), value("G")))),
                                             value(0)),
                                         DESCEND, PRUNE)))
                                 .execute(Document.class)
                                 .next();
        Document expected = parse("{ '_id' : 1, 'title' : '123 Department Report', 'tags' : [ 'G', 'STLW' ],'year' : 2014, 'subsections' :"
                                  + " [{ 'subtitle' : 'Section 1: Overview', 'tags' : [ 'SI', 'G' ],'content' : 'Section 1: This is the "
                                  + "content of section 1.' },{ 'subtitle' : 'Section 2: Analysis', 'tags' : [ 'STLW' ],'content' : "
                                  + "'Section 2: This is the content of section 2.' }]}");

        assertEquals(expected, actual);
    }

    @Test
    public void testReplaceRoot() {
        List<Document> documents = java.util.Arrays.asList(parse("{'_id': 1, 'name': {'first': 'John', 'last': 'Backus'}}"),
            parse("{'_id': 2, 'name': {'first': 'John', 'last': 'McCarthy'}}"),
            parse("{'_id': 3, 'name': {'first': 'Grace', 'last': 'Hopper'}}"),
            parse("{'_id': 4, 'firstname': 'Ole-Johan', 'lastname': 'Dahl'}"));

        getDatabase().getCollection("authors").insertMany(documents);

        List<Document> actual = getDs().aggregate(Author.class)
                                       .match(exists("name"),
                                           type("name", Type.ARRAY).not(),
                                           type("name", Type.OBJECT))
                                       .replaceRoot(ReplaceRoot.with(field("name")))
                                       .execute(Document.class)
                                       .toList();
        List<Document> expected = documents.subList(0, 3)
                                           .stream()
                                           .map(d -> (Document) d.get("name"))
                                           .collect(toList());
        assertDocumentEquals(expected, actual);

        actual = getDs().aggregate(Author.class)
                        .replaceRoot(ReplaceRoot.with(ifNull().target(field("name"))
                                                              .field("_id", field("_id"))
                                                              .field("missingName", value(true))))
                        .execute(Document.class)
                        .toList();
        expected = documents.subList(0, 3)
                            .stream()
                            .map(d -> (Document) d.get("name"))
                            .collect(toList());
        expected.add(new Document("_id", 4)
                         .append("missingName", true));
        assertDocumentEquals(expected, actual);

        actual = getDs().aggregate(Author.class)
                        .replaceRoot(ReplaceRoot.with(mergeObjects()
                                                          .add(Expressions.of()
                                                                          .field("_id", field("_id"))
                                                                          .field("first", value(""))
                                                                          .field("last", value("")))
                                                          .add(field("name"))))
                        .execute(Document.class)
                        .toList();
        expected = documents.subList(0, 3)
                            .stream()
                            .peek(d -> d.putAll((Document) d.remove("name")))
                            .collect(toList());
        expected.add(new Document("_id", 4)
                         .append("first", "")
                         .append("last", ""));
        assertDocumentEquals(expected, actual);
    }

    @Test
    public void testReplaceWith() {
        checkMinServerVersion(4.2);
        List<Document> documents = java.util.Arrays.asList(parse("{'_id': 1, 'name': {'first': 'John', 'last': 'Backus'}}"),
            parse("{'_id': 2, 'name': {'first': 'John', 'last': 'McCarthy'}}"),
            parse("{'_id': 3, 'name': {'first': 'Grace', 'last': 'Hopper'}}"),
            parse("{'_id': 4, 'firstname': 'Ole-Johan', 'lastname': 'Dahl'}"));

        getDatabase().getCollection("authors").insertMany(documents);

        List<Document> actual = getDs().aggregate(Author.class)
                                       .match(exists("name"),
                                           type("name", Type.ARRAY).not(),
                                           type("name", Type.OBJECT))
                                       .replaceWith(with(field("name")))
                                       .execute(Document.class)
                                       .toList();
        List<Document> expected = documents.subList(0, 3)
                                           .stream()
                                           .map(d -> (Document) d.get("name"))
                                           .collect(toList());
        assertDocumentEquals(expected, actual);

        actual = getDs().aggregate(Author.class)
                        .replaceWith(with(ifNull().target(field("name"))
                                                  .field("_id", field("_id"))
                                                  .field("missingName", value(true))))
                        .execute(Document.class)
                        .toList();
        expected = documents.subList(0, 3)
                            .stream()
                            .map(d -> (Document) d.get("name"))
                            .collect(toList());
        expected.add(new Document("_id", 4)
                         .append("missingName", true));
        assertDocumentEquals(expected, actual);

        actual = getDs().aggregate(Author.class)
                        .replaceWith(with(mergeObjects()
                                              .add(Expressions.of()
                                                              .field("_id", field("_id"))
                                                              .field("first", value(""))
                                                              .field("last", value("")))
                                              .add(field("name"))))
                        .execute(Document.class)
                        .toList();
        expected = documents.subList(0, 3)
                            .stream()
                            .peek(d -> d.putAll((Document) d.remove("name")))
                            .collect(toList());
        expected.add(new Document("_id", 4)
                         .append("first", "")
                         .append("last", ""));
        assertDocumentEquals(expected, actual);
    }

    @Test
    public void testSample() {
        getDs().save(asList(new User("John", LocalDate.now()),
            new User("Paul", LocalDate.now()),
            new User("George", LocalDate.now()),
            new User("Ringo", LocalDate.now())));
        Aggregation<User> pipeline = getDs()
                                         .aggregate(User.class)
                                         .sample(3);

        assertEquals(3, pipeline.execute(User.class).toList().size());
    }

    @Test
    public void testSet() {
        List<Document> list = java.util.Arrays.asList(parse("{ _id: 1, student: 'Maya', homework: [ 10, 5, 10 ],quiz: [ 10, 8 ],extraCredit: 0 }"),
            parse("{ _id: 2, student: 'Ryan', homework: [ 5, 6, 5 ],quiz: [ 8, 8 ],extraCredit: 8 }"));

        getDatabase().getCollection("scores").insertMany(list);

        List<Document> result = getDs().aggregate(Score.class)
                                       .set(AddFields.of()
                                                     .field("totalHomework", sum(field("homework")))
                                                     .field("totalQuiz", sum(field("quiz"))))
                                       .set(AddFields.of()
                                                     .field("totalScore", add(field("totalHomework"),
                                                         field("totalQuiz"), field("extraCredit"))))
                                       .execute(Document.class)
                                       .toList();

        list = java.util.Arrays.asList(parse("{ '_id' : 1, 'student' : 'Maya', 'homework' : [ 10, 5, 10 ],'quiz' : [ 10, 8 ],'extraCredit' : 0, 'totalHomework' : 25,"
                  + " 'totalQuiz' : 18, 'totalScore' : 43 }"),
            parse("{ '_id' : 2, 'student' : 'Ryan', 'homework' : [ 5, 6, 5 ],'quiz' : [ 8, 8 ],'extraCredit' : 8, 'totalHomework' : 16, "
                  + "'totalQuiz' : 16, 'totalScore' : 40 }"));

        assertEquals(list, result);
    }

    @Test
    public void testUnset() {
        checkMinServerVersion(4.2);
        List<Document> documents = java.util.Arrays.asList(parse("{'_id': 1, title: 'Antelope Antics', isbn: '0001122223334', author: {last:'An', first: 'Auntie' }, copies: "
                  + "[ {warehouse: 'A', qty: 5 }, {warehouse: 'B', qty: 15 } ] }"),
            parse("{'_id': 2, title: 'Bees Babble', isbn: '999999999333', author: {last:'Bumble', first: 'Bee' }, copies: [ "
                  + "{warehouse: 'A', qty: 2 }, {warehouse: 'B', qty: 5 } ] }"));
        getDatabase().getCollection("books")
                     .insertMany(documents);

        for (final Document document : documents) {
            document.remove("copies");
        }

        List<Document> copies = getDs().aggregate(Book.class)
                                       .unset(Unset.fields("copies"))
                                       .execute(Document.class)
                                       .toList();

        assertEquals(documents, copies);

    }

    private void compare(final int id, final List<Document> expected, final List<Document> actual) {
        assertEquals(find(id, expected), find(id, actual));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private Document find(final int id, final List<Document> documents) {
        return documents.stream().filter(d -> d.getInteger("_id").equals(id)).findFirst().get();
    }

    @Entity(useDiscriminator = false)
    public static class Artwork {
        @Id
        private ObjectId id;
        private Double price;
    }

    @Entity(value = "employees", useDiscriminator = false)
    private static class Employee {
        @Id
        private ObjectId id;
    }

    @Embedded
    static class ProjectedAuthor {
        private String last;
        private String first;

        public ProjectedAuthor() {
        }

        public ProjectedAuthor(final String last, final String first) {
            this.last = last;
            this.first = first;
        }

        @Override
        public int hashCode() {
            return Objects.hash(last, first);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ProjectedAuthor)) {
                return false;
            }
            final ProjectedAuthor that = (ProjectedAuthor) o;
            return last.equals(that.last) &&
                   first.equals(that.first);
        }
    }

    @Entity
    static class ProjectedBook {
        @Id
        private Integer id;
        private String title;
        private ProjectedAuthor author;

        ProjectedBook() {
        }

        public ProjectedBook(final Integer id, final String title, final String last, final String first) {
            this.id = id;
            this.title = title;
            author = new ProjectedAuthor(last, first);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, title, author);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ProjectedBook)) {
                return false;
            }
            final ProjectedBook that = (ProjectedBook) o;
            return Objects.equals(id, that.id) &&
                   title.equals(that.title) &&
                   author.equals(that.author);
        }
    }

    @Entity("salaries")
    public static class Salary {
        @Id
        private ObjectId id;
    }

    @Entity(value = "scores", useDiscriminator = false)
    private static class Score {
        @Id
        private ObjectId id;
        private int score;
    }
}
