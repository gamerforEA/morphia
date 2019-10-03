package dev.morphia.annotations;

import dev.morphia.mapping.codec.PropertyCodec;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Documented
@Inherited
public @interface Handler {
    Class<? extends PropertyCodec> value();
}