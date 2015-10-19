package info.bluefloyd.jenkins;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.core.util.Base64Encoder;
import info.bluefloyd.jira.model.IssueSummary;
import info.bluefloyd.jira.model.IssueSummaryList;
import info.bluefloyd.jira.model.RestResult;
import info.bluefloyd.jira.model.TransitionList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Simple generic REST client based on native HTTP. Also contains a logic layer
 * to allow easy interaction with the JIRA REST API.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class RESTClient {

  // REST paths for the calls we want to make - suffixed onto the "restAPIUrl
  private static final String REST_SEARCH_PATH = "/search?jql";
  private static final String REST_ADD_COMMENT_PATH = "/issue/{issue-key}/comment";
  private static final String REST_UPDATE_STATUS_PATH = "/issue/{issue-key}/transitions";
  private static final String REST_UPDATE_FIELD_PATH = "/issue/{issue-key}";

  private final String baseAPIUrl;
  private final String userName;
  private final String password;
  private final PrintStream logger;
  private final boolean debug = false;

  // Constructor - set up required information
  public RESTClient(String baseAPIUrl, String userName, String password, PrintStream logger) {
    this.baseAPIUrl = baseAPIUrl;
    this.userName = userName;
    this.password = password;
    this.logger = logger;
  }

  /**
   * Get back a minimal list of the issues we are interested in, as determined
   * by the given JQL. We only recover the first 10 issues, and more than that
   * is likely to be a mistake.
   *
   * @param jql
   * @return The list of issues
   * @throws MalformedURLException
   * @throws IOException
   */
  public IssueSummaryList findIssuesByJQL(String jql) throws MalformedURLException, IOException {

    URL findIssueURL = new URL(baseAPIUrl + REST_SEARCH_PATH);

    if (debug) {
      logger.println("***Using this URL for finding the issues: " + findIssueURL.toString());
    }

    String bodydata = "{"
            + "    \"jql\": \"" + jql + "\",\n"
            + "    \"startAt\": 0,\n"
            + "    \"maxResults\": 10,\n"
            + "    \"fields\": [\n"
            + "        \"summary\"\n"
            + "    ]\n"
            + "}";

    RestResult result = doPost(findIssueURL, userName, password, bodydata);

    ObjectMapper mapper = new ObjectMapper();
    IssueSummaryList summaryList = mapper.readValue(result.getResultMessage(), IssueSummaryList.class);

    return summaryList;
  }

  /**
   * Update the status of a given issue.
   *
   * @param issue The issue we want to update
   * @param realWorkflowActionName The target status
   * @throws MalformedURLException
   */
  public void updateIssueStatus(IssueSummary issue, String realWorkflowActionName) throws MalformedURLException {
    String transitionPath = REST_UPDATE_STATUS_PATH.replaceAll("\\{issue-key\\}", issue.getKey());
    URL transitionURL = new URL(baseAPIUrl + transitionPath);

    if (debug) {
      logger.println("***Using this URL for finding the transition: " + transitionURL.toString());
    }

    if (!realWorkflowActionName.trim().isEmpty()) {

      // See if the issue can transition to the given status, and transition
      try {
        RestResult result = doGet(transitionURL, userName, password);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        TransitionList possibleTransition = mapper.readValue(result.getResultMessage(), TransitionList.class);

        // See if we can find the required transition in the list
        if (possibleTransition.containsTransition(realWorkflowActionName)) {
          Integer targetTransitionId = possibleTransition.getTransitionId(realWorkflowActionName);
          String bodydata = "{"
                  + "    \"transition\": \"" + targetTransitionId + "\"\n"
                  + "}";

          // There is a result, but we don't care
          result = doPost(transitionURL, userName, password, bodydata);
          
          if (!result.isValidResult()) {
            logger.println("Could not update status for issue: " + issue.getKey() + ". Cause: " + result.getResultMessage());
          }
        } else {
          logger.println("Not possible to transtion " + issue.getKey() + " to status " + realWorkflowActionName);
          logger.println("Possible transtions:" + possibleTransition.getTransitions().toString());
        }
      } catch (IOException e) {
        logger.println("Could find transitions for issue " + issue.getKey() + ". Cause: " + e.getMessage());
      }
    }
  }

  /**
   * Add a comment to an issue.
   *
   * @param issue The issue to update
   * @param realComment The comment text to add
   * @throws MalformedURLException
   * @throws IOException
   */
  public void addIssueComment(IssueSummary issue, String realComment) throws MalformedURLException, IOException {

    String issuePath = REST_ADD_COMMENT_PATH.replaceAll("\\{issue-key\\}", issue.getKey());
    URL addCommentURL = new URL(baseAPIUrl + issuePath);

    if (debug) {
      logger.println("***Using this URL for adding the comment: " + addCommentURL.toString());
    }

    if (!realComment.trim().isEmpty()) {
      try {
        String bodydata = "{"
                + "    \"body\": \"" + realComment + "\"\n"
                + "}";

        RestResult result = doPost(addCommentURL, userName, password, bodydata);
        
        if (!result.isValidResult()) {
          logger.println("Could not set comment " + realComment + " in issue " + issue.getKey() + ". Cause: " + result.getResultMessage());
        }
      } catch (IOException e) {
        logger.println("Could not add message to issue " + issue.getKey() + ". Cause: " + e.getMessage());
      }
    }
  }

  /**
   * Update the status of the given field to the given value.
   *
   * @param issue
   * @param customFieldId The field we are trying to change
   * @param realFieldValue The new value
   * @throws MalformedURLException
   */
  public void updateIssueField(IssueSummary issue, String customFieldId, String realFieldValue) throws MalformedURLException {
    String setFieldsPath = REST_UPDATE_FIELD_PATH.replaceAll("\\{issue-key\\}", issue.getKey());
    URL setFieldsURL = new URL(baseAPIUrl + setFieldsPath);

    if (debug) {
      logger.println("***Using this URL for adding the comment: " + setFieldsURL.toString());
    }

    if (customFieldId != null && !customFieldId.trim().isEmpty()) {
      try {
        String bodydata = "{\n"
                + "    \"fields\": {\n"
                + "        \"" + customFieldId + "\": \"" + realFieldValue + "\"\n"
                + "    }\n"
                + "}";

        RestResult result = doPut(setFieldsURL, userName, password, bodydata);
        
        if (!result.isValidResult()) {
          logger.println("Could not set field " + customFieldId + " in issue " + issue.getKey() + ". Cause: " + result.getResultMessage());
        }
      } catch (IOException e) {
        logger.println("Could not set field " + customFieldId + " in issue " + issue.getKey() + ". Cause: " + e.getMessage());
      }
    }
  }

//  private void updateFixedVersions(SOAPClient client, SOAPSession session, RemoteIssue issue, PrintStream logger) {
//    // NOT resettingFixedVersions and EMPTY fixedVersionNames: do not need to update the issue,
//    // otherwise:
//    if (resettingFixedVersions || !fixedVersionNames.isEmpty()) {
//
//      //Copy The Given Remote ID's to be applied to a local Variable. In case Old Versions should be kept we need to add them here
//      Collection<String> finalVersionIds = new HashSet<String>();
//      if (!fixedVersionNames.isEmpty()) {
//        finalVersionIds.addAll(mapFixedVersionNamesToIds(client, session, issue.getProject(), fixedVersionNames, logger));
//      }
//
//      // if not reset origin fixed versions, then also merge their IDs to the set.
//      if (!resettingFixedVersions) {
//        for (RemoteVersion ver : issue.getFixVersions()) {
//          finalVersionIds.add(ver.getId());
//        }
//      }
//      // do the update
//      boolean updateSuccessful = client.updateFixedVersions(session, issue, finalVersionIds);
//      if (!updateSuccessful) {
//        logger.println("Could not update fixed versions for issue: "
//                + issue.getKey() + " to " + finalVersionIds
//                + ". For details on the exact problem consult the Jenkins server logs.");
//      }
//    }
//  }
  /**
   * Converts version names to IDs for the specified project. Non-existent
   * versions are ignored, error messages are logged.
   * <p>
   * The jira soap api needs <code>ID</code>s of the fixed versions instead the
   * human readable <code>name</code>s. The (fixed) versions are project
   * specific. Since the issues found by <code>jql</code> do not necessarily
   * belong to the same jira project. the versions must be retrieved for every
   * single issue. In some cases, however, the issues do belong to the same
   * project, so the Soap call to get versions may well be redundant. Those
   * unnecessary soap calls may cause performance problem if number of issues is
   * large. {@link #projectVersionNameIdCache} as a primitive cache, is intended
   * to improve the situation (could this cause concurrent issues?).
   * </p>
   *
   * @param session
   * @param projectKey	key of the project
   * @param versionNames	a collection of human readable jira version names (jira
   * built-in or configured per project)
   * @return	corresponding jira version ids
   */
//  private Collection<String> mapFixedVersionNamesToIds(SOAPClient client, SOAPSession session, String projectKey, Collection<String> versionNames, PrintStream logger) {
//    // lazy fetching project versions and initializing the name-id map for the versions if necessary
//    Map<String, String> map = projectVersionNameIdCache.get(projectKey);
//    if (map == null) {
//      map = new ConcurrentHashMap<String, String>();
//      projectVersionNameIdCache.put(projectKey, map);
//      List<RemoteVersion> versions = client.getVersions(session, projectKey);
//      for (RemoteVersion ver : versions) {
//        map.put(ver.getName(), ver.getId());
//      }
//    }
//    // getting the ids corresponding to the names
//    Collection<String> ids = new HashSet<String>();
//    for (String name : versionNames) {
//      if (name != null) {
//        final String id = map.get(name.trim());
//        if (id == null) {
//          if (createNonExistingFixedVersions) {
//            logger.println("Creating Non-existent version " + name + " in project " + projectKey);
//            RemoteVersion newVersion = client.addVersion(session, projectKey, name);
//            if (newVersion.getId() != null) {
//              ids.add(newVersion.getId());
//              map.put(name, newVersion.getId());
//            } else {
//              logger.println("There was a problem creating Version " + name + " in project " + projectKey);
//            }
//          } else {
//            logger.println("Cannot find version " + name + " in project " + projectKey);
//          }
//        } else {
//          ids.add(id);
//        }
//      }
//    }
//    return ids;
//  }
  // ---------------------------------------------------------------------------
  // Generic REST call implementations
  // ---------------------------------------------------------------------------
  /**
   * Perform a GET action on the given URL with the credentials. Deemed success
   * if the result code is 200 or 201.
   *
   * @param url The full REST URL to use
   * @param userName The user name to use
   * @param password The password to use
   * @return The REST response
   * @throws IOException
   */
  private RestResult doGet(URL url, String userName, String password) throws IOException {

    RestResult result = new RestResult();
    
    String rawAuth = userName + ":" + password;
    Base64Encoder encoder = new Base64Encoder();
    String basicAuthToken = "Basic " + encoder.encode(rawAuth.getBytes("UTF-8"));

    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Accept", "application/json");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", basicAuthToken);

    BufferedReader br = new BufferedReader(new InputStreamReader(
            (conn.getInputStream())));

    StringBuilder output = new StringBuilder();
    String outputLine;
    while ((outputLine = br.readLine()) != null) {
      output.append(outputLine);
    }

    result.setResultCode(conn.getResponseCode());
    result.setResultMessage(output.toString());
    
    if ((conn.getResponseCode() == 200) || (conn.getResponseCode() == 201)) {
      result.setValidResult(true);
    }

    conn.disconnect();

    return result;
  }

  /**
   * Perform a POST action on the given URL with the credentials and body.
   * Deemed success if the result code is 200 or 201.
   *
   * @param url The full REST URL to use
   * @param userName The user name to use
   * @param password The password to use
   * @param bodydata The post body we are using
   * @return The REST response
   * @throws IOException
   */
  private RestResult doPost(URL url, String userName, String password, String bodydata) throws IOException {

    RestResult result = new RestResult();
    
    byte[] postDataBytes = bodydata.getBytes("UTF-8");

    String rawAuth = userName + ":" + password;
    Base64Encoder encoder = new Base64Encoder();
    String basicAuthToken = "Basic " + encoder.encode(rawAuth.getBytes("UTF-8"));

    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Accept", "application/json");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", basicAuthToken);
    conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

    conn.setDoOutput(true);
    OutputStream os = conn.getOutputStream();
    os.write(postDataBytes);
    os.flush();

    BufferedReader br = new BufferedReader(new InputStreamReader(
            (conn.getInputStream())));

    StringBuilder output = new StringBuilder();
    String outputLine;
    while ((outputLine = br.readLine()) != null) {
      output.append(outputLine);
    }

    result.setResultCode(conn.getResponseCode());
    result.setResultMessage(output.toString());
    
    if ((conn.getResponseCode() == 200) || (conn.getResponseCode() == 201)) {
      result.setValidResult(true);
    }
    
    conn.disconnect();

    return result;
  }

  /**
   * Perform a PUT action on the given URL with the credentials and body. Deemed
   * success if the result code is 200 or 204.
   *
   * @param url The full REST URL to use
   * @param userName The user name to use
   * @param password The password to use
   * @param bodydata The post body we are using
   * @return The REST response
   * @throws IOException
   */
  private RestResult doPut(URL url, String userName, String password, String bodydata) throws IOException {

    RestResult result = new RestResult();
    byte[] postDataBytes = bodydata.getBytes("UTF-8");

    String rawAuth = userName + ":" + password;
    Base64Encoder encoder = new Base64Encoder();
    String basicAuthToken = "Basic " + encoder.encode(rawAuth.getBytes("UTF-8"));

    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("PUT");
    conn.setRequestProperty("Accept", "application/json");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", basicAuthToken);
    conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

    conn.setDoOutput(true);
    OutputStream os = conn.getOutputStream();
    os.write(postDataBytes);
    os.flush();

    BufferedReader br = new BufferedReader(new InputStreamReader(
            (conn.getInputStream())));

    StringBuilder output = new StringBuilder();
    String outputLine;
    while ((outputLine = br.readLine()) != null) {
      output.append(outputLine);
    }

    result.setResultCode(conn.getResponseCode());
    result.setResultMessage(output.toString());
    
    if ((conn.getResponseCode() == 200) || (conn.getResponseCode() == 204)) {
      result.setValidResult(true);
    }
    
    conn.disconnect();

    return result;
  }
}
