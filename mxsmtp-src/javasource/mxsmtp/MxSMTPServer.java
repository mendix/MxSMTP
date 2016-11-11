package mxsmtp;

import mxsmtp.proxies.ServerSetting;

import org.subethamail.smtp.server.SMTPServer;

import com.mendix.core.Core;
import com.mendix.logging.ILogNode;

public class MxSMTPServer
{
	private static final String	MXSMTP_SOFTWARENAME	= "MxSMTP";
	
	private static MxSMTPServer instance;
	
	public static MxSMTPServer getInstance() {
		if (instance == null)
			throw new RuntimeException("MxSMTPServer: assertion error: unable to obtain instance; not started yet");
		return instance;
	}

	private final ServerSetting	config;
	private final SMTPServer	smtpServer;

	public static final ILogNode LOG = Core.getLogger("MxSMTP");
	
	private MxSMTPServer(ServerSetting config)
	{
		LOG.info("Starting smtp server...");

		this.config = config;

		MxSMTPMessageHandlerFactory factory = new MxSMTPMessageHandlerFactory();
		this.smtpServer = new SMTPServer(factory);
		
		smtpServer.setPort(config.getport());
		smtpServer.setMaxMessageSize(config.getmaxMessageSize());
		smtpServer.setHostName(config.gethostName());
		smtpServer.setSoftwareName(MXSMTP_SOFTWARENAME);
		smtpServer.start();
		
		LOG.info("Smtp server started");
	}

	public static Boolean start(ServerSetting config)
	{
		boolean restart = instance != null;
	    if (restart) {
			instance.getServer().stop();
		}
	
	    instance = new MxSMTPServer(config);
        LOG.info("MxSMTP server " + (restart ? "re" : "" ) + "started.");
    		
		return true;
	}
	
	public static void stop() {
        if (instance != null) {
            instance.getServer().stop();
            instance = null;
        }
        LOG.info("MxSMTP server stopped");
	}
	
	public static boolean isRunning()
	{
		return instance != null && instance.getServer().isRunning();
	}
	
	private SMTPServer getServer() {
	    return smtpServer;
	}

	public ServerSetting getConfig()
	{
		return config;
	}

}
