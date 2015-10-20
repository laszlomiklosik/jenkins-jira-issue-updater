package info.bluefloyd.jira.model;

/**
 * Holder class for possible transitions. These are per JIRA and status.
 * 
 * We do not need to map all of the properties, therefore we ignore anything 
 * we are not specifically interested in the Jackson mapper.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class PossibleTransition {
  private String id;
  private String name;
  
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
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }
}
