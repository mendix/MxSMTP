package mxsmtp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import mxsmtp.proxies.Attachment;
import mxsmtp.proxies.MessageState;
import mxsmtp.proxies.SmtpMessage;
import mxsmtp.proxies.microflows.Microflows;
import net.freeutils.tnef.MAPIProp;
import net.freeutils.tnef.Message;
import net.freeutils.tnef.TNEFInputStream;

import org.apache.commons.io.IOUtils;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

import com.mendix.core.Core;
import com.mendix.core.CoreException;
import com.mendix.logging.ILogNode;
import com.mendix.systemwideinterfaces.core.IContext;
import communitycommons.ORM;

import com.mendix.util.classloading.Runner;

public class MxSMTPMessageHandler implements MessageHandler
{
    public static final ILogNode LOG = Core.getLogger("MxSMTP");

    private final MessageContext context;
    private final MxSMTPServer server;
    private final IContext mxcontext;
    private boolean hasdata = false;

    private SmtpMessage firstMessage;
    private Map<String, SmtpMessage> messages = new HashMap<String, SmtpMessage>();
    private ArrayList<Attachment> attachments = new ArrayList<Attachment>();

	public MxSMTPMessageHandler(MessageContext messageContext)
	{
		this.context = messageContext;
		this.server = MxSMTPServer.getInstance();
		this.mxcontext = Core.createSystemContext();
	}

	@Override
    public void from(String from) throws RejectException
    {
    	LOG.trace("From: " + from);
    	
    	boolean checkresult = false;
    	this.firstMessage = new SmtpMessage(mxcontext);
        try {
    		firstMessage.setRemoteAddress(context.getRemoteAddress().toString());
    		if (!from.trim().isEmpty()) {
    			firstMessage.setFrom(from.trim().toLowerCase());
    			checkresult = Microflows.checkMessageSender(mxcontext, firstMessage);
    		}
    		else { //MWE: sprintr ticket 7849, this occurs...
    			LOG.warn("No from address specified for message");
    		}
    	}
    	catch(Exception e) {
    		LOG.error("Severe: error while processing SMTP FROM request on message " + firstMessage.getNr() + ": " + e.getMessage(), e);
    		firstMessage.setState(MessageState.Failed_to_process);
    		
    		checkresult = false;
    	} finally {
    	    ORM.commitSilent(mxcontext, firstMessage.getMendixObject());
    	}
        
    	if (!checkresult) {
    		throw new RejectException();
    	}
    }

    @Override
    public void recipient(String recipient) throws RejectException
    {
    	LOG.trace("RcptTo: " + recipient);
    	
        Boolean checkresult = false;

        SmtpMessage message;
        if (messages.size() == 0) {
            message = firstMessage;
        } else {
            message = new SmtpMessage(mxcontext);
            message.setFrom(firstMessage.getFrom());
            message.setRemoteAddress(firstMessage.getRemoteAddress());
            message.setState(MessageState.Sender_accepted);
        }

        message.setTo(recipient.trim().toLowerCase());
        messages.put(recipient.trim().toLowerCase(), message);

        try {
            checkresult = Microflows.checkMessageRecipient(mxcontext, message);
        } catch (Exception e) {
            LOG.error("Severe: error while processing SMTP RCPT request: " + e.getMessage(), e);
            firstMessage.setState(MessageState.Failed_to_process);
            
            checkresult = false;
        } finally {
            ORM.commitSilent(mxcontext, message.getMendixObject());
        }
        
    	if (!checkresult) {
    		throw new RejectException();
    	}
    }

    @Override
	public void data(InputStream data) throws RejectException, TooMuchDataException, IOException
	{	
		LOG.trace("Data... ");
		
		hasdata = true;
		try {
		    final MimeMessage message = parseMessage(data);

			//parse the header
			StringBuilder hbuilder = new StringBuilder();
			@SuppressWarnings("unchecked")
            Enumeration<String> headers = message.getAllHeaderLines();
			while (headers.hasMoreElements()) {
				hbuilder.append(headers.nextElement()).append("\r\n");
			}
			String header = hbuilder.toString();

			//set the message properties
			for(SmtpMessage m : messages.values()) {
				if (accepted(m)) {
					m.setSubject(message.getSubject());
					m.setHeaders(header);
					m.setState(MessageState.Received);
				}
			}

			//parse the content
            // Weird Runner stuff is related to classloader bug in Mx 5.8.1+, see tickets 203211, 
			// 203377 and possibly others 
			new Runner<Object>() {
	            @Override
	            protected Object execute() throws Exception
	            {
	                Object content = message.getContent();
	                if (content instanceof Multipart) {
	                    handleMultipart((Multipart) content);
	                } else {
	                    handlePart(message);
	                }
	                return null;
	            }
	        }.runUsingClassLoaderOf(Transport.class);
			
			//link the files to the messages
			storefiles();

			//set the message properties
			for(SmtpMessage msg : messages.values()) {
				if (accepted(msg)) { 
					msg.setState(MessageState.Parsed);
				}
			}
		}
		catch(Exception e) { 
			LOG.error("Unable to process data: " +e.getMessage(), e);
			for(SmtpMessage msg : messages.values()) {
				if (accepted(msg)) { 
					msg.setState(MessageState.Unreadable);
				}
			}
		} finally {
		    for (SmtpMessage msg : messages.values()) {
		        ORM.commitSilent(mxcontext, msg.getMendixObject());
		    }
		}
	}

	@Override
    public void done()
    {
    	LOG.trace("Done. ");	
    	
        boolean result = true;
        for (SmtpMessage msg : messages.values()) {
            try {
                if (!hasdata) {
                    msg.delete(); // if we did not receive data, this 'message' was just used to setup the connection
                } else {
                    msg.commit();
                    LOG.debug("Received message, now processing...: " + msg.getNr());
                    if (accepted(msg)) {
                        result = (result && Microflows.processMessage(mxcontext, msg));
                    }

                    if (msg.getState() == MessageState.Processed && server.getConfig().getdeleteAfterProcess()) {
                        msg.delete();
                    }
                    LOG.info("Received and processed message " + msg.getNr());
                }
            } catch (Exception e) {
                LOG.error("Severe: failed to update message status to received for message " + msg.getNr() + ": " + e.getMessage(), e);
                throw new RejectException();
            }
        }
    	if (!result) {
    		LOG.warn("Message "+ firstMessage.getNr() + " was not accepted by the message processor callback");
    	}
    }

    private void storefiles()
	{
		if (attachments.size() > 0) {
			for(SmtpMessage m : messages.values()) {
				if (accepted(m)) {
					m.setSmtpMessage_AttachMent(attachments);
					m.setHasAttachment(true);
				}
			}
		}
	}

	private boolean accepted(SmtpMessage m)
	{
		return m.getState() != MessageState.Recipient_rejected && 
			m.getState() != MessageState.Sender_rejected && 
			m.getState() != MessageState.Unreadable;
	}

	private void handleMultipart(Multipart multipart) throws MessagingException, IOException, CoreException {
	    for (int i=0, n=multipart.getCount(); i<n; i++) { 
			handlePart(multipart.getBodyPart(i));
	    }
	}
	
	private void handlePart(Part part) throws MessagingException, IOException, CoreException {
		String disposition = part.getDisposition();
		String contentType = part.getContentType();
		if (disposition == null) { // When just body
			
			String content = "";
			Object co = part.getContent();
			if (co instanceof String) {
				content = (String) co;
			
				// Check if plain
	            if (contentType == null) {
	                LOG.warn("Undefined contenttype for message " + firstMessage.getNr());
	                storePlainText(content, false);
	            } else if (contentType.startsWith("text/plain")) {
	                storePlainText(content, false);
	            } else if (contentType.startsWith("text/html")) {
	                storePlainText(content, true);
	            } else { // Don't think this will happen
	                LOG.warn("Invalid contenttype for message " + firstMessage.getNr() + ": " + contentType);
	                storePlainText(content, false);
	            }
			} else if (co instanceof MimeMessage) {
				MimeMessage mm = (MimeMessage) co; //MWE: is this the attachment or the message?
				saveFile(mm.getFileName(), mm.getInputStream(), mm.getContentType()); 
				return;
			} else if (co instanceof Multipart) {
				handleMultipart((Multipart) co);
				return;
			} else {
				//#10518: do not fail upon not being able to read a part of a message
				//MxSMTPServer.log.warn(String.format("Storing inline attachment %s (%s / %s)", part.getFileName(), part.getContentType(), co.getClass().getName()));
				saveFile(part.getFileName(), part.getInputStream(), part.getContentType());
				return; 
			}
		} else if (disposition.equalsIgnoreCase(Part.ATTACHMENT)) { 
			saveFile(part.getFileName(), part.getInputStream(), contentType);
		} else if (disposition.equalsIgnoreCase(Part.INLINE)) { 
			saveFile(part.getFileName(), part.getInputStream(), contentType);
	    } else {  // Should never happen
			LOG.warn("Invalid content disposition for message " + firstMessage.getNr() + ": " + disposition);
	    }
	}

	private void storePlainText(String content, boolean html) 
    {
        for (SmtpMessage m : messages.values()) {
            if (accepted(m)) {
                m.setPlainText(content);
                if (html) {
                    if (server.getConfig().getenableXSSFilter()) {
                        try {
                            m.setRichText(communitycommons.StringUtils.XSSSanitize(content, server.getConfig().getXSSFilterType()));
                        } catch (Exception e) {
                            LOG.error("Unable to perform XSS check (Is the 'communitycommons' library part of this project?) " + e.getMessage(), e);
                            throw new RuntimeException("Unable to perform XSS check (Is the 'communitycommons' library part of this project?) " + e.getMessage(), e);
                        }
                    } else {
                        m.setRichText(content);
                    }
                    m.setHasRichText(true);
                }
            }
        }
    }

	private void saveFile(String fileName, InputStream inputStream, String contentType) throws CoreException
	{
		Attachment a = new Attachment(mxcontext);
		a.setmimeType(contentType);
		if (fileName != null) {
			try {
				a.setName(MimeUtility.decodeText(fileName));
			} catch (UnsupportedEncodingException e) {
				LOG.warn("Failed to decode failename: " + fileName);
				a.setName(fileName);
			}
		} else {
			//no filename, detect from mimetype
			String fn = "attachment";
			//this is probably only need for images, as other files are attached correctly instead of being part of the main body, so we have a valid filename
			if (contentType.startsWith("image/jpeg") || contentType.startsWith("image/jpg")) {
				fn += ".jpg";
			} else if (contentType.startsWith("image/bmp")) {
				fn += ".bmp";
			} else if (contentType.startsWith("image/gif")) {
				fn += ".gif";
			} else if (contentType.startsWith("image/png")) {
				fn += ".png";
			}
			a.setName(fn);
		}
		
		// store also commits the filedocument
		Core.storeFileDocumentContent(mxcontext, a.getMendixObject(), inputStream);
		
		//ms-tnef based attachment?
		if (a.getmimeType().contains("application/ms-tnef")) {
			LOG.info("Found tnef attachment - parsing tnef");
			storeTNEFattachments(a);
		} else { //only add if not a tnef attachment!
			this.attachments.add(a);
		}
	}

	private void storeTNEFattachments(Attachment a)
	{
		InputStream in = null;
		TNEFInputStream is = null;
		
		try {
			//try to open the stream
			in = Core.getFileDocumentContent(mxcontext, a.getMendixObject());
			is = new TNEFInputStream(in); //fails if no tnef
			
			LOG.debug("Found MS-TNEF attachments");
		} catch(Exception e){
			// No TNEF file
			LOG.warn("Failed to open TNEF attachment: " + e.getMessage(), e);
		}
		
		//tnef file
		if (is != null) {
			try {
				//read message and attachment from the filedoc
				Message msg = new Message(is);
				@SuppressWarnings("unchecked")
                List<net.freeutils.tnef.Attachment> attachments = msg.getAttachments();
				LOG.debug("TNEF nr of attachments: " + msg.getAttachments().size());
				
				if (attachments.size() == 0) {
					LOG.warn("No attachments found in TNEF message!");
				}
				
				//store all attachments
				for(net.freeutils.tnef.Attachment att : attachments) { //should at least be one!
					LOG.debug("Storing TNEF attachment.. ");
					
					//store in temp file					
					File tmp = File.createTempFile("tnef", "att");
					att.writeTo(new FileOutputStream(tmp));
					
					//create new attachment from the temp file
					Attachment newattachment = new Attachment(mxcontext);
					
					String filename = att.getFilename() != null ? att.getFilename() : a.getName();
					newattachment.setmimeType((String)att.getMAPIProps().getPropValue(MAPIProp.PR_ATTACH_MIME_TAG));
					// store also commits the filedocument
					Core.storeFileDocumentContent(mxcontext, newattachment.getMendixObject(), filename, new FileInputStream(tmp));
					
					this.attachments.add(newattachment);
					
					tmp.deleteOnExit();
					tmp.delete();
				}
				
				is.close();
				a.delete(); //delete the original filedoc where the attachments have been extracted from

				LOG.debug("Parsing tnef succeeded");
			}
			catch(Exception e){
				LOG.error("Failed to read TNEF: " + e.getMessage(), e);
			}
			finally {
				IOUtils.closeQuietly(in);
			}
		}
	}

	private MimeMessage parseMessage(InputStream input) throws MessagingException
	{
		Session s = Session.getDefaultInstance(new Properties());
		MimeMessage message = new MimeMessage(s, input);
		return message;
	}

}
