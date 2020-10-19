/*
  Copyright (C) 2010 Olafur Gauti Gudmundsson
  <p/>
  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
  obtain a copy of the License at
  <p/>
  http://www.apache.org/licenses/LICENSE-2.0
  <p/>
  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
  and limitations under the License.
 */


package dev.morphia.test;


import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.annotations.AlsoLoad;
import dev.morphia.annotations.Embedded;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.LoadOnly;
import dev.morphia.annotations.experimental.Constructor;
import dev.morphia.annotations.experimental.Name;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.MappedField;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.mapping.MappingException;
import dev.morphia.mapping.NamingStrategy;
import dev.morphia.mapping.experimental.MorphiaReference;
import dev.morphia.mapping.lazy.proxy.ReferenceException;
import dev.morphia.mapping.validation.ConstraintViolationException;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.test.models.Author;
import dev.morphia.test.models.BannedUser;
import dev.morphia.test.models.Book;
import dev.morphia.test.models.CityPopulation;
import dev.morphia.test.models.State;
import dev.morphia.test.models.User;
import dev.morphia.test.models.errors.BadConstructorBased;
import dev.morphia.test.models.errors.ContainsDocument;
import dev.morphia.test.models.errors.ContainsMapLike;
import dev.morphia.test.models.errors.ContainsXKeyMap;
import dev.morphia.test.models.errors.IdOnEmbedded;
import dev.morphia.test.models.errors.MissingId;
import dev.morphia.test.models.errors.OuterClass.NonStaticInnerClass;
import dev.morphia.utils.ReflectionUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.morphia.query.experimental.filters.Filters.eq;
import static dev.morphia.query.experimental.filters.Filters.exists;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


@SuppressWarnings({"unchecked", "unchecked"})
public class TestMapping extends TestBase {

    @Test
    public void badConstructorMapping() {
        Assertions.assertThrows(ConstraintViolationException.class, () -> {
            getDs().getMapper().map(BadConstructorBased.class);
        });
    }

    @Test
    public void collectionNaming() {
        MapperOptions options = MapperOptions.builder()
                                             .collectionNaming(NamingStrategy.lowerCase())
                                             .build();
        Datastore datastore = Morphia.createDatastore(TestBase.TEST_DB_NAME, options);
        List<MappedClass> map = datastore.getMapper().map(ContainsMapWithEmbeddedInterface.class, ContainsIntegerList.class);

        assertEquals("containsmapwithembeddedinterface", map.get(0).getCollectionName());
        assertEquals("cil", map.get(1).getCollectionName());

        options = MapperOptions.builder()
                               .collectionNaming(NamingStrategy.kebabCase())
                               .build();
        datastore = Morphia.createDatastore(TestBase.TEST_DB_NAME, options);
        map = datastore.getMapper().map(ContainsMapWithEmbeddedInterface.class, ContainsIntegerList.class);

        assertEquals("contains-map-with-embedded-interface", map.get(0).getCollectionName());
        assertEquals("cil", map.get(1).getCollectionName());
    }

    @Test
    public void constructors() {
        getDs().getMapper().map(ConstructorBased.class);

        ContainsFinalField value = new ContainsFinalField();
        ConstructorBased instance = new ConstructorBased(new ObjectId(), "test instance", MorphiaReference.wrap(value));

        getDs().save(java.util.Arrays.asList(value, instance));

        ConstructorBased first = getDs().find(ConstructorBased.class).first();
        assertNotNull(first);
        assertEquals(instance, first);
    }

    @Test
    public void fieldNaming() {
        MapperOptions options = MapperOptions.builder()
                                             .fieldNaming(NamingStrategy.snakeCase())
                                             .build();
        Datastore datastore1 = Morphia.createDatastore(TestBase.TEST_DB_NAME, options);
        List<MappedClass> map = datastore1.getMapper().map(ContainsMapWithEmbeddedInterface.class, ContainsIntegerList.class);

        List<MappedField> fields = map.get(0).getFields();
        validateField(fields, "_id", "id");
        validateField(fields, "embedded_values", "embeddedValues");

        fields = map.get(1).getFields();
        validateField(fields, "_id", "id");
        validateField(fields, "int_list", "intList");

        options = MapperOptions.builder()
                               .fieldNaming(NamingStrategy.kebabCase())
                               .build();
        final Datastore datastore2 = Morphia.createDatastore(TestBase.TEST_DB_NAME, options);
        map = datastore2.getMapper().map(ContainsMapWithEmbeddedInterface.class, ContainsIntegerList.class);

        fields = map.get(0).getFields();
        validateField(fields, "_id", "id");
        validateField(fields, "embedded-values", "embeddedValues");

        fields = map.get(1).getFields();
        validateField(fields, "_id", "id");
        validateField(fields, "int-list", "intList");

    }

    @Test
    public void testAlsoLoad() {
        getMapper().map(ContainsIntegerListNew.class, ContainsIntegerList.class);
        final ContainsIntegerList cil = new ContainsIntegerList();
        cil.intList.add(1);
        getDs().save(cil);
        final ContainsIntegerList cilLoaded = getDs().find(ContainsIntegerList.class)
                                                     .filter(eq("_id", cil.id))
                                                     .first();
        assertNotNull(cilLoaded);
        assertNotNull(cilLoaded.intList);
        assertEquals(cilLoaded.intList.size(), cil.intList.size());
        assertEquals(cilLoaded.intList.get(0), cil.intList.get(0));

        final ContainsIntegerListNew cilNew = getDs().find(ContainsIntegerListNew.class).filter(eq("_id", cil.id)).first();
        assertNotNull(cilNew);
        assertNotNull(cilNew.integers);
        assertEquals(1, cilNew.integers.size());
        assertEquals(1, (int) cil.intList.get(0));
    }

    @Test
    public void testBadMappings() {
        Assertions.assertThrows(MappingException.class, () -> {
            getMapper().map(MissingId.class);
            fail("Validation: Missing @Id field not caught");
        });

        Assertions.assertThrows(MappingException.class, () -> {
            getMapper().map(IdOnEmbedded.class);
            fail("Validation: @Id field on @Embedded not caught");
        });

        Assertions.assertThrows(MappingException.class, () -> {
            getMapper().map(NonStaticInnerClass.class);
            fail("Validation: Non-static inner class allowed");
        });
    }

    @Test
    public void testBasicMapping() {
        Mapper mapper = getDs().getMapper();
        mapper.map(java.util.Arrays.asList(State.class, CityPopulation.class));

        final State state = new State();
        state.state = "NY";
        state.biggest = new CityPopulation("NYC", 8336817L);
        state.smallest = new CityPopulation("Red House", 38L);

        getDs().save(state);

        Query<State> query = getDs().find(State.class)
                                    .filter(eq("_id", state.id));
        State loaded = query.first();

        assertEquals(state, loaded);

        MappedClass mappedClass = mapper.getMappedClass(State.class);
        assertEquals(java.util.Arrays.asList("_id", "state", "biggestCity", "smallestCity"), mappedClass.getFields()
                                                                                                        .stream()
                                                                                                        .map(MappedField::getMappedFieldName)
                                                                                                        .collect(toList()));
    }

    @Test
    public void testByteArrayMapping() {
        getMapper().map(ContainsByteArray.class);
        final ObjectId savedKey = getDs().save(new ContainsByteArray()).id;
        final ContainsByteArray loaded = getDs().find(ContainsByteArray.class)
                                                .filter(eq("_id", savedKey))
                                                .first();
        assertEquals(new String((new ContainsByteArray()).bytes), new String(loaded.bytes));
        assertNotNull(loaded.id);
    }

    @Test
    public void testCollectionMapping() {
        getMapper().map(ContainsCollection.class);
        final ObjectId savedKey = getDs().save(new ContainsCollection()).id;
        final ContainsCollection loaded = getDs().find(ContainsCollection.class)
                                                 .filter(eq("_id", savedKey))
                                                 .first();
        assertEquals(loaded.coll, (new ContainsCollection()).coll);
        assertNotNull(loaded.id);
    }

    @Test
    public void testEmbeddedArrayElementHasNoClassname() {
        getMapper().map(ContainsEmbeddedArray.class);
        final ContainsEmbeddedArray cea = new ContainsEmbeddedArray();
        cea.res = new RenamedEmbedded[]{new RenamedEmbedded()};

        final Document document = getMapper().toDocument(cea);
        List<Document> res = (List<Document>) document.get("res");
        assertFalse(res.get(0).containsKey(getMapper().getOptions().getDiscriminatorKey()));
    }

    @Test
    public void testEmbeddedDocument() {
        getMapper().map(ContainsDocument.class);
        getDs().save(new ContainsDocument());
        assertNotNull(getDs().find(ContainsDocument.class).iterator(new FindOptions().limit(1))
                             .next());
    }

    @Test
    public void testEmbeddedEntity() {
        getMapper().map(ContainsEmbeddedEntity.class);
        getDs().save(new ContainsEmbeddedEntity());
        final ContainsEmbeddedEntity ceeLoaded = getDs().find(ContainsEmbeddedEntity.class).iterator(new FindOptions().limit(1))
                                                        .next();
        assertNotNull(ceeLoaded);
        assertNotNull(ceeLoaded.id);
        assertNotNull(ceeLoaded.cil);
        assertNull(ceeLoaded.cil.id);

    }

    @Test
    public void testEmbeddedEntityDocumentHasNoClassname() {
        getMapper().map(ContainsEmbeddedEntity.class);
        final ContainsEmbeddedEntity cee = new ContainsEmbeddedEntity();
        cee.cil = new ContainsIntegerList();
        cee.cil.intList = Collections.singletonList(1);
        final Document document = getMapper().toDocument(cee);
        assertFalse(((Document) document.get("cil")).containsKey(getMapper().getOptions().getDiscriminatorKey()));
    }

    @Test
    public void testEnumKeyedMap() {
        final ContainsEnum1KeyMap map = new ContainsEnum1KeyMap();
        map.values.put(Enum1.A, "I'm a");
        map.values.put(Enum1.B, "I'm b");
        map.embeddedValues.put(Enum1.A, "I'm a");
        map.embeddedValues.put(Enum1.B, "I'm b");

        getDs().save(map);

        final ContainsEnum1KeyMap mapLoaded = getDs().find(ContainsEnum1KeyMap.class).filter(eq("_id", map.id)).first();

        assertNotNull(mapLoaded);
        assertEquals(2, mapLoaded.values.size());
        assertNotNull(mapLoaded.values.get(Enum1.A));
        assertNotNull(mapLoaded.values.get(Enum1.B));
        assertEquals(2, mapLoaded.embeddedValues.size());
        assertNotNull(mapLoaded.embeddedValues.get(Enum1.A));
        assertNotNull(mapLoaded.embeddedValues.get(Enum1.B));
    }

    @Test
    public void testFinalField() {
        getMapper().map(ContainsFinalField.class);
        final ObjectId savedKey = getDs().save(new ContainsFinalField("blah")).id;
        final ContainsFinalField loaded = getDs().find(ContainsFinalField.class)
                                                 .filter(eq("_id", savedKey))
                                                 .first();
        assertNotNull(loaded);
        assertNotNull(loaded.name);
        assertEquals("blah", loaded.name);
    }

    @Test
    public void testFinalFieldNotPersisted() {
        MapperOptions options = MapperOptions.builder(getMapper().getOptions())
                                             .ignoreFinals(true)
                                             .build();
        final Datastore datastore = Morphia.createDatastore(getMongoClient(), getDatabase().getName(), options);

        getMapper().map(ContainsFinalField.class);
        final ObjectId savedKey = datastore.save(new ContainsFinalField("blah")).id;
        final ContainsFinalField loaded = datastore.find(ContainsFinalField.class)
                                                   .filter(eq("_id", savedKey))
                                                   .first();
        assertNotNull(loaded);
        assertNotNull(loaded.name);
        assertEquals("foo", loaded.name);
    }

    @Test
    public void testFinalIdField() {
        getMapper().map(HasFinalFieldId.class);
        final long savedKey = getDs().save(new HasFinalFieldId(12)).id;
        final HasFinalFieldId loaded = getDs().find(HasFinalFieldId.class)
                                              .filter(eq("_id", savedKey))
                                              .first();
        assertNotNull(loaded);
        assertEquals(12, loaded.id);
    }

    @Test
    public void testExternalClass() {
        Datastore datastore = Morphia.createDatastore(TEST_DB_NAME);
        List<MappedClass> mappedClasses = datastore.getMapper().map(UnannotatedEmbedded.class);
        assertEquals(1, mappedClasses.size(), "Should be able to map explicitly passed class references");

        datastore = Morphia.createDatastore(TEST_DB_NAME);
        datastore.getMapper().mapPackage(ReflectionUtils.getPackageName(UnannotatedEmbedded.class));
        assertFalse(datastore.getMapper().isMapped(UnannotatedEmbedded.class),
            "Should not be able to map unannotated classes with mapPackage");
    }

    @Test
    public void testIntKeySetStringMap() {
        final ContainsIntKeySetStringMap map = new ContainsIntKeySetStringMap();
        map.values.put(1, Collections.singleton("I'm 1"));
        map.values.put(2, Collections.singleton("I'm 2"));

        getDs().save(map);

        final ContainsIntKeySetStringMap mapLoaded = getDs().find(ContainsIntKeySetStringMap.class)
                                                            .filter(eq("_id", map.id))
                                                            .first();

        assertNotNull(mapLoaded);
        assertEquals(2, mapLoaded.values.size());
        assertNotNull(mapLoaded.values.get(1));
        assertNotNull(mapLoaded.values.get(2));
        assertEquals(1, mapLoaded.values.get(1).size());

        assertNotNull(getDs().find(ContainsIntKeyMap.class).filter(exists("values.2")));
        assertEquals(0, getDs().find(ContainsIntKeyMap.class).filter(exists("values.2").not()).count());
        assertNotNull(getDs().find(ContainsIntKeyMap.class).filter(exists("values.4").not()));
        assertEquals(0, getDs().find(ContainsIntKeyMap.class).filter(exists("values.4")).count());
    }

    @Test
    public void testIntKeyedMap() {
        final ContainsIntKeyMap map = new ContainsIntKeyMap();
        map.values.put(1, "I'm 1");
        map.values.put(2, "I'm 2");

        getDs().save(map);

        final ContainsIntKeyMap mapLoaded = getDs().find(ContainsIntKeyMap.class)
                                                   .filter(eq("_id", map.id))
                                                   .first();

        assertNotNull(mapLoaded);
        assertEquals(2, mapLoaded.values.size());
        assertNotNull(mapLoaded.values.get(1));
        assertNotNull(mapLoaded.values.get(2));

        assertNotNull(getDs().find(ContainsIntKeyMap.class)
                             .filter(exists("values.2")));
        assertEquals(0, getDs().find(ContainsIntKeyMap.class)
                               .filter(exists("values.2").not())
                               .count());
        assertNotNull(getDs().find(ContainsIntKeyMap.class)
                             .filter(exists("values.4").not()));
        assertEquals(0, getDs().find(ContainsIntKeyMap.class)
                               .filter(exists("values.4"))
                               .count());
    }

    @Test
    public void testIntLists() {
        ContainsIntegerList cil = new ContainsIntegerList();
        getDs().save(cil);
        ContainsIntegerList cilLoaded = getDs().find(ContainsIntegerList.class)
                                               .filter(eq("_id", cil.id))
                                               .first();
        assertNotNull(cilLoaded);
        assertNotNull(cilLoaded.intList);
        assertEquals(cilLoaded.intList.size(), cil.intList.size());


        cil = new ContainsIntegerList();
        cil.intList = null;
        getDs().save(cil);
        cilLoaded = getDs().find(ContainsIntegerList.class)
                           .filter(eq("_id", cil.id))
                           .first();
        assertNotNull(cilLoaded);
        assertNotNull(cilLoaded.intList);
        assertEquals(0, cilLoaded.intList.size());

        cil = new ContainsIntegerList();
        cil.intList.add(1);
        getDs().save(cil);
        cilLoaded = getDs().find(ContainsIntegerList.class)
                           .filter(eq("_id", cil.id))
                           .first();
        assertNotNull(cilLoaded);
        assertNotNull(cilLoaded.intList);
        assertEquals(1, cilLoaded.intList.size());
        assertEquals(1, (int) cilLoaded.intList.get(0));
    }

    @Test
    @Disabled("need to add this feature")
    @SuppressWarnings("unchecked")
    public void testGenericKeyedMap() {
        final ContainsXKeyMap<Integer> map = new ContainsXKeyMap<>();
        map.values.put(1, "I'm 1");
        map.values.put(2, "I'm 2");

        getDs().save(map);

        final ContainsXKeyMap<Integer> mapLoaded = getDs().find(ContainsXKeyMap.class).filter(eq("_id", map.id)).first();

        assertNotNull(mapLoaded);
        assertEquals(2, mapLoaded.values.size());
        assertNotNull(mapLoaded.values.get(1));
        assertNotNull(mapLoaded.values.get(2));
    }

    @Test
    public void testLongArrayMapping() {
        getMapper().map(ContainsLongAndStringArray.class);
        getDs().save(new ContainsLongAndStringArray());
        ContainsLongAndStringArray loaded = getDs().find(ContainsLongAndStringArray.class).iterator(new FindOptions().limit(1))
                                                   .next();
        assertArrayEquals(loaded.longs, (new ContainsLongAndStringArray()).longs);
        assertArrayEquals(loaded.strings, (new ContainsLongAndStringArray()).strings);

        final ContainsLongAndStringArray array = new ContainsLongAndStringArray();
        array.strings = new String[]{"a", "B", "c"};
        array.longs = new Long[]{4L, 5L, 4L};
        getDs().save(array);
        loaded = getDs().find(ContainsLongAndStringArray.class)
                        .filter(eq("_id", array.id))
                        .first();
        assertArrayEquals(loaded.longs, array.longs);
        assertArrayEquals(loaded.strings, array.strings);

        assertNotNull(loaded.id);
    }

    @Test
    public void testLoadOnly() {
        getDs().save(new Normal("value"));
        Normal n = getDs().find(Normal.class).iterator(new FindOptions().limit(1))
                          .next();
        assertNotNull(n);
        assertNotNull(n.name);
        getDs().delete(n);
        getDs().save(new NormalWithLoadOnly());
        n = getDs().find(Normal.class).iterator(new FindOptions().limit(1))
                   .next();
        assertNotNull(n);
        assertNull(n.name);
        getDs().delete(n);
        getDs().save(new Normal("value21"));
        final NormalWithLoadOnly notSaved = getDs().find(NormalWithLoadOnly.class).iterator(new FindOptions().limit(1))
                                                   .next();
        assertNotNull(notSaved);
        assertNotNull(notSaved.name);
        assertEquals("never", notSaved.name);
    }

    @Test
    public void testMapLike() {
        final ContainsMapLike ml = new ContainsMapLike();
        ml.m.put("first", "test");
        getDs().save(ml);
        final ContainsMapLike mlLoaded = getDs().find(ContainsMapLike.class).iterator(new FindOptions().limit(1))
                                                .next();
        assertNotNull(mlLoaded);
        assertNotNull(mlLoaded.m);
        assertTrue(mlLoaded.m.containsKey("first"));
    }

    @Test
    public void testObjectIdKeyedMap() {
        getMapper().map(ContainsObjectIdKeyMap.class);
        final ContainsObjectIdKeyMap map = new ContainsObjectIdKeyMap();
        final ObjectId o1 = new ObjectId("111111111111111111111111");
        final ObjectId o2 = new ObjectId("222222222222222222222222");
        map.values.put(o1, "I'm 1s");
        map.values.put(o2, "I'm 2s");

        getDs().save(map);

        final ContainsObjectIdKeyMap mapLoaded = getDs().find(ContainsObjectIdKeyMap.class).filter(eq("_id", map.id)).first();

        assertNotNull(mapLoaded);
        assertEquals(2, mapLoaded.values.size());
        assertNotNull(mapLoaded.values.get(o1));
        assertNotNull(mapLoaded.values.get(o2));

        assertNotNull(getDs().find(ContainsIntKeyMap.class).filter(exists("values.111111111111111111111111")));
        assertEquals(0, getDs().find(ContainsIntKeyMap.class).filter(exists("values.111111111111111111111111").not()).count());
        assertNotNull(getDs().find(ContainsIntKeyMap.class).filter(exists("values.4").not()));
        assertEquals(0, getDs().find(ContainsIntKeyMap.class).filter(exists("values.4")).count());
    }

    @Test
    public void testPrimMap() {
        final ContainsPrimitiveMap primMap = new ContainsPrimitiveMap();
        primMap.embeddedValues.put("first", 1L);
        primMap.embeddedValues.put("second", 2L);
        primMap.values.put("first", 1L);
        primMap.values.put("second", 2L);
        getDs().save(primMap);

        final ContainsPrimitiveMap primMapLoaded = getDs().find(ContainsPrimitiveMap.class)
                                                          .filter(eq("_id", primMap.id))
                                                          .first();

        assertNotNull(primMapLoaded);
        assertEquals(2, primMapLoaded.embeddedValues.size());
        assertEquals(2, primMapLoaded.values.size());
    }

    @Test
    public void testPrimMapWithNullValue() {
        final ContainsPrimitiveMap primMap = new ContainsPrimitiveMap();
        primMap.embeddedValues.put("first", null);
        primMap.embeddedValues.put("second", 2L);
        primMap.values.put("first", null);
        primMap.values.put("second", 2L);
        getDs().save(primMap);

        final ContainsPrimitiveMap primMapLoaded = getDs().find(ContainsPrimitiveMap.class)
                                                          .filter(eq("_id", primMap.id))
                                                          .first();

        assertNotNull(primMapLoaded);
        assertEquals(2, primMapLoaded.embeddedValues.size());
        assertEquals(2, primMapLoaded.values.size());
    }

    @Test
    public void testMapWithEmbeddedInterface() {
        final ContainsMapWithEmbeddedInterface aMap = new ContainsMapWithEmbeddedInterface();
        final Foo f1 = new Foo1();
        final Foo f2 = new Foo2();

        aMap.embeddedValues.put("first", f1);
        aMap.embeddedValues.put("second", f2);
        getDs().save(aMap);

        final ContainsMapWithEmbeddedInterface mapLoaded = getDs().find(ContainsMapWithEmbeddedInterface.class)
                                                                  .iterator(new FindOptions().limit(1))
                                                                  .next();

        assertNotNull(mapLoaded);
        assertEquals(2, mapLoaded.embeddedValues.size());
        assertTrue(mapLoaded.embeddedValues.get("first") instanceof Foo1);
        assertTrue(mapLoaded.embeddedValues.get("second") instanceof Foo2);

    }

    @Test
    @Tag("references")
    @Disabled("entity caching needs to be implemented")
    public void testRecursiveReference() {
/*
        getMapper().map(RecursiveParent.class, RecursiveChild.class);

        final RecursiveParent parent = getDs().save(new RecursiveParent());
        final RecursiveChild child = getDs().save(new RecursiveChild());

        final RecursiveParent parentLoaded = getDs().find(RecursiveParent.class)
                                                    .filter(eq("_id", parent.getId()))
                                                    .first();
        final RecursiveChild childLoaded = getDs().find(RecursiveChild.class)
                                                  .filter(eq("_id", child.getId()))
                                                  .first();

        parentLoaded.setChild(childLoaded);
        childLoaded.setParent(parentLoaded);

        getDs().save(parentLoaded);
        getDs().save(childLoaded);

        final RecursiveParent finalParentLoaded = getDs().find(RecursiveParent.class)
                                                         .filter(eq("_id", parent.getId()))
                                                         .first();
        final RecursiveChild finalChildLoaded = getDs().find(RecursiveChild.class)
                                                       .filter(eq("_id", child.getId()))
                                                       .first();


        assertNotNull(finalParentLoaded.getChild());
        assertNotNull(finalChildLoaded.getParent());
*/
    }

    @Test
    public void testReferenceWithoutIdValue() {
        assertThrows(ReferenceException.class, () -> {
            getMapper().map(Book.class, Author.class);
            final Book book = new Book();
            book.author = new Author();
            getDs().save(book);
        });
    }

    @Test
    public void testUuidId() {
        getMapper().map(Collections.singletonList(ContainsUuidId.class));
        final ContainsUuidId uuidId = new ContainsUuidId();
        final UUID before = uuidId.id;
        getDs().save(uuidId);
        final ContainsUuidId loaded = getDs().find(ContainsUuidId.class).filter(eq("_id", before)).first();
        assertNotNull(loaded);
        assertNotNull(loaded.id);
        assertEquals(before, loaded.id);
    }

    @Test
    public void testUUID() {
        getMapper().map(ContainsUUID.class);
        final ContainsUUID uuid = new ContainsUUID();
        final UUID before = uuid.uuid;
        getDs().save(uuid);
        final ContainsUUID loaded = getDs().find(ContainsUUID.class).iterator(new FindOptions().limit(1))
                                           .next();
        assertNotNull(loaded);
        assertNotNull(loaded.id);
        assertNotNull(loaded.uuid);
        assertEquals(before, loaded.uuid);
    }

    @Test
    public void childMapping() {
        List<MappedClass> list = getMapper().map(User.class, BannedUser.class);

        assertEquals("users", list.get(0).getCollectionName());
        assertEquals("banned", list.get(1).getCollectionName());
    }

    private void validateField(final List<MappedField> fields, final String mapped, final String java) {
        assertNotNull(fields.stream().filter(f -> f.getMappedFieldName().equals(mapped)
                                                  && f.getJavaFieldName().equals(java)),
            mapped);
    }

    public enum Enum1 {
        A,
        B
    }

    @Embedded
    private interface Foo {
    }

    @Entity
    public abstract static class BaseEntity {
        @Id
        private ObjectId id;

        public String getId() {
            return id.toString();
        }

        public void setId(final String id) {
            this.id = new ObjectId(id);
        }
    }

    @Entity
    public static class ConstructorBased {
        @Id
        private final ObjectId id;
        private final String name;
        private final MorphiaReference<ContainsFinalField> reference;

        @Constructor
        public ConstructorBased(@Name("id") final ObjectId id,
                                @Name("name") final String name,
                                @Name("reference") final MorphiaReference<ContainsFinalField> reference) {
            this.id = id;
            this.name = name;
            this.reference = reference;
        }

        @Override
        public int hashCode() {
            int result = id != null ? id.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (reference != null ? reference.hashCode() : 0);
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ConstructorBased)) {
                return false;
            }

            final ConstructorBased that = (ConstructorBased) o;

            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }
            if (name != null ? !name.equals(that.name) : that.name != null) {
                return false;
            }
            return reference != null ? reference.equals(that.reference) : that.reference == null;
        }
    }

    @Entity
    private static class ContainsByteArray {
        private final byte[] bytes = "Scott".getBytes();
        @Id
        private ObjectId id;
    }

    @Entity
    private static final class ContainsCollection {
        private final Collection<String> coll = new ArrayList<>();
        @Id
        private ObjectId id;

        private ContainsCollection() {
            coll.add("hi");
            coll.add("Scott");
        }
    }

    @Entity
    private static class ContainsEmbeddedArray {
        @Id
        private final ObjectId id = new ObjectId();
        private RenamedEmbedded[] res;
    }

    @Entity
    private static class ContainsEmbeddedEntity {
        @Id
        private final ObjectId id = new ObjectId();
        private ContainsIntegerList cil = new ContainsIntegerList();
    }

    @Entity
    private static class ContainsEnum1KeyMap {
        private final Map<Enum1, String> values = new HashMap<>();
        private final Map<Enum1, String> embeddedValues = new HashMap<>();
        @Id
        private ObjectId id;
    }

    @Entity
    private static class ContainsFinalField {
        private final String name;
        @Id
        private ObjectId id;

        protected ContainsFinalField() {
            name = "foo";
        }

        ContainsFinalField(final String name) {
            this.name = name;
        }
    }

    @Entity
    private static class ContainsIntKeyMap {
        private final Map<Integer, String> values = new HashMap<>();
        @Id
        private ObjectId id;
    }

    @Entity
    private static class ContainsIntKeySetStringMap {
        private final Map<Integer, Set<String>> values = new HashMap<>();
        @Id
        private ObjectId id;
    }

    @Entity(value = "cil", useDiscriminator = false)
    private static class ContainsIntegerList {
        @Id
        private ObjectId id;
        private List<Integer> intList = new ArrayList<>();
    }

    @Entity(value = "cil", useDiscriminator = false)
    private static class ContainsIntegerListNew {
        @Id
        private ObjectId id;
        @AlsoLoad("intList")
        private final List<Integer> integers = new ArrayList<>();
    }

    @Entity
    private static class ContainsLongAndStringArray {
        @Id
        private ObjectId id;
        private Long[] longs = {0L, 1L, 2L};
        private String[] strings = {"Scott", "Rocks"};
    }

    @Entity
    private static class ContainsMapWithEmbeddedInterface {
        private final Map<String, Foo> embeddedValues = new HashMap<>();
        @Id
        private ObjectId id;
    }

    @Entity
    private static class ContainsObjectIdKeyMap {
        private final Map<ObjectId, String> values = new HashMap<>();
        @Id
        private ObjectId id;
    }

    @Entity
    private static class ContainsPrimitiveMap {
        private final Map<String, Long> embeddedValues = new HashMap<>();
        private final Map<String, Long> values = new HashMap<>();
        @Id
        private ObjectId id;
    }

    @Entity(useDiscriminator = false)
    private static class ContainsUUID {
        private final UUID uuid = UUID.randomUUID();
        @Id
        private ObjectId id;
    }

    @Entity(useDiscriminator = false)
    private static class ContainsUuidId {
        @Id
        private final UUID id = UUID.randomUUID();
    }

    private static class Foo1 implements Foo {
        private String s;
    }

    private static class Foo2 implements Foo {
        private int i;
    }

    @Entity
    private static class HasFinalFieldId {
        @Id
        private final long id;
        private final String name = "some string";

        protected HasFinalFieldId() {
            id = -1;
        }

        HasFinalFieldId(final long id) {
            this.id = id;
        }
    }

    @Entity(value = "Normal", useDiscriminator = false)
    static class Normal {
        @Id
        private final ObjectId id = new ObjectId();
        private String name;

        Normal(final String name) {
            this.name = name;
        }

        protected Normal() {
        }
    }

    @Entity(value = "Normal", useDiscriminator = false)
    private static class NormalWithLoadOnly {
        @Id
        private final ObjectId id = new ObjectId();
        @LoadOnly
        private final String name = "never";
    }

    @Embedded(useDiscriminator = false)
    private static class RenamedEmbedded {
        private String name;
    }

    private static class UnannotatedEmbedded {
        private String field;
        private Long number;
    }
}
