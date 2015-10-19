package info.bluefloyd.jira.model;

/**
 * Field Summary. Used as part of the issue summary, encapsulates the "summary"
 * field.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class FieldSummary {
  private String summary;

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
}
