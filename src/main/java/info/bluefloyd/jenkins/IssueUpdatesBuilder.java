package info.bluefloyd.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.atlassian.jira.rpc.soap.client.RemoteIssue;

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

	private static final String HTTP_PROTOCOL_PREFIX = "http://";
	private static final String HTTPS_PROTOCOL_PREFIX = "https://";

	private final String soapUrl;
	private final String userName;
	private final String password;
	private final String jql;
	private final String workflowActionName;
	private final String comment;

	@DataBoundConstructor
	public IssueUpdatesBuilder(String soapUrl, String userName, String password, String jql, String workflowActionName, String comment) {
		this.soapUrl = soapUrl;
		this.userName = userName;
		this.password = password;
		this.jql = jql;
		this.workflowActionName = workflowActionName;
		this.comment = comment;
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

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		PrintStream logger = listener.getLogger();
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
		List<RemoteIssue> issues = client.findIssuesByJQL(session, jql);
		if (issues.isEmpty()) {
			logger.println("Your JQL, '" + jql + "' did not return any issues. No issues will be updated during this build.");
		} else {
			logger.println("Issues selected for update: ");
		}

		for (RemoteIssue issue : issues) {
			listener.getLogger().println(issue.getKey() + "  \t" + issue.getSummary());
			updateIssueStatus(client, session, issue, logger);
			addIssueComment(client, session, issue, logger);
		}
		return true;
	}

	private void updateIssueStatus(SOAPClient client, SOAPSession session, RemoteIssue issue, PrintStream logger) {
		boolean statusChangeSuccessful = false;
		if (!workflowActionName.trim().isEmpty()) {
			statusChangeSuccessful = client.updateIssueWorkflowStatus(session, issue.getKey(), workflowActionName);
			if (!statusChangeSuccessful) {
				logger.println("Could not update status for issue: " + issue.getKey()
						+ ". The reason is likely that the Jira workflow scheme does not permit it. For details on the exact problem consult the Jenkins server logs.");
			}
		}
	}

	private void addIssueComment(SOAPClient client, SOAPSession session, RemoteIssue issue, PrintStream logger) {
		boolean addMessageSuccessful = false;
		if (!comment.trim().isEmpty()) {
			addMessageSuccessful = client.addIssueComment(session, issue.getKey(), comment);
			if (!addMessageSuccessful) {
				logger.println("Could not add message to issue " + issue.getKey());
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
