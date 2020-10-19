package dev.morphia;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import dev.morphia.aggregation.experimental.AggregationOptions;
import dev.morphia.query.CountOptions;
import dev.morphia.query.FindAndDeleteOptions;
import dev.morphia.query.FindOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("SameParameterValue")
public class OptionsTest {
    @Test
    public void aggregationOptions() {
        scan(com.mongodb.AggregationOptions.class, AggregationOptions.class, false, java.util.Arrays.asList(ReadConcern.class, ReadPreference.class,
            WriteConcern.class));
    }

    @Test
    public void countOptions() {
        scan(com.mongodb.client.model.CountOptions.class, CountOptions.class, true, java.util.Arrays.asList(ReadConcern.class, ReadPreference.class));
    }

    @Test
    public void deleteOptions() {
        scan(com.mongodb.client.model.DeleteOptions.class, DeleteOptions.class, true, java.util.Collections.singletonList(WriteConcern.class));
    }

    @Test
    public void findAndDeleteOptions() {
        scan(FindOneAndDeleteOptions.class, FindAndDeleteOptions.class, true, java.util.Collections.singletonList(WriteConcern.class));
    }

    @Test
    public void findAndModifyOptions() {
        scan(FindOneAndUpdateOptions.class, ModifyOptions.class, true, java.util.Collections.singletonList(WriteConcern.class));
    }

    @Test
    public void findOptions() {
        beanScan(FindIterable.class, FindOptions.class, java.util.Arrays.asList("filter", "projection"));
    }

    @Test
    public void insertManyOptions() {
        scan(com.mongodb.client.model.InsertManyOptions.class, InsertManyOptions.class, false, java.util.Collections.singletonList(WriteConcern.class));
    }

    @Test
    public void insertOneOptions() {
        scan(com.mongodb.client.model.InsertOneOptions.class, InsertOneOptions.class, false, java.util.Collections.singletonList(WriteConcern.class));
    }

    @Test
    public void updateOptions() {
        scan(com.mongodb.client.model.UpdateOptions.class, UpdateOptions.class, true, java.util.Collections.singletonList(WriteConcern.class));
    }

    private void beanScan(final Class<?> driver, final Class<?> morphia, final List<String> filtered) {
        try {
            Method[] methods = driver.getDeclaredMethods();
            for (final Method method : methods) {
                if (!filtered.contains(method.getName()) && method.getAnnotation(Deprecated.class) == null) {
                    morphia.getDeclaredMethod(method.getName(), convert(method.getParameterTypes()));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void checkOverride(final Class<?> driverType, final Class<?> morphiaType, final Method method) throws NoSuchMethodException {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Method morphiaMethod = morphiaType.getMethod(method.getName(), parameterTypes);
        Assert.assertTrue(method.toString(), !method.getReturnType().equals(driverType)
                                             || morphiaMethod.getReturnType().equals(morphiaType));

        if (parameterTypes.equals(new Class[]{Bson.class})) {
            Assert.assertTrue(method.toString(), !method.getReturnType().equals(driverType)
                                                 || morphiaType.getMethod(method.getName(), Document.class)
                                                               .getReturnType().equals(morphiaType));

        }
    }

    private Class<?>[] convert(final Class<?>[] types) {
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(Bson.class)) {
                types[i] = Document.class;
            }
        }
        return types;
    }

    private void scan(final Class<?> driverType, final Class<?> morphiaType, final boolean subclass, List<Class<?>> localFields) {
        try {
            Method[] methods = driverType.getDeclaredMethods();
            Assert.assertEquals("Options class should be a subclass", subclass, driverType.equals(morphiaType.getSuperclass()));
            for (final Method method : methods) {
                if (method.getAnnotation(Deprecated.class) == null && !method.getName().equals("builder")) {
                    checkOverride(driverType, morphiaType, method);
                }
            }
            for (final Class<?> localField : localFields) {
                String name = localField.getSimpleName()
                                        .replaceAll("^get", "");
                name = name.substring(0, 1).toLowerCase() + name.substring(1);

                Field field = morphiaType.getDeclaredField(name);
                Assert.assertEquals(localField.getName(), field.getType(), localField);

                Method declaredMethod = morphiaType.getDeclaredMethod(name);
                Assert.assertEquals(declaredMethod.toString(), declaredMethod.getReturnType(), localField);

                declaredMethod = morphiaType.getDeclaredMethod(name, localField);
                Assert.assertEquals(declaredMethod.toString(), declaredMethod.getReturnType(), morphiaType);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
