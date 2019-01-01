package com.dcrandroid.util;

import com.dcrandroid.data.Constants;
import com.dcrandroid.data.TransactionResponse;
import com.dcrandroid.data.TransactionResponse.Transaction;
import com.dcrandroid.data.TransactionResponse.Transaction.TransactionInput;
import com.dcrandroid.data.TransactionResponse.Transaction.TransactionOutput;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by collins on 1/10/18.
 */

public class TransactionsParser {

    private TransactionsParser() {
    }

    public static TransactionResponse parseTransactions(String json) {
        TransactionResponse response = new TransactionResponse();

        try {
            JSONObject transactions = new JSONObject(json);
            JSONArray minedTransactionsArray = transactions.getJSONArray(Constants.MINED_TRANSACTIONS);
            JSONArray unminedTransactionsArray = transactions.getJSONArray(Constants.UNMINED_TRANSACTIONS);

            ArrayList<Transaction> tempList = new ArrayList<>();
            for (int i = 0; i < minedTransactionsArray.length(); i++) {
                Transaction item = parseTransaction(minedTransactionsArray.get(i).toString());
                tempList.add(item);
            }

            response.getMinedTransactions().addAll(tempList);
            tempList.clear();

            for (int i = 0; i < unminedTransactionsArray.length(); i++) {
                Transaction item = parseTransaction(unminedTransactionsArray.get(i).toString());
                tempList.add(item);
            }

            response.getUnminedTransactions().addAll(tempList);
            tempList.clear();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;
    }

    public static Transaction parseTransaction(String json) throws JSONException {
        JSONObject tx = new JSONObject(json);

        Transaction transaction = new Transaction();
        ArrayList<TransactionOutput> outputs = new ArrayList<>();
        JSONArray cdt = tx.getJSONArray(Constants.CREDITS);
        for (int j = 0; j < cdt.length(); j++) {
            TransactionOutput output = new Transaction.TransactionOutput();
            output.setAccount(cdt.getJSONObject(j).getInt(Constants.ACCOUNT));
            output.setInternal(cdt.getJSONObject(j).getBoolean(Constants.INTERNAL));
            output.setAddress(cdt.getJSONObject(j).getString(Constants.ADDRESS));
            output.setIndex(cdt.getJSONObject(j).getInt(Constants.INDEX));
            output.setAmount(cdt.getJSONObject(j).getLong(Constants.AMOUNT));
            transaction.setTotalOutput(transaction.getTotalOutput() + output.getAmount());
            outputs.add(output);
        }
        ArrayList<TransactionInput> inputs = new ArrayList<>();
        JSONArray dbt = tx.getJSONArray(Constants.DEBITS);
        for (int j = 0; j < dbt.length(); j++) {
            TransactionInput input = new TransactionInput();
            input.setIndex(dbt.getJSONObject(j).getInt(Constants.INDEX));
            input.setPreviousAccount(dbt.getJSONObject(j).getLong(Constants.PREVIOUS_ACCOUNT));
            input.setPreviousAmount(dbt.getJSONObject(j).getLong(Constants.PREVIOUS_AMOUNT));
            input.setAccountName(dbt.getJSONObject(j).getString(Constants.ACCOUNT_NAME));
            transaction.setTotalInput(transaction.getTotalInput() + input.getPreviousAmount());
            inputs.add(input);
        }
        transaction.setFee(tx.getLong(Constants.FEE));
        transaction.setHash(tx.getString(Constants.HASH));
        transaction.setRaw(tx.getString(Constants.RAW));
        transaction.setTimestamp(tx.getLong(Constants.TIMESTAMP));
        transaction.setType(tx.getString(Constants.TYPE));
        transaction.setHeight(tx.getInt(Constants.HEIGHT));
        transaction.setDirection(tx.getInt(Constants.DIRECTION));
        transaction.setOutputs(outputs);
        transaction.setInputs(inputs);
        transaction.setAmount(tx.getLong(Constants.AMOUNT));
        return transaction;
    }
}
