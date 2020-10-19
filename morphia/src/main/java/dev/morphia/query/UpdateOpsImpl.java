package dev.morphia.query;

import dev.morphia.Datastore;
import dev.morphia.mapping.Mapper;
import dev.morphia.query.experimental.filters.Filters;
import dev.morphia.query.experimental.updates.PopOperator;
import dev.morphia.query.experimental.updates.PushOperator;
import dev.morphia.query.experimental.updates.UpdateOperators;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @param <T> the type to update
 */
@SuppressWarnings("removal")
@Deprecated
public class UpdateOpsImpl<T> extends UpdateBase<T> implements UpdateOperations<T> {
    private Document ops = new Document();
    private boolean validateNames = true;

    /**
     * Creates an UpdateOpsImpl for the type given.
     *
     * @param datastore the datastore to use
     * @param type      the type to update
     * @param mapper    the Mapper to use
     */
    public UpdateOpsImpl(final Datastore datastore, final Class<T> type, final Mapper mapper) {
        super(datastore, mapper, null, null, type);
    }

    static <T> List<T> iterToList(final Iterable<T> it) {
        if (it instanceof List) {
            return (List<T>) it;
        }
        if (it == null) {
            return null;
        }

        final List<T> ar = new ArrayList<>();
        for (final T o : it) {
            ar.add(o);
        }

        return ar;
    }

    @Override
    public UpdateOperations<T> addToSet(final String field, final Object value) {
        add(UpdateOperators.addToSet(field, value));
        return this;
    }

    @Override
    public UpdateOperations<T> addToSet(final String field, final List<?> values) {
        add(UpdateOperators.addToSet(field, values));
        return this;
    }

    @Override
    public UpdateOperations<T> addToSet(final String field, final Iterable<?> values) {
        return addToSet(field, iterToList(values));
    }

    @Override
    public UpdateOperations<T> dec(final String field) {
        return inc(field, -1);
    }

    @Override
    public UpdateOperations<T> dec(final String field, final Number value) {
        if ((value instanceof Long) || (value instanceof Integer)) {
            return inc(field, (value.longValue() * -1));
        }
        if ((value instanceof Double) || (value instanceof Float)) {
            return inc(field, (value.doubleValue() * -1));
        }
        throw new IllegalArgumentException(
            "Currently only the following types are allowed: integer, long, double, float.");
    }

    @Override
    public UpdateOperations<T> disableValidation() {
        validateNames = false;
        return this;
    }

    @Override
    public UpdateOperations<T> enableValidation() {
        validateNames = true;
        return this;
    }

    @Override
    public UpdateOperations<T> inc(final String field) {
        return inc(field, 1);
    }

    @Override
    public UpdateOperations<T> inc(final String field, final Number value) {
        add(UpdateOperators.inc(field, value));
        return this;
    }

    @Override
    public UpdateOperations<T> max(final String field, final Number value) {
        add(UpdateOperators.max(field, value));
        return this;
    }

    @Override
    public UpdateOperations<T> min(final String field, final Number value) {
        add(UpdateOperators.min(field, value));
        return this;
    }

    @Override
    public UpdateOperations<T> push(final String field, final Object value) {
        add(UpdateOperators.push(field, value));
        return this;
    }

    @Override
    public UpdateOperations<T> push(final String field, final Object value, final PushOptions options) {
        PushOperator push = UpdateOperators.push(field, value);
        options.update(push);

        add(push);
        return this;
    }

    @Override
    public UpdateOperations<T> push(final String field, final List<?> values) {
        add(UpdateOperators.push(field, values));
        return this;
    }

    @Override
    public UpdateOperations<T> push(final String field, final List<?> values, final PushOptions options) {
        PushOperator push = UpdateOperators.push(field, values);
        options.update(push);

        add(push);
        return this;
    }

    @Override
    public UpdateOperations<T> removeAll(final String field, final Object value) {
        add(UpdateOperators.pull(field, Filters.eq(field, value)));
        return this;
    }

    @Override
    public UpdateOperations<T> removeAll(final String field, final List<?> values) {
        add(UpdateOperators.pullAll(field, values));
        return this;
    }

    @Override
    public UpdateOperations<T> removeFirst(final String field) {
        return remove(field, true);
    }

    @Override
    public UpdateOperations<T> removeLast(final String field) {
        return remove(field, false);
    }

    @Override
    public UpdateOperations<T> set(final String field, final Object value) {
        add(UpdateOperators.set(field, value));
        return this;
    }

    @Override
    public UpdateOperations<T> setOnInsert(final String field, final Object value) {
        add(UpdateOperators.setOnInsert(Collections.singletonMap(field, value)));
        return this;
    }

    @Override
    public UpdateOperations<T> unset(final String field) {
        add(UpdateOperators.unset(field));
        return this;
    }

    /**
     * @return the operations listed
     */
    public Document getOps() {
        return new Document(ops);
    }

    /**
     * Sets the operations for this UpdateOpsImpl
     *
     * @param ops the operations
     */
    public void setOps(final Document ops) {
        this.ops = ops;
    }

    protected UpdateOperations<T> remove(final String fieldExpr, final boolean firstNotLast) {
        PopOperator pop = UpdateOperators.pop(fieldExpr);
        if (firstNotLast) {
            pop.removeFirst();
        }
        add(pop);
        return this;
    }
}
