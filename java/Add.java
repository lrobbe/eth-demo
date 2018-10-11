package com.example;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Add implements RequestHandler<Map<String, String>, String> {

	static final String HEX_CHARACTERS = "0123456789abcdef";
	static SecureRandom rnd = new SecureRandom();

	/*
	 * Function main() for running local.
	 */
	public static void main(String[] args) {
		// Create an instance of this class for testing
		Add test = new Add();

		// Create a hashmap with some test data
		Map<String, String> data = new LinkedHashMap<String, String>();
		data.put("text", "Lorem ipsum dolor sit amet, consectetur adipiscing elit,...");

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

	public String handleRequest(Map<String, String> event, Context context) {
		initLogger();

		String reference = getReference();
		String message = event.get("text");

		// Put the text hash and document reference in map
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode data = mapper.createArrayNode();
		ObjectNode node = mapper.createObjectNode();
		node.put("id", reference);
		data.add(node);

		try {
			// Initialise the client.
			AmazonDynamoDB dbClient = null;
			AmazonSNS snsClient = null;

			if (context == null) {
				BasicAWSCredentials awsCreds = new BasicAWSCredentials(Constants.API_KEY, Constants.API_SECRET);
				dbClient = AmazonDynamoDBClientBuilder.standard()
						.withCredentials(new AWSStaticCredentialsProvider(awsCreds)).withRegion(Constants.REGION)
						.build();
				snsClient = AmazonSNSClientBuilder.standard()
						.withCredentials(new AWSStaticCredentialsProvider(awsCreds)).withRegion(Constants.REGION)
						.build();
			} else {
				dbClient = AmazonDynamoDBClientBuilder.standard().withRegion(Constants.REGION).build();
				snsClient = AmazonSNSClientBuilder.standard().withRegion(Constants.REGION).build();
			}

			// Write to DynamoDB
			DynamoDB dynamoDB = new DynamoDB(dbClient);
			Table table = dynamoDB.getTable("documents");

			Item item = new Item().withPrimaryKey("id", reference).withString("hash", "processing").withString("text",
					message);

			table.putItem(item);

			// Add to SNS
			PublishRequest publishRequest = new PublishRequest(Constants.SNS_ARN, reference);
			PublishResult publishResult = snsClient.publish(publishRequest);
			System.out.println("Message submitted: " + publishResult.getMessageId());
		} catch (Exception e) {
			System.out.println("Error executing the function.");
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String sStackTrace = sw.toString(); 
			System.out.println(sStackTrace);
			
			return null;
		} 

		// Return the document reference map
		String response;
		try {
			response = mapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			response = "{}";
		}

		return response;
	}
}
