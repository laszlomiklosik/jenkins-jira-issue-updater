package info.bluefloyd.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import info.bluefloyd.jira.SOAPClient;
import info.bluefloyd.jira.SOAPSession;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.atlassian.jira.rpc.soap.client.JiraSoapService;
import com.atlassian.jira.rpc.soap.client.RemoteIssue;

/**
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link JiraIssueUpdater} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 * 
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 * 
 * @author Laszlo Miklosik
 */
public class JiraIssueUpdater extends Builder {

	private final String soapUrl;
	private final String userName;
	private final String password;
	private final String jql;
	private final String workflowActionName;
	private final String comment;

	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public JiraIssueUpdater(String soapUrl, String userName, String password, String jql, String workflowActionName, String comment) {
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
		SOAPClient soapClient = new SOAPClient();
		SOAPSession soapSession = soapClient.connect(soapUrl, userName, password);
		JiraSoapService jiraSoapService = soapSession.getJiraSoapService();
		String authToken = soapSession.getAuthenticationToken();
		List<RemoteIssue> issues = soapClient.findIssuesByJQL(jiraSoapService, authToken, jql);
		listener.getLogger().println("Issues selected for update: ");
		for (RemoteIssue issue : issues) {
			listener.getLogger().println(issue.getKey() + "  \t" + issue.getSummary());
			if (!workflowActionName.trim().isEmpty()) {
				soapClient.updateIssueWorkflowStatus(jiraSoapService, authToken, issue.getKey(), workflowActionName);
			}
			if (!comment.trim().isEmpty()) {
				soapClient.addIssueComment(jiraSoapService, authToken, issue.getKey(), comment);
			}
		}
		return true;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link JiraIssueUpdater}. Used as a singleton. The class
	 * is marked as public so that it can be accessed from views.
	 * 
	 * <p>
	 * See
	 * <tt>src/main/resources/hudson/plugins/hello_world/JiraIssueUpdater/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	// This indicates to Jenkins that this is an implementation of an extension
	// point.
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
			if (!value.startsWith("http://") && !value.startsWith("https://")) {
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
			// Indicates that this builder can be used with all kinds of project
			// types
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
