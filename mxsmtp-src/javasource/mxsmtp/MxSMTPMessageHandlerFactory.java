package mxsmtp;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;

import com.mendix.core.Core;
import com.mendix.logging.ILogNode;

public class MxSMTPMessageHandlerFactory implements MessageHandlerFactory
{
    public static final ILogNode LOG = Core.getLogger("MxSMTP");
    
	@Override
	public MessageHandler create(MessageContext arg0)
	{
			LOG.info("Incoming SMTP connection from: " + arg0.getRemoteAddress());
			return new MxSMTPMessageHandler(arg0);
	}

}
