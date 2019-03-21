package java_bootcamp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCClientConfiguration;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.DataFeed;
import net.corda.core.node.services.Vault;
import net.corda.core.utilities.NetworkHostAndPort;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.bouncycastle.jcajce.provider.digest.SHA256;
import org.bson.*;
import org.bson.codecs.*;
import org.bson.io.BasicOutputBuffer;
import org.jetbrains.annotations.NotNull;
import rx.Observable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ClientRPC {

    //TODO Replace sysout with Loggers

    private static Codec<BsonDocument> DOCUMENT_CODEC = new BsonDocumentCodec();
    private static Codec<BsonValue> VALUE_CODEC = new BsonValueCodec();

    public static void main(String[] args) throws ActiveMQException, InterruptedException, ExecutionException, ClassNotFoundException, IllegalAccessException, InstantiationException {

        //Connect to one of the Party running on 10004 port
        final NetworkHostAndPort nodeAddress = NetworkHostAndPort.parse("localhost:10004");
        final CordaRPCClient client = new CordaRPCClient(nodeAddress, CordaRPCClientConfiguration.DEFAULT);

        final CordaRPCOps proxy = client.start("user1", "test").getProxy();

        // Grab all existing and future TradeState states in the vault.
        final DataFeed<Vault.Page<TradeState>, Vault.Update<TradeState>> dataFeed = proxy.vaultTrack(TradeState.class);
        final Vault.Page<TradeState> snapshot = dataFeed.getSnapshot();
        final Observable<Vault.Update<TradeState>> updates = dataFeed.getUpdates();

        // Invoke StoreInDB and then reconcile on every event 
        snapshot.getStates().forEach(ClientRPC::storeInDBAndReconcile);
        updates.toBlocking().subscribe(update -> update.getProduced().forEach(ClientRPC::storeInDBAndReconcile));
    }

    private static void storeInDBAndReconcile(StateAndRef<TradeState> state) {
        try{
            BsonDocument tradeStateAsBson = fromInputStream(new ByteArrayInputStream(state.getState().getData().getTradeStateAsBson()));
            byte [] payload = ((BsonBinary)tradeStateAsBson.get("payload")).getData();
            BsonDocument payloadBson = fromInputStream(new ByteArrayInputStream(payload));
            BsonValue payloadHash = tradeStateAsBson.get("payloadhash");

            MongoDatabase cordaDb = new MongoHelper().getDatabase();
            MongoCollection<BsonDocument> bsonCollection = cordaDb.getCollection("ledger", BsonDocument.class);
            insertDocumentToDB(payloadBson, payloadHash, bsonCollection);
            reconcilePayloadHash(payloadHash, bsonCollection);

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private static void reconcilePayloadHash(BsonValue payloadHash, MongoCollection<BsonDocument> collection) throws NoSuchAlgorithmException {
        //Fetching from MongoDB and comparing hash
        FindIterable<BsonDocument> bsonFromDB = collection.find();
        MongoCursor<BsonDocument> bsonDocumentMongoCursor = bsonFromDB.iterator();
        try {
            while(bsonDocumentMongoCursor.hasNext()) {
                BsonDocument documentFromDb = bsonDocumentMongoCursor.next();
                BsonDocument payloadFromDB = (BsonDocument) documentFromDb.get("payload");
                byte [] data = SHA256.Digest.getInstance("SHA256").digest( toInputStream(payloadFromDB));                    ;

                StringBuffer stringBuffer = new StringBuffer();
                for (byte bytes : data) {
                    stringBuffer.append(String.format("%02x", bytes & 0xff));
                }

                if(payloadHash.asString().getValue().equals(stringBuffer.toString())){
                    System.out.println("Hash stored in Corda Vault MATCHES WITH Hash of data stored in MongoDB");
                }
            }
        } finally {
            bsonDocumentMongoCursor.close();
        }
    }
    @NotNull
    private static void insertDocumentToDB(BsonDocument bsonDocumentPayload, BsonValue payloadHash, MongoCollection<BsonDocument> bsonCollection) {
        BsonDocument bsonDocument = new BsonDocument();
        bsonDocument.put("payload",bsonDocumentPayload);
        bsonDocument.put("payloadhash",payloadHash.asString());
        bsonCollection.insertOne(bsonDocument);
        System.out.println("Bson Record Inserted in MongoDb");
        return ;
    }

    public static byte[] toInputStream(final BsonDocument document) {
        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(outputBuffer);
        DOCUMENT_CODEC.encode(writer, document, EncoderContext.builder().isEncodingCollectibleDocument(true).build());
        return outputBuffer.toByteArray();
    }

   public static BsonDocument fromInputStream(final InputStream input) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = input.read(buffer)) != -1) {
            outputStream.write(buffer, 0, len);
        }
        outputStream.close();
        BsonBinaryReader bsonReader = new BsonBinaryReader(ByteBuffer.wrap(outputStream.toByteArray()));
        return DOCUMENT_CODEC.decode(bsonReader, DecoderContext.builder().build());
    }




}