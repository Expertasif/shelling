package burp;


import uk.co.pentest.SHELLING.SHELLING;
import uk.co.pentest.SHELLING.ShellingTab;
import uk.co.pentest.SHELLING.PayloadFactory;

/**
 * The main entry class that Burp calls to load/unload the extension.
 */
public class BurpExtender implements IBurpExtender, IExtensionStateListener {

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        SHELLING.callbacks = callbacks;
        callbacks.setExtensionName("SHELLING Extension");
        SHELLING.ShellingTab = new ShellingTab();
        callbacks.addSuiteTab(SHELLING.ShellingTab);
        
        callbacks.registerExtensionStateListener(this);

        callbacks.registerIntruderPayloadGeneratorFactory(new PayloadFactory(SHELLING.ShellingTab, "cmd"));
        callbacks.registerIntruderPayloadGeneratorFactory(new PayloadFactory(SHELLING.ShellingTab, "mark"));
        callbacks.registerIntruderPayloadGeneratorFactory(new PayloadFactory(SHELLING.ShellingTab, "byte"));
   
    }

    @Override
    public void extensionUnloaded() {
    }
    public static IBurpExtenderCallbacks getBurpCallbacks() {
        return SHELLING.callbacks;
    }
    

}