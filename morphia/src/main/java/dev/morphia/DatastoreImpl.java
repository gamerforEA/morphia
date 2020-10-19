package dev.morphia;

import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ValidationOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import dev.morphia.aggregation.experimental.Aggregation;
import dev.morphia.aggregation.experimental.AggregationImpl;
import dev.morphia.annotations.CappedAt;
import dev.morphia.annotations.Validation;
import dev.morphia.experimental.MorphiaSession;
import dev.morphia.experimental.MorphiaSessionImpl;
import dev.morphia.internal.SessionConfigurable;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.MappedField;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.mapping.MappingException;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.QueryFactory;
import dev.morphia.query.UpdateException;
import dev.morphia.query.ValidationException;
import dev.morphia.query.experimental.updates.UpdateOperators;
import dev.morphia.sofia.Sofia;
import dev.morphia.transactions.experimental.MorphiaTransaction;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

import static dev.morphia.query.experimental.filters.Filters.eq;
import static dev.morphia.query.experimental.filters.Filters.in;
import static java.util.Collections.singletonList;
import static org.bson.Document.parse;

/**
 * A generic (type-safe) wrapper around mongodb collections
 *
 * @morphia.internal
 */
@SuppressWarnings({"unchecked", "rawtypes", "removal"})
public class DatastoreImpl implements AdvancedDatastore {
    private static final Logger LOG = LoggerFactory.getLogger(DatastoreImpl.class);

    private final MongoDatabase database;
    private final MongoClient mongoClient;
    private final Mapper mapper;

    private QueryFactory queryFactory;

    protected DatastoreImpl(final MongoClient mongoClient, final MapperOptions options, final String dbName) {
        this.mongoClient = mongoClient;
        MongoDatabase database = mongoClient.getDatabase(dbName);
        this.mapper = new Mapper(this, database.getCodecRegistry(), options);

        this.database = database
                            .withCodecRegistry(mapper.getCodecRegistry());
        this.queryFactory = options.getQueryFactory();
    }

    /**
     * Copy constructor for a datastore
     *
     * @param database     the database
     * @param mongoClient  the client
     * @param mapper       the mapper
     * @param queryFactory the query factory
     * @morphia.internal
     * @since 2.0
     */
    public DatastoreImpl(final MongoDatabase database, final MongoClient mongoClient, final Mapper mapper,
                         final QueryFactory queryFactory) {
        this.database = database;
        this.mongoClient = mongoClient;
        this.mapper = mapper;
        this.queryFactory = queryFactory;
    }

    @Override
    public dev.morphia.aggregation.AggregationPipeline createAggregation(final String collection, final Class<?> clazz) {
        return new dev.morphia.aggregation.AggregationPipelineImpl(this, getDatabase().getCollection(collection), clazz);
    }

    @Override
    public <T> Query<T> find(final String collection) {
        return getQueryFactory().createQuery(this, mapper.getClassFromCollection(collection));
    }

    @Override
    public <T> void insert(final T entity) {
        insert(entity, new InsertOneOptions()
                           .writeConcern(mapper.getWriteConcern(entity.getClass())));
    }

    @Override
    public <T> void insert(final T entity, final InsertOneOptions options) {
        insert(mapper.getCollection(entity.getClass()), entity, options);
    }

    /**
     * @return the logged query
     * @morphia.internal
     */
    @Override
    public String getLoggedQuery(final FindOptions options) {
        if (options != null && options.isLogQuery()) {
            String json = "{}";
            Document first = getDatabase()
                                 .getCollection("system.profile")
                                 .find(new Document("command.comment", "logged query: " + options.getQueryLogId()),
                                     Document.class)
                                 .projection(new Document("command.filter", 1))
                                 .first();
            if (first != null) {
                Document command = (Document) first.get("command");
                Document filter = (Document) command.get("filter");
                if (filter != null) {
                    json = filter.toJson(mapper.getCodecRegistry().get(Document.class));
                }
            }
            return json;
        } else {
            throw new IllegalStateException(Sofia.queryNotLogged());
        }
    }

    @Override
    @SuppressWarnings("removal")
    public <T> Query<T> queryByExample(final String collection, final T ex) {
        return queryByExample(ex);
    }

    protected <T> void insert(final MongoCollection collection, final T entity, final InsertOneOptions options) {
        setInitialVersion(mapper.getMappedClass(entity.getClass()).getVersionField(), entity);
        MongoCollection mongoCollection = mapper.enforceWriteConcern(collection, entity.getClass());
        ClientSession clientSession = findSession(options);
        if (clientSession == null) {
            mongoCollection.insertOne(entity, options.getOptions());
        } else {
            mongoCollection.insertOne(clientSession, entity, options.getOptions());
        }
    }

    private <T> void setInitialVersion(final MappedField versionField, final T entity) {
        if (versionField != null) {
            Object value = versionField.getFieldValue(entity);
            if (value != null && !value.equals(0)) {
                throw new ValidationException(Sofia.versionManuallySet());
            } else {
                versionField.setFieldValue(entity, 1L);
            }
        }
    }

    @Override
    public ClientSession findSession(final SessionConfigurable<?> configurable) {
        return configurable.clientSession() != null
               ? configurable.clientSession()
               : getSession();
    }

    @Override
    public <T> T withTransaction(final MorphiaTransaction<T> body) {
        return doTransaction(startSession(), body);
    }

    @Override
    public MorphiaSession startSession() {
        return new MorphiaSessionImpl(mongoClient.startSession(), mongoClient, database, mapper, queryFactory);
    }

    @Override
    public MorphiaSession startSession(final ClientSessionOptions options) {
        return new MorphiaSessionImpl(mongoClient.startSession(options), mongoClient, database, mapper, queryFactory);
    }

    @Override
    public <T> T withTransaction(final ClientSessionOptions options, final MorphiaTransaction<T> transaction) {
        return doTransaction(startSession(options), transaction);
    }

    @Override
    public Aggregation<Document> aggregate(final String source) {
        return new AggregationImpl(this, getDatabase().getCollection(source));
    }

    @Override
    public <T> Aggregation<T> aggregate(final Class<T> source) {
        return new AggregationImpl(this, mapper.getCollection(source));
    }

    @Override
    public dev.morphia.aggregation.AggregationPipeline createAggregation(final Class source) {
        return new dev.morphia.aggregation.AggregationPipelineImpl(this, mapper.getCollection(source), source);
    }

    @Override
    public <T> Query<T> find(final Class<T> type) {
        return getQueryFactory().createQuery(this, type);
    }

    @Override
    public <T> Query<T> find(final String collection, final Class<T> type) {
        return getQueryFactory().createQuery(this, collection, type);
    }

    /**
     * @return the Mapper used by this Datastore
     */
    public Mapper getMapper() {
        return mapper;
    }

    @Override
    public <T> DeleteResult delete(final T entity) {
        return delete(entity, new DeleteOptions().writeConcern(mapper.getWriteConcern(entity.getClass())));
    }

    /**
     * Deletes the given entity (by @Id), with the WriteConcern
     *
     * @param entity  the entity to delete
     * @param options the options to use when deleting
     * @return results of the delete
     */
    @Override
    public <T> DeleteResult delete(final T entity, final DeleteOptions options) {
        if (entity instanceof Class<?>) {
            throw new MappingException("Did you mean to delete all documents? -- ds.createQuery(???.class).delete()");
        }
        return find(entity.getClass())
                   .filter(eq("_id", mapper.getId(entity)))
                   .delete(options);
    }

    @Override
    public void ensureCaps() {
        List<String> collectionNames = database.listCollectionNames().into(new ArrayList<>());
        for (final MappedClass mc : mapper.getMappedClasses()) {
            if (mc.getEntityAnnotation() != null) {
                CappedAt cappedAt = mc.getEntityAnnotation().cap();
                if (cappedAt.value() > 0) {
                    final CappedAt cap = mc.getEntityAnnotation().cap();
                    final String collName = mc.getCollectionName();
                    final CreateCollectionOptions dbCapOpts = new CreateCollectionOptions()
                                                                  .capped(true);
                    if (cap.value() > 0) {
                        dbCapOpts.sizeInBytes(cap.value());
                    }
                    if (cap.count() > 0) {
                        dbCapOpts.maxDocuments(cap.count());
                    }
                    final MongoDatabase database = getDatabase();
                    if (collectionNames.contains(collName)) {
                        final Document dbResult = database.runCommand(new Document("collstats", collName));
                        if (dbResult.getBoolean("capped")) {
                            LOG.debug("MongoCollection already exists and is capped already; doing nothing. " + dbResult);
                        } else {
                            LOG.warn("MongoCollection already exists with same name(" + collName
                                     + ") and is not capped; not creating capped version!");
                        }
                    } else {
                        getDatabase().createCollection(collName, dbCapOpts);
                        LOG.debug("Created capped MongoCollection (" + collName + ") with opts " + dbCapOpts);
                    }
                }
            }
        }
    }

    @Override
    public void enableDocumentValidation() {
        for (final MappedClass mc : mapper.getMappedClasses()) {
            enableValidation(mc, mc.getAnnotation(Validation.class));
        }
    }

    @Override
    public void ensureIndexes() {
        if (mapper.getMappedClasses().isEmpty()) {
            Sofia.logNoMappedClasses();
        }
        final IndexHelper indexHelper = new IndexHelper(mapper);
        for (final MappedClass mc : mapper.getMappedClasses()) {
            if (mc.getEntityAnnotation() != null) {
                indexHelper.createIndex(mapper.getCollection(mc.getType()), mc);
            }
        }
    }

    @Override
    public <T> void ensureIndexes(final Class<T> clazz) {
        final IndexHelper indexHelper = new IndexHelper(mapper);
        indexHelper.createIndex(mapper.getCollection(clazz), mapper.getMappedClass(clazz));
    }

    private <T> List<T> getByKeys(final Class<T> clazz, final Iterable<Key<T>> keys) {

        final Map<String, List<Key>> kindMap = new HashMap<>();
        final List<T> entities = new ArrayList<>();
        for (final Key<?> key : keys) {
            mapper.updateCollection(key);

            if (kindMap.containsKey(key.getCollection())) {
                kindMap.get(key.getCollection()).add(key);
            } else {
                kindMap.put(key.getCollection(), new ArrayList<>(singletonList((Key) key)));
            }
        }
        for (final Map.Entry<String, List<Key>> entry : kindMap.entrySet()) {
            final List<Key> kindKeys = entry.getValue();

            final List<Object> objIds = new ArrayList<>();
            for (final Key key : kindKeys) {
                objIds.add(key.getId());
            }
            final List kindResults = find(entry.getKey()).disableValidation()
                                                         .filter(in("_id", objIds))
                                                         .iterator()
                                                         .toList();
            entities.addAll(kindResults);
        }

        return entities;
    }

    @Override
    public MongoDatabase getDatabase() {
        return database;
    }

    @Override
    public QueryFactory getQueryFactory() {
        return queryFactory;
    }

    @Override
    public void setQueryFactory(final QueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public <T> T merge(final T entity) {
        return merge(entity, new InsertOneOptions());
    }

    @Override
    public <T> T merge(final T entity, final InsertOneOptions options) {
        final Object id = mapper.getId(entity);
        if (id == null) {
            throw new MappingException("Could not get id for " + entity.getClass().getName());
        }

        final Document document = mapper.toDocument(entity);
        document.remove("_id");

        final Query<T> query = (Query<T>) find(entity.getClass()).filter(eq("_id", id));
        if (!tryVersionedUpdate(entity, mapper.getCollection(entity.getClass()), options)) {
            UpdateResult execute = query.update(UpdateOperators.set(entity))
                                        .execute(new UpdateOptions()
                                                     .clientSession(findSession(options))
                                                     .writeConcern(options.writeConcern()));
            if (execute.getModifiedCount() != 1) {
                throw new UpdateException("Nothing updated");
            }
        }

        return query.first();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Query<T> queryByExample(final T example) {
        return getQueryFactory().createQuery(this, (Class<T>) example.getClass(), mapper.toDocument(example));
    }

    @Override
    public <T> void insert(final List<T> entities, final InsertManyOptions options) {
        if (!entities.isEmpty()) {
            Class<?> type = entities.get(0).getClass();
            MappedClass mappedClass = mapper.getMappedClass(type);
            final MongoCollection collection = mapper.getCollection(type);
            MappedField versionField = mappedClass.getVersionField();
            if (versionField != null) {
                for (final T entity : entities) {
                    setInitialVersion(versionField, entity);
                }
            }

            MongoCollection mongoCollection = options.prepare(collection);
            if (options.clientSession() == null) {
                mongoCollection.insertMany(entities, options.getOptions());
            } else {
                mongoCollection.insertMany(options.clientSession(), entities, options.getOptions());
            }
        }
    }

    @Override
    public <T> T save(final T entity) {
        return save(entity, new InsertOneOptions());
    }

    @Override
    public <T> T save(final T entity, final InsertOneOptions options) {
        if (entity == null) {
            throw new UpdateException(Sofia.cannotPersistNullEntity());
        }

        save(mapper.getCollection(entity.getClass()), entity, options);
        return entity;
    }

    private <T> void save(final MongoCollection collection, final T entity, final InsertOneOptions options) {
        if (entity == null) {
            throw new UpdateException(Sofia.cannotPersistNullEntity());
        }

        if (!tryVersionedUpdate(entity, collection, options)) {
            saveDocument(entity, collection, options);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> save(final List<T> entities, final InsertManyOptions options) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        Map<MongoCollection, List<T>> grouped = new LinkedHashMap<>();
        List<T> list = new ArrayList<>();
        for (final T entity : entities) {
            MappedClass mappedClass = getMapper().getMappedClass(entity.getClass());
            if (getMapper().getId(entity) != null || mappedClass.getVersionField() != null) {
                list.add(entity);
            } else {
                grouped.computeIfAbsent(mapper.getCollection(entity.getClass()), c -> new ArrayList<>())
                       .add(entity);
            }
        }

        for (Entry<MongoCollection, List<T>> entry : grouped.entrySet()) {
            MongoCollection<T> collection = entry.getKey(); // options.prepare(mapper.getCollection(entry.getKey()));
            if (options.clientSession() == null) {
                collection.insertMany(entry.getValue(), options.getOptions());
            } else {
                collection.insertMany(options.clientSession(), entry.getValue(), options.getOptions());
            }
        }

        InsertOneOptions insertOneOptions = new InsertOneOptions()
                                                .bypassDocumentValidation(options.getBypassDocumentValidation())
                                                .clientSession(findSession(options))
                                                .writeConcern(options.writeConcern());
        for (final T entity : list) {
            save(entity, insertOneOptions);
        }
        return entities;
    }

    protected <T> void saveDocument(final T entity, final MongoCollection<T> collection, final InsertOneOptions options) {
        Object id = mapper.getMappedClass(entity.getClass()).getIdField().getFieldValue(entity);
        ClientSession clientSession = findSession(options);

        if (id == null) {
            if (clientSession == null) {
                options.prepare(collection).insertOne(entity, options.getOptions());
            } else {
                options.prepare(collection).insertOne(clientSession, entity, options.getOptions());
            }
        } else {
            ReplaceOptions updateOptions = new ReplaceOptions()
                                               .bypassDocumentValidation(options.getBypassDocumentValidation())
                                               .upsert(true);
            MongoCollection<T> updated = collection;
            if (options.writeConcern() != null) {
                updated = collection.withWriteConcern(options.writeConcern());
            }
            if (clientSession == null) {
                updated.replaceOne(new Document("_id", id), entity, updateOptions);
            } else {
                updated.replaceOne(clientSession, new Document("_id", id), entity, updateOptions);
            }
        }
    }

    private <T> boolean tryVersionedUpdate(final T entity, final MongoCollection collection, final InsertOneOptions options) {
        final MappedClass mc = mapper.getMappedClass(entity.getClass());
        if (mc.getVersionField() == null) {
            return false;
        }

        MappedField idField = mc.getIdField();
        final Object idValue = idField.getFieldValue(entity);
        final MappedField versionField = mc.getVersionField();

        Long oldVersion = (Long) versionField.getFieldValue(entity);
        long newVersion = oldVersion == null ? 1L : oldVersion + 1;
        ClientSession session = findSession(options);

        if (newVersion == 1) {
            try {
                updateVersion(entity, versionField, newVersion);
                if (session == null) {
                    options.prepare(collection).insertOne(entity, options.getOptions());
                } else {
                    options.prepare(collection).insertOne(session, entity, options.getOptions());
                }
            } catch (MongoWriteException e) {
                updateVersion(entity, versionField, oldVersion);
                throw new ConcurrentModificationException(Sofia.concurrentModification(entity.getClass().getName(), idValue));
            }
        } else if (idValue != null) {
            final UpdateResult res = find(collection.getNamespace().getCollectionName())
                                         .filter(eq("_id", idValue),
                                             eq(versionField.getMappedFieldName(), oldVersion))
                                         .update(UpdateOperators.set(entity))
                                         .execute(new UpdateOptions()
                                                      .bypassDocumentValidation(options.getBypassDocumentValidation())
                                                      .clientSession(session)
                                                      .writeConcern(options.writeConcern()));

            if (res.getModifiedCount() != 1) {
                throw new ConcurrentModificationException(Sofia.concurrentModification(entity.getClass().getName(), idValue));
            }
            updateVersion(entity, versionField, newVersion);
        }

        return true;
    }

    private <T> void updateVersion(final T entity, final MappedField field, final Long newVersion) {
        field.setFieldValue(entity, newVersion);
    }

    void enableValidation(final MappedClass mc, final Validation validation) {
        if (validation != null) {
            String collectionName = mc.getCollectionName();
            try {
                getDatabase().runCommand(new Document("collMod", collectionName)
                                             .append("validator", parse(validation.value()))
                                             .append("validationLevel", validation.level().getValue())
                                             .append("validationAction", validation.action().getValue()));
            } catch (MongoCommandException e) {
                if (e.getCode() == 26) {
                    getDatabase().createCollection(collectionName,
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

    private <T> T doTransaction(final MorphiaSession morphiaSession, final MorphiaTransaction<T> body) {
        try (morphiaSession) {
            return morphiaSession.getSession().withTransaction(() -> body.execute(morphiaSession));
        }
    }

    @Override
    public <T> void refresh(final T entity) {
        getMapper().refresh(entity);
    }
}
