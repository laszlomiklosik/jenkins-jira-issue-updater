package info.bluefloyd.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import info.bluefloyd.jenkins.SOAPClient;
import info.bluefloyd.jenkins.SOAPSession;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.atlassian.jira.rpc.soap.client.RemoteIssue;
import com.atlassian.jira.rpc.soap.client.RemoteVersion;
import java.util.ArrayList;

/**
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link IssueUpdatesBuilder} is created. The created instance is persisted to
 * the project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 * 
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 * 
 * @author Laszlo Miklosik
 */
public class IssueUpdatesBuilder extends Builder {

	private static final String BUILD_PARAMETER_PREFIX = "$";
	private static final String HTTP_PROTOCOL_PREFIX = "http://";
	private static final String HTTPS_PROTOCOL_PREFIX = "https://";
	// Delimiter separates fixed versions
	private static final String	DELIMITER	= ",";

	private final String soapUrl;
	private final String userName;
	private final String password;
	private final String jql;
	private final String workflowActionName;
	private final String comment;
	private final String customFieldId;
	private final String customFieldValue;
	private String realJql;
	private String realWorkflowActionName;
	private String realComment;
	private String realFieldValue;
	
	private boolean resettingFixedVersions;
	private String fixedVersions;
	private boolean failIfJqlFails;
	private boolean failIfNoIssuesReturned;
	transient List<String> fixedVersionNames;
	
	// Temporarily cache the version String-ID mapping for multiple
	// projects, to avoid performance penalty may be caused by excessive
	// getVersions() invocations.  
	// Map<ProjectKey, Map<VersionName, VersionID>>
	transient Map<String, Map<String, String>> projectVersionNameIdCache;

	@DataBoundConstructor
	public IssueUpdatesBuilder(String soapUrl, String userName, String password, String jql, String workflowActionName,
							   String comment, String customFieldId, String customFieldValue, boolean resettingFixedVersions,
							   String fixedVersions, boolean failIfJqlFails, boolean failIfNoIssuesReturned) {
		this.soapUrl = soapUrl;
		this.userName = userName;
		this.password = password;
		this.jql = jql;
		this.workflowActionName = workflowActionName;
		this.comment = comment;
		this.customFieldId = customFieldId;
		this.customFieldValue = customFieldValue;
		this.resettingFixedVersions = resettingFixedVersions;
		this.fixedVersions = fixedVersions;
		this.failIfJqlFails = failIfJqlFails;
		this.failIfNoIssuesReturned = failIfNoIssuesReturned;
	}

	/**
	 * @return the soapUrl
	 */
	public String getSoapUrl() {
		return soapUrl;
	}

	/**
	 * @return the userName
	 */
	public String getUserName() {
		return userName;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @return the jql
	 */
	public String getJql() {
		return jql;
	}

	/**
	 * @return the workflowActionName
	 */
	public String getWorkflowActionName() {
		return workflowActionName;
	}

	/**
	 * @return the comment
	 */
	public String getComment() {
		return comment;
	}
	
	public String getCustomFieldId() {
		return customFieldId;
	}

	public String getCustomFieldValue() {
		return customFieldValue;
	}

	public String getFixedVersions()
	{
		return fixedVersions;
	}

	public boolean isResettingFixedVersions()
	{
		return resettingFixedVersions;
	}

	public boolean isFailIfJqlFails() {
		return failIfJqlFails;
	}

	public boolean isFailIfNoIssuesReturned() {
		return failIfNoIssuesReturned;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
		PrintStream logger = listener.getLogger();       
		
		Map<String, String> vars = new HashMap<String, String>(); 
		vars.putAll(build.getEnvironment(listener));
		vars.putAll(build.getBuildVariables());
		
		substituteEnvVars( vars );
        
		SOAPClient client = new SOAPClient();
		SOAPSession session = client.connect(soapUrl, userName, password);
		if (session == null) {
			logger.println("Could not connect to Jira. The cause is one of the following: ");
			logger.println("- cannot reach Jira via the configured SOAP URL: " + soapUrl
					+ ". Make sure Jira is started, reachable from this machine, has SOAP enabled and the given SOAP url is correct.");
			logger.println("- the given Jira credentials are incorrect.");
			logger.println("You can find details on the exact problem in the Jenkins server logs.");
			return true;
		}

		List<RemoteIssue> issues = new ArrayList<RemoteIssue>();
		try {
			issues = client.findIssuesByJQL(session, realJql);
		} catch (JqlException e) {
			if (this.failIfJqlFails) {
				logger.println("Jira could not execute your JQL, '" + realJql + "': " + e.getMessage());
				return false;
			}
		}
		if (issues.isEmpty()) {
			logger.println("Your JQL, '" + realJql + "' did not return any issues. No issues will be updated during this build.");
			if (this.failIfNoIssuesReturned) {
				logger.println("Checkbox 'Fail this build if no issues are matched' checked, failing build");
				return false;
			}
		} else {
			if (realWorkflowActionName.isEmpty()) {
				logger.println("No workflow action was specified, thus no status update will be made for any of the matching issues.");
			}
			if (realComment.isEmpty()) {
				logger.println("No comment was specified, thus no comment will be added to any of the matching issues.");
			}
			logger.println("Using JQL: " + realJql);
			logger.println("The selected issues (" + issues.size() + " in total) are:");
		}

		// reset the cache
		projectVersionNameIdCache = new ConcurrentHashMap<String, Map<String,String>>();
		
		for (RemoteIssue issue : issues) {
			listener.getLogger().println(issue.getKey() + "  \t" + issue.getSummary());
			updateIssueStatus(client, session, issue, logger);
			addIssueComment(client, session, issue, logger);
			updateIssueField(client, session, issue, logger);
			updateFixedVersions(client, session, issue, logger);
		}
		return true;
	}

	void substituteEnvVars( Map<String, String> vars )
	{
		realJql = jql;
		realWorkflowActionName = workflowActionName;
		realComment = comment;
		realFieldValue = customFieldValue;
		String expandedFixedVersions = fixedVersions == null ? "" : fixedVersions.trim();
		
		// build parameter substitution
		for ( Entry<String, String> entry : vars.entrySet() ) {		
			realJql = substituteEnvVar( realJql, entry.getKey(), entry.getValue() );
			realWorkflowActionName = substituteEnvVar( realWorkflowActionName, entry.getKey(), entry.getValue() );
			realComment = substituteEnvVar( realComment, entry.getKey(), entry.getValue() );
			realFieldValue = substituteEnvVar( realFieldValue, entry.getKey(), entry.getValue() );
			expandedFixedVersions = substituteEnvVar( expandedFixedVersions, entry.getKey(), entry.getValue() );
		}
		// NOTE: did not trim
		fixedVersionNames = Arrays.asList( expandedFixedVersions.trim().split( DELIMITER ) );
	}
	
	String substituteEnvVar( String origin, String varName, String replacement ) {
		String key = BUILD_PARAMETER_PREFIX + varName;
		if( origin != null && origin.contains( key ) ) {
			return origin.replaceAll( Pattern.quote( key ), Matcher.quoteReplacement(replacement) );
		}
		return origin;
	}

	private void updateFixedVersions(SOAPClient client, SOAPSession session, RemoteIssue issue, PrintStream logger) {
		// NOT resettingFixedVersions and EMPTY fixedVersionNames: do not need to update the issue,
		// otherwise:
		if ( resettingFixedVersions || ! fixedVersionNames.isEmpty() ) {
			// add the ids of the fixed versions
			Collection<String> finalVersionIds = new HashSet<String>();
			if ( ! fixedVersionNames.isEmpty() ) {
				finalVersionIds.addAll( mapFixedVersionNamesToIds( client, session, issue.getProject(), fixedVersionNames, logger ) );
			}
			// if not reset origin fixed versions, then also merge their IDs to the set.
			if ( ! resettingFixedVersions ) {
				for( RemoteVersion ver : issue.getFixVersions() ) {
					finalVersionIds.add( ver.getId() );
				}
			}
			// do the update
			boolean updateSuccessful = client.updateFixedVersions( session, issue, finalVersionIds );
			if ( ! updateSuccessful) {
					logger.println("Could not update fixed versions for issue: "
							+ issue.getKey() + " to " + finalVersionIds
							+ ". For details on the exact problem consult the Jenkins server logs.");
			}
		}
	}
	
	/**
	 * Converts version names to IDs for the specified project.
	 * Non-existent versions are ignored, error messages are logged.
	 * <p>
	 * The jira soap api needs <code>ID</code>s of the fixed versions instead the human readable
	 * <code>name</code>s. The (fixed) versions are project specific.
	 * Since the issues found by <code>jql</code> do not necessarily belong to the
	 * same jira project. the versions must be retrieved for every single issue. 
	 * In some cases, however, the issues do belong to the same project,
	 * so the Soap call to get versions may well be redundant. Those unnecessary
	 * soap calls may cause performance problem if number of issues is large. 
	 * {@link #projectVersionNameIdCache} as a primitive cache, is intended to 
	 * improve the situation (could this cause concurrent issues?).
	 * </p>	 
	 * @param session
	 * @param projectKey	key of the project
	 * @param versionNames	a collection of human readable jira version names
	 * 			(jira built-in or configured per project)
	 * @return		corresponding jira version ids 	
	 */
	private Collection<String> mapFixedVersionNamesToIds( SOAPClient client, SOAPSession session, String projectKey, Collection<String> versionNames, PrintStream logger ) {
		// lazy fetching project versions and initializing the name-id map for the versions if necessary
		Map<String, String> map = projectVersionNameIdCache.get( projectKey ); 
		if ( map == null ) {
			map = new ConcurrentHashMap<String, String>();
			projectVersionNameIdCache.put( projectKey, map );
			List<RemoteVersion> versions = client.getVersions( session, projectKey );
			for ( RemoteVersion ver : versions ) {
				map.put( ver.getName(), ver.getId() );
			}
		}
		// getting the ids corresponding to the names
		Collection<String> ids = new HashSet<String>();
		for( String name : versionNames ){
			if ( name != null )
			{
				final String id = map.get( name.trim() );
				if ( id == null ) {
					logger.println( "Cannot find version " + name + " in project " + projectKey );
				} else {
					ids.add( id );
				}
			}
		}
		return ids;
	}

	private void updateIssueStatus(SOAPClient client, SOAPSession session, RemoteIssue issue, PrintStream logger) {
		boolean statusChangeSuccessful = false;
		if (!realWorkflowActionName.trim().isEmpty()) {
			statusChangeSuccessful = client.updateIssueWorkflowStatus(session, issue.getKey(), realWorkflowActionName);
			if (!statusChangeSuccessful) {
				logger.println("Could not update status for issue: "
						+ issue.getKey()
						+ ". The reason is likely that the Jira workflow scheme does not permit it. For details on the exact problem consult the Jenkins server logs.");
			}
		}
	}

	private void addIssueComment(SOAPClient client, SOAPSession session, RemoteIssue issue, PrintStream logger) {
		boolean addMessageSuccessful = false;
		if (!realComment.trim().isEmpty()) {
			addMessageSuccessful = client.addIssueComment(session, issue.getKey(), realComment);
			if (!addMessageSuccessful) {
				logger.println("Could not add message to issue " + issue.getKey());
			}
		}
	}

	private void updateIssueField(SOAPClient client, SOAPSession session, RemoteIssue issue, PrintStream logger) {
		boolean updateFieldSuccessful = false;
		if ( customFieldId != null && !customFieldId.trim().isEmpty()) {
			updateFieldSuccessful = client.updateIssueField(session, issue.getKey(), customFieldId, realFieldValue);
			if (!updateFieldSuccessful) {
				logger.println("Could not update field " + customFieldId + "for issue " + issue.getKey());
			}
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link IssueUpdatesBuilder}. Used as a singleton. The
	 * class is marked as public so that it can be accessed from views.
	 * 
	 * <p>
	 * See <tt>src/main/resources/jenkins/JiraIssueUpdatesBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		/**
		 * Performs on-the-fly validation of the form field 'soapUrl'.
		 * 
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		public FormValidation doCheckSoapUrl(@QueryParameter String value) throws IOException, ServletException {
			if (!value.startsWith(HTTP_PROTOCOL_PREFIX) && !value.startsWith(HTTPS_PROTOCOL_PREFIX)) {
				return FormValidation.error("The Jira URL is mandatory amd must start with http:// or https://");
			}
			return FormValidation.ok();
		}

		/**
		 * Performs on-the-fly validation of the form field 'userName'.
		 * 
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		public FormValidation doCheckUserName(@QueryParameter String value) throws IOException, ServletException {
			if (value.length() == 0) {
				return FormValidation.error("Please set the Jira user name to be used.");
			}
			if (value.length() < 3) {
				return FormValidation.warning("Isn't the user name too short?");
			}
			return FormValidation.ok();
		}

		/**
		 * Performs on-the-fly validation of the form field 'password'.
		 * 
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		public FormValidation doCheckPassword(@QueryParameter String value) throws IOException, ServletException {
			if (value.length() == 0) {
				return FormValidation.error("Please set the Jira user password to be used.");
			}
			if (value.length() < 3) {
				return FormValidation.warning("Isn't the password too short?");
			}

			return FormValidation.ok();
		}

		/**
		 * Performs on-the-fly validation of the form field 'Jql'.
		 * 
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		public FormValidation doCheckJql(@QueryParameter String value) throws IOException, ServletException {
			if (value.length() == 0) {
				return FormValidation.error("Please set the JQL used to select the issues to update.");
			}
			if (!value.toLowerCase().contains("project=")) {
				return FormValidation
						.warning("Is a project mentioned in the JQL? Using \"project=\" is recommended to select a the issues from a given project.");
			}
			if (!value.toLowerCase().contains("status=")) {
				return FormValidation
						.warning("Is an issue status mentioned in the JQL? Using \"status=\" is recommended to select the issues by status.");
			}
			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// This builder can be used with all kinds of project types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Jira Issue Updater";
		}
	}
}
