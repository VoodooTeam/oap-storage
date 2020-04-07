/*
 * The MIT License (MIT)
 *
 * Copyright (c) Open Application Platform Authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package oap.storage.mongo;

import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

import static oap.storage.mongo.MigrationConfig.CONFIGURATION;

@Slf4j
@ToString( exclude = { "migrations", "shell", "mongoClient", "database" } )
public class MongoClient implements Closeable {
    final MongoDatabase database;
    final com.mongodb.MongoClient mongoClient;
    public final String host;
    public final int port;
    private String databaseName;
    private List<MigrationConfig> migrations;
    public Version databaseVersion = Version.UNDEFINED;
    private MongoShell shell = new MongoShell();

    public MongoClient( String host, int port, String database ) {
        this( host, port, database, CONFIGURATION.fromClassPath() );
    }

    public MongoClient( String host, int port, String database, List<MigrationConfig> migrations ) {
        this.host = host;
        this.port = port;
        this.databaseName = database;
        this.migrations = migrations;
        this.mongoClient = new com.mongodb.MongoClient( new ServerAddress( host, port ),
            MongoClientOptions.builder().codecRegistry( CodecRegistries.fromRegistries(
                CodecRegistries.fromCodecs( new JodaTimeCodec() ),
                com.mongodb.MongoClient.getDefaultCodecRegistry() ) ).build() );
        this.database = mongoClient.getDatabase( database );
        fetchVersion();
    }

    private void fetchVersion() {
        MongoCollection<Document> collection = this.getCollection( "version" );
        Document document = collection.find().first();
        this.databaseVersion =
            document != null ? new Version(
                document.getInteger( "main", 0 ),
                document.getInteger( "ext", 0 ) )
                : Version.UNDEFINED;
    }

    public void start() {
        log.debug( "starting mongo client {}", this );
        for( Migration migration : Migration.of( database.getName(), migrations ) ) {
            log.debug( "executing migrator {}", migration );
            log.debug( "current version is {}", databaseVersion );
            migration.execute( shell, host, port );
            updateVersion( migration.version );
            fetchVersion();
        }
        log.debug( "migration complete, current version is {}", databaseVersion );
    }

    public CodecRegistry getCodecRegistry() {
        return database.getCodecRegistry();
    }

    public <T> MongoCollection<T> getCollection( String collection, Class<T> clazz ) {
        return database.getCollection( collection, clazz );
    }

    public MongoCollection<Document> getCollection( String collection ) {
        return database.getCollection( collection );
    }

    @Override
    public void close() {
        mongoClient.close();
    }

    public void updateVersion( Version version ) {
        this.getCollection( "version" ).replaceOne( new Document( "_id", "version" ),
            new Document( Map.of( "main", version.main, "ext", version.ext ) ),
            new ReplaceOptions().upsert( true ) );
    }
}
