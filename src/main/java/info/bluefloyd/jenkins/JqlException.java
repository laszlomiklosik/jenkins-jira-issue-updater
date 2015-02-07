package info.bluefloyd.jenkins;

/**
 * Exception to use when JQL execution returns an error
 * 
 * @author Janis Baiza
 */
public class JqlException extends Exception {
    JqlException(String s) {
      super(s);
   }
}
