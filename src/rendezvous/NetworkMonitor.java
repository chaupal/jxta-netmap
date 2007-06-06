/*
 *  Copyright (c) 2003-2005 Sun Microsystems, Inc.  All rights
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
 *  $Id: NetworkMonitor.java,v 1.7 2007/02/06 18:27:52 hamada Exp $
 */
package rendezvous;

import net.jxta.discovery.DiscoveryService;
import net.jxta.document.Advertisement;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.XMLDocument;
import net.jxta.endpoint.*;
import net.jxta.id.ID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.PipeMsgEvent;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.pipe.PipeService;
import net.jxta.platform.NetworkManager;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.protocol.RdvAdvertisement;
import net.jxta.rendezvous.RendezVousService;
import net.jxta.rendezvous.RendezvousEvent;
import net.jxta.rendezvous.RendezvousListener;
import rendezvous.event.StatusEvent;
import rendezvous.event.StatusListener;
import rendezvous.protocol.iViewMessage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An iview protocol based network monitor
 */

public class NetworkMonitor implements EndpointListener, PipeMsgListener, RendezvousListener {

    /**
     * The Log4J debugging category.
     */
    private final static Logger LOG = Logger.getLogger(NetworkMonitor.class.getName());

    private static long QueryInterval = 30 * 1000;
    private DiscoveryService discovery = null;
    private long earliestResponseDate = 0;
    private InputPipe inputPipe;
    private long lastQueryDate = 0;
    private long latestRespondedQueryDate = 0;
    private ArrayList listeners = new ArrayList();
    private StatusListener msgListener = null;
    private ID myid;
    private final Map nameTable = new HashMap();

    /**
     * the netpeergroup
     */
    protected static PeerGroup netPeerGroup = null;
    private PipeAdvertisement pipeAdv = null;
    private PipeService pipeService = null;
    private long qid = 0;
    private final Map rdvStats = new HashMap();
    private RendezVousService rendezvous = null;
    private long rendezvousConnectTime = -1;
    private long t0 = -1;
    private Timer timer = new Timer();
    private boolean timerTaskStarted = false;

    /**
     * Default constructor.
     */
    public NetworkMonitor(String name, URI home) {
        startJxta(name, home);
    }

    /**
     * Gets the mIARating attribute of the JxtaNetMap object
     *
     * @param rdvId rendezvous id
     * @return The mIARating value
     */
    public long getMIARating(String rdvId) {
        RendezvousStats rs = (RendezvousStats) rdvStats.get(rdvId);

        if (rs == null) {
            return 0;
        }
        return rs.getMIARating();
    }


    /**
     * Return all those that have an MIA rating above the threshold (like 10:
     * 10 times slower than their best).
     *
     * @param low  low threshold
     * @param high high threshold
     * @return an iterator of missing in action rendezvous ids
     */
    public Iterator getMIARatings(long low, long high) {
        ArrayList mias = new ArrayList();
        Iterator all = rdvStats.keySet().iterator();

        while (all.hasNext()) {
            String key = (String) all.next();
            long rating = getMIARating(key);

            if (rating >= low && rating <= high) {
                mias.add(key);
            }
        }
        return mias.iterator();
    }


    /**
     * Gets the rdvLagTime attribute of the JxtaNetMap object
     *
     * @param rdvId rendezvous id
     * @return The rdvLagTime value
     */
    public long getRdvLagTime(String rdvId) {
        RendezvousStats rs = (RendezvousStats) rdvStats.get(rdvId);

        if (rs == null) {
            return -1;
        }
        return rs.getLagTime();
    }


    /**
     * returns a resource InputStream
     *
     * @param resource resource name
     * @return returns a resource InputStream
     * @throws IOException if an I/O error occurs
     */
    private static InputStream getResourceInputStream(String resource) throws IOException {
        ClassLoader cl = NetworkMonitor.class.getClassLoader();

        return cl.getResourceAsStream(resource);
    }


    /**
     * The main program for the JxtaNetMap class
     *
     * @param args The command line arguments
     */
    public static void main(String[] args) {
        String jxta_home = System.getProperty("JXTA_HOME", ".jxta");
        File home = new File(jxta_home);

        Thread.currentThread().setName(NetworkMonitor.class.getName() + ".main()");

        String hostname = "iViewRendezvous";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException uhe) {
        }

        NetworkMonitor monitor = new NetworkMonitor(hostname, home.toURI());

        String mainLock = "Main Lock";
        synchronized (mainLock) {
            try {
                mainLock.wait();
            } catch (InterruptedException ie) {
            }
        }

    }


    /**
     * Description of the Method
     *
     * @param pidStr Description of the Parameter
     * @return Description of the Return Value
     */
    private String nameRdvNode(String pidStr) {

        String name = (String) nameTable.get(pidStr);
        if (name != null) {
            return name;
        }

        try {
            Enumeration res = discovery.getLocalAdvertisements(DiscoveryService.ADV, RdvAdvertisement.PeerIDTag, pidStr);
            while (res.hasMoreElements()) {
                Advertisement a = (Advertisement) res.nextElement();
                if (!(a instanceof RdvAdvertisement)) {
                    continue;
                }
                RdvAdvertisement ra = (RdvAdvertisement) a;
                name = ra.getName();
                nameTable.put(pidStr, name);
                return name;
            }
            // No luck. query next the net for next time
            discovery.getRemoteAdvertisements(null, DiscoveryService.ADV, RdvAdvertisement.PeerIDTag, pidStr, 1);

        } catch (Exception ohwell) {
            if (LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, "Failed while searching for name", ohwell);
            }
        }
        return pidStr;
    }


    /**
     * {@inheritDoc}
     *
     * @param event Description of the Parameter
     */
    public void pipeMsgEvent(PipeMsgEvent event) {
        Message msg = null;
        try {
            // grab the message from the event
            msg = event.getMessage();
            if (msg == null) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Received an empty message");
                }
                return;
            }
            MessageElement msgElement = msg.getMessageElement("jxta", iViewRendezvous.HANDLE);
            if (msgElement == null) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Received a message with no elements");
                }
                return;
            }

            StructuredDocument doc = StructuredDocumentFactory.newStructuredDocument(
                    msgElement.getMimeType(), msgElement.getStream());

            if (doc instanceof XMLDocument) {
                iViewMessage response = new iViewMessage(doc);
                // System.out.println("Received a response\n"+response.toString());
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Received a Response from :" +
                            response.getName());
                }
                printStats(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }


    /**
     * prints stats
     *
     * @param view the iview message
     */
    private synchronized void printStats(iViewMessage view) {
        iViewMessage.Entry entry = null;
        ID id = view.getSrcID();
        updateStats(id.toString(), view.getQueryDate());
        long lagtime = getRdvLagTime(id.toString());
        String lag = "lag=";
        if (lagtime < 0) {
            lag += "N/A|";
        } else {
            lag += lagtime + "ms|";
        }
        String stat = "|" + view.getName() + "| uptime=" + (view.getUptime() / 60000) + "m|thread count=" + view.getThreadNb() + "|mem used="
                + ((view.getTotalHeap() - view.getFreeHeap()) / (1024 * 1024)) + "M|total heap="
                + (view.getTotalHeap() / (1024 * 1024)) + "M|max heap=" + (view.getMaxHeap() / (1024 * 1024)) + "M|";
        stat += lag;
        stat += "client count=" + view.getEntries().size() + "|";
        stat += "up rdv=" + nameRdvNode(view.getUpPeer().toString()) + "|";
        stat += "dn rdv=" + nameRdvNode(view.getDownPeer().toString()) + "|";
        System.out.println(stat);
        if (msgListener != null) {
            try {
                StatusEvent event = new StatusEvent(this, stat, StatusEvent.SUCESS);
                msgListener.statusEvent(event);
            } catch (Throwable t) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "Exception occurred during listener callback", t);
                }
            }
        }
    }


    /**
     * {@inheritDoc}
     *
     * @param message Description of the Parameter
     * @param srcAddr Description of the Parameter
     * @param dstAddr Description of the Parameter
     */
    public void processIncomingMessage(Message message,
                                       EndpointAddress srcAddr,
                                       EndpointAddress dstAddr) {

        List list = rendezvous.getLocalWalkView();
        MessageElement element = (MessageElement)
                message.getMessageElement("jxta", iViewRendezvous.QUERY);

        if (element != null) {
            if (LOG.isLoggable(Level.FINE)) {
                String msg = "Recevied a query from : ";
                msg = msg +
                        message.getMessageElement("jxta", iViewRendezvous.QUERYPEERNAME).toString() + ", QID: " +
                        message.getMessageElement("jxta", iViewRendezvous.QUERYID).toString();
                LOG.fine(msg);
            }
        }
    }


    /**
     * issue a query
     */
    private void query() {

        Message message = new Message();

        message.addMessageElement("jxta", new StringMessageElement(iViewRendezvous.QUERY, netPeerGroup.getPeerID().toString(), null));
        message.addMessageElement("jxta", new StringMessageElement(iViewRendezvous.QUERYDATE, "" + System.currentTimeMillis(), null));
        message.addMessageElement("jxta", new StringMessageElement(iViewRendezvous.QUERYPEERNAME, netPeerGroup.getPeerName(), null));
        message.addMessageElement("jxta", new StringMessageElement(iViewRendezvous.QUERYID, "" + qid++, null));
        try {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Sending a Query ..............");
            }
            lastQueryDate = System.currentTimeMillis();
            rendezvous.walk(message, iViewRendezvous.HANDLE, iViewRendezvous.QUERY, 1);
        } catch (Exception io) {
            io.printStackTrace();
        }
    }


    /**
     * rendezvousEvent the rendezvous event
     *
     * @param event the rendezvous event
     */
    public synchronized void rendezvousEvent(RendezvousEvent event) {
        switch (event.getType()) {
            case RendezvousEvent.RDVCONNECT:
            case RendezvousEvent.RDVRECONNECT:
                rendezvousConnectTime = System.currentTimeMillis() - t0;
                System.out.println("Rendezvous connect time :" + rendezvousConnectTime + "ms");
                if (msgListener != null) {
                    try {
                        StatusEvent ev = new StatusEvent(this, "Rendezvous connect time :" + rendezvousConnectTime + "ms", StatusEvent.SUCESS);
                        msgListener.statusEvent(ev);
                    } catch (Throwable t) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.log(Level.FINE, "Exception occurred during listener callback", t);
                        }
                    }
                }
                startQueries();
                break;
            default:
                break;
        }
    }


    /**
     * @param msgListener The new listener value
     */
    public void setListener(StatusListener msgListener) {
        msgListener = msgListener;
    }


    /**
     * Starts the JXTA Platform This method be called after the platform has
     * been initialized
     */
    private void startJxta(String name, URI home) {

        try {
            t0 = System.currentTimeMillis();

            NetworkManager manager = new NetworkManager(NetworkManager.ConfigMode.EDGE, name, home);
            manager.startNetwork();

            netPeerGroup = manager.getNetPeerGroup();

            myid = netPeerGroup.getPeerID();
            discovery = netPeerGroup.getDiscoveryService();
            rendezvous = netPeerGroup.getRendezVousService();
            rendezvous.addListener(this);
            netPeerGroup.getEndpointService().addIncomingMessageListener(this, iViewRendezvous.HANDLE, iViewRendezvous.QUERY);
            pipeAdv = iViewRendezvous.createPipeAdv(iViewRendezvous.pipeID);
            pipeService = netPeerGroup.getPipeService();
            inputPipe = pipeService.createInputPipe(pipeAdv, this);
            if (rendezvous.isConnectedToRendezVous()) {
                rendezvousConnectTime = System.currentTimeMillis() - t0;
                System.out.println("Rendezvous connect time :" + rendezvousConnectTime + "ms");
                if (msgListener != null) {
                    try {
                        StatusEvent event = new StatusEvent(this, "Rendezvous connect time :" + rendezvousConnectTime + "ms", StatusEvent.SUCESS);
                        msgListener.statusEvent(event);
                    } catch (Throwable t) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.log(Level.FINE, "Exception occurred during listener callback", t);
                        }
                    }
                }
                startQueries();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * schedules query timer tasks
     */
    private void startQueries() {
        if (timerTaskStarted) {
            return;
        }
        timerTaskStarted = true;
        TimerTask queryTask =
                new TimerTask() {
                    public void run() {
                        Iterator rdvs = getMIARatings(0, 2);

                        while (rdvs.hasNext()) {
                            String itsId = (String) rdvs.next();
                            //FIXME printout good rating

                        }
                        rdvs = getMIARatings(2, 5);
                        while (rdvs.hasNext()) {
                            String itsId = (String) rdvs.next();
                            String message = "|RDV SLOW RESPONSE=" + nameRdvNode(itsId);
                            System.out.println(message);
                            if (msgListener != null) {
                                try {
                                    StatusEvent event = new StatusEvent(this, message, StatusEvent.FAILURE);
                                    msgListener.statusEvent(event);
                                } catch (Throwable t) {
                                    if (LOG.isLoggable(Level.FINE)) {
                                        LOG.log(Level.FINE, "Exception occurred during listener callback", t);
                                    }
                                }
                            }
                        }
                        Iterator mias = getMIARatings(6, Long.MAX_VALUE);
                        while (mias.hasNext()) {
                            String miaId = (String) mias.next();
                            String message = "|RDV MIA=" + nameRdvNode(miaId);
                            System.out.println(message);
                            if (msgListener != null) {
                                try {
                                    StatusEvent event = new StatusEvent(this, message, StatusEvent.FAILURE);
                                    msgListener.statusEvent(event);
                                } catch (Throwable t) {
                                    if (LOG.isLoggable(Level.FINE)) {
                                        LOG.log(Level.FINE, "Exception occurred during listener callback", t);
                                    }
                                }
                            }
                        }
                        query();
                    }
                };
        timer.schedule(queryTask, 1000 * 5, QueryInterval);
    }


    /**
     * If a peer does not provide us with the query time-Stamp, we settle for a
     * best guess the latest one. This will still detect MIAs reasonably.
     *
     * @param rdvId rendezvous id
     */
    public void updateStats(String rdvId) {
        updateStats(rdvId, lastQueryDate);
    }


    /**
     * updates stats for a given rendezvous
     *
     * @param rdvId     rendezvous id
     * @param queryDate query date in millis
     */
    public void updateStats(String rdvId, long queryDate) {
        long now = System.currentTimeMillis();

        if (latestRespondedQueryDate < queryDate) {
            latestRespondedQueryDate = queryDate;
            earliestResponseDate = now;
        }
        RendezvousStats rs = (RendezvousStats) rdvStats.get(rdvId);

        if (rs == null) {
            rs = new RendezvousStats();
            rdvStats.put(rdvId, rs);
        }
        rs.gotResponse(queryDate, now);
    }


    /**
     * rendezvous status tracker object
     */
    private class RendezvousStats {

        long lastRespondedQueryDate = 0;
        long lastResponseDate = 0;
        long personalShortestDelay = Long.MAX_VALUE;


        /**
         * Constructor for the RendezvousStats object
         */
        RendezvousStats() {
        }


        /**
         * How much this peer lags behind the most recent best.
         *
         * @return The lagTime value
         */
        long getLagTime() {
            long globalBest = earliestResponseDate - latestRespondedQueryDate;

            // We could display how the best ever of this peer compares, but it seems
            // more useful to display its current performance.
            // return personalShortestDelay - globalBest;
            return lastResponseDate - lastRespondedQueryDate - globalBest;
        }


        /**
         * returns the last response time
         *
         * @return The lastResponseDate value
         */
        long getLastResponseDate() {
            return lastResponseDate;
        }


        /**
         * Gets the lastResponseDelay attribute of the RendezvousStats object
         *
         * @return The lastResponseDelay value
         */
        long getLastResponseDelay() {
            return lastResponseDate - lastRespondedQueryDate;
        }


        /**
         * How long has this peer been silent; counted in number of queries. It
         * may be that this peer has an inordinately long RTT, but we should
         * still be receiving regular responses (albeit to old queries). If
         * not, it means that the queries or responses are likely lost; or the
         * peer is dead. The precision is dependent upon the query frequency,
         * since we are essentially monitoring
         *
         * @return The mIARating value
         */
        long getMIARating() {

            long now = System.currentTimeMillis();
            long silentTime = now - lastResponseDate;

            return silentTime / QueryInterval;
        }

        /**
         * calculates response from the rendezvous
         *
         * @param queryDate query data
         * @param time      last response date
         */
        void gotResponse(long queryDate, long time) {
            if (lastRespondedQueryDate < queryDate) {
                lastRespondedQueryDate = queryDate;
                lastResponseDate = time;
                long delay = lastResponseDate - lastRespondedQueryDate;

                if (delay < personalShortestDelay) {
                    personalShortestDelay = delay;
                }
            }
        }
    }
}

