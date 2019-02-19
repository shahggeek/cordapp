package java_bootcamp;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

@InitiatedBy(TradeFlow.class)
public class TradeFlowResponder  extends FlowLogic<SignedTransaction> {

    private final FlowSession counterpartySession;

    public TradeFlowResponder(FlowSession counterpartySession) {
        this.counterpartySession = counterpartySession;
    }

    private final ProgressTracker progressTracker = new ProgressTracker();

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {

        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherSession, ProgressTracker progressTracker) {
                super(otherSession, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) throws FlowException {
                // Whatever checking you want to do...
            }
        }

       return subFlow(new SignTxFlow(counterpartySession, SignTransactionFlow.tracker()));

    }
}
