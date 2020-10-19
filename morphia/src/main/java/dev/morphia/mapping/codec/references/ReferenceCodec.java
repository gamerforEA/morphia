package dev.morphia.mapping.codec.references;

import com.mongodb.DBRef;
import com.mongodb.client.MongoCollection;
import dev.morphia.Datastore;
import dev.morphia.Key;
import dev.morphia.annotations.Reference;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.MappingException;
import dev.morphia.mapping.codec.Conversions;
import dev.morphia.mapping.codec.DocumentWriter;
import dev.morphia.mapping.codec.PropertyCodec;
import dev.morphia.mapping.codec.pojo.PropertyHandler;
import dev.morphia.mapping.codec.reader.DocumentReader;
import dev.morphia.mapping.experimental.ListReference;
import dev.morphia.mapping.experimental.MapReference;
import dev.morphia.mapping.experimental.MorphiaReference;
import dev.morphia.mapping.experimental.SetReference;
import dev.morphia.mapping.experimental.SingleReference;
import dev.morphia.mapping.lazy.proxy.ReferenceException;
import dev.morphia.sofia.Sofia;
import dev.morphia.utils.ReflectionUtils;
import morphia.org.bson.codecs.pojo.TypeData;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType.Loaded;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.BsonTypeClassMap;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecConfigurationException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @morphia.internal
 */
@SuppressWarnings("unchecked")
public class ReferenceCodec extends PropertyCodec<Object> implements PropertyHandler {
    private final Reference annotation;
    private BsonTypeClassMap bsonTypeClassMap = new BsonTypeClassMap();

    /**
     * Creates a codec
     *
     * @param datastore the datastore to use
     * @param field     the reference field
     * @param typeData  the field type data
     */
    public ReferenceCodec(final Datastore datastore, final Field field, final TypeData typeData) {
        super(datastore, field, typeData);
        annotation = field.getAnnotation(Reference.class);
    }

    /**
     * Decodes an ID value
     *
     * @param decode         the value to decode
     * @param mapper         the mapper to use
     * @param decoderContext the decoder context
     * @return the decoded value
     */
    public static Object processId(final Object decode, final Mapper mapper, final DecoderContext decoderContext) {
        Object processed = decode;
        if (processed instanceof Iterable) {
            Iterable iterable = (Iterable) processed;
            List ids = new ArrayList();
            for (final Object o : iterable) {
                ids.add(processId(o, mapper, decoderContext));
            }
            processed = ids;
        } else if (processed instanceof Document) {
            Document document = (Document) processed;
            if (document.containsKey("$ref")) {
                Object id = document.get("$id");
                if (id instanceof Document) {
                    id = mapper.getCodecRegistry()
                               .get(Object.class)
                               .decode(new DocumentReader((Document) id), decoderContext);
                }
                processed = new DBRef((String) document.get("$ref"), id);
            } else if (document.containsKey(mapper.getOptions().getDiscriminatorKey())) {
                try {
                    processed = mapper.getCodecRegistry()
                                      .get(mapper.getClass(document))
                                      .decode(new DocumentReader(document), decoderContext);
                } catch (CodecConfigurationException e) {
                    throw new MappingException(Sofia.cannotFindTypeInDocument(), e);
                }

            }
        }
        return processed;
    }

    /**
     * Encodes a value
     *
     * @param mapper           the mapper to use
     * @param datastore        the datastore to use
     * @param value            the value to encode
     * @param fieldMappedClass the mapped class of the field type
     * @return the encoded value
     * @morphia.internal
     */
    public static Object encodeId(final Mapper mapper, final Datastore datastore, final Object value, final MappedClass fieldMappedClass) {
        Object idValue;
        MongoCollection<?> collection = null;
        if (value instanceof Key) {
            idValue = ((Key) value).getId();
        } else {
            idValue = mapper.getId(value);
            if (idValue == null) {
                return !mapper.isMappable(value.getClass()) ? value : null;
            }
            collection = mapper.getCollection(value.getClass());
        }

        String valueCollectionName = collection != null ? collection.getNamespace().getCollectionName() : null;
        String fieldCollectionName = fieldMappedClass.getCollectionName();

        Reference annotation = fieldMappedClass.getAnnotation(Reference.class);
        if (annotation != null && !annotation.idOnly()
            || valueCollectionName != null && !valueCollectionName.equals(fieldCollectionName)) {
            if (idValue == null) {
                throw new MappingException("The ID value can not be null");
            }
            idValue = new DBRef(valueCollectionName, idValue);
        }
        return idValue;
    }

    @Override
    public Object decode(final BsonReader reader, final DecoderContext decoderContext) {
        Object decode = getDatastore().getMapper().getCodecRegistry()
                                      .get(bsonTypeClassMap.get(reader.getCurrentBsonType()))
                                      .decode(reader, decoderContext);
        decode = processId(decode, getDatastore().getMapper(), decoderContext);
        return fetch(decode);
    }

    @Override
    public Object encode(final Object value) {
        try {
            DocumentWriter writer = new DocumentWriter();
            writer.writeStartDocument();
            writer.writeName("ref");
            encode(writer, value, EncoderContext.builder().build());
            writer.writeEndDocument();
            return writer.getDocument().get("ref");
        } catch (ReferenceException e) {
            return value;
        }
    }

    @Override
    public void encode(final BsonWriter writer, final Object instance, final EncoderContext encoderContext) {
        Object idValue = collectIdValues(instance);

        if (idValue != null) {
            final Codec codec = getDatastore().getMapper().getCodecRegistry().get(idValue.getClass());
            codec.encode(writer, idValue, encoderContext);
        } else {
            throw new ReferenceException(Sofia.noIdForReference());
        }
    }

    @Override
    public Class getEncoderClass() {
        TypeData type = getTypeData();
        List typeParameters = type.getTypeParameters();
        if (!typeParameters.isEmpty()) {
            type = (TypeData) typeParameters.get(typeParameters.size() - 1);
        }
        return type.getType();
    }

    private Object collectIdValues(final Object value) {
        if (value instanceof Collection) {
            List ids = new ArrayList(((Collection) value).size());
            for (Object o : (Collection) value) {
                ids.add(collectIdValues(o));
            }
            return ids;
        } else if (value instanceof Map) {
            final LinkedHashMap ids = new LinkedHashMap();
            Map<Object, Object> map = (Map<Object, Object>) value;
            for (final Map.Entry<Object, Object> o : map.entrySet()) {
                ids.put(o.getKey().toString(), collectIdValues(o.getValue()));
            }
            return ids;
        } else if (value.getClass().isArray()) {
            List ids = new ArrayList(((Object[]) value).length);
            for (Object o : (Object[]) value) {
                ids.add(collectIdValues(o));
            }
            return ids;
        } else {
            return encodeId(getDatastore().getMapper(), getDatastore(), value, getFieldMappedClass());
        }
    }

    private <T> T createProxy(final MorphiaReference reference) {
        ReferenceProxy referenceProxy = new ReferenceProxy(reference);
        try {
            Class<?> type = getField().getType();
            String name = (ReflectionUtils.getPackageName(type).startsWith("java") ? type.getSimpleName() : type.getName()) + "$$Proxy";
            return ((Loaded<T>) new ByteBuddy()
                                    .subclass(type)
                                    .implement(MorphiaProxy.class)
                                    .name(name)

                                    .invokable(ElementMatchers.isDeclaredBy(type))
                                    .intercept(InvocationHandlerAdapter.of(referenceProxy))

                                    .method(ElementMatchers.isDeclaredBy(MorphiaProxy.class))
                                    .intercept(InvocationHandlerAdapter.of(referenceProxy))

                                    .make()
                                    .load(Thread.currentThread().getContextClassLoader(), Default.WRAPPER))
                       .getLoaded()
                       .getDeclaredConstructor()
                       .newInstance();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw new MappingException(e.getMessage(), e);
        }
    }

    private Object fetch(final Object value) {
        MorphiaReference reference;
        final Class<?> type = getField().getType();
        if (List.class.isAssignableFrom(type)) {
            reference = readList((List) value);
        } else if (Map.class.isAssignableFrom(type)) {
            reference = readMap((Map<Object, Object>) value);
        } else if (Set.class.isAssignableFrom(type)) {
            reference = readSet((List) value);
        } else if (type.isArray()) {
            reference = readList((List) value);
        } else if (value instanceof Document) {
            reference = readDocument((Document) value);
        } else {
            reference = readSingle(value);
        }
        reference.ignoreMissing(annotation.ignoreMissing());

        return !annotation.lazy() ? reference.get() : createProxy(reference);
    }

    MorphiaReference readDocument(final Document value) {
        Mapper mapper = getDatastore().getMapper();
        final Object id = mapper.getCodecRegistry().get(Object.class)
                                .decode(new DocumentReader(value), DecoderContext.builder().build());
        return readSingle(id);
    }

    MorphiaReference readList(final List value) {
        return new ListReference(getDatastore(), getFieldMappedClass(), value);
    }

    MorphiaReference readMap(final Map<Object, Object> value) {
        final Object ids = new LinkedHashMap<>();
        Class keyType = ((TypeData) getTypeData().getTypeParameters().get(0)).getType();
        for (final Entry entry : value.entrySet()) {
            ((Map) ids).put(Conversions.convert(entry.getKey(), keyType), entry.getValue());
        }

        return new MapReference(getDatastore(), (Map<String, Object>) ids, getFieldMappedClass());
    }

    MorphiaReference readSet(final List value) {
        return new SetReference(getDatastore(), getFieldMappedClass(), value);
    }

    MorphiaReference readSingle(final Object value) {
        return new SingleReference(getDatastore(), getFieldMappedClass(), value);
    }
}

