package info.bluefloyd.jenkins;

import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.InvisibleAction;

/**
 * Used to store part of the project config with the build to which it relates
 * so that a-links can be constructed to fitnesse hosts that are already running 
 * (when fitnesse was not started by the build). 
 * 
 * @author Laszlo Miklosik
 * @author Ian Sparkes, Swisscom AG
 */
public class IssueUpdaterBuildAction extends InvisibleAction implements Action {

  IssueUpdaterBuildAction(AbstractProject<?, ?> project) {
  }
}
