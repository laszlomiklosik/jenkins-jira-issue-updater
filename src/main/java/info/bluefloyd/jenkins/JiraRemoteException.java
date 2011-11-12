package info.bluefloyd.jenkins;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Wrapper around Jira's remote exceptions.
 * 
 * @author Laszlo Miklosik
 * 
 */
public class JiraRemoteException extends RuntimeException {

	private static final long serialVersionUID = 8406077108270156631L;
	private static final Log LOGGER = LogFactory.getLog(JiraRemoteException.class);

	public JiraRemoteException(String message) {
		LOGGER.error(message);
	}
}
