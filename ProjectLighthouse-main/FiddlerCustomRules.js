import System;
import System.Windows.Forms;
import Fiddler;

class Handlers
{
    static function OnBeforeRequest(oSession: Session) {
        var host = oSession.hostname.ToLower();
        
        if (host.IndexOf("littlebigplanetps3.online.scee.com") > -1) {
            FiddlerApplication.Log.LogString("LBP3 PS3 Request detected: " + oSession.fullUrl);
            
            // Redirect to local Lighthouse game server
            oSession.host = "192.168.100.6:10061";
            
            oSession["ui-color"] = "green";
            oSession["ui-backcolor"] = "lime";
        }
        else if (host.IndexOf("littlebigplanet3.online.scee.com") > -1 || host.IndexOf("lbp3.online.scee.com") > -1 || host.IndexOf("lbp3-ps4.online.scee.com") > -1) {
            FiddlerApplication.Log.LogString("LBP3 PS4 Request detected: " + oSession.fullUrl);
            
            // Redirect to local Lighthouse API server (handles CONNECT for PS4)
            oSession.host = "192.168.100.6:10060";
            
            oSession["ui-color"] = "blue";
            oSession["ui-backcolor"] = "cyan";
        }
    }

    static function OnBeforeResponse(oSession: Session) { }
}