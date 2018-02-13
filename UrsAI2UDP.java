package de.UllisRoboterSeite.UrsAI2UDP;

// Autor: http://UllisRoboterSeite.de
// Doku:  http://bienonline.magix.net/public/....
// Created: 2018-01-25
//
// Version 1.0 (2018-01-25)
// -------------------------
// - Basis-Version

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.util.MediaUtil;
import com.google.appinventor.components.runtime.*;
import java.io.IOException;
import java.net.*;
import android.os.Handler;

@DesignerComponent(version= 1, 
                   description="Block zur UDP-Kommunikation. Siehe http://UllisRoboterSeite.de",
                   category=com.google.appinventor.components.common.ComponentCategory.EXTENSION, 
                   nonVisible=true, 
                   iconName="http://bienonline.magix.net/public/_grafiken/udp.png")
@SimpleObject(external=true)
@UsesPermissions(permissionNames="android.permission.INTERNET,android.permission.WAKE_LOCK,android.permission.INTERNET,android.permission.ACCESS_NETWORK_STATE")
public class UrsAI2UDP extends AndroidNonvisibleComponent implements Component {

    public static final int VERSION = 1;
    private ServerThread ST = null;
    private boolean _DropOwnBroadcast = true;
    private DatagramSocket listenSocket = null;
    private String _LocalIP;

    public UrsAI2UDP(ComponentContainer container) {
        super(container.$form());
        try{
           DatagramSocket socket = new DatagramSocket();
           socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
           _LocalIP = socket.getLocalAddress().getHostAddress();
           socket.disconnect();
           socket = null;
        } catch (Exception e)
        {}
    } // ctor

        // Definition der Eigenschaften (Properties).
    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public boolean dropOwnBroadcast() {
        return _DropOwnBroadcast;
    }

    // Einrichten der Eigenschaften (Properties).
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN, defaultValue = "" + true)
    @SimpleProperty(description = "Schaltet den Empfang eigener Broadcast-Pakete ab.")
    public void dropOwnBroadcast(boolean value) {
        _DropOwnBroadcast = value;
     }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public String localIP() {
        return _LocalIP;
    }
     
    @SimpleProperty(category = PropertyCategory.BEHAVIOR)
    public boolean isRunning() {
        if (ST == null)
            return false;
        
        return ST.isRunning;
    }

    @SimpleFunction(description="Senden eines Datagramms")
    public void Xmit(String toIP, int toPort, int fromPort, String Message) {
        DatagramSocket ds = null;;
        InetAddress toAddr;
        String errMsg = "";
        boolean useListenSocket = false; // true, wenn der Listenport aktiv ist und auf den gelichen Port wie fromPort lauscht.

        // Remote-IP-Adresse einlesen
        // Remote-IP-Adresse einlesen
        try {
            toAddr = InetAddress.getByName(toIP);
        } catch (Exception ex) {
            errMsg = "Ungültige IP-Adresse: ";
            XmitError(1, errMsg + toIP + " " + ex.getMessage());
            return;
        }

        try {
            if (fromPort <= 0){
               ds = new DatagramSocket();  // beliebigen Port nutzen
            } else {
                if (listenSocket != null) {
                    if (listenSocket.getLocalPort() == fromPort) {
                        useListenSocket = true;
                    }
                }
            }
            if(!useListenSocket && ds == null) {
                ds = new DatagramSocket(fromPort);
            }
        } catch (Exception ex) {
            errMsg = "Ungültige fromPort-Angabe: ";
            XmitError(2, errMsg + fromPort + " " + ex.getMessage());
            return;
        }
        DatagramPacket dp = new DatagramPacket(Message.getBytes(), Message.length(), toAddr, toPort);

        try {
            if(ds != null)
                ds.setBroadcast(true);
        } catch (Exception ex) {
            if(ds != null)
               ds.close();
            errMsg = "Broadcast nicht möglich: ";
            XmitError(3, errMsg + ex.getMessage());
            return;
        }
        try {
            if (ds != null)
                ds.send(dp);
            else
                listenSocket.send(dp);
        } catch (Exception ex) {
            if (ds != null)
                ds.close();
            errMsg = "Senden nicht möglich: ";
            XmitError(4, errMsg + ex.getMessage());
            return;
        }
        if (ds != null)
            ds.close();
    } // Xmit

    // Fehlerbehandlung beim Senden.
    @SimpleEvent(description = "Beim Senden des Datagramms ist ein Fehler aufgetreten.")
    public void XmitError(int ErrorCode, String ErrorMsg){
        EventDispatcher.dispatchEvent(this, "XmitError", ErrorCode, ErrorMsg);
    } // XmitError
	
    // Fehlerbehandlung beim Empfangen.
    @SimpleEvent(description = "Beim Empfangen von Datagrammen ist ein Fehler aufgetreten.")
    public void RcvError(int ErrorCode, String ErrorMsg){
        EventDispatcher.dispatchEvent(this, "RcvError", ErrorCode, ErrorMsg);
    } // XmitError
/*
        // Debug-Meldung ausgeben.
    @SimpleEvent(description = "Debug-Meldung ausgegeben.")
    public void debugMsg(String Msg){
        EventDispatcher.dispatchEvent(this, "debugMsg", Msg);
    } // XmitError
*/
    
    // Listener starten
    @SimpleFunction(description="Auf den Empfang von Datagrammen warten.")
    public void StartListening(int port) {
        if (ST != null) {
            ST.stopRequest = true;
            while (ST.isRunning);
        }

        ST = new ServerThread(this);
        try {
            ST.begin(port);
        } catch (Exception ex) {
            return;
        } // try
    } // StartListening

    @SimpleFunction(description="Das Warten auf den Empfang von Datagrammen beenden.")
    public void StopListening() {
        if (ST != null) {
            ST.stopRequest = true;
            while (ST.isRunning);
        }
    } // StopListening
    
    @SimpleEvent(description = "Es wurde ein Datagramm empfangen.")
    public void DataReceived(String Data, String RemoteIP, int RemotePort){
        EventDispatcher.dispatchEvent(this, "DataReceived", Data, RemoteIP, RemotePort);
    }
    
    @SimpleEvent(description = "Der UDP-Server wurde gestartet.")
    public void ServerStarted(String LocalIP, int LocalPort){
        EventDispatcher.dispatchEvent(this, "ServerStarted", LocalIP, LocalPort);
    }
    @SimpleEvent(description = "Der UDP-Server wurde gestoppt.")
    public void ServerStopped(){
        EventDispatcher.dispatchEvent(this, "ServerStopped");
    }
    
private class ServerThread extends Thread {
    public boolean stopRequest = false;
    public boolean isRunning = false;
    private UrsAI2UDP parent;
    final Handler handler = new Handler();

    
    public ServerThread(UrsAI2UDP p) {
        parent = p;
    }
    
    public void begin(int port) throws IOException {
        parent.listenSocket = new DatagramSocket(port);
        parent.listenSocket.setBroadcast(true);
        parent.listenSocket.setSoTimeout(100);
        isRunning = true;
        ServerStarted(parent._LocalIP, listenSocket.getLocalPort());
        start();
    } // begin

    @Override public void run() {
        while (!stopRequest) {
            try {
                byte[] buf = new byte[2048];

                // receive request
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                parent.listenSocket.receive(packet);
                final String RemoteIP = packet.getAddress().getHostAddress();
                final int RemotePort = packet.getPort();
                final String received = new String(packet.getData(), 0, packet.getLength());
                if(!parent._DropOwnBroadcast || !RemoteIP.equals(parent._LocalIP))
                    handler.post(new Runnable() {
                        public void run() {
                            parent.DataReceived(received, RemoteIP, RemotePort);
                        } // run
                    }); // post
            } catch (SocketTimeoutException e) {// nicht zu tun 
            }
            catch (Exception ex) {
                 String errMsg = "Fehler beim Empfangen: ";
                 parent.RcvError(5, errMsg + ex.getMessage());
            } // try
        } // while
        parent.listenSocket.close();
        parent.listenSocket = null;
        isRunning=false;
        handler.post(new Runnable() {
            public void run() {
             parent.ServerStopped();
            } // run
        }); // post
    } // run
} // class


} // class UrsAI2UDP


