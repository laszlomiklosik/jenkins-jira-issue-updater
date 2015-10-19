package info.bluefloyd.jira.model;

import java.util.ArrayList;
import java.util.List;

/**
 * List of issue summaries we get back from JIRA.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class IssueSummaryList {

  private String expand;
  private String startAt;
  private String maxResults;
  private String total;
  private ArrayList<IssueSummary> issues;

  /**
   * @return the expand
   */
  public String getExpand() {
    return expand;
  }

  /**
   * @param expand the expand to set
   */
  public void setExpand(String expand) {
    this.expand = expand;
  }

  /**
   * @return the startAt
   */
  public String getStartAt() {
    return startAt;
  }

  /**
   * @param startAt the startAt to set
   */
  public void setStartAt(String startAt) {
    this.startAt = startAt;
  }

  /**
   * @return the maxResults
   */
  public String getMaxResults() {
    return maxResults;
  }

  /**
   * @param maxResults the maxResults to set
   */
  public void setMaxResults(String maxResults) {
    this.maxResults = maxResults;
  }

  /**
   * @return the total
   */
  public String getTotal() {
    return total;
  }

  /**
   * @param total the total to set
   */
  public void setTotal(String total) {
    this.total = total;
  }

  /**
   * @return the issues
   */
  public List<IssueSummary> getIssues() {
    return issues;
  }

  /**
   * @param issues the issues to set
   */
  public void setIssues(ArrayList<IssueSummary> issues) {
    this.issues = issues;
  }
}
