/*
 *  Copyright (c) 2001 Sun Microsystems, Inc.  All rights
 *  reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by the
 *  Sun Microsystems, Inc. for Project JXTA."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must
 *  not be used to endorse or promote products derived from this
 *  software without prior written permission. For written
 *  permission, please contact Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA",
 *  nor may "JXTA" appear in their name, without prior written
 *  permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL SUN MICROSYSTEMS OR
 *  ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 *  USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *  ========================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of Project JXTA.  For more
 *  information on Project JXTA, please see
 *  <http://www.jxta.org/>.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation.
 *
 *  $Id: Rendezvous.java,v 1.8 2007/02/06 18:27:52 hamada Exp $
 */
package rendezvous;

import net.jxta.credential.AuthenticationCredential;
import net.jxta.credential.Credential;
import net.jxta.discovery.DiscoveryService;
import net.jxta.endpoint.EndpointService;
import net.jxta.impl.membership.pse.StringAuthenticator;
import net.jxta.membership.MembershipService;
import net.jxta.peergroup.PeerGroup;
import net.jxta.platform.Application;
import net.jxta.platform.NetworkManager;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.rendezvous.RendezVousService;
import net.jxta.rendezvous.RendezvousEvent;
import net.jxta.rendezvous.RendezvousListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is a very simple application, which can be used as an app to run a
 * rendezvous.
 */
public class Rendezvous implements RendezvousListener {
    private final static Logger LOG = Logger.getLogger(Rendezvous.class.getName());
    PeerGroup netPeerGroup = null;
    DiscoveryService discovery = null;
    RendezVousService rendezvous = null;
    EndpointService endpoint = null;

    final Map<String, String> nodes = new HashMap<String, String>();
    boolean shutdown = false;
    int maxNumberOfClients = 0;
    /**
     * jxta shell init script
     */
    public final static String CONFIG_FILE = ".rdvrc";

    // The main thread will keep running and keep holding a reference
    // to JXTA until the quit command comes. When the quit command is
    // received, *this* application will clear all references to JXTA
    // and terminate. This does not necessarily mean that JXTA will
    // shutdown; there's no shutdown API in JXTA. Another application
    // may still have a reference to JXTA.
    // If JXTA was not help running by anything else than
    // this object, then, it will shutdown.
    // Note that this object is sharing ist reference to the netPeerGroup
    // with the main application, if there's one. So either that application
    // or this object can alone put an end to JXTA.

    // NOTE: For security reasons, there is not yet a mechanism to send
    // the quit command.

    /**
     * main Method
     *
     * @param args command line arguments
     */
    public static void main(String args[]) {
        String jxta_home = System.getProperty("JXTA_HOME", ".jxta");
        File home = new File(jxta_home);

        Thread.currentThread().setName(iViewRendezvous.class.getName() + ".main()");

        String hostname = "Rendezvous";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException uhe) {
            //ignored
        }

        Rendezvous rend = new Rendezvous(hostname, home.toURI());

        rend.waitForQuit();
        rend.releaseJxta();
    }

    /**
     * Constructor for the Rendezvous object
     *
     * @param name the node name
     * @param home home dir
     */
    public Rendezvous(String name, URI home) {

        try {
            // create, and Start the default jxta NetPeerGroup
            if (LOG.isLoggable(Level.INFO)) {
                LOG.info("Starting JXTA ....");
            }

            NetworkManager manager = new NetworkManager(NetworkManager.ConfigMode.SUPER, name, home);
            manager.startNetwork();

            netPeerGroup = manager.getNetPeerGroup();

            endpoint = netPeerGroup.getEndpointService();
            discovery = netPeerGroup.getDiscoveryService();
            rendezvous = netPeerGroup.getRendezVousService();
            MembershipService membership = netPeerGroup.getMembershipService();

            Credential cred = membership.getDefaultCredential();

            try {
                if (null == cred) {
                    AuthenticationCredential authCred = new AuthenticationCredential(netPeerGroup, "StringAuthentication", null);

                    StringAuthenticator auth = null;
                    try {
                        auth = (StringAuthenticator) membership.apply(authCred);
                    } catch (Exception failed) {
                        //ignored
                    }

                    if (null != auth) {
                        auth.setAuth1_KeyStorePassword(System.getProperty("net.jxta.tls.password", "").toCharArray());
                        auth.setAuth2Identity(netPeerGroup.getPeerID());
                        auth.setAuth3_IdentityPassword(System.getProperty("net.jxta.tls.password", "").toCharArray());
                        if (auth.isReadyForJoin()) {
                            membership.join(auth);
                            if (LOG.isLoggable(Level.INFO)) {
                                LOG.info("Authenticated");
                            }
                        }
                    }
                }
            } catch (Exception failed) {
                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, "Authentication failed", failed);
                }
            }

            rendezvous.addListener(this);
            if (LOG.isLoggable(Level.INFO)) {
                LOG.info("Registered for rendezvous events");
            }

            String initialApp = getConfig();
            if (initialApp != null) {
                startInitApp(initialApp, netPeerGroup);
            }
            if (LOG.isLoggable(Level.INFO)) {
                LOG.info("JXTA started ....");
            }
        } catch (Exception e) {
            if (LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, "fatal error : group creation failure", e);
            }
            System.exit(1);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void rendezvousEvent(RendezvousEvent event) {

        if (LOG.isLoggable(Level.INFO)) {
            LOG.info("{" + idToName(event.getPeer()) + "} " + event.toString());
        }
        printStats();
    }

    /**
     * prints current stats to log
     */
    private void printStats() {
        int count = rendezvous.getConnectedPeerIDs().size();
        if (count > maxNumberOfClients) {
            maxNumberOfClients = count;
        }
        if (LOG.isLoggable(Level.INFO)) {
            LOG.info("\t" + count + " Clients Connected, ThreadCount : " + Thread.activeCount());
        }
    }

    // No quit command can be received yet. Therefore, we just wait forever.
    /**
     * waits for a quit notification
     */
    protected synchronized void waitForQuit() {
        while (!shutdown) {
            try {
                wait();
            } catch (InterruptedException ie) {
                Thread.interrupted();
            }
        }
    }

    /**
     * releases the net peer group object
     */
    protected void releaseJxta() {
        rendezvous.removeListener(this);
        if (LOG.isLoggable(Level.INFO)) {
            LOG.info("Unreferencing net peer group");
        }
        netPeerGroup.unref();
    }

    /**
     * Starts any defined initial group applications
     *
     * @param appClassName Description of the Parameter
     * @param group        Description of the Parameter
     */
    private static void startInitApp(String appClassName, PeerGroup group) {
        Application app;

        try {
            // find the class
            Class<?> appClass = Class.forName(appClassName);
            app = (Application) appClass.newInstance();
            app.init(group, null, null);
            app.startApp(new String[0]);
            if (LOG.isLoggable(Level.INFO)) {
                LOG.info(appClassName + " is started");
            }
        } catch (Throwable e) {
            System.err.println("Cannot start : " + appClassName);
            e.printStackTrace(System.err);
        }
    }

    /**
     * Gets the config attribute of the Rendezvous class
     *
     * @return The config value
     */
    private static String getConfig() {

        try {
            InputStreamReader in = new InputStreamReader(new FileInputStream(CONFIG_FILE));
            BufferedReader reader = new BufferedReader(in);
            return reader.readLine();
        } catch (Exception ez1) {
            return null;
        }
    }

    /**
     * given a peerid string, retrun the peername
     *
     * @param id peerid
     * @return peer name
     */
    protected String idToName(String id) {
        if (id == null) {
            return "";
        }
        String name = nodes.get(id);
        if (name != null) {
            return name;
        }
        Enumeration res;
        try {
            res = discovery.getLocalAdvertisements(DiscoveryService.PEER, "PID", id);
            if ((null == res) || (!res.hasMoreElements())) {
                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        while (res.hasMoreElements()) {
            PeerAdvertisement peer = (PeerAdvertisement) res.nextElement();
            name = peer.getName();
            nodes.put(id, name);
            return name;
        }
        return "";
    }
}

