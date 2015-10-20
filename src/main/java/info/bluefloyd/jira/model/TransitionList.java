package info.bluefloyd.jira.model;

import java.util.List;

/**
 * Holder class for all of the transitions which a JIRA has. We do not need
 * to map all of the properties, therefore we ignore anything we are not
 * specifically interested in the Jackson mapper.
 * 
 * @author Ian Sparkes, Swisscom AG
 */
public class TransitionList {
  private String expand;
  private List<PossibleTransition> transitions;

  /**
   * @return the transitions
   */
  public List<PossibleTransition> getTransitions() {
    return transitions;
  }

  /**
   * @param transitions the transitions to set
   */
  public void setTransitions(List<PossibleTransition> transitions) {
    this.transitions = transitions;
  }
  
  public boolean containsTransition(String targetTransition) {
    for (PossibleTransition possibleTransition : getTransitions()) {
      if (possibleTransition.getName().equalsIgnoreCase(targetTransition)) {
        return true;
      }
    }
    
    return false;
  }
  
  public Integer getTransitionId(String targetTransition) {
    for (PossibleTransition possibleTransition : getTransitions()) {
      if (possibleTransition.getName().equalsIgnoreCase(targetTransition)) {
        Integer id = Integer.parseInt(possibleTransition.getId());
        return id;
      }
    }
    
    return null;
  }

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
}
