package java_bootcamp;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCClientConfiguration;
import net.corda.core.contracts.ContractState;
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
import rx.Observable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class ExampleClientRPC {

    private static Codec<BsonDocument> DOCUMENT_CODEC = new BsonDocumentCodec();
    private static Codec<BsonValue> VALUE_CODEC = new BsonValueCodec();

    private static void logState(StateAndRef<TradeState> state) {
        System.out.println("{Data: }"+ state.getState().getData());
        System.out.println("{Contract: }"+ state.getState().getContract());
        System.out.println("{Hash: }"+ state.getRef().getTxhash().toString());
        try{
            byte[] tradeStateAsBson = state.getState().getData().getTradeStateAsBson();
            BsonDocument bsonDocument = fromInputStream(new ByteArrayInputStream(tradeStateAsBson));
            byte [] payload = ((BsonBinary)bsonDocument.get("payload")).getData();
            BsonDocument bsonDocumentReal = fromInputStream(new ByteArrayInputStream(payload));
            System.out.println("{Trade payload: }"+ bsonDocumentReal.toString());
            BsonValue payloadhash = bsonDocument.get("payloadhash");
            System.out.println("{Trade payloadhash: }"+ payloadhash.asString().getValue());

            //Inserting into MongoDB
            BsonDocument mongoDbDoc = new BsonDocument();
            mongoDbDoc.put("dataString", new BsonString(bsonDocumentReal.toString()));
            mongoDbDoc.put("dataBinary", new BsonBinary(payload));
            mongoDbDoc.put("hash", new BsonString(payloadhash.asString().getValue()));
            mongoDbDoc.put("contractId", new BsonString(UUID.randomUUID().toString()));

            MongoHelper mongoHelper = new MongoHelper();
            MongoDatabase cordaDb = mongoHelper.getDatabase();
            MongoCollection<BsonDocument> ledger = cordaDb.getCollection("ledger", BsonDocument.class);
            ledger.insertOne(mongoDbDoc);
            System.out.println("Bson Record Inserted in MongoDb");

            //Fetching from MongoDB and comparing hash
            FindIterable<BsonDocument> bsonFromDB = ledger.find();
            MongoCursor<BsonDocument> bsonDocumentMongoCursor = bsonFromDB.iterator();
            try {
                while(bsonDocumentMongoCursor.hasNext()) {
                    BsonDocument document = bsonDocumentMongoCursor.next();
                    byte [] data = SHA256.Digest.getInstance("SHA256").digest(document.get("dataBinary").asBinary().getData());
                    System.out.println("From DB - dataBinary : "+document.getBinary("dataBinary").getData().toString());
                    System.out.println("From DB - hash: "+document.getString("hash"));
                    System.out.println("Applied SHA2 on dataBinary : "+data.toString());
                    StringBuffer stringBuffer = new StringBuffer();
                    for (byte bytes : data) {
                        stringBuffer.append(String.format("%02x", bytes & 0xff));
                    }
                    System.out.println("Converted SHA2 formatted data to Hex: "+stringBuffer.toString());

                    if(payloadhash.asString().getValue().equals(stringBuffer.toString())){
                        System.out.println("Hash stored in Corda Vault MATCHES WITH Hash of data stored in MongoDB");
                    }
                }
            } finally {
                bsonDocumentMongoCursor.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }

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

    public static void main(String[] args) throws ActiveMQException, InterruptedException, ExecutionException, ClassNotFoundException, IllegalAccessException, InstantiationException {

        final NetworkHostAndPort nodeAddress = NetworkHostAndPort.parse("localhost:10004");
        final CordaRPCClient client = new CordaRPCClient(nodeAddress, CordaRPCClientConfiguration.DEFAULT);

        // Can be amended in the com.example.Main file.
        final CordaRPCOps proxy = client.start("user1", "test").getProxy();

        // Grab all existing and future IOU states in the vault.
        final DataFeed<Vault.Page<TradeState>, Vault.Update<TradeState>> dataFeed = proxy.vaultTrack(TradeState.class);
        final Vault.Page<TradeState> snapshot = dataFeed.getSnapshot();
        final Observable<Vault.Update<TradeState>> updates = dataFeed.getUpdates();

        // Log the 'placed' IOUs and listen for new ones.
        snapshot.getStates().forEach(ExampleClientRPC::logState);
        updates.toBlocking().subscribe(update -> update.getProduced().forEach(ExampleClientRPC::logState));
    }


}