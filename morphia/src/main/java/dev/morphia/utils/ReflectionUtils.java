package dev.morphia.utils;

/**
 * @morphia.internal
 */
public final class ReflectionUtils
{
    private ReflectionUtils() {
    }

    public static String getPackageName(Class<?> clazz) {
        while (clazz.isArray()) {
            clazz = clazz.getComponentType();
        }

        if (clazz.isPrimitive()) {
            return "java.lang";
        }

        String clazzName = clazz.getName();
        int dotIdx = clazzName.lastIndexOf('.');
        return dotIdx == -1 ? "" : clazzName.substring(0, dotIdx);
    }

}
