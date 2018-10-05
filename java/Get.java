package com.example;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
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
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Get implements RequestHandler<Map<String, String>, String> {

	/*
	 * Function main() for running local
	 */
	public static void main(String[] args) {
		Get test = new Get();

		// Create a hashmap with some test data
		Map<String, String> data = new LinkedHashMap<String, String>();
		data.put("id", "*");

		// Request all references from the database
		System.out.println(test.handleRequest(data, null));

		// Create a new hashmap: replace the id here
		data = new LinkedHashMap<String, String>();
		data.put("id", "REPLACE_THIS_VALUE");

		// Request element
		System.out.println(test.handleRequest(data, null));

		// Replace the reference here
		data = new LinkedHashMap<String, String>();
		data.put("reference", "REPLACE_THIS_VALUE");
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

	public String handleRequest(Map<String, String> event, Context context) {
		initLogger();

		if (event.get("reference") != null && !event.get("reference").isEmpty()) {
			String message = event.get("reference");

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

				// Execute the get function of our contract
				String result = contract.get(message).send();
				return "[{\"hash\": \"" + result + "\"}]";
			} catch (Exception e) {
				System.out.println("Error executing the function");
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				String sStackTrace = sw.toString(); // stack trace as a string
				System.out.println(sStackTrace);
			}
			return null;
		}
		if (event.get("id") != null) {
			String message = (String) event.get("id");

			// Initialise the client.
			AmazonDynamoDB client = null;
			if (context == null) {
				BasicAWSCredentials awsCreds = new BasicAWSCredentials(Constants.API_KEY, Constants.API_SECRET);
				client = AmazonDynamoDBClientBuilder.standard()
						.withCredentials(new AWSStaticCredentialsProvider(awsCreds)).withRegion(Constants.REGION)
						.build();
			} else {
				client = AmazonDynamoDBClientBuilder.standard().withRegion(Constants.REGION).build();
			}

			// The data variable, contains return value
			ObjectMapper mapper = new ObjectMapper();
			ArrayNode data = mapper.createArrayNode();

			if (message.equals("*")) {
				// In case we request all
				ScanRequest scanRequest = new ScanRequest().withTableName("documents");

				ScanResult result = client.scan(scanRequest);
				for (Map<String, AttributeValue> item : result.getItems()) {
					ObjectNode node = mapper.createObjectNode();
					node.put("id", item.get("id").getS());
					node.put("hash", item.get("hash").getS());
					node.put("text", item.get("text").getS());
					data.add(node);
				}

			} else {
				// In case we request one document
				DynamoDB dynamoDB = new DynamoDB(client);

				Table table = dynamoDB.getTable("documents");
				QuerySpec spec = new QuerySpec().withKeyConditionExpression("id = :v_id")
						.withValueMap(new ValueMap().withString(":v_id", message));

				ItemCollection<QueryOutcome> items = table.query(spec);

				Iterator<Item> iterator = items.iterator();
				Item item = null;
				while (iterator.hasNext()) {
					item = iterator.next();
					ObjectNode node = mapper.createObjectNode();
					node.put("id", item.get("id").toString());
					node.put("hash", item.get("hash").toString());
					node.put("text", item.get("text").toString());
					data.add(node);
				}
			}

			// Return the documents
			String response;
			try {
				response = mapper.writeValueAsString(data);
			} catch (JsonProcessingException e) {
				response = "{}";
			}

			return response;
		}

		return null;
	}
}