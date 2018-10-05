package com.example;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Hex;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.core.methods.response.Web3ClientVersion;
import org.web3j.protocol.http.HttpService;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNS;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;

public class Process implements RequestHandler<SNSEvent, Object> {

	static final String HEX_CHARACTERS = "0123456789abcdef";
	static SecureRandom rnd = new SecureRandom();

	/*
	 * Function main() for running local.
	 */
	public static void main(String[] args) {
		// Create an instance of this class for testing
		Process test = new Process();

		SNS sns = new SNS();
		sns.setMessage("REPLACE_THIS_VALUE");

		SNSEvent data = new SNSEvent();
		SNSRecord record = new SNSRecord();
		record.setSns(sns);
		List<SNSRecord> records = new ArrayList<SNSRecord>();
		records.add(record);
		data.setRecords(records);

		// Submit the data
		System.out.println(test.handleRequest(data, null));
	}

	/*
	 * Initialise the logger. This is not necessarily needed but if we don't
	 * initialise this the Web3J framework will spawn a lot of noisy logs.
	 */
	public void initLogger() {
		ConsoleAppender console = new ConsoleAppender();
		String PATTERN = "%d [%p|%c|%C{1}] %m%n";
		console.setLayout(new PatternLayout(PATTERN));
		console.setThreshold(Level.FATAL);
		console.activateOptions();
		Logger.getRootLogger().addAppender(console);
	}

	/*
	 * Generate a document reference. This is based on the current time plus four
	 * random characters.
	 */
	public String getReference() {
		long now = Instant.now().toEpochMilli();
		String ref = Long.toHexString(now);
		String random = "";

		for (int i = 0; i < 4; i++) {
			random = random + HEX_CHARACTERS.charAt(rnd.nextInt(HEX_CHARACTERS.length()));
		}

		return ref + random;
	}

	public String handleRequest(SNSEvent request, Context context) {
		initLogger();
		String message = request.getRecords().get(0).getSNS().getMessage();

		AmazonDynamoDB client = null;
		if (context == null) {
			BasicAWSCredentials awsCreds = new BasicAWSCredentials(Constants.API_KEY, Constants.API_SECRET);
			client = AmazonDynamoDBClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(awsCreds))
					.withRegion(Constants.REGION).build();
		} else {
			client = AmazonDynamoDBClientBuilder.standard().withRegion(Constants.REGION).build();
		}

		DynamoDB dynamoDB = new DynamoDB(client);

		Table table = dynamoDB.getTable("documents");
		QuerySpec spec = new QuerySpec().withKeyConditionExpression("id = :v_id")
				.withValueMap(new ValueMap().withString(":v_id", message));

		ItemCollection<QueryOutcome> items = table.query(spec);

		Iterator<Item> iterator = items.iterator();
		Item item = null;
		while (iterator.hasNext()) {
			item = iterator.next();

			// Generate a SHA3 hash from the text
			SHA3.DigestSHA3 digestSHA3 = new SHA3.Digest256();
			byte[] digest = digestSHA3.digest(item.get("text").toString().getBytes());
			String hash = Hex.toHexString(digest);

			// Connect to Ropsten network
			Web3j web3 = Web3j.build(new HttpService("https://ropsten.infura.io/v3/" + Constants.INFURA_KEY));
			Web3ClientVersion web3ClientVersion;
			try {
				web3ClientVersion = web3.web3ClientVersion().send();
				String clientVersion = web3ClientVersion.getWeb3ClientVersion();
				System.out.println("Connected to client version: " + clientVersion);

				// Define address credentials for using
				Credentials credentials = Credentials.create(Constants.PRIVATE_KEY);

				// Initialise the Validator contract
				Validator contract = Validator.load(Constants.CONTRACT_ADDRESS, web3, credentials, Constants.GAS_PRICE,
						Constants.GAS_LIMIT);

				// Execute the add function of our contract
				TransactionReceipt result = contract.add(item.get("id").toString(), hash).send();
				System.out.println("Submitted as: " + result.getTransactionHash());

				UpdateItemSpec updateItemSpec = new UpdateItemSpec().withPrimaryKey("id", item.get("id").toString())
						.withUpdateExpression("set #hash = :v_hash").withNameMap(new NameMap().with("#hash", "hash"))
						.withValueMap(new ValueMap().withString(":v_hash", hash)).withReturnValues(ReturnValue.ALL_NEW);

				table.updateItem(updateItemSpec);

			} catch (Exception e) {
				System.out.println("Error executing the function.");
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				String sStackTrace = sw.toString(); // stack trace as a string
				System.out.println(sStackTrace);

				return null;
			}

		}

		return message;
	}
}
