package java_bootcamp;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

import java.util.ArrayList;
import java.util.List;

/* Our state, defining a shared fact on the ledger.
 * See src/main/java/examples/ArtState.java for an example. */
public class TokenState implements  ContractState{

    private Party issuer;
    private Party owner;
    private int amount;

    public TokenState(Party issuer,Party owner, int amount){
        this.issuer = issuer;
        this.owner = owner;
        this.amount = amount;
    }

    public Party getIssuer() {
        return issuer;
    }

    public Party getOwner() {
        return owner;
    }

    public int getAmount() {
        return amount;
    }

    public List<AbstractParty> getParticipants(){
        List<AbstractParty> participants = new ArrayList<>();
        participants.add(this.issuer);
        participants.add(this.owner);
        return participants;
    }
}