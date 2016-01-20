package info.bluefloyd.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.ModelObject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import info.bluefloyd.jira.model.IssueSummary;
import info.bluefloyd.jira.model.IssueSummaryList;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Results Recorder Action equivalent of the build step "IssueUpdatesBuilder".
 * Publishes the same functionality in the Post Build Actions part of the 
 * Jenkins build.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class IssueUpdaterResultsRecorder extends Recorder {

  private static final String BUILD_PARAMETER_PREFIX = "$";
  private static final String HTTP_PROTOCOL_PREFIX = "http://";
  private static final String HTTPS_PROTOCOL_PREFIX = "https://";
  private static final String FIXED_VERSIONS_LIST_DELIMITER = ",";

  private final String restAPIUrl;
  private final String userName;
  private final String password;
  private final String jql;
  private final String workflowActionName;
  private final String comment;
  private final String commentFile;
  private final String customFieldId;
  private final String customFieldValue;
  private final boolean resettingFixedVersions;
  private final boolean createNonExistingFixedVersions;
  private final String fixedVersions;
  private final boolean failIfJqlFails;
  private final boolean failIfNoIssuesReturned;
  private final boolean failIfNoJiraConnection;

  // Worker variables
  private String realJql;
  private String realWorkflowActionName;
  private String realComment;
  private String realFieldValue;

  transient List<String> fixedVersionNames;

  // Temporarily cache the version String-ID mapping for multiple
  // projects, to avoid performance penalty may be caused by excessive
  // getVersions() invocations.  
  // Map<ProjectKey, Map<VersionName, VersionID>>
  transient Map<String, Map<String, String>> projectVersionNameIdCache;

  @DataBoundConstructor
  public IssueUpdaterResultsRecorder(String restAPIUrl, String userName, String password, String jql, String workflowActionName,
          String comment, String commentFile, String customFieldId, String customFieldValue, boolean resettingFixedVersions,
          boolean createNonExistingFixedVersions, String fixedVersions, boolean failIfJqlFails,
          boolean failIfNoIssuesReturned, boolean failIfNoJiraConnection) {
    this.restAPIUrl = restAPIUrl;
    this.userName = userName;
    this.password = password;
    this.jql = jql;
    this.workflowActionName = workflowActionName;
    this.comment = comment;
    this.commentFile = commentFile;
    this.customFieldId = customFieldId;
    this.customFieldValue = customFieldValue;
    this.resettingFixedVersions = resettingFixedVersions;
    this.createNonExistingFixedVersions = createNonExistingFixedVersions;
    this.fixedVersions = fixedVersions;
    this.failIfJqlFails = failIfJqlFails;
    this.failIfNoIssuesReturned = failIfNoIssuesReturned;
    this.failIfNoJiraConnection = failIfNoJiraConnection;
  }

  /**
   * {@link Recorder}
   *
   * @param project
   * @return
   */
  @Override
  public Collection<Action> getProjectActions(AbstractProject<?, ?> project) {
    final Collection<Action> list = new ArrayList<Action>();
    list.add(new IssueUpdaterBuildAction(project));
    return list;
  }

  /**
   * {@link BuildStep}
   *
   * @return
   */
  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.BUILD;
  }

  /**
   * {@link BuildStep}
   *
   * @param build
   * @param launcher
   * @param listener
   * @return
   * @throws java.lang.InterruptedException
   * @throws java.io.IOException
   */
  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
          throws InterruptedException, IOException {
    PrintStream logger = listener.getLogger();
    logger.println("-------------------------------------------------------");
    logger.println("JIRA Update Results Recorder");
    logger.println("-------------------------------------------------------");

    Map<String, String> vars = new HashMap<String, String>();
    vars.putAll(build.getEnvironment(listener));
    vars.putAll(build.getBuildVariables());
    substituteEnvVars(vars, logger);

    RESTClient client = new RESTClient(getRestAPIUrl(),getUserName(), getPassword(),logger);
    
    // Find the list of issues we are interested in, maximum of 10000
    IssueSummaryList issueSummary = client.findIssuesByJQL(realJql);
    if (issueSummary == null) {
      return !failIfJqlFails;
    }

    if (issueSummary.getIssues().isEmpty()) {
      logger.println("Your JQL, '" + realJql + "' did not return any issues. No issues will be updated during this build.");
      if (failIfNoIssuesReturned) {
        logger.println("Checkbox 'Fail this build if no issues are matched' checked, failing build");
        return false;
      } else {
        return true;
      }
    }
    
    // reset the cache
    projectVersionNameIdCache = new ConcurrentHashMap<String, Map<String, String>>();

    if (fixedVersions != null && !fixedVersions.isEmpty()) {
      fixedVersionNames = Arrays.asList(fixedVersions.split(FIXED_VERSIONS_LIST_DELIMITER));
    }
    
    // Perform the actions on each found JIRA
    if (issueSummary.getIssues() != null) {
      for (IssueSummary issue : issueSummary.getIssues()) {
        logger.println("Updating " + issue.getKey() + "  \t" + issue.getFields().getSummary());
        client.updateIssueStatus(issue, realWorkflowActionName);
        client.addIssueComment(issue, realComment);
        client.updateIssueField(issue, customFieldId, realFieldValue);
        //client.updateFixedVersions(issue, fixedVersionNames, resettingFixedVersions, logger);
      }
    }
    return true;
  }

  /**
   * {@link Publisher}
   *
   * @return
   */
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  /**
   * See
   * <tt>src/main/resources/hudson/plugins/fitnesse/FitnesseResultsRecorder/config.jelly</tt>
   */
  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

    /**
     * Performs on-the-fly validation of the form field 'restUrlBase'.
     *
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the
     * browser.
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    public FormValidation doCheckRESTUrl(@QueryParameter String value) throws IOException, ServletException {
      if (!value.startsWith(HTTP_PROTOCOL_PREFIX) && !value.startsWith(HTTPS_PROTOCOL_PREFIX)) {
        return FormValidation.error("The Jira URL is mandatory and must start with http:// or https://");
      }
      return FormValidation.ok();
    }

    /**
     * Performs on-the-fly validation of the form field 'userName'.
     *
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the
     * browser.
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
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
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the
     * browser.
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
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
     * @param value This parameter receives the value that the user has typed.
     * @return Indicates the outcome of the validation. This is sent to the
     * browser.
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
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

    /**
     * {@link BuildStepDescriptor}
     *
     * @param jobType
     * @return
     */
    @Override
    @SuppressWarnings("rawtypes")
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      // works with any kind of project
      return true;
    }

    /**
     * {@link ModelObject}
     *
     * @return
     */
    @Override
    public String getDisplayName() {
      return "JIRA Issue Results Updater";
    }
  }

  /**
   * @return the restUrlBase
   */
  public String getRestAPIUrl() {
    return restAPIUrl;
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

  /**
   * @return the comment filename
   */
  public String getCommentFile() {
    return commentFile;
  }

  public String getCustomFieldId() {
    return customFieldId;
  }

  public String getCustomFieldValue() {
    return customFieldValue;
  }

  public String getFixedVersions() {
    return fixedVersions;
  }

  public boolean isResettingFixedVersions() {
    return resettingFixedVersions;
  }

  public boolean isFailIfJqlFails() {
    return failIfJqlFails;
  }

  public boolean isFailIfNoIssuesReturned() {
    return failIfNoIssuesReturned;
  }

  public boolean isFailIfNoJiraConnection() {
    return failIfNoJiraConnection;
  }

  @Deprecated
  void substituteEnvVars(Map<String, String> vars) {
    substituteEnvVars(vars, null);
  }

  void substituteEnvVars(Map<String, String> vars, PrintStream logger) {
    realJql = jql;
    realWorkflowActionName = workflowActionName;
    try {
      if (StringUtils.isEmpty(commentFile)) {
        realComment = comment;
      } else {
        String realCommentFile = commentFile;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
          realCommentFile = substituteEnvVar(realCommentFile, entry.getKey(), entry.getValue());
        }
        realComment = FileUtils.readFileToString(new File(realCommentFile), "utf-8");
      }
    } catch (IOException e) {
      realComment = comment;
      if (logger != null) {
        logger.println(e.getMessage());
      } else {
        System.out.println(e.getMessage());
      }
    }
    realFieldValue = customFieldValue;
    String expandedFixedVersions = fixedVersions == null ? "" : fixedVersions.trim();
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      realJql = substituteEnvVar(realJql, entry.getKey(), entry.getValue());
      realWorkflowActionName = substituteEnvVar(realWorkflowActionName, entry.getKey(), entry.getValue());
      realComment = substituteEnvVar(realComment, entry.getKey(), entry.getValue());
      realFieldValue = substituteEnvVar(realFieldValue, entry.getKey(), entry.getValue());
      expandedFixedVersions = substituteEnvVar(expandedFixedVersions, entry.getKey(), entry.getValue());
    }
    fixedVersionNames = Arrays.asList(expandedFixedVersions.trim().split(FIXED_VERSIONS_LIST_DELIMITER));
  }

  String substituteEnvVar(String origin, String varName, String replacement) {
    String key = BUILD_PARAMETER_PREFIX + varName;
    if (origin != null && origin.contains(key)) {
      return origin.replaceAll(Pattern.quote(key), Matcher.quoteReplacement(replacement));
    }
    return origin;
  }

}
