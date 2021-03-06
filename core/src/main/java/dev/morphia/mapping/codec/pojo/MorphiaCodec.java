package dev.morphia.mapping.codec.pojo;

import dev.morphia.Datastore;
import dev.morphia.mapping.DiscriminatorLookup;
import dev.morphia.mapping.MappedClass;
import dev.morphia.mapping.Mapper;
import dev.morphia.mapping.codec.PropertyCodecRegistryImpl;
import org.bson.BsonReader;
import org.bson.BsonValue;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PropertyCodecProvider;
import org.bson.codecs.pojo.PropertyCodecRegistry;
import org.bson.types.ObjectId;

import java.util.List;

import static dev.morphia.mapping.codec.Conversions.convert;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * the codec used by Morphia
 *
 * @param <T> the entity type
 * @morphia.internal
 * @since 2.0
 */
@SuppressWarnings("unchecked")
public class MorphiaCodec<T> implements CollectibleCodec<T> {
    private final FieldModel idField;
    private final Mapper mapper;
    private final EntityModel entityModel;
    private final MappedClass mappedClass;
    private final CodecRegistry registry;
    private final PropertyCodecRegistry propertyCodecRegistry;
    private final DiscriminatorLookup discriminatorLookup;
    private final EntityEncoder encoder = new EntityEncoder(this);

    /**
     * Creates a new codec
     *
     * @param datastore              the datastore
     * @param mappedClass            the MappedClass backing this codec
     * @param propertyCodecProviders the codec provider for properties
     * @param registry               the codec registry for lookups
     * @param discriminatorLookup    the discriminator to type lookup
     */
    public MorphiaCodec(Datastore datastore, MappedClass mappedClass,
                        List<PropertyCodecProvider> propertyCodecProviders,
                        DiscriminatorLookup discriminatorLookup, CodecRegistry registry) {
        this.mappedClass = mappedClass;
        this.mapper = datastore.getMapper();
        this.discriminatorLookup = discriminatorLookup;

        this.entityModel = mappedClass.getEntityModel();
        this.registry = fromRegistries(fromCodecs(this), registry);
        this.propertyCodecRegistry = new PropertyCodecRegistryImpl(this, registry, propertyCodecProviders);
        idField = mappedClass.getIdField();
        specializePropertyCodecs();
    }

    @Override
    public T decode(BsonReader reader, DecoderContext decoderContext) {
        return (T) getDecoder().decode(reader, decoderContext);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void specializePropertyCodecs() {
        EntityModel entityModel = getEntityModel();
        for (FieldModel fieldModel : entityModel.getFieldModels()) {
            Codec codec = fieldModel.getCodec() != null ? fieldModel.getCodec()
                                                        : propertyCodecRegistry.get(fieldModel.getTypeData());
            fieldModel.cachedCodec(codec);
        }
    }

    /**
     * @return the entity model backing this codec
     */
    public EntityModel getEntityModel() {
        return entityModel;
    }

    protected EntityDecoder getDecoder() {
        return new EntityDecoder(this);
    }

    @Override
    public void encode(BsonWriter writer, Object value, EncoderContext encoderContext) {
        encoder.encode(writer, value, encoderContext);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class getEncoderClass() {
        return getEntityModel().getType();
    }

    @Override
    public Object generateIdIfAbsentFromDocument(Object entity) {
        if (!documentHasId(entity)) {
            idField.setValue(entity, convert(new ObjectId(), idField.getType()));
        }
        return entity;
    }

    @Override
    public boolean documentHasId(Object entity) {
        return mappedClass.getIdField().getValue(entity) != null;
    }

    @Override
    public BsonValue getDocumentId(Object document) {
        throw new UnsupportedOperationException("is this even necessary?");
/*
        final Object id = mappedClass.getIdField().getFieldValue(document);
        final DocumentWriter writer = new DocumentWriter();
        ((Codec) registry.get(id.getClass()))
            .encode(writer, id, EncoderContext.builder().build());
        Document doc = writer.getDocument();

        return null;
*/
    }

    /**
     * @return the MappedClass for this codec
     */
    public MappedClass getMappedClass() {
        return mappedClass;
    }

    /**
     * @return the mapper being used
     */
    public Mapper getMapper() {
        return mapper;
    }

    DiscriminatorLookup getDiscriminatorLookup() {
        return discriminatorLookup;
    }

    CodecRegistry getRegistry() {
        return registry;
    }

}
