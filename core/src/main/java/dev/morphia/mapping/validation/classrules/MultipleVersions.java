package dev.morphia.mapping.validation.classrules;


import dev.morphia.annotations.Version;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.codec.pojo.FieldModel;
import dev.morphia.mapping.validation.ClassConstraint;
import dev.morphia.mapping.validation.ConstraintViolation;
import dev.morphia.mapping.validation.ConstraintViolation.Level;

import java.util.List;
import java.util.Set;


/**
 * @author Uwe Schaefer, (us@thomas-daily.de)
 */
public class MultipleVersions implements ClassConstraint {

    @Override
    public void check(Mapper mapper, MappedClass mc, Set<ConstraintViolation> ve) {
        final List<FieldModel> versionFields = mc.getFields(Version.class);
        if (versionFields.size() > 1) {
            ve.add(new ConstraintViolation(Level.FATAL, mc, getClass(),
                                           "Multiple @" + Version.class + " annotations are not allowed. ("
                                           + new FieldEnumString(versionFields)));
        }
    }
}
