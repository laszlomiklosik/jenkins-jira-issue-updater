package info.bluefloyd.jenkins;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.atlassian.jira.rpc.soap.client.JiraSoapService;
import com.atlassian.jira.rpc.soap.client.RemoteAuthenticationException;
import com.atlassian.jira.rpc.soap.client.RemoteComment;
import com.atlassian.jira.rpc.soap.client.RemoteField;
import com.atlassian.jira.rpc.soap.client.RemoteFieldValue;
import com.atlassian.jira.rpc.soap.client.RemoteIssue;
import com.atlassian.jira.rpc.soap.client.RemoteNamedObject;
import com.atlassian.jira.rpc.soap.client.RemotePermissionException;

public class SOAPClient {

	private static final Log LOGGER = LogFactory.getLog(SOAPClient.class);
	private static final int MAX_NUMBER_OF_ISSUES_RETURNED = 1000;

	public SOAPSession connect(String jiraSoapWsUrl, String userName, String password) {
		SOAPSession soapSession;
		try {
			soapSession = new SOAPSession(new URL(jiraSoapWsUrl));
		} catch (MalformedURLException e1) {
			throw new RuntimeException("Invalid URL: " + jiraSoapWsUrl);
		}
		try {
			soapSession.connect(userName, password);
		} catch (RemoteAuthenticationException e) {
			LOGGER.info("Authentication to Jira failed!");
			throw new RuntimeException("Jira username or password is incorrect!");
		} catch (RemoteException e) {
			throw new RuntimeException("Could not connect to Jira via SOAP.");
		}
		return soapSession;
	}

	public List<RemoteIssue> findIssuesByJQL(JiraSoapService jiraSoapService, String token, String jql) {
		LOGGER.info("Searching for issues by JQL query: " + jql);
		RemoteIssue[] issuesFromTextSearch = null;
		try {
			issuesFromTextSearch = jiraSoapService.getIssuesFromJqlSearch(token, jql, MAX_NUMBER_OF_ISSUES_RETURNED + 1);
		} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
			throw new RuntimeException("Cannot execute Jira issue search by JQL: " + jql, e);
		} catch (RemoteException e) {
			throw new RuntimeException("Cannot execute Jira issue search by JQL: " + jql, e);
		}
		if (issuesFromTextSearch != null) {
			List<RemoteIssue> results = Arrays.asList(issuesFromTextSearch);
			if (issuesFromTextSearch.length > MAX_NUMBER_OF_ISSUES_RETURNED) {
				LOGGER.error("more than " + MAX_NUMBER_OF_ISSUES_RETURNED + " issues found, returning only the first "
						+ MAX_NUMBER_OF_ISSUES_RETURNED);
				return results.subList(0, MAX_NUMBER_OF_ISSUES_RETURNED);
			}
			return results;
		} else {
			return new ArrayList<RemoteIssue>();
		}
	}

	public void updateIssueWorkflowStatus(JiraSoapService jiraSoapService, String token, String issueKey, String workflowActionName) {
		LOGGER.info("Attempting to update status for issue: " + issueKey + " by executing workflow action: " + workflowActionName);
		RemoteNamedObject[] actions = null;
		try {
			actions = jiraSoapService.getAvailableActions(token, issueKey);
		} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
			LOGGER.error("Error getting available issue workflow actions", e);
		} catch (RemoteException e) {
			LOGGER.error("Error getting issue workflow actions", e);
		}
		boolean statusUpdated = false;
		if (actions != null) {
			for (RemoteNamedObject action : actions) {
				LOGGER.info(action.getName() + "\t id " + action.getId());
				if (action.getName().equalsIgnoreCase(workflowActionName)) {
					try {
						jiraSoapService.progressWorkflowAction(token, issueKey, action.getId(), null);
						statusUpdated = true;
						LOGGER.error("Successfully updated status for issue: " + issueKey);
					} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
						LOGGER.error("Error updating issue workflow status", e);
					} catch (RemoteException e) {
						LOGGER.error("Error updating issue workflow status", e);
					}
				}
			}
		}
		if (!statusUpdated) {
			LOGGER.error("Could not update status for issue: " + issueKey);
		}
	}

	public List<RemoteField> getIssueFields(JiraSoapService jiraSoapService, String token, String issueKey) {
		RemoteField[] availableFields = null;
		try {
			availableFields = jiraSoapService.getFieldsForEdit(token, issueKey);
		} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
			LOGGER.error("Error getting available issue fields", e);
		} catch (RemoteException e) {
			LOGGER.error("Error getting available issue fields", e);
		}
		if (availableFields != null) {
			return Arrays.asList(availableFields);
		} else {
			return new ArrayList<RemoteField>();
		}
	}

	public void updateIssueFields(JiraSoapService jiraSoapService, String token, String issueKey,
			Map<String, String[]> issueFieldNamesWithValues) {
		Set<RemoteFieldValue> fieldValues = new HashSet<RemoteFieldValue>();
		for (Map.Entry<String, String[]> entry : issueFieldNamesWithValues.entrySet()) {
			RemoteFieldValue fieldValue = new RemoteFieldValue(entry.getKey(), entry.getValue());
			fieldValues.add(fieldValue);
		}
		RemoteFieldValue[] remoteFieldValues = (RemoteFieldValue[]) fieldValues.toArray(new RemoteFieldValue[fieldValues.size()]);
		String errorMessage = "Error updating issue fields for issue: " + issueKey;
		try {
			jiraSoapService.updateIssue(token, issueKey, remoteFieldValues);
		} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
			LOGGER.error(errorMessage, e);
		} catch (RemoteException e) {
			LOGGER.error(errorMessage, e);
		}
	}

	public void addIssueComment(JiraSoapService jiraSoapService, String token, final String issueKey, String commentText) {
		RemoteComment comment = new RemoteComment();
		comment.setBody(commentText);
		String errorMessage = "Error adding  comment to issue: " + issueKey;
		try {
			jiraSoapService.addComment(token, issueKey, comment);
		} catch (RemotePermissionException e) {
			LOGGER.error(errorMessage, e);
		} catch (RemoteAuthenticationException e) {
			LOGGER.error(errorMessage, e);
		} catch (com.atlassian.jira.rpc.soap.client.RemoteException e) {
			LOGGER.error(errorMessage, e);
		} catch (RemoteException e) {
			LOGGER.error(errorMessage, e);
		}
	}
}
