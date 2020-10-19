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

import com.mongodb.DBRef;
import dev.morphia.Key;
import dev.morphia.annotations.AlsoLoad;
import dev.morphia.annotations.Reference;
import dev.morphia.annotations.Transient;
import dev.morphia.mapping.codec.Conversions;
import dev.morphia.mapping.codec.pojo.FieldModel;
import dev.morphia.mapping.codec.references.MorphiaProxy;
import morphia.org.bson.codecs.pojo.TypeData;
import org.bson.Document;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Arrays.asList;

/**
 * @morphia.internal
 */
@SuppressWarnings("unchecked")
public class MappedField {
    private final Map<Class<? extends Annotation>, Annotation> annotations;
    private MappedClass declaringClass;
    private FieldModel fieldModel;

    private List<String> loadNames; // List of stored names in order of trying, contains nameToStore and potential aliases

    MappedField(final MappedClass declaringClass, final FieldModel f) {
        this.declaringClass = declaringClass;
        fieldModel = f;

        final List<Annotation> list = fieldModel.getAnnotations();
        this.annotations = list.stream()
                               .map(ann -> this.<Class<? extends Annotation>, Annotation>map(ann.annotationType(), ann))
                               .reduce(new HashMap<>(), (map1, update) -> {
                                   map1.putAll(update);
                                   return map1;
                               });
        discoverNames();
    }

    /**
     * @return the name of the field's (key)name for mongodb
     */
    public String getMappedFieldName() {
        return fieldModel.getMappedName();
    }

    /**
     * Indicates whether the annotation is present in the mapping (does not check the java field annotations, just the ones discovered)
     *
     * @param ann the annotation to search for
     * @return true if the annotation was found
     */
    public boolean hasAnnotation(final Class ann) {
        return annotations.containsKey(ann);
    }

    /**
     * @return the declaring class of the java field
     */
    public MappedClass getDeclaringClass() {
        return declaringClass;
    }

    /**
     * @return the underlying java field
     */
    public Field getField() {
        return fieldModel.getField();
    }

    /**
     * @param document the Document get the value from
     * @return the value from first mapping of this field
     */
    public Object getDocumentValue(final Document document) {
        return document.get(getFirstFieldName(document));
    }

    /**
     * @return the name of the field's (key)name for mongodb, in order of loading.
     */
    public List<String> getLoadNames() {
        return loadNames;
    }

    /**
     * @return the full name of the class plus java field name
     */
    public String getFullName() {
        Field field = fieldModel.getField();
        return String.format("%s#%s", field.getDeclaringClass().getName(), field.getName());
    }

    /**
     * Gets the value of the field mapped on the instance given.
     *
     * @param instance the instance to use
     * @return the value stored in the java field
     */
    public Object getFieldValue(final Object instance) {
        try {
            Object target = instance;
            if (target instanceof MorphiaProxy) {
                target = ((MorphiaProxy) instance).unwrap();
            }
            return fieldModel.getField().get(target);
        } catch (ReflectiveOperationException e) {
            throw new MappingException(e.getMessage(), e);
        }
    }

    /**
     * @return the name of the java field, as declared on the class
     */
    public String getJavaFieldName() {
        return fieldModel.getName();
    }

    /**
     * If the java field is a list/array/map then the sub-type T is returned (ex. List&lt;T&gt;, T[], Map&lt;?,T&gt;)
     *
     * @return the parameterized type of the field
     */
    public Class getSpecializedType() {
        Class specializedType;
        if (getType().isArray()) {
            specializedType = getType().getComponentType();
        } else {
            final List<TypeData<?>> typeParameters = fieldModel.getTypeData().getTypeParameters();
            specializedType = !typeParameters.isEmpty() ? typeParameters.get(0).getType() : null;
        }

        return specializedType;
    }

    /**
     * @return the type of the underlying java field
     */
    public Class getType() {
        return fieldModel.getTypeData().getType();
    }

    /**
     * @return true if this field is marked as transient
     */
    public boolean isTransient() {
        return !hasAnnotation(Transient.class)
               && !hasAnnotation(java.beans.Transient.class)
               && Modifier.isTransient(getType().getModifiers());
    }

    /**
     * @return true if this field is a container type such as a List, Map, Set, or array
     */
    public boolean isMultipleValues() {
        return !isScalarValue();
    }

    /**
     * @return true if this field is not a container type such as a List, Map, Set, or array
     */
    public boolean isScalarValue() {
        return !isMap() && !isArray() && !isCollection();
    }

    /**
     * @return true if the MappedField is a Map
     */
    public boolean isMap() {
        return Map.class.isAssignableFrom(getTypeData().getType());
    }

    /**
     * @return true if the MappedField is an array
     */
    public boolean isArray() {
        return getType().isArray();
    }

    /**
     * @return the field's type data
     */
    public TypeData<?> getTypeData() {
        return fieldModel.getTypeData();
    }

    /**
     * @return true if this field is a reference to a foreign document
     * @see Reference
     * @see Key
     * @see DBRef
     */
    public boolean isReference() {
        return hasAnnotation(Reference.class) || Key.class == getType() || DBRef.class == getType();
    }

    /**
     * @param clazz the annotation to search for
     * @param <T>   the type of the annotation
     * @return the annotation instance if it exists on this field
     */
    @SuppressWarnings("unchecked")
    public <T extends Annotation> T getAnnotation(final Class<T> clazz) {
        return (T) annotations.get(clazz);
    }

    /**
     * @return true if the MappedField is a Set
     */
    public boolean isSet() {
        return Set.class.isAssignableFrom(getTypeData().getType());
    }

    /**
     * Sets the value for the java field
     *
     * @param instance the instance to update
     * @param value    the value to set
     */
    public void setFieldValue(final Object instance, final Object value) {
        try {
            final Field field = fieldModel.getField();
            field.set(instance, Conversions.convert(value, field.getType()));
        } catch (IllegalAccessException e) {
            throw new MappingException(e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return format("%s : %s", getMappedFieldName(), fieldModel.getTypeData().toString());
    }

    /**
     * Gets the parameterized type of a List or the key type of a Map, e.g.
     *
     * @return the unwrapped type
     */
    public Class getNormalizedType() {
        return fieldModel.getNormalizedType();
    }

    /**
     * @return the underlying field model
     */
    public FieldModel getFieldModel() {
        return fieldModel;
    }

    protected List<String> inferLoadNames() {
        final AlsoLoad al = (AlsoLoad) annotations.get(AlsoLoad.class);
        if (al != null && al.value() != null && al.value().length > 0) {
            final List<String> names = new ArrayList<>();
            names.add(getMappedFieldName());
            names.addAll(asList(al.value()));
            return names;
        } else {
            return Collections.singletonList(getMappedFieldName());
        }
    }

    private <K, V> Map<K, V> map(final K key, final V value) {
        final HashMap<K, V> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private void discoverNames() {
        loadNames = inferLoadNames();
    }

    private String getFirstFieldName(final Document document) {
        List<String> names = new ArrayList<>();
        names.add(getMappedFieldName());
        names.addAll(getLoadNames());
        List<String> list = names.stream()
                                 .filter(name -> document.containsKey(name))
                                 .collect(Collectors.toList());
        if (list.size() > 1) {
            throw new MappingException(format("Found more than one field mapping for ", getFullName()));
        }
        return list.get(0);
    }

    private boolean isCollection() {
        return Collection.class.isAssignableFrom(getTypeData().getType());
    }
}
