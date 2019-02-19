package java_bootcamp;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.bouncycastle.jcajce.provider.digest.SHA256;
import org.bson.BsonBinary;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class TradeState implements ContractState {

    private String tradeId;
    private BigDecimal amount;
    private String book;
    private String trader;

    Party initiatingParty;
    Party counterparty;

    private static Codec<BsonDocument> DOCUMENT_CODEC = new BsonDocumentCodec();

    public TradeState(String tradeId, String book, String trader, BigDecimal amount, Party initiatingParty, Party counterparty){
        this.tradeId = tradeId;
        this.amount = amount;
        this.book = book;
        this.trader = trader;
        this.initiatingParty = initiatingParty;
        this.counterparty = counterparty;
    }

    public String getTradeId() {
        return tradeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getBook() {
        return book;
    }

    public String getTrader() {
        return trader;
    }

    public Party getInitiatingParty() {
        return initiatingParty;
    }

    public Party getCounterparty(){
        return counterparty;
    }


    public static void main(String args[]){
        Party counterparty = null;
        Party initiatingParty = null;
        TradeState tradeState = new TradeState("1001", "A101", "shah", new BigDecimal("1000000"), initiatingParty, counterparty);
    }

    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(initiatingParty,counterparty);
    }

    public byte[] getTradeStateAsBson() throws NoSuchAlgorithmException {
        BsonDocument bdoc = new BsonDocument();
        bdoc.put("tradeId", new BsonString(tradeId));
        bdoc.put("book", new BsonString(book));
        bdoc.put("trader", new BsonString(trader));
        bdoc.put("amount", new BsonString(amount.toString()));
        byte[] payload = toInputStream(bdoc);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
        StringBuffer stringBuffer = new StringBuffer();
        for (byte bytes : digest) {
            stringBuffer.append(String.format("%02x", bytes & 0xff));
        }

        BsonDocument finaldoc = new BsonDocument();
        finaldoc.put("payload", new BsonBinary(payload));
        finaldoc.put("payloadhash", new BsonString(stringBuffer.toString()));

        return toInputStream(finaldoc);
    }

    private static byte[] toInputStream(final BsonDocument document) {
        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(outputBuffer);
        DOCUMENT_CODEC.encode(writer, document, EncoderContext.builder().isEncodingCollectibleDocument(true).build());
        // return new ByteArrayInputStream(outputBuffer.toByteArray());
        return outputBuffer.toByteArray();
    }

}
