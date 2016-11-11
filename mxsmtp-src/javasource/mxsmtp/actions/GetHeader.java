// This file was generated by Mendix Business Modeler.
//
// WARNING: Only the following code will be retained when actions are regenerated:
// - the import list
// - the code between BEGIN USER CODE and END USER CODE
// - the code between BEGIN EXTRA CODE and END EXTRA CODE
// Other code you write will be lost the next time you deploy the project.
// Special characters, e.g., é, ö, à, etc. are supported in comments.

package mxsmtp.actions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import javax.mail.Header;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.webui.CustomJavaAction;

/**
 * Returns the value of a mime header for the message.
 */
public class GetHeader extends CustomJavaAction<String>
{
	private IMendixObject __message;
	private mxsmtp.proxies.SmtpMessage message;
	private String header;

	public GetHeader(IContext context, IMendixObject message, String header)
	{
		super(context);
		this.__message = message;
		this.header = header;
	}

	@Override
	public String executeAction() throws Exception
	{
		this.message = __message == null ? null : mxsmtp.proxies.SmtpMessage.initialize(getContext(), __message);

		// BEGIN USER CODE
		String content = message.getHeaders();
		Session s = Session.getDefaultInstance(new Properties());
		InputStream is = new ByteArrayInputStream(content.getBytes());
		MimeMessage n = new MimeMessage(s, is);
		String headername = this.header.toLowerCase();
		
		for (@SuppressWarnings("unchecked") Enumeration<Header> e = n.getAllHeaders(); e.hasMoreElements();) {
			Header h = e.nextElement();
		    if (h.getName().toLowerCase().equals(headername))
		    	return h.getValue();
		}
		return null;
		// END USER CODE
	}

	/**
	 * Returns a string representation of this action
	 */
	@Override
	public String toString()
	{
		return "GetHeader";
	}

	// BEGIN EXTRA CODE
	// END EXTRA CODE
}