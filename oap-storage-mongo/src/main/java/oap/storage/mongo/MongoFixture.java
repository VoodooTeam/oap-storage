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

import lombok.extern.slf4j.Slf4j;
import oap.system.Env;
import oap.testng.AbstractEnvFixture;
import oap.testng.Suite;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static oap.testng.Asserts.contentOfTestResource;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;


@Slf4j
public class MongoFixture extends AbstractEnvFixture<MongoFixture> {
    public static final int mongoPort = 27017;
    public static final String mongoHost = Env.get( "MONGO_HOST", "localhost" );
    public static final String mongoUser = Env.get( "MONGO_USER", null );
    public static final String mongoPassword = Env.get( "MONGO_PASSWORD", null );
    public static final String mongoDatabase = "db_" + StringUtils.replaceChars( Suite.uniqueExecutionId(), ".-", "_" );
    private Consumer<MongoFixture> databaseInitializer = mf -> {};
    private MongoClient mongoClient;

    public MongoFixture() {
        this( "" );
    }

    public MongoFixture( String databaseSuffix ) {
        define( "MONGO_HOST", mongoHost );
        define( "MONGO_PORT", String.valueOf( mongoPort ) );
        define( "MONGO_DATABASE", mongoDatabase );
        if( isNotEmpty( mongoUser ) && isNotEmpty( mongoPassword ) ) {
            define( "MONGO_USER", mongoUser );
            define( "MONGO_PASSWORD", mongoPassword );
        }
        log.debug( "binding MONGO_DATABASE to {}", mongoDatabase + databaseSuffix );
    }

    public static MongoShell createMongoShell() {
        var mongoClientPath = Env
            .get( "MONGO_CLIENT_PATH" )
            .or( () -> Env.get( "CONFIG.services.oap-storage-mongo.oap-storage-mongo-shell.parameters.path" ) )
            .or( () -> Optional.ofNullable( System.getProperty( "MONGO_CLIENT_PATH" ) ) )
            .or( () -> Optional.ofNullable( System.getProperty( "CONFIG.services.oap-storage-mongo.oap-storage-mongo-shell.parameters.path" ) ) )
            .orElse( null );

        return mongoClientPath != null ? new MongoShell( mongoClientPath ) : new MongoShell();
    }

    public void dropTestDatabases() {
        final Pattern pattern = Pattern.compile( ".+_(\\d+)" );
        try( com.mongodb.MongoClient mongoClient = new com.mongodb.MongoClient( mongoHost, mongoPort ) ) {
            Consumer<String> drop = database -> {
                Matcher matcher = pattern.matcher( database );
                if( matcher.find() )
                    if( new Date().getTime() - Long.parseLong( matcher.group( 1 ) ) > 1000 * 60 * 60 * 12 )
                        mongoClient.dropDatabase( database );
            };
            mongoClient.listDatabaseNames().forEach( drop );
        }
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public void insertDocument( Class<?> contextClass, String collection, String resourceName ) {
        mongoClient.getCollection( collection ).insertOne( Document.parse( contentOfTestResource( contextClass, resourceName, Map.of() ) ) );
    }

    @Override
    protected void before() {
        super.before();

        mongoClient = createMongoClient();
        databaseInitializer.accept( this );
    }

    @NotNull
    private MongoClient createMongoClient() {
        return new MongoClient( mongoHost, mongoPort, mongoDatabase, mongoDatabase, List.of(), createMongoShell() );
    }

    @Override
    public void after() {
        mongoClient.dropDatabase();
        mongoClient.close();

        super.after();
    }

    public void initializeVersion( Version version ) {
        mongoClient.updateVersion( version );
    }

    public MongoFixture withDatabaseInitializer( Consumer<MongoFixture> initializer ) {
        this.databaseInitializer = initializer;
        return this;
    }
}
