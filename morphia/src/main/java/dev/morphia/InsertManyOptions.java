/*
 * Copyright 2016 MongoDB, Inc.
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

package dev.morphia;

import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import dev.morphia.internal.SessionConfigurable;
import dev.morphia.internal.WriteConfigurable;

/**
 * Options related to insertion of documents into MongoDB.  The setter methods return {@code this} so that a chaining style can be used.
 *
 * @since 1.3
 */
public class InsertManyOptions implements SessionConfigurable<InsertManyOptions>, WriteConfigurable<InsertManyOptions> {
    private com.mongodb.client.model.InsertManyOptions options = new com.mongodb.client.model.InsertManyOptions();
    private WriteConcern writeConcern = WriteConcern.ACKNOWLEDGED;
    private ClientSession clientSession;

    /**
     * Creates a new options wrapper
     */
    public InsertManyOptions() {
    }

    /**
     * @param that the options to copy
     * @morphia.internal
     * @since 2.0
     */
    public InsertManyOptions(final InsertManyOptions that) {
        this.options = that.options;
        this.writeConcern = that.writeConcern;
        this.clientSession = that.clientSession;
    }

    /**
     * Sets whether to bypass document validation.
     *
     * @param bypassDocumentValidation whether to bypass document validation, or null if unspecified
     * @return this
     * @mongodb.server.release 3.2
     */
    public InsertManyOptions bypassDocumentValidation(final Boolean bypassDocumentValidation) {
        options.bypassDocumentValidation(bypassDocumentValidation);
        return this;
    }

    /**
     * Set the client session to use for the insert.
     *
     * @param clientSession the client session
     * @return this
     */
    public InsertManyOptions clientSession(final ClientSession clientSession) {
        this.clientSession = clientSession;
        return this;
    }

    /**
     * The client session to use for the insertion.
     *
     * @return the client session
     */
    public ClientSession clientSession() {
        return clientSession;
    }

    /**
     * Gets whether to bypass document validation, or null if unspecified.  The default is null.
     *
     * @return whether to bypass document validation, or null if unspecified.
     * @mongodb.server.release 3.2
     */
    public Boolean getBypassDocumentValidation() {
        return options.getBypassDocumentValidation();
    }

    /**
     * @return the driver version of this instance
     */
    public com.mongodb.client.model.InsertManyOptions getOptions() {
        return options;
    }

    @Override
    @Deprecated
    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    /**
     * Gets whether the documents should be inserted in the order provided, stopping on the first failed insertion. The default is true.
     * If false, the server will attempt to insert all the documents regardless of an failures.
     *
     * @return whether the the documents should be inserted in order
     */
    public boolean isOrdered() {
        return options.isOrdered();
    }

    /**
     * Sets whether the server should insert the documents in the order provided.
     *
     * @param ordered true if documents should be inserted in order
     * @return this
     */
    public InsertManyOptions ordered(final boolean ordered) {
        options.ordered(ordered);
        return this;
    }

    @Override
    public InsertManyOptions writeConcern(final WriteConcern writeConcern) {
        this.writeConcern = writeConcern;
        return this;
    }

    @Override
    public WriteConcern writeConcern() {
        return writeConcern;
    }
}
