package java_bootcamp;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.List;

@InitiatingFlow
@StartableByRPC
public class TradeFlow extends FlowLogic<SignedTransaction> {

    private final ProgressTracker progressTracker = new ProgressTracker();

    private final String tradeId;
    private final Party counterparty;
    private final BigDecimal amount;

    public TradeFlow(String tradeId, Party counterparty, BigDecimal amount) {
        this.tradeId = tradeId;
        this.counterparty = counterparty;
        this.amount = amount;
    }


    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    public SignedTransaction call() throws FlowException {
        // We choose our transaction's notary (the notary prevents double-spends).
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        // We get a reference to our own identity.
        Party initiatingParty = getOurIdentity();

        TradeState tradeState = new TradeState( tradeId, "A101", "shah", amount, initiatingParty, counterparty);
        TradeContract.Create command = new TradeContract.Create();

        TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
        List<PublicKey> requiredSigns = ImmutableList.of(initiatingParty.getOwningKey(),counterparty.getOwningKey());
        transactionBuilder
                .addOutputState(tradeState, TradeContract.ID)
                .addCommand(command,requiredSigns);

        transactionBuilder.verify(getServiceHub());

        SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(transactionBuilder);
        FlowSession ownerSession = initiateFlow(counterparty);

        SignedTransaction fullySignedTx = subFlow(
                new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(ownerSession)));

       return  subFlow(new FinalityFlow(fullySignedTx));
    }
}
