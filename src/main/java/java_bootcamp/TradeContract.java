package java_bootcamp;

import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.List;

public class TradeContract  implements Contract {

    public static String ID = "java_bootcamp.TradeContract";

    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {


        if(tx.getCommands().size() != 1){
            throw new IllegalArgumentException("Transaction must have one command");
        }
        Command command = tx.getCommand(0);
        List<PublicKey> requiredSigners = command.getSigners();
        CommandData commandType = command.getValue();

        if(commandType instanceof  Create){
            //TODO
            if(tx.getInputStates().size() != 0){
                throw new IllegalArgumentException("Transaction must have zero inputs");
            }
            if(tx.getOutputStates().size() != 1){
                throw new IllegalArgumentException("Transaction must have one output");
            }

            ContractState outputState = tx.getOutput(0);
            if( ! (outputState instanceof  TradeState)){
                throw new IllegalArgumentException("Output must be of TradeState type");
            }

            TradeState tradeState = (TradeState) outputState;

            if(tradeState.getInitiatingParty() == tradeState.getCounterparty()){
                throw new IllegalArgumentException("Both parties of trade can not bee same");
            }

            if(tradeState.getAmount().compareTo(BigDecimal.ZERO) == 0){
                throw new IllegalArgumentException("Amount cant be zero");
            }

            PublicKey initiatingPartySign = tradeState.getInitiatingParty().getOwningKey();
            PublicKey counterPartySign = tradeState.getCounterparty().getOwningKey();

            if(!(requiredSigners.contains(initiatingPartySign))){
                throw new IllegalArgumentException(" initiatingParty must sign Trade");
            }

            if(!(requiredSigners.contains(counterPartySign))){
                throw new IllegalArgumentException(" counterParty must sign Trade");
            }

        } else if(commandType instanceof  CounterTrade){
            //TODO

            if(tx.getInputStates().size() != 1){
                throw new IllegalArgumentException("Transaction must have one inputs");
            }

            if(tx.getOutputStates().size() != 1){
                throw new IllegalArgumentException("Transaction must have one output");
            }

            ContractState inputState = tx.getInput(0);
            if( ! (inputState instanceof  TradeState)){
                throw new IllegalArgumentException("Input must be of TradeState type");
            }

            ContractState outputState = tx.getOutput(0);
            if( ! (outputState instanceof  TradeState)){
                throw new IllegalArgumentException("Output must be of TradeState type");
            }

            TradeState inputTrade = (TradeState) inputState;
            TradeState outputTrade = (TradeState) outputState;

            if( inputTrade.getAmount().compareTo(outputTrade.getAmount()) != 0 ){
                throw new IllegalArgumentException("In CounterTrade amount should be same as original trade");
            }

            PublicKey initiatingPartySign = outputTrade.getInitiatingParty().getOwningKey();
            PublicKey counterPartySign = outputTrade.getCounterparty().getOwningKey();

            if(!(requiredSigners.contains(initiatingPartySign))){
                throw new IllegalArgumentException(" initiatingParty must sign counter Trade");
            }

            if(!(requiredSigners.contains(counterPartySign))){
                throw new IllegalArgumentException(" counterParty must sign counter Trade");
            }

        } else{
            throw new IllegalArgumentException("CommandType not recognized");
        }

    }

    public static class Create implements CommandData {}

    public static class CounterTrade implements  CommandData {}
}
