package java_bootcamp;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;

public class MongoHelper {

    private MongoClient mongoClient;

    private MongoDatabase database;

    public MongoHelper(){
        this.mongoClient = new MongoClient("localhost",27017);
        this.database = this.mongoClient.getDatabase("testcorda");
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public MongoDatabase getDatabase(){
        return  database;
    }

}
