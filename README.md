# MxSMTP

This module lets your application run a mail server. It will process any messages it receives and stores these in your application.

_Please note that this is an advanced module and will require specific server and DNS adjustments in order to work._

## Description
This module lets your application run a mail server. It will process any messages it receives and stores these in your application.
_Note: This module is not supported in the Mendix Cloud._

## Typical usage scenario
- Receiving emails into your application
- Letting users reply to emails sent by the application

## Dependencies
- Community Commons Module (available in Mendix App Store)
- javax.mail.jar
- jtnef-1.8.0.jar
- subethasmtp-3.1.7.jar
- activation.jar

## Configuration
There are 3 microflows that are used to handle emails that are received. These all receive the input they need and should return booleans to signify if the email should be processed or rejected.
CheckMessageSender : This microflow is used to check the From address for this message. This is run once for each message.
CheckMessageRecipient : This calls a submicroflow that receives the 'To' and the 'From' addressess. This is run for each of the recipients that are set in the To and CC. Here you could for example retrieve and check objects based on the input arguments ( <hash>@myapplication.com, where you can then retrieve an object for the hash to confirm something or further hook it up in your system ) or use some other type of matching with your system. Do note that From addressess can be easily faked, make sure you keep this in mind when checking these.
ProcessMessage : If the message passed the first two checks, it ends up in this microflow for any final checks. You have the entire SMTPMessage object here so you could check for anything that the previous two checks might have missed or do further actions with the message.

### Settings
Port : The port to be listening on.
Max message size : The maximum message size in bytes.
Host name : The hostname the server will be receiving on.
Double-bounce address : Some email relays use a test connection before actually delivering the email. Specify their 'from' address here.
Delete after process : Boolean to delete the email after it has been processed. This can also be done manually using the 'Purge old mails' tab.
Apply XSS filter to incoming mails : Enable/disable XSS filtering.
XSS filter level : The XSS filter to use.
 
After this is set-up, you will need to make sure that the emails reach your new server. There are two important situations:
#### Deploying on localhost
You can easily test the module by downloading the newest Email module. You set it up according to the Email module's documentation and for the SMTP settings, you use the same ones that your MxSMTP server is running (default: localhost on port 25000).
Any emails you send using the Email module will now end up in the same application's MxSMTP module! This way you can test To/From addresses (which you can set in the Email module) and possible content (plain or HTML) and attachments.
#### Deploying on a server
Deploying on a real server is a bit trickier as you will need to edit your DNS settings.
Say your application wants to receive all the emails that are send to @myapplication.com. You will need to edit your DNS that handles all the incoming traffic on 'myapplication.com' to forward any emails it gets to your application at the location you have set it at (for example: email.myapplication.com:25000). This will make sure your application will receive them and from there you can either process them or send them somewhere else.
If you already have a mail server listening to '@myapplication.com' you will need to configure it so it sends all the emails you want in your application to it.
 

Setting these DNS forwarding options require advanced access and knowledge of the server in question. Make sure you check and test this with the people responsible for administrating the server.