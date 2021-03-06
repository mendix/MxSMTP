// This file was generated by Mendix Business Modeler.
//
// WARNING: Only the following code will be retained when actions are regenerated:
// - the import list
// - the code between BEGIN USER CODE and END USER CODE
// - the code between BEGIN EXTRA CODE and END EXTRA CODE
// Other code you write will be lost the next time you deploy the project.
// Special characters, e.g., é, ö, à, etc. are supported in comments.

package mxsmtp.actions;

import mxsmtp.MxSMTPServer;
import com.mendix.systemwideinterfaces.core.IContext;
import com.mendix.systemwideinterfaces.core.IMendixObject;
import com.mendix.webui.CustomJavaAction;

/**
 * Starts the SMTP server. If server is already started, stop and restart server.
 */
public class StartSMTPServer extends CustomJavaAction<Boolean>
{
	private IMendixObject __config;
	private mxsmtp.proxies.ServerSetting config;

	public StartSMTPServer(IContext context, IMendixObject config)
	{
		super(context);
		this.__config = config;
	}

	@Override
	public Boolean executeAction() throws Exception
	{
		this.config = __config == null ? null : mxsmtp.proxies.ServerSetting.initialize(getContext(), __config);

		// BEGIN USER CODE
		return MxSMTPServer.start(config);
		// END USER CODE
	}

	/**
	 * Returns a string representation of this action
	 */
	@Override
	public String toString()
	{
		return "StartSMTPServer";
	}

	// BEGIN EXTRA CODE
	// END EXTRA CODE
}
