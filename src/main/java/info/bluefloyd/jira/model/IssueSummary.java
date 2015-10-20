package info.bluefloyd.jira.model;

/**
 * Issue Summary. Encapsulates the issue information we get back from the
 * "find issues" rest call.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class IssueSummary {
  private String expand;
  private String id;
  private String self;
  private String key;
  private FieldSummary fields;

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
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * @param id the id to set
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * @return the self
   */
  public String getSelf() {
    return self;
  }

  /**
   * @param self the self to set
   */
  public void setSelf(String self) {
    this.self = self;
  }

  /**
   * @return the key
   */
  public String getKey() {
    return key;
  }

  /**
   * @param key the key to set
   */
  public void setKey(String key) {
    this.key = key;
  }

  /**
   * @return the fields
   */
  public FieldSummary getFields() {
    return fields;
  }

  /**
   * @param fields the fields to set
   */
  public void setFields(FieldSummary fields) {
    this.fields = fields;
  }
}
