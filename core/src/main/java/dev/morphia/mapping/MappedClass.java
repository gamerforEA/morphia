/*
 * Copyright 2008-2016 MongoDB, Inc.
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
package dev.morphia.mapping;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationOptions;
import dev.morphia.annotations.Embedded;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Validation;
import dev.morphia.annotations.Version;
import dev.morphia.mapping.codec.pojo.EntityModel;
import dev.morphia.mapping.codec.pojo.FieldModel;
import dev.morphia.mapping.validation.MappingValidator;
import dev.morphia.sofia.Sofia;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.String.format;
import static org.bson.Document.parse;

/**
 * @morphia.internal
 */
public class MappedClass {
    private static final Logger LOG = LoggerFactory.getLogger(MappedClass.class);

    /**
     * a list of the fields to map
     */
    private final List<FieldModel> fields = new ArrayList<>();
    /**
     * the type we are mapping to/from
     */
    private final EntityModel entityModel;
    private final List<MappedClass> subtypes = new ArrayList<>();
    /**
     * special fields representing the Key of the object
     */
    private FieldModel idField;
    private MappedClass superClass;

    /**
     * Creates a MappedClass instance
     *
     * @param entityModel the ClassModel
     * @param mapper      the Mapper to use
     */
    public MappedClass(EntityModel entityModel, Mapper mapper) {
        this.entityModel = entityModel;

        discover(mapper);

        if (LOG.isDebugEnabled()) {
            LOG.debug("MappedClass done: " + this);
        }
    }

    /**
     * Call the lifecycle methods
     *
     * @param event    the lifecycle annotation
     * @param entity   the entity to process
     * @param document the document to use
     * @param mapper   the Mapper to use
     */
    public void callLifecycleMethods(Class<? extends Annotation> event, Object entity, Document document,
                                     Mapper mapper) {
        entityModel.callLifecycleMethods(event, entity, document, mapper);
    }

    /**
     * Enables any document validation defined on the class
     *
     * @param database the database to update
     * @morphia.internal
     */
    public void enableDocumentValidation(MongoDatabase database) {
        Validation validation = getAnnotation(Validation.class);
        if (validation != null) {
            String collectionName = getCollectionName();
            try {
                database.runCommand(new Document("collMod", collectionName)
                                        .append("validator", parse(validation.value()))
                                        .append("validationLevel", validation.level().getValue())
                                        .append("validationAction", validation.action().getValue()));
            } catch (MongoCommandException e) {
                if (e.getCode() == 26) {
                    database.createCollection(collectionName,
                        new CreateCollectionOptions()
                            .validationOptions(new ValidationOptions()
                                                   .validator(parse(validation.value()))
                                                   .validationLevel(validation.level())
                                                   .validationAction(validation.action())));
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Looks for an annotation of the type given
     *
     * @param clazz the type to search for
     * @param <T>   the annotation type
     * @return the instance if it was found or null
     * @morphia.internal
     */
    public <T extends Annotation> T getAnnotation(Class<T> clazz) {
        return entityModel.getAnnotation(clazz);
    }

    /**
     * @return the collName
     */
    public String getCollectionName() {
        return entityModel.getCollectionName();
    }

    /**
     * @return the embeddedAn
     */
    public Embedded getEmbeddedAnnotation() {
        return entityModel.getAnnotation(Embedded.class);
    }

    /**
     * @return the entityAn
     */
    public Entity getEntityAnnotation() {
        return entityModel.getAnnotation(Entity.class);
    }

    /**
     * @return the underlying model of the type
     */
    public EntityModel getEntityModel() {
        return entityModel;
    }

    /**
     * Returns fields annotated with the clazz
     *
     * @param clazz The Annotation to find.
     * @return the list of fields
     */
    public List<FieldModel> getFields(Class<? extends Annotation> clazz) {
        final List<FieldModel> results = new ArrayList<>();
        for (FieldModel fieldModel : fields) {
            if (fieldModel.hasAnnotation(clazz)) {
                results.add(fieldModel);
            }
        }
        return results;
    }

    /**
     * @return the fields
     */
    public List<FieldModel> getFields() {
        return fields;
    }

    /**
     * @return the idField
     */
    public FieldModel getIdField() {
        return idField;
    }

    /**
     * Returns the MappedField by the name that it will stored in mongodb as
     *
     * @param storedName the name to search for
     * @return true if that mapped field name is found
     */
    public FieldModel getMappedField(String storedName) {
        return fields.stream()
                     .filter(mappedField -> mappedField.getMappedName().equals(storedName)
                                            || mappedField.getName().equals(storedName))
                     .findFirst()
                     .orElse(null);
    }

    /**
     * Returns MappedField for a given java field name on the this MappedClass
     *
     * @param name the Java field name to search for
     * @return the MappedField for the named Java field
     */
    public FieldModel getMappedFieldByJavaField(String name) {
        for (FieldModel fieldModel : fields) {
            if (name.equals(fieldModel.getName())) {
                return fieldModel;
            }
        }

        return null;
    }

    /**
     * @return the MappedClasses for all the known subtypes
     */
    public List<MappedClass> getSubtypes() {
        return Collections.unmodifiableList(subtypes);
    }

    /**
     * This is an internal method subject to change without notice.
     *
     * @return the parent class of this type if there is one null otherwise
     * @since 1.3
     */
    public MappedClass getSuperClass() {
        return superClass;
    }

    /**
     * @return the clazz
     */
    public Class<?> getType() {
        return entityModel.getType();
    }

    /**
     * @return the ID field for the class
     */
    public FieldModel getVersionField() {
        List<FieldModel> fields = getFields(Version.class);
        return fields.isEmpty() ? null : fields.get(0);
    }

    /**
     * Checks if this mapped type has the given lifecycle event defined
     *
     * @param type the event type
     * @return true if this annotation has been found
     */
    public boolean hasLifecycle(Class<? extends Annotation> type) {
        return entityModel.hasLifecycle(type);
    }

    @Override
    public int hashCode() {
        return getType().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final MappedClass that = (MappedClass) o;

        return getType().equals(that.getType());

    }

    @Override
    public String toString() {
        return format("%s[%s]", getType().getSimpleName(), getCollectionName());
    }

    /**
     * This is an internal method subject to change without notice.
     *
     * @return true if the MappedClass is abstract
     * @since 1.3
     */
    public boolean isAbstract() {
        return Modifier.isAbstract(getType().getModifiers());
    }

    /**
     * @return true if the MappedClass is an interface
     */
    public boolean isInterface() {
        return getType().isInterface();
    }

    /**
     * Update mappings based on fields/annotations.
     */
    public void update() {
        final List<FieldModel> fields = getFields(Id.class);
        if (fields != null && !fields.isEmpty()) {
            idField = fields.get(0);
        }
    }

    /**
     * Validates this MappedClass
     *
     * @param mapper the Mapper to use for validation
     */
    public void validate(Mapper mapper) {
        new MappingValidator(entityModel.getInstanceCreatorFactory().create())
            .validate(mapper, this);
    }

    private void addSubtype(MappedClass mappedClass) {
        subtypes.add(mappedClass);
    }

    /**
     * Discovers interesting (that we care about) things about the class.
     */
    private void discover(Mapper mapper) {
        Class<?> superclass = getType().getSuperclass();
        if (superclass != null && !superclass.equals(Object.class)) {
            superClass = mapper.getMappedClass(superclass);
            if (superClass != null) {
                superClass.addSubtype(this);
            }
        }

        for (Class<?> aClass : getType().getInterfaces()) {
            final MappedClass mappedClass = mapper.getMappedClass(aClass);
            if (mappedClass != null) {
                mappedClass.addSubtype(this);
            }
        }

        discoverFields();

        update();
    }

    private void discoverFields() {
        entityModel.getFieldModels().forEach(field -> {
            if (!field.isTransient()) {
                fields.add(field);
            } else {
                Sofia.logIgnoringTransientField(field.getFullName());
            }
        });
    }

}
