package dev.morphia;


import dev.morphia.annotations.Embedded;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.PostLoad;
import dev.morphia.annotations.Property;
import dev.morphia.annotations.Reference;
import dev.morphia.mapping.EmbeddedMappingTest.AnotherNested;
import dev.morphia.mapping.EmbeddedMappingTest.Nested;
import dev.morphia.mapping.EmbeddedMappingTest.NestedImpl;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.lazy.LazyFeatureDependencies;
import dev.morphia.testmodel.Rectangle;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.Serializable;
import java.util.List;

import static dev.morphia.query.experimental.filters.Filters.eq;
import static java.util.Arrays.asList;


public class TestMapper extends TestBase {

    @Test
    public void annotations() {
        List<MappedClass> classes = getMapper().map(java.util.Collections.singletonList(Rectangle.class));
        MappedClass mappedClass = classes.get(0);
        Entity annotation = mappedClass.getAnnotation(Entity.class);
        Assert.assertEquals("shapes", mappedClass.getCollectionName());
        Assert.assertNotNull(annotation.toString(), annotation);
    }

    @Test
    public void serializableId() {
        final CustomId cId = new CustomId();
        cId.id = new ObjectId();
        cId.type = "banker";
        final UsesCustomIdObject object = new UsesCustomIdObject();
        object.id = cId;
        object.text = "hllo";
        getDs().save(object);
    }

    @Test
    @Category(Reference.class)
    @Ignore("entity caching needs to be implemented")
    public void singleLookup() {
        A.loadCount = 0;
        final A a = new A();
        HoldsMultipleA holder = new HoldsMultipleA();
        holder.a1 = a;
        holder.a2 = a;
        getDs().save(asList(a, holder));
        holder = getDs().find(HoldsMultipleA.class).filter(eq("_id", holder.id)).first();
        Assert.assertEquals(1, A.loadCount);
        Assert.assertTrue(holder.a1 == holder.a2);
    }

    @Test
    @Category(Reference.class)
    public void singleProxy() {
        Assume.assumeTrue(LazyFeatureDependencies.assertProxyClassesPresent());

        A.loadCount = 0;
        final A a = new A();
        HoldsMultipleALazily holder = new HoldsMultipleALazily();
        holder.a1 = a;
        holder.a2 = a;
        holder.a3 = a;
        getDs().save(asList(a, holder));
        Assert.assertEquals(0, A.loadCount);
        holder = getDs().find(HoldsMultipleALazily.class).filter(eq("_id", holder.id)).first();
        Assert.assertNotNull(holder.a2);
        Assert.assertEquals(1, A.loadCount);
        Assert.assertFalse(holder.a1 == holder.a2);
        // FIXME currently not guaranteed:
        // Assert.assertTrue(holder.a1 == holder.a3);

        // A.loadCount=0;
        // Assert.assertEquals(holder.a1.getId(), holder.a2.getId());

    }

    @Test
    public void subTypes() {
        getMapper().map(NestedImpl.class, AnotherNested.class);

        Mapper mapper = getMapper();
        List<MappedClass> subTypes = mapper.getSubTypes(mapper.getMappedClass(Nested.class));
        Assert.assertTrue(subTypes.contains(mapper.getMappedClass(NestedImpl.class)));
        Assert.assertTrue(subTypes.contains(mapper.getMappedClass(AnotherNested.class)));
    }

    @Entity
    public static class A {
        private static int loadCount;
        @Id
        private ObjectId id;

        @PostLoad
        protected void postConstruct() {
            if (loadCount > 1) {
                throw new RuntimeException();
            }

            loadCount++;
        }

        String getId() {
            return id.toString();
        }
    }

    @Entity("holders")
    public static class HoldsMultipleA {
        @Id
        private ObjectId id;
        @Reference
        private A a1;
        @Reference
        private A a2;
    }

    @Entity("holders")
    public static class HoldsMultipleALazily {
        @Id
        private ObjectId id;
        @Reference(lazy = true)
        private A a1;
        @Reference
        private A a2;
        @Reference(lazy = true)
        private A a3;
    }

    @Embedded
    public static class CustomId implements Serializable {

        @Property("v")
        private ObjectId id;
        @Property("t")
        private String type;

        public ObjectId getId() {
            return id;
        }

        public void setId(final ObjectId id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(final String type) {
            this.type = type;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof CustomId)) {
                return false;
            }
            final CustomId other = (CustomId) obj;
            if (id == null) {
                if (other.id != null) {
                    return false;
                }
            } else if (!id.equals(other.id)) {
                return false;
            }
            if (type == null) {
                return other.type == null;
            } else return type.equals(other.type);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("CustomId [");
            if (id != null) {
                builder.append("id=").append(id).append(", ");
            }
            if (type != null) {
                builder.append("type=").append(type);
            }
            builder.append("]");
            return builder.toString();
        }
    }

    @Entity
    public static class UsesCustomIdObject {
        @Id
        private CustomId id;
        private String text;

        public CustomId getId() {
            return id;
        }

        public void setId(final CustomId id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }
    }

}
