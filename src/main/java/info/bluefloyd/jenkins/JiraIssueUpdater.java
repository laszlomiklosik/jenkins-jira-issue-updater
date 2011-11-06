package info.bluefloyd.jenkins;

import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import info.bluefloyd.jira.SOAPClient;
import info.bluefloyd.jira.SOAPSession;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.atlassian.jira.rpc.soap.client.JiraSoapService;
import com.atlassian.jira.rpc.soap.client.RemoteField;
import com.atlassian.jira.rpc.soap.client.RemoteIssue;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

/**
 * Sample {@link Builder}.
 * 
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
 * @author Kohsuke Kawaguchi
 */
public class JiraIssueUpdater extends Builder {

	private final String soapUrl;
	private final String userName;
	private final String password;
	private final String jql;
	private final String workflowActionName;

	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public JiraIssueUpdater(String soapUrl, String userName, String password, String jql, String workflowActionName) {
		this.soapUrl = soapUrl;
		this.userName = userName;
		this.password = password;
		this.jql = jql;
		this.workflowActionName = workflowActionName;
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

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
		// This also shows how you can consult the global configuration of the
		// builder
		if (getDescriptor().useFrench())
			listener.getLogger().println("Bonjour, " + userName + "! " + workflowActionName);
		else {
			listener.getLogger().println("Hello, " + userName + "! " + workflowActionName );
			// TODO build complete logic for updating workflow status and adding comment for each matching issue!
			
			// 1. Retrieve issues for updating by JQL
			SOAPClient soapClient = new SOAPClient();
			SOAPSession soapSession = soapClient.connect(soapUrl, userName, password);
			JiraSoapService jiraSoapService = soapSession.getJiraSoapService();
			String authToken = soapSession.getAuthenticationToken();
			List<RemoteIssue> issues = soapClient.findIssuesByJQL(jiraSoapService, authToken, jql);
			for (RemoteIssue issue : issues) {
				listener.getLogger().println(issue.getKey() + "  \t" + issue.getSummary());
			}			

			// 2. update issue status
			// soapClient.updateIssueWorkflowStatus(jiraSoapService, authToken,
			// "PROJ-1748", WORKFLOW_ACTION_NAME);

			// 3. add issue comment
			// soapClient.addIssueComment(jiraSoapService, authToken, "PROJ-1748",
			// COMMENT_TEXT);

			// 4. get list of existing issue fields
			 List<RemoteField> fields = soapClient.getIssueFields(jiraSoapService,
			 authToken, "PROJ-1748");
			 for (RemoteField field : fields) {
				listener.getLogger().println("issue field id: " + field.getId());
			 }

			// 5. update some issue fields
			// Map<String, String []> issueFields = new HashMap<String, String[]>();
			// issueFields.put("environment", new String [] {"TEST"});
			// soapClient.updateIssueFields(jiraSoapService, authToken, "PROJ-1748",
			// issueFields);
		}
		return true;
	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
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
		 * To persist global configuration information, simply store it in a
		 * field and call save().
		 * 
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private boolean useFrench;
		
		
		// TODO add validation for each field!

		/**
		 * Performs on-the-fly validation of the form field 'soapUrl'.
		 * 
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		public FormValidation doCheckSoapUrl(@QueryParameter String value) throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set the Jira soap url");
			if (value.length() < 10)
				return FormValidation.warning("Isn't the url too short?");
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
			if (value.length() == 0)
				return FormValidation.error("Please set the Jira user name to be used.");
			if (value.length() < 4)
				return FormValidation.warning("Isn't the user name too short?");
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

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			useFrench = formData.getBoolean("useFrench");
			// ^Can also use req.bindJSON(this, formData);
			// (easier when there are many fields; need set* methods for this,
			// like setUseFrench)
			save();
			return super.configure(req, formData);
		}

		/**
		 * This method returns true if the global configuration says we should
		 * speak French.
		 */
		public boolean useFrench() {
			return useFrench;
		}
	}
}
