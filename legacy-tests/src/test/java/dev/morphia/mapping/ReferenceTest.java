package dev.morphia.mapping;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import dev.morphia.Datastore;
import dev.morphia.Key;
import dev.morphia.Morphia;
import dev.morphia.aggregation.experimental.stages.Lookup;
import dev.morphia.annotations.Embedded;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Reference;
import dev.morphia.mapping.experimental.MorphiaReference;
import dev.morphia.mapping.lazy.ProxyTestBase;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.testutil.TestEntity;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.*;

import static dev.morphia.aggregation.experimental.stages.Unwind.on;
import static dev.morphia.mapping.lazy.LazyFeatureDependencies.assertProxyClassesPresent;
import static dev.morphia.query.experimental.filters.Filters.eq;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(Reference.class)
public class ReferenceTest extends ProxyTestBase {
    @Test
    public void maps() {
        Ref ref = new Ref("refId");
        getDs().save(ref);
        // create entity B with a reference to A
        Sets sets = new Sets();
        sets.refs = new HashSet<>();
        sets.refs.add(ref);
        getDs().save(sets);

        // this query throws a NullPointerException
        Assert.assertNotNull(getDs().find(Sets.class)
                                    .filter(eq("refs", ref))
                                    .first());

        final MapOfSet map = new MapOfSet();
        map.strings = new HashMap<>();
        map.strings.put("name", new TreeSet<>(asList("one", "two", "three")));
        getDs().save(map);
        final MapOfSet first = getDs().find(MapOfSet.class).first();
        Assert.assertEquals(map, first);
    }

    @Test
    public final void testArrayPersistence() {
        ArrayOfReferences a = new ArrayOfReferences();
        final Ref ref1 = new Ref();
        final Ref ref2 = new Ref();

        a.refs[0] = ref1;
        a.refs[1] = ref2;

        getDs().save(asList(ref2, ref1, a));

        getDs().find(ArrayOfReferences.class)
               .filter(eq("_id", a.getId()))
               .first();
    }

    @Test
    public void testComplexIds() {
        ComplexParent parent = new ComplexParent();
        parent.complex = new Complex(new ChildId("Bob", 67), "Kelso");
        parent.list = Collections.singletonList(new Complex(new ChildId("Turk", 27), "Turk"));
        parent.lazyList = Collections.singletonList(new Complex(new ChildId("Bippity", 67), "Boppity"));

        getDs().save(parent.complex);
        getDs().save(parent.list);
        getDs().save(parent.lazyList);
        getDs().save(parent);

        ComplexParent loaded = getDs().find(ComplexParent.class)
                                      .filter(eq("_id", parent.id))
                                      .first();
        assertEquals(parent, loaded);
    }

    @Test
    public void testFetchKeys() {
        List<Complex> list = asList(new Complex(new ChildId("Turk", 27), "Turk"),
            new Complex(new ChildId("JD", 26), "Dorian"),
            new Complex(new ChildId("Carla", 29), "Espinosa"));
        getDs().save(list);

        MongoCursor<Key<Complex>> keys = getDs().find(Complex.class).keys();
        assertTrue(keys.hasNext());
        assertEquals(list.get(0).getId(), keys.next().getId());
        assertEquals(list.get(1).getId(), keys.next().getId());
        assertEquals(list.get(2).getId(), keys.next().getId());
        assertFalse(keys.hasNext());
    }

    @Test
    public void testAggregationLookups() {
        final Author author = new Author("Jane Austen");
        getDs().save(author);

        Book book = addBook(author);
        Set<Book> set = addSetOfBooks(author);
        List<Book> list = addListOfBooks(author);
        //        Map<String, Book> map = addBookMap(author);

        getDs().save(author);
        Object document = getDs().aggregate(Author.class)
                                 .lookup(Lookup.from(Book.class)
                                               .as("set")
                                               .foreignField("_id")
                                               .localField("set"))
                                 .lookup(Lookup.from(Book.class)
                                               .as("list")
                                               .foreignField("_id")
                                               .localField("list"))
                                 //  TODO how to fetch the values from a nested document for cross-referencing?
                                 //                                   .lookup(Lookup.from(Book.class)
                                 //                                                 .as("map")
                                 //                                                 .foreignField("_id")
                                 //                                                 .localField("map.$"))
                                 .execute(Author.class)
                                 .tryNext();

        final Author loaded = (Author) document;
        Book foundBook = getDs().aggregate(Book.class)
                                .lookup(Lookup.from(Author.class)
                                              .as("author")
                                              .foreignField("_id")
                                              .localField("author"))
                                .unwind(on("author"))
                                .execute(Book.class)
                                .next();
        Assert.assertTrue(foundBook.author.isResolved());
        Assert.assertEquals(author, foundBook.author.get());

        final Set<Book> set1 = loaded.getSet();
        assertEquals(set.size(), set1.size());
        for (final Book book1 : set) {
            assertTrue("Looking for " + book1 + " in " + set1, set1.contains(book1));
        }

        Assert.assertEquals(list, loaded.getList());
        for (final Book book1 : list) {
            assertTrue("Looking for " + book1 + " in " + list, list.contains(book1));
        }
        //        validateMap(map, loaded);
    }

    @Test
    public void testIdOnlyReferences() {
        final List<Ref> refs = asList(new Ref("foo"), new Ref("bar"), new Ref("baz"));
        final Container c = new Container(refs);

        // test that we can save it
        final ObjectId key = getDs().save(c).getId();
        getDs().save(refs);

        // ensure that we're not using DBRef
        final MongoCollection<Document> collection = getDocumentCollection(Container.class);
        final Document persisted = collection.find(new Document("_id", key)).first();
        assertNotNull(persisted);
        assertEquals("foo", persisted.get("singleRef"));
        assertEquals("foo", persisted.get("lazySingleRef"));

        final List<String> expectedList = new ArrayList<>();
        expectedList.add("foo");
        expectedList.add("bar");
        expectedList.add("baz");
        assertEquals(expectedList, persisted.get("collectionRef"));
        assertEquals(expectedList, persisted.get("lazyCollectionRef"));

        final Document expectedMap = new Document();
        expectedMap.put("0", "foo");
        expectedMap.put("1", "bar");
        expectedMap.put("2", "baz");
        assertEquals(expectedMap, persisted.get("mapRef"));
        assertEquals(expectedMap, persisted.get("lazyMapRef"));

        // ensure that we can retrieve it
        final Container retrieved = getDs().find(Container.class)
                                           .filter(eq("_id", key))
                                           .first();

        assertEquals(refs.get(0), retrieved.getSingleRef());
        if (assertProxyClassesPresent()) {
            assertIsProxy(retrieved.getLazySingleRef());
        }
        assertEquals(refs.get(0), retrieved.getLazySingleRef());

        final List<Ref> expectedRefList = new ArrayList<>();
        final Map<Integer, Ref> expectedRefMap = new LinkedHashMap<>();

        for (int i = 0; i < refs.size(); i++) {
            expectedRefList.add(refs.get(i));
            expectedRefMap.put(i, refs.get(i));
        }

        assertEquals(expectedRefList, retrieved.getCollectionRef());
        assertEquals(expectedRefList, retrieved.getLazyCollectionRef());
        assertEquals(expectedRefMap, retrieved.getMapRef());
        assertEquals(expectedRefMap.keySet(), retrieved.getLazyMapRef().keySet());
    }

    @Test
    public void testFindByEntityReference() {
        final Ref ref = new Ref("refId");
        getDs().save(ref);

        final Container container = new Container();
        container.singleRef = ref;
        getDs().save(container);

        Assert.assertNotNull(getDs().find(Container.class)
                                    .filter(eq("singleRef", ref)).iterator(new FindOptions().limit(1))
                                    .next());
    }

    @Test
    public final void testMultiDimArrayPersistence() {
        MultiDimArrayOfReferences a = new MultiDimArrayOfReferences();
        final Ref ref1 = new Ref();
        final Ref ref2 = new Ref();

        a.arrays = new Ref[][][]{
            new Ref[][]{
                new Ref[]{ref1, ref2}
            }
        };
        a.lists = Collections.singletonList(Arrays.asList(Collections.singletonList(ref1), Collections.singletonList(ref2)));
        getDs().save(asList(ref2, ref1, a));

        assertEquals(a, getDs().find(MultiDimArrayOfReferences.class)
                               .filter(eq("_id", a.getId()))
                               .first());
    }

    @Test
    public void testNullReferences() {
        Container container = new Container();
        container.lazyMapRef = null;
        container.singleRef = null;
        container.lazySingleRef = null;
        container.collectionRef = null;
        container.lazyCollectionRef = null;
        container.mapRef = null;
        container.lazyMapRef = null;

        MapperOptions options = MapperOptions.builder(getMapper().getOptions())
                                             .storeNulls(true)
                                             .build();
        Datastore datastore = Morphia.createDatastore(getMongoClient(), getDatabase().getName(), options);

        datastore.save(container);
        allNull(container);

        options = MapperOptions.builder(getMapper().getOptions())
                               .storeNulls(true)
                               .build();
        datastore = Morphia.createDatastore(getMongoClient(), getDatabase().getName(), options);
        datastore.save(container);
        allNull(container);
    }

    @Test
    public void testReferencesWithoutMapping() {
        Child child1 = new Child();
        getDs().save(child1);

        Parent parent1 = new Parent();
        parent1.children.add(child1);
        getDs().save(parent1);

        List<Parent> parentList = getDs().find(Parent.class).iterator().toList();
        Assert.assertEquals(1, parentList.size());

        // reset Datastore to reset internal Mapper cache, so Child class
        // already cached by previous save is cleared
        Datastore localDs = Morphia.createDatastore(getMongoClient(), getDatabase().getName());

        parentList = localDs.find(Parent.class).iterator().toList();
        Assert.assertEquals(1, parentList.size());
    }

    @Test
    public void testReferenceQueryWithoutValidation() {
        Ref ref = getDs().save(new Ref("no validation"));
        getDs().save(new Container(singletonList(ref)));

        final Query<Container> query = getDs().find(Container.class)
                                              .disableValidation()
                                              .filter(eq("singleRef", ref));
        Assert.assertNotNull(query.iterator(new FindOptions().limit(1)).next());
    }

    protected Book addBook(final Author author) {
        final Book book = new Book("Pride and Prejudice");
        book.setAuthor(author);
        return getDs().save(book);
    }

    protected List<Book> addListOfBooks(final Author author) {
        List<Book> list = new ArrayList<>();
        list.add(new Book("Sense and Sensibility"));
        list.add(new Book("Pride and Prejudice"));
        list.add(new Book("Mansfield Park"));
        list.add(new Book("Emma"));
        list.add(new Book("Northanger Abbey"));
        for (final Book book : list) {
            book.setAuthor(author);
            getDs().save(book);
        }
        author.setList(list);
        return list;
    }

    protected Set<Book> addSetOfBooks(final Author author) {
        Set<Book> set = new HashSet<>(5);
        set.add(new Book("Sense and Sensibility"));
        set.add(new Book("Pride and Prejudice"));
        set.add(new Book("Mansfield Park"));
        set.add(new Book("Emma"));
        set.add(new Book("Northanger Abbey"));
        for (final Book book : set) {
            book.setAuthor(author);
            getDs().save(book);
        }
        author.setSet(set);
        return set;
    }

    private void allNull(final Container container) {
        Assert.assertNull(container.lazyMapRef);
        Assert.assertNull(container.singleRef);
        Assert.assertNull(container.lazySingleRef);
        Assert.assertNull(container.collectionRef);
        Assert.assertNull(container.lazyCollectionRef);
        Assert.assertNull(container.mapRef);
    }

    public static class ArrayOfReferences extends TestEntity {
        @Reference
        private final Ref[] refs = new Ref[2];
    }

    @Entity
    private static class Author {
        @Id
        private ObjectId id;

        private String name;

        @Reference
        private List<Book> list;
        @Reference
        private Set<Book> set;

        public Author() {
        }

        public Author(final String name) {
            this.name = name;
        }

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public List<Book> getList() {
            return list;
        }

        public void setList(final List<Book> list) {
            this.list = list;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public Set<Book> getSet() {
            return set;
        }

        public void setSet(final Set<Book> set) {
            this.set = set;
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final Author author = (Author) o;

            if (id != null ? !id.equals(author.id) : author.id != null) {
                return false;
            }
            return name != null ? name.equals(author.name) : author.name == null;
        }
    }

    @Entity
    private static class Book {
        @Id
        private ObjectId id;
        private String name;
        private MorphiaReference<Author> author;

        public Book() {
        }

        public Book(final String name) {
            this.name = name;
        }

        public Author getAuthor() {
            return author.get();
        }

        public void setAuthor(final Author author) {
            this.author = MorphiaReference.wrap(author);
        }

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final Book book = (Book) o;

            if (id != null ? !id.equals(book.id) : book.id != null) {
                return false;
            }
            return name != null ? name.equals(book.name) : book.name == null;
        }

        @Override
        public String toString() {
            return "Book{" +
                   "name='" + name + "', " +
                   "hash=" + hashCode() +
                   '}';
        }

        public void setName(final String name) {
            this.name = name;
        }
    }

    @Entity(value = "children", useDiscriminator = false)
    static class Child {
        @Id
        private ObjectId id;

    }

    @Embedded
    public static class ChildId {
        private String name;
        private int age;

        ChildId() {
        }

        public ChildId(final String name, final int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        @Override
        public int hashCode() {
            int result = getName() != null ? getName().hashCode() : 0;
            result = 31 * result + getAge();
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ChildId)) {
                return false;
            }

            final ChildId childId = (ChildId) o;

            if (getAge() != childId.getAge()) {
                return false;
            }
            return getName() != null ? getName().equals(childId.getName()) : childId.getName() == null;

        }

        int getAge() {
            return age;
        }
    }

    @Entity("complex")
    public static class Complex {
        @Id
        private ChildId id;

        private String value;

        Complex() {
        }

        public Complex(final ChildId id, final String value) {
            this.id = id;
            this.value = value;
        }

        public ChildId getId() {
            return id;
        }

        public void setId(final ChildId id) {
            this.id = id;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }

        @Override
        public int hashCode() {
            int result = getId() != null ? getId().hashCode() : 0;
            result = 31 * result + (getValue() != null ? getValue().hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Complex)) {
                return false;
            }

            final Complex complex = (Complex) o;

            if (getId() != null ? !getId().equals(complex.getId()) : complex.getId() != null) {
                return false;
            }
            return getValue() != null ? getValue().equals(complex.getValue()) : complex.getValue() == null;

        }
    }

    @Entity
    private static class ComplexParent {
        @Id
        private ObjectId id;

        @Reference
        private Complex complex;

        @Reference
        private List<Complex> list = new ArrayList<>();

        @Reference(lazy = true)
        private List<Complex> lazyList = new ArrayList<>();

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public List<Complex> getList() {
            return list;
        }

        public void setList(final List<Complex> list) {
            this.list = list;
        }

        @Override
        public int hashCode() {
            int result = getId() != null ? getId().hashCode() : 0;
            result = 31 * result + (getComplex() != null ? getComplex().hashCode() : 0);
            result = 31 * result + (getList() != null ? getList().hashCode() : 0);
            result = 31 * result + (getLazyList() != null ? getLazyList().hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ComplexParent)) {
                return false;
            }

            final ComplexParent that = (ComplexParent) o;

            if (getId() != null ? !getId().equals(that.getId()) : that.getId() != null) {
                return false;
            }
            if (getComplex() != null ? !getComplex().equals(that.getComplex()) : that.getComplex() != null) {
                return false;
            }
            if (getList() != null ? !getList().equals(that.getList()) : that.getList() != null) {
                return false;
            }
            return getLazyList() != null ? getLazyList().equals(that.getLazyList()) : that.getLazyList() == null;

        }

        Complex getComplex() {
            return complex;
        }

        public void setComplex(final Complex complex) {
            this.complex = complex;
        }

        List<Complex> getLazyList() {
            return lazyList;
        }

        public void setLazyList(final List<Complex> lazyList) {
            this.lazyList = lazyList;
        }
    }

    @Entity
    public static class Container {
        @Id
        private ObjectId id;

        @Reference(idOnly = true)
        private Ref singleRef;

        @Reference(idOnly = true, lazy = true)
        private Ref lazySingleRef;

        @Reference(idOnly = true)
        private List<Ref> collectionRef;

        @Reference(idOnly = true, lazy = true)
        private List<Ref> lazyCollectionRef;

        @Reference(idOnly = true)
        private LinkedHashMap<Integer, Ref> mapRef;

        @Reference(idOnly = true, lazy = true)
        private LinkedHashMap<Integer, Ref> lazyMapRef;

        /* required by morphia */
        Container() {
        }

        Container(final List<Ref> refs) {
            singleRef = refs.get(0);
            lazySingleRef = refs.get(0);
            collectionRef = refs;
            lazyCollectionRef = refs;
            mapRef = new LinkedHashMap<>();
            lazyMapRef = new LinkedHashMap<>();

            for (int i = 0; i < refs.size(); i++) {
                mapRef.put(i, refs.get(i));
                lazyMapRef.put(i, refs.get(i));
            }
        }

        List<Ref> getCollectionRef() {
            return collectionRef;
        }

        ObjectId getId() {
            return id;
        }

        List<Ref> getLazyCollectionRef() {
            return lazyCollectionRef;
        }

        LinkedHashMap<Integer, Ref> getLazyMapRef() {
            return lazyMapRef;
        }

        Ref getLazySingleRef() {
            return lazySingleRef;
        }

        LinkedHashMap<Integer, Ref> getMapRef() {
            return mapRef;
        }

        Ref getSingleRef() {
            return singleRef;
        }
    }

    @Entity("cs")
    public static class MapOfSet {
        @Id
        private ObjectId id;

        private Map<String, Set<String>> strings;

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (strings != null ? strings.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final MapOfSet map = (MapOfSet) o;

            if (id != null ? !id.equals(map.id) : map.id != null) {
                return false;
            }
            return strings != null ? strings.equals(map.strings) : map.strings == null;
        }
    }

    public static class MultiDimArrayOfReferences extends TestEntity {
        @Reference(idOnly = true)
        private Ref[][][] arrays;
        private List<List<List<Ref>>> lists;

        @Override
        public int hashCode() {
            int result = Arrays.deepHashCode(arrays);
            result = 31 * result + (lists != null ? lists.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MultiDimArrayOfReferences)) {
                return false;
            }

            final MultiDimArrayOfReferences that = (MultiDimArrayOfReferences) o;

            if (!Arrays.deepEquals(arrays, that.arrays)) {
                return false;
            }
            return lists != null ? lists.equals(that.lists) : that.lists == null;
        }
    }

    @Entity("sets")
    public static class Sets {

        @Id
        private ObjectId id;

        @Reference
        private Set<Ref> refs;
    }

    @Entity(value = "parents", useDiscriminator = false)
    private static class Parent {

        @Id
        private ObjectId id;
        @Reference(lazy = true)
        private final List<Child> children = new ArrayList<>();

    }

    @Entity
    public static class Ref {
        @Id
        private String id;

        public Ref() {
        }

        Ref(final String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(final String id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return getId() != null ? getId().hashCode() : 0;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Ref)) {
                return false;
            }

            final Ref ref = (Ref) o;

            return getId() != null ? getId().equals(ref.getId()) : ref.getId() == null;
        }

        @Override
        public String toString() {
            return String.format("Ref{id='%s'}", id);
        }
    }
}
