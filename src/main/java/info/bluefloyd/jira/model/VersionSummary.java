/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package info.bluefloyd.jira.model;

/**
 *
 * @author ian
 */
public class VersionSummary {
  private boolean archived;
  private String description;
  private String id;
  private String name;
  private boolean released;
  private String self;

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
   * @return the archived
   */
  public boolean isArchived() {
    return archived;
  }

  /**
   * @param archived the archived to set
   */
  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  /**
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * @param description the description to set
   */
  public void setDescription(String description) {
    this.description = description;
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

  /**
   * @return the released
   */
  public boolean isReleased() {
    return released;
  }

  /**
   * @param released the released to set
   */
  public void setReleased(boolean released) {
    this.released = released;
  }
}
