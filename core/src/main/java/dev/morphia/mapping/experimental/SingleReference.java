package dev.morphia.mapping.experimental;

import com.mongodb.DBRef;
import dev.morphia.Datastore;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.codec.pojo.FieldModel;
import dev.morphia.mapping.lazy.proxy.ReferenceException;
import dev.morphia.query.Query;
import dev.morphia.sofia.Sofia;
import org.bson.Document;

import java.util.List;

import static dev.morphia.query.experimental.filters.Filters.eq;

/**
 * @param <T>
 * @morphia.internal
 */
@SuppressWarnings("unchecked")
public class SingleReference<T> extends MorphiaReference<T> {
    private MappedClass mappedClass;
    private Object id;
    private T value;

    /**
     * @param datastore   the datastore to use
     * @param mappedClass the entity's mapped class
     * @param id          the ID value
     * @morphia.internal
     */
    public SingleReference(Datastore datastore, MappedClass mappedClass, Object id) {
        super(datastore);
        this.mappedClass = mappedClass;
        this.id = id;
        if (mappedClass.getType().isInstance(id)) {
            value = (T) id;
            this.id = mappedClass.getIdField().getValue(value);
            resolve();
        }

    }

    SingleReference(T value) {
        this.value = value;
    }

    /**
     * Decodes a document in to an entity
     *
     * @param datastore   the datastore
     * @param mapper      the mapper
     * @param mappedField the MappedField
     * @param paramType   the type of the underlying entity
     * @param document    the Document to decode
     * @return the entity
     */
    public static MorphiaReference<?> decode(Datastore datastore,
                                             Mapper mapper,
                                             FieldModel mappedField,
                                             Class<?> paramType, Document document) {
        final MappedClass mappedClass = mapper.getMappedClass(paramType);
        Object id = document.get(mappedField.getMappedName());

        return new SingleReference<>(datastore, mappedClass, id);
    }

    MappedClass getMappedClass(Mapper mapper) {
        if (mappedClass == null) {
            mappedClass = mapper.getMappedClass(get().getClass());
        }

        return mappedClass;
    }

    @Override
    public T get() {
        if (!isResolved() && value == null && id != null) {
            value = (T) buildQuery().iterator().tryNext();
            if (value == null && !ignoreMissing()) {
                throw new ReferenceException(
                    Sofia.missingReferencedEntity(mappedClass.getType().getSimpleName()));
            }
            resolve();
        }
        return value;
    }

    @Override
    public Class<T> getType() {
        return (Class<T>) mappedClass.getType();
    }

    Query<?> buildQuery() {
        final Query<?> query;
        if (id instanceof DBRef) {
            final Class<?> clazz = getDatastore()
                                       .getMapper()
                                       .getClassFromCollection(((DBRef) id).getCollectionName());
            query = getDatastore().find(clazz)
                                  .filter(eq("_id", ((DBRef) id).getId()));
        } else {
            query = getDatastore().find(mappedClass.getType())
                                  .filter(eq("_id", id));
        }
        return query;
    }


    @Override
    public List<Object> getIds() {
        return List.of(id);
    }

    @Override
    Object getId(Mapper mapper, Datastore datastore, MappedClass fieldClass) {
        if (id == null) {
            MappedClass mappedClass = getMappedClass(mapper);
            id = mappedClass.getIdField().getValue(get());
            if (!mappedClass.equals(fieldClass)) {
                id = new DBRef(mappedClass.getCollectionName(), id);
            }
        }
        return id;
    }

    @Override
    public Object encode(Mapper mapper, Object value, FieldModel optionalExtraInfo) {
        if (isResolved()) {
            return wrapId(mapper, optionalExtraInfo, get());
        } else {
            return null;
        }

    }

}
