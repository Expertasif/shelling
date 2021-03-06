/*
 The simple scanner check class for SHELLING.
 Sends all the payloads one after another, supports time feedback channel only

DNS/HTTP are supported by the CollaboratorScannerCheck class

*/

package burp;

import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.util.Iterator;
import uk.co.pentest.SHELLING.ShellingPayloadGenerator;
import uk.co.pentest.SHELLING.ShellingTab;


public class DirectScannerCheck extends ShellingScannerCheck {

        private ShellingTab tab;
	private IBurpCollaboratorClientContext collabClient;	
        private String collabLoc;
        private List<IBurpCollaboratorInteraction> collabInter;
        private List<IScanIssue> issues;
        private IHttpRequestResponse base;
        private IHttpRequestResponse attackReq;
        
        private IBurpCollaboratorInteraction inter;
        private Iterator<IBurpCollaboratorInteraction> collabInterItr;        
        
	public DirectScannerCheck(IBurpExtenderCallbacks cb, ShellingTab tab) 
        {           
            super(cb,tab);
            this.tab = tab;
            checkHttpService = null;
	}
	
	@Override
	public int consolidateDuplicateIssues(IScanIssue existingIssue,IScanIssue newIssue) {
		return -1;
	}
	
	@Override
	public List<IScanIssue> doActiveScan(IHttpRequestResponse baseRequestResponse,IScannerInsertionPoint insertionPoint) 
        {            
                this.issues = null;
                if(tab.shellingPanel.scannerChecks==false) return this.issues; // the switch
              
                
        	IRequestInfo reqInfo = helpers.analyzeRequest(baseRequestResponse);
		URL url = reqInfo.getUrl();
                int port = url.getPort();
		boolean https=false;
                String host = url.getHost();
                if(url.getProtocol()=="https") https=true;
		String urlStr = url.getProtocol()+"://"+url.getHost()+":"+url.getPort()+url.getPath();
		if(!createCheckHttpService(host,port,https))  
                {
                    callbacks.printError("HTTP connection failed");
                    callbacks.issueAlert("HTTP connection failed");
                    return issues;
                }
                generator = new ShellingPayloadGenerator(tab);
                int counter=0;
                if(tab.shellingPanel.feedbackChannel=="DNS")
                {
                    this.collabClient = callbacks.createBurpCollaboratorClientContext(); 
                    this.collabLoc = collabClient.generatePayload(true);                    
                }
                while(generator.hasMorePayloads())
                {
                    byte[] payload = generator.getNextPayload(insertionPoint.getBaseValue().getBytes());               
                    if(tab.shellingPanel.feedbackChannel=="DNS")
                    {
                        String payloadS = callbacks.getHelpers().bytesToString(payload);
                        payloadS = payloadS.replace("BURP_COLLAB_DOMAIN",collabLoc);
                        payload = callbacks.getHelpers().stringToBytes(payloadS);
                    }
                    
                    if(payload.length==1) 
                    { //payload generation failed, move onto next command
			callbacks.printError("Payload generation failed!");
			callbacks.issueAlert("Payload generation failed!");
                        return this.issues;
                    }
                    byte [] req = insertionPoint.buildRequest(payload);
                    //callbacks.printError((new String(req))+"\n\n");
                    
                    // feedback channel logic can be coded here, no need for separate checks
                    if(tab.shellingPanel.feedbackChannel=="time")
                    {
                        long millisBefore = System.currentTimeMillis();
                        attackReq = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(),req);
                        byte[] resp = attackReq.getResponse();
                        long millisAfter = System.currentTimeMillis();
                        if(millisAfter-millisBefore>15000) // default sleep is 15 seconds, so the difference has to be at least 15 seconds
                        {
                            // raise an issue, abort further checks                        
                            //callbacks.printError(new String(exploitRR.getResponse()));					
                            this.issues = new ArrayList<IScanIssue>(1);			
                            BinaryPayloadIssue issue;
                            issue = new BinaryPayloadIssue(callbacks,attackReq,"");
                            this.issues.add((IScanIssue) issue);
                            return this.issues;
                        }
                    }
                    else
                    {
                        counter++;
                        attackReq = callbacks.makeHttpRequest(baseRequestResponse.getHttpService(),req);
                        byte[] resp = attackReq.getResponse();
                    
                        if(counter%200==0&&checkCollabInteractions()) // check for feedback every 200 requests
                        {                         
                           // raise an issue, abort further checks                        
                           //callbacks.printError(new String(exploitRR.getResponse()));					
                           this.tab.shellingPanel.logOutput("Returning issues");
                           return this.issues;
                        }                                                
                    }
                }
               
                if(tab.shellingPanel.feedbackChannel=="DNS")
                {
                    try 
                    {   
                	Thread.sleep(20); // the question is what is the safe wait time here (not assuming to catch non-synchronous occurrences here - which is bollocks, by the way, has to be sorted as well)
                    } 
                    catch(Exception e) 
                    {
                           // whateva
                    }
                    checkCollabInteractions(); // check for feedback once again
                    return this.issues;
                }
                return this.issues;
        }	        
    private boolean checkCollabInteractions()
    {
        // only DNS interactions are supported at the moment
        this.collabInter = this.collabClient.fetchCollaboratorInteractionsFor(this.collabLoc);
        this.tab.shellingPanel.logOutput("Checking for interactions with "+collabLoc);
        if(this.collabInter.size()>0) 
        { 
            this.tab.shellingPanel.logOutput("Got an interaction for "+collabLoc+"!");
                //if interaction(s) were found from the current poll request, add all to overall list and continue
            this.collabInterItr = this.collabInter.iterator();
                // only reading one, first interaction (at least now)
            this.inter = this.collabInterItr.next();
                
 	    this.issues = new ArrayList<IScanIssue>(1);			
                // This method is used to retrieve a property of the interaction. 
                // Properties of all interactions are: interaction_id, type, client_ip, and time_stamp. 
                // Properties of DNS interactions are: query_type and raw_query. The raw_query value is Base64-encoded. 
                // Properties of HTTP interactions are: protocol, request, and response. 
                // The request and response values are Base64-encoded
                
            byte[] collabQuery = this.helpers.base64Decode(this.inter.getProperty("raw_query"));
            
            // NOW, WHAT FOLLOWS IS THE UGLIEST SCULPTURE I HAVE EVER CODED:
            
             
            String rawS = this.helpers.bytesToString(collabQuery);
            this.tab.shellingPanel.logOutput("Raw query: "+rawS);
            byte[] trimed = new byte[collabQuery.length-16];
            for(int i=13;i<collabQuery.length-3;i++)
            {
               trimed[i-13]=collabQuery[i];
            }
            String collabQueryS = this.helpers.bytesToString(trimed);
            this.tab.shellingPanel.logOutput("Trimed query: "+collabQueryS);   
            byte[] t = new byte[1];
            t[0]=(byte)0x1e; // Record Separator            
            String parts[] = collabQueryS.trim().split(this.callbacks.getHelpers().bytesToString(t));
            
            String payloadIndexS="0";
            
            if(parts.length>1) 
            {
                payloadIndexS=parts[0];            
                this.tab.shellingPanel.logOutput("Parts[0] (payload index):"+parts[0]);
            }
            if(payloadIndexS.startsWith("a")) payloadIndexS = payloadIndexS.replace("a","");
            int payloadIndex  = Integer.parseInt(payloadIndexS);
            //int payloadIndex = buf.getInt();
            String theGoldenPayload = this.generator.getPayload(payloadIndex);
            String details = "\nThe golden payload seems to be: "+theGoldenPayload;
            
            // BUT IT WORKS
            this.issues.add((IScanIssue) new BinaryPayloadIssue(this.callbacks,this.attackReq,details));
            // we need to change the constructor in order to pass some additional data            
            return true;
        }
        return false;
    }

}
