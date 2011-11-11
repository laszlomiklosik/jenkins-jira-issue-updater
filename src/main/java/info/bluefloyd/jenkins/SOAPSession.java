package info.bluefloyd.jenkins;

import java.net.URL;
import java.rmi.RemoteException;

import javax.xml.rpc.ServiceException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.atlassian.jira.rpc.soap.client.JiraSoapService;
import com.atlassian.jira.rpc.soap.client.JiraSoapServiceService;
import com.atlassian.jira.rpc.soap.client.JiraSoapServiceServiceLocator;

/**
 * This represents a SOAP session with JIRA including that state of being logged
 * in or not
 */
public class SOAPSession {
	private static final Log LOGGER = LogFactory.getLog(SOAPSession.class);

	private JiraSoapServiceService jiraSoapServiceLocator;
	private JiraSoapService jiraSoapService;
	private String token;

	public SOAPSession(URL webServicePort) {
		jiraSoapServiceLocator = new JiraSoapServiceServiceLocator();
		try {
			if (webServicePort == null) {
				jiraSoapService = jiraSoapServiceLocator.getJirasoapserviceV2();
			} else {
				jiraSoapService = jiraSoapServiceLocator.getJirasoapserviceV2(webServicePort);
				LOGGER.info("SOAP Session service endpoint at " + webServicePort.toExternalForm());
			}
		} catch (ServiceException e) {
			throw new RuntimeException("ServiceException during SOAPClient contruction", e);
		}
	}

	public SOAPSession() {
		this(null);
	}

	public void connect(String userName, String password) throws RemoteException {
		LOGGER.info("Connnecting via SOAP as : " + userName);
		token = getJiraSoapService().login(userName, password);
		LOGGER.info("Connected");
	}

	public String getAuthenticationToken() {
		return token;
	}

	public JiraSoapService getJiraSoapService() {
		return jiraSoapService;
	}

	public JiraSoapServiceService getJiraSoapServiceLocator() {
		return jiraSoapServiceLocator;
	}
}
