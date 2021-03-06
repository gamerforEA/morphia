package dev.morphia.mapping.validation;


import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.codec.pojo.FieldModel;


/**
 * @author Uwe Schaefer, (us@thomas-daily.de)
 */
public class ConstraintViolation {
    private final MappedClass clazz;
    private final Class<? extends ClassConstraint> validator;
    private final String message;
    private final Level level;
    private FieldModel field;

    /**
     * Creates a violation instance to record invalid mapping metadata
     *
     * @param level     the severity of the violation
     * @param clazz     the errant class
     * @param field     the errant field
     * @param validator the constraint failed
     * @param message   the message for the failure
     */
    public ConstraintViolation(Level level, MappedClass clazz, FieldModel field,
                               Class<? extends ClassConstraint> validator, String message) {
        this(level, clazz, validator, message);
        this.field = field;
    }

    /**
     * Creates a violation instance to record invalid mapping metadata
     *
     * @param level     the severity of the violation
     * @param clazz     the errant class
     * @param validator the constraint failed
     * @param message   the message for the failure
     */
    public ConstraintViolation(Level level, MappedClass clazz, Class<? extends ClassConstraint> validator,
                               String message) {
        this.level = level;
        this.clazz = clazz;
        this.message = message;
        this.validator = validator;
    }

    /**
     * @return the severity of the violation
     */
    public Level getLevel() {
        return level;
    }

    /**
     * @return the qualified name of the failing mapping
     */
    public String getPrefix() {
        final String fn = (field != null) ? field.getName() : "";
        return clazz.getType().getName() + "." + fn;
    }

    /**
     * @return a human friendly version of the violation
     */
    public String render() {
        return String.format("%s complained about %s : %s", validator.getSimpleName(), getPrefix(), message);
    }

    /**
     * Levels of constraint violations
     */
    public enum Level {
        MINOR,
        INFO,
        WARNING,
        SEVERE,
        FATAL
    }
}
