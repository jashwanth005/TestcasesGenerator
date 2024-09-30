package com.jiratestcases;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.net.URI;
import java.util.Iterator;

public class JiraOpenAIIntegration {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String JIRA_URL = dotenv.get("JIRA_URL");
    private static final String JIRA_USERNAME = dotenv.get("JIRA_USERNAME");
    private static final String JIRA_API_TOKEN = dotenv.get("JIRA_API_TOKEN");
    private static final String OPENAI_API_KEY = dotenv.get("OPENAI_API_KEY");
    private static final String OPENAI_API_URL = dotenv.get("OPENAI_API_URL");

    public static void main(String[] args) throws Exception {
        // Step 1: Fetch Jira Ticket
        String ticketId = dotenv.get("ticketId");  // Replace with actual ticket ID
        Issue issue = fetchJiraTicket(ticketId);
        String title = issue.getSummary();
        String description = issue.getDescription();

        // Step 2: Generate Test Cases using OpenAI
        String testCases = generateTestCasesWithOpenAI(title, description);

        // Step 3: Save the test cases to an Excel file
        saveTestCasesToExcel(testCases, "test_cases.xlsx");
    }

    // Function to fetch Jira ticket
    private static Issue fetchJiraTicket(String ticketId) throws Exception {
        URI jiraServerUri = new URI(JIRA_URL);
        AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
        JiraRestClient jiraRestClient = factory.createWithBasicHttpAuthentication(jiraServerUri, JIRA_USERNAME, JIRA_API_TOKEN);
        
        Issue issue = jiraRestClient.getIssueClient().getIssue(ticketId).claim();
        jiraRestClient.close();
        return issue;
    }

    // Function to call OpenAI API and generate test cases
    private static String generateTestCasesWithOpenAI(String title, String description) throws Exception {
        OkHttpClient client = new OkHttpClient();

        // Create request body for OpenAI API
        JSONObject requestBody = new JSONObject();
        JSONArray messages = new JSONArray();
        
        messages.put(new JSONObject().put("role", "system").put("content", "You are a QA engineer."));
        messages.put(new JSONObject().put("role", "user").put("content", "Based on the following Jira ticket, generate detailed test cases.\n\n" +
                "Title: " + title + "\nDescription: " + description + "\n\n" +
                "Provide test cases in this format:\n" +
                "- Test Case ID: TC001\n" +
                "- Scenario: [Test scenario here]\n" +
                "- Steps: \n    1. [Step 1]\n    2. [Step 2]\n" +
                "- Expected Result: [Expected result here]"));

        requestBody.put("model", "gpt-3.5-turbo");
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 500);
        requestBody.put("temperature", 0.5);

        Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .header("Authorization", "Bearer " + OPENAI_API_KEY)
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .build();

        // Execute request and get response
        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();

        // Parse the response
        JSONObject jsonResponse = new JSONObject(responseBody);
        return jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }

    // Function to save test cases to Excel file
    private static void saveTestCasesToExcel(String testCaseString, String filePath) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Test Cases");

        // Split the test case string into individual test cases
        String[] testCases = testCaseString.split("- Test Case ID: ");
        int rowNum = 0;

        // Create header row
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Test Case ID");
        headerRow.createCell(1).setCellValue("Scenario");
        headerRow.createCell(2).setCellValue("Steps");
        headerRow.createCell(3).setCellValue("Expected Result");

        // Process each test case
        for (String testCase : testCases) {
            if (testCase.trim().isEmpty()) continue;

            Row row = sheet.createRow(rowNum++);
            String[] lines = testCase.split("\n");
            String caseId = lines[0].trim();
            String scenario = "";
            String steps = "";
            String expectedResult = "";

            for (String line : lines) {
                if (line.contains("Scenario:")) {
                    scenario = line.split(":")[1].trim();
                } else if (line.startsWith("    1.")) {
                    steps = line.trim();
                } else if (line.startsWith("Expected Result:")) {
                    expectedResult = line.split(":")[1].trim();
                }
            }

            // Add data to Excel row
            row.createCell(0).setCellValue(caseId);
            row.createCell(1).setCellValue(scenario);
            row.createCell(2).setCellValue(steps);
            row.createCell(3).setCellValue(expectedResult);
        }

        // Save Excel file
        FileOutputStream fileOut = new FileOutputStream(filePath);
        workbook.write(fileOut);
        fileOut.close();
        workbook.close();

        System.out.println("Test cases saved to " + filePath);
    }
}
