package info.bluefloyd.jira.model;

import java.util.List;

/**
 * Field Summary. Used as part of the issue summary, encapsulates the "summary"
 * field.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class FieldSummary {
  private String summary;
  private List<VersionSummary> versions;

  /**
   * @return the summary
   */
  public String getSummary() {
    return summary;
  }

  /**
   * @param summary the summary to set
   */
  public void setSummary(String summary) {
    this.summary = summary;
  }

  /**
   * @return the versions
   */
  public List<VersionSummary> getVersions() {
    return versions;
  }

  /**
   * @param versions the versions to set
   */
  public void setVersions(List<VersionSummary> versions) {
    this.versions = versions;
  }

}
