/*
 *  TouchGraph LLC. Apache-Style Software License
 *
 *
 *  Copyright (c) 2001-2002 Alexander Shapiro. All rights reserved.
 *  Copyright (c) 2004-2005 Sun Microsystems Inc. All rights reserved.
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
 *  "This product includes software developed by
 *  TouchGraph LLC (http://www.touchgraph.com/)."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "TouchGraph" or "TouchGraph LLC" must not be used to endorse
 *  or promote products derived from this software without prior written
 *  permission.  For written permission, please contact
 *  alex@touchgraph.com
 *
 *  5. Products derived from this software may not be called "TouchGraph",
 *  nor may "TouchGraph" appear in their name, without prior written
 *  permission of alex@touchgraph.com.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL TOUCHGRAPH OR ITS CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  ====================================================================
 *
 */

import com.touchgraph.graphlayout.*;
import com.touchgraph.graphlayout.interaction.*;
import net.jxta.credential.AuthenticationCredential;
import net.jxta.credential.Credential;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.Advertisement;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.XMLDocument;
import net.jxta.endpoint.*;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.impl.membership.pse.StringAuthenticator;
import net.jxta.membership.MembershipService;
import net.jxta.peergroup.NetPeerGroupFactory;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.PipeMsgEvent;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.pipe.PipeService;
import net.jxta.platform.NetworkConfigurator;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.protocol.RdvAdvertisement;
import net.jxta.rendezvous.RendezVousService;
import net.jxta.rendezvous.RendezvousEvent;
import net.jxta.rendezvous.RendezvousListener;
import rendezvous.iViewRendezvous;
import rendezvous.protocol.iViewMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JxtaNetMap contains code for adding scroll-bars and interfaces to the
 * TGPanel The "GL" prefix indicates that this class is GraphLayout specific,
 * and will probably need to be rewritten for other applications.
 *
 * @author Alexander Shapiro
 * @author Mohamed AbdelAziz
 * @version 1.21 $Id: JxtaNetMap.java,v 1.37 2007/02/06 18:27:51 hamada Exp $
 */
public class JxtaNetMap extends GLPanel implements EndpointListener, PipeMsgListener, RendezvousListener {

    /**
     * Logger
     */
    private final static Logger LOG = Logger.getLogger(JxtaNetMap.class.getName());

    private final static boolean splitDupes = true;

    private final static Color LghtGryBlue = new Color(153, 153, 255);
    private final static Color LtGreen = new Color(204, 255, 204);
    private final static Color ltPurple = new Color(185, 185, 219);
    private final static Color sunGreen = new Color(192, 231, 179);
    private final static Color sunRed = new Color(0, 63, 81);

    //Edge colors (keep up and down color different from other edge colors)
    //private final Color edgeColor = Color.darkGray;
    private final static Color edgeColor = ltPurple;
    private final static Color upColor = new Color(29, 235, 78);
    private final static Color downColor = new Color(235, 29, 29);

    // Node colors
    private final static Color goodColor = sunGreen;
    private final static Color badColor = Color.red;
    private final static Color dupeColor = new Color(177, 153, 153);

    // Artificial anchor point and edges color
    private final static Color dimColor = LghtGryBlue;

    /**
     * the netpeergroup
     */
    protected static PeerGroup netPeerGroup = null;

    private DiscoveryService discovery = null;
    private EndpointService endpoint = null;
    private RendezVousService rendezvous = null;
    private PipeService pipeService = null;
    private PipeAdvertisement pipeAdv = null;
    private InputPipe inputPipe;
    private ID myid;
    private Node anchor = null;
    private long qid = 0;
    private final String lock = new String("Node Lock");

    // Interface color
    private Color defaultColor = Color.lightGray;

    private final Map<String, RendezvousStats> rdvStats = new HashMap<String, RendezvousStats>();

    private static long QueryInterval = 30 * 1000;
    private long lastQueryDate = 0;

    private long latestRespondedQueryDate = 0;
    private long earliestResponseDate = 0;
    private boolean timerTaskStarted = false;
    private final static String greendot = "/resources/green32.png";
    private final static String reddot = "/resources/red32.png";
    private Image greenImage;
    private Image redImage;
    private Icon redIcon;
    private Icon greenIcon;
    private JLabel statusDot;

    /**
     * rendezvous status tracker object
     */
    class RendezvousStats {

        long lastRespondedQueryDate = 0;
        long lastResponseDate = 0;
        long personalShortestDelay = Long.MAX_VALUE;

        /**
         * Constructor for the RendezvousStats object
         */
        RendezvousStats() {
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


        // How much this peer lags behind the most recent best.
        /**
         * Gets the lagTime attribute of the RendezvousStats object
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

        // How long has this peer been silent; counted in number of queries.
        // It may be that this peer has an inordinately long RTT, but we should still be
        // receiving regular responses (albeit to old queries). If not, it means that
        // the queries or responses are likely lost; or the peer is dead. The precision
        // is dependent upon the query frequency, since we are essentially monitoring
        // query loss.
        /**
         * Gets the mIARating attribute of the RendezvousStats object
         *
         * @return The mIARating value
         */
        long getMIARating() {
            long now = System.currentTimeMillis();
            long silentTime = now - lastResponseDate;
            return silentTime / QueryInterval;
        }
    }

    /**
     * If a peer does not provide us with the query time-Stamp, we settle
     * for a best guess the latest one. This will still detect MIAs reasonably.
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
        RendezvousStats rs = rdvStats.get(rdvId);

        if (rs == null) {
            rs = new RendezvousStats();
            rdvStats.put(rdvId, rs);
        }
        rs.gotResponse(queryDate, now);
    }


    /**
     * Gets the rdvLagTime attribute of the JxtaNetMap object
     *
     * @param rdvId rendezvous id
     * @return The rdvLagTime value
     */
    public long getRdvLagTime(String rdvId) {
        RendezvousStats rs = rdvStats.get(rdvId);

        if (rs == null) {
            return -1;
        }
        return rs.getLagTime();
    }

    /**
     * Gets the mIARating attribute of the JxtaNetMap object
     *
     * @param rdvId rendezvous id
     * @return The mIARating value
     */
    public long getMIARating(String rdvId) {
        RendezvousStats rs = rdvStats.get(rdvId);

        if (rs == null) {
            return 0;
        }
        return rs.getMIARating();
    }

    /**
     * Return all those that have an MIA rating above the threshold
     * (like 10: 10 times slower than their best).
     *
     * @param low  low threshold
     * @param high high threshold
     * @return an iterator of missing in action rendezvous ids
     */
    public Iterator<String> getMIARatings(long low, long high) {
        ArrayList<String> mias = new ArrayList<String>();

        for (Object o : rdvStats.keySet()) {
            String key = (String) o;
            long rating = getMIARating(key);

            if (rating >= low && rating <= high) {
                mias.add(key);
            }
        }
        return mias.iterator();
    }

    /**
     * Default constructor.
     */
    public JxtaNetMap() {
        scrollBarHash = new Hashtable();
        tgLensSet = new TGLensSet();
        tgPanel = new TGPanel();
        hvScroll = new HVScroll(tgPanel, tgLensSet);
        zoomScroll = new ZoomScroll(tgPanel);
        // hyperScroll = new HyperScroll(tgPanel);
        rotateScroll = new RotateScroll(tgPanel);
        localityScroll = new LocalityScroll(tgPanel);
        initialize();
    }

    /**
     * Constructor with a Color to be used for UI background.
     *
     * @param color default background color
     */
    public JxtaNetMap(Color color) {
        defaultColor = color;
        this.setBackground(color);
        scrollBarHash = new Hashtable();
        tgLensSet = new TGLensSet();
        tgPanel = new TGPanel();
        tgPanel.setBackground(color);
        hvScroll = new HVScroll(tgPanel, tgLensSet);
        zoomScroll = new ZoomScroll(tgPanel);
        rotateScroll = new RotateScroll(tgPanel);
        localityScroll = new LocalityScroll(tgPanel);
        initialize();
    }

    /**
     * Initialize panel, lens, and establish a random graph as a demonstration.
     */
    public void initialize() {
        buildPanel();
        buildLens();
        tgPanel.setLensSet(tgLensSet);
        addUIs();

        // Anchor node: The display engine needs all nodes to be transitively connected
        // it does not deal properly with multiple clusters. As a result, we make sure that
        // all clusters are anchored to an artificial anchor point. Since this impairs visualization
        // somewhat, we make sure that only one node in a given cluster is anchored in that manner.
        // That node is by convention, the top-most node of the cluster; that is, the rendezvous
        // that does not know an up-peer AND that no peer considers its down-peer.
        // Rdvs are created anchored, and the anchor is dropped as soon as it is proven that it
        // is not the top-most node. Whenever a rdv becomes a top-most node, we re-anchor it.

        try {
            anchor = tgPanel.addNode(".", " ");
            // Add a default anchor node
        } catch (Exception huh) {
            if (LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, "Cannot add even one node: ", huh);
            }
            return;
        }
        anchor.setBackColor(new Color(157, 176, 214));
        anchor.setType(Node.TYPE_CIRCLE);
        anchor.setLabel("n=0");
        tgPanel.setSelect(anchor);

    }


    /**
     * Colors duplicate nodes
     *
     * @param theId node id
     */
    private void colorDupes(String theId) {

        int comma = theId.indexOf(',');

        if (comma == -1) {
            // the given ID is not that of a split dupe. Ignore.
            return;
        }
        String baseId = theId.substring(0, comma + 1);
        // Include the ',' so that non-dupeIDs are ignored.

        // Check for dupes. Change color as needed.
        boolean foundOne = false;

        Iterator allNodes = tgPanel.getAllNodes();
        Node loner = null;
        while (allNodes.hasNext()) {
            Node aNode = (Node) allNodes.next();
            String anId = aNode.getID();

            if (anId.startsWith(baseId)) {
                // Here's one.
                if (foundOne) {
                    // Not the first. So, there are dupes. So color this node
                    aNode.setBackColor(dupeColor);
                    if (loner != null) {
                        // The first guy has not been colored yet.
                        loner.setBackColor(dupeColor);
                        loner = null;
                    }
                } else {
                    loner = aNode;
                    foundOne = true;
                }
            }
        }
        if (loner != null) {
            // There is only one. Color it as such.
            loner.setBackColor(Node.BACK_DEFAULT_COLOR);
        }
    }


    /**
     * The convention is that the top-most node must be anchored to the
     * artificial anchor point. Check if the given node has a top node. If not,
     * then it is the top-most node and so must be attached to the artificial
     * anchor point. Note: this method does not check whether the given is
     * already attached to the artificial anchor point. That's the invoker's
     * business. Limitation: if used on an edge-peer node, it will always
     * return true.
     *
     * @param theNode the node
     * @return true if top node
     */
    private boolean isTopNode(Node theNode) {

        Iterator currentEdges = theNode.getEdges();

        if (currentEdges == null) {
            return true;
        }
        while (currentEdges.hasNext()) {
            Edge e = (Edge) currentEdges.next();
            Node n = e.getFrom();

            // Anchorage is only through rdvs or alleged rdvs.
            if ((n.getType() != Node.TYPE_RECTANGLE) && (n.getType() != Node.TYPE_ROUNDRECT)) {
                continue;
            }
            if ((n != theNode) && (e.getColor().equals(downColor))) {
                return false;
                // found edge that says, this node is some other's down peer.
            }
            if ((n == theNode) && (e.getColor().equals(upColor))) {
                return false;
                // found edge that says we have an upPeer.
            }
        }
        return true;
    }


    /**
     * deletes a node from the graph
     *
     * @param theEdge the edge node to remove
     */
    private void killEdge(Edge theEdge) {

        Node theNode = theEdge.getTo();

        tgPanel.deleteEdge(theEdge);

        // It is not a rendezvous. It is removed when it becomes free standing.
        if (theNode.getType() != Node.TYPE_RECTANGLE) {
            int cnt = theNode.edgeCount();

            if (cnt == 1) {
                // Check that it's not just the anchorage.
                Edge e = tgPanel.findEdge(anchor, theNode);

                if (e != null) {
                    tgPanel.deleteEdge(e);
                    cnt = 0;
                }
            }
            if (cnt == 0) {
                String theId = theNode.getID();

                tgPanel.deleteNode(theNode);
                tgPanel.setSelect(anchor);

                // Re-color dupes as it may have changed.
                if (splitDupes) {
                    colorDupes(theId);
                }
                return;
            }
        }

        // Still here ? Ok, check anchorage for rdvs and alledged rdvs.
        // (The test above guarantees that alledged rdvs have something else than the default
        // anchorage, but it does not mean they actual have a default anchorage).

        if ((theNode.getType() == Node.TYPE_RECTANGLE) || (theNode.getType() == Node.TYPE_ROUNDRECT)) {

            if (isTopNode(theNode)) {
                // Make sure it is anchored.
                if (tgPanel.findEdge(anchor, theNode) == null) {
                    Edge e = tgPanel.addEdge(anchor, theNode, 2 * Edge.DEFAULT_LENGTH);

                    e.setColor(dimColor);
                }
            }
        }
    }


    /**
     * We're supposed to try and find the name of an alledged rdv that has not talked
     * to us yet. We might be lucky and have its rdvAdv around. If not, we'll try and
     * discover it. It would be tempting to ask the rdv service to probe it so that it gets
     * into the peerview, but if we have no rdvAdv for it, nor peeradv, then the rdvService
     * would have to discover one first (or the router). So, it would not help.
     *
     * @param theNode the node to name
     */
    private void nameRdvNode(Node theNode) {

        String pidStr = theNode.getID();

        try {
            Enumeration<Advertisement> res = discovery.getLocalAdvertisements(DiscoveryService.ADV, RdvAdvertisement.PeerIDTag, pidStr);

            while (res.hasMoreElements()) {
                Advertisement a = res.nextElement();
                if (!(a instanceof RdvAdvertisement)) {
                    continue;
                }
                RdvAdvertisement ra = (RdvAdvertisement) a;
                String name = ra.getName();
                theNode.setLabel("(" + name + ")");
                return;
            }

            // No cheese. Try so that we might get something next time.
            discovery.getRemoteAdvertisements(null, DiscoveryService.ADV, RdvAdvertisement.PeerIDTag, pidStr, 1);

        } catch (Exception ohwell) {
            if (LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, "Failed while searching for name", ohwell);
            }
        }
    }

    /**
     * Draws a received message
     *
     * @param view the iview message
     */
    private synchronized void draw(iViewMessage view) {
        iViewMessage.Entry entry = null;
        Node r = null;

        try {
            synchronized (lock) {
                ID id = view.getSrcID();
                String name = null;

                if (view.getQueryDate() != -1) {
                    updateStats(id.toString(), view.getQueryDate());
                } else {
                    updateStats(id.toString());
                }

                if (view.getThreadNb() != -1) {
                    name = view.getName() + " up:" + (view.getUptime() / 60000) + "m th:" + view.getThreadNb() + " used:"
                            + ((view.getTotalHeap() - view.getFreeHeap()) / (1024 * 1024)) + "M heap Total/Max:"
                            + (view.getTotalHeap() / (1024 * 1024)) + "M/" + (view.getMaxHeap() / (1024 * 1024)) + "M MAX Clients :" + view.getMaxNoClients();
                } else {
                    name = view.getName();
                }
                long lagtime = getRdvLagTime(id.toString());

                if (lagtime < 0) {
                    name += " lag: N/A";
                } else {
                    name += " lag:" + lagtime + "ms";
                }

                r = tgPanel.findNode(id.toString());
                if (r == null) {
                    r = tgPanel.addNode(id.toString(), name);
                    // anchor it for now.
                    Edge e = tgPanel.addEdge(anchor, r, 2 * Edge.DEFAULT_LENGTH);
                    if (id.equals(myid)) {
                        e.setColor(Color.red);
                    } else {
                        e.setColor(dimColor);
                    }

                } else {
                    r.setLabel(name);
                }

                r.setType(Node.TYPE_RECTANGLE);
                Iterator currentEdges = r.getEdges();
                HashMap<String, Edge> formerEdges = new HashMap<String, Edge>(r.edgeCount());

                while ((currentEdges != null) && currentEdges.hasNext()) {
                    Edge e = (Edge) currentEdges.next();
                    Node n = e.getTo();
                    // if (n.getType() != Node.TYPE_RECTANGLE) {
                    // Store only edges FROM the respondent.
                    if (n != r) {
                        formerEdges.put(n.getID(), e);
                    }
                }

                List<iViewMessage.Entry> entries = view.getEntries();

                ID upPeer = view.getUpPeer();
                ID downPeer = view.getDownPeer();

                String upPeerStr = upPeer.toString();
                String downPeerStr = downPeer.toString();

                if (!upPeer.equals(ID.nullID)) {
                    entries.add(new iViewMessage.Entry(upPeer, "?"));
                }
                if (!downPeer.equals(ID.nullID)) {
                    entries.add(new iViewMessage.Entry(downPeer, "?"));
                }

                for (Object entry1 : entries) {
                    entry = (iViewMessage.Entry) entry1;
                    String ename = entry.name;
                    if (name.length() > 255) {
                        ename = entry.id.toString();
                    }
                    String entryId = entry.id.toString();
                    Edge theEdge = formerEdges.get(entryId);
                    Node theNode = null;

                    if (theEdge == null) {

                        // ONLY for reported edge peers. Do the optional dupe splitting.
                        // If the peer already exists as a non-edge, it will not get de-duped.
                        // We will just point at it, thereby emphasizing the possible inconsistency.
                        if (splitDupes && (!entryId.equals(upPeerStr)) && (!entryId.equals(downPeerStr))) {
                            String dedupeId = entryId + "," + r.getID();

                            theEdge = formerEdges.get(dedupeId);
                            if (theEdge == null) {
                                theNode = tgPanel.addNode(dedupeId, ename);
                                theNode.setType(Node.TYPE_ELLIPSE);

                                // Add a provisional edge to simplify code. We'll replace it
                                // if needed.
                                theEdge = tgPanel.addEdge(r, theNode, Edge.DEFAULT_LENGTH);
                                theEdge.setColor(edgeColor);
                                colorDupes(dedupeId);
                                // the comma must be in-there.
                            } else {
                                // Just make sure it's taken off the list of "exes".
                                formerEdges.remove(dedupeId);
                                theNode = theEdge.getTo();
                            }
                        } else {
                            theNode = tgPanel.findNode(entryId);
                            if (theNode == null) {
                                theNode = tgPanel.addNode(entryId, ename);
                                theNode.setType(Node.TYPE_ELLIPSE);
                            }
                            // Add a provisional edge to simplify code. We'll replace it
                            // if needed.
                            theEdge = tgPanel.addEdge(r, theNode, Edge.DEFAULT_LENGTH);
                            theEdge.setColor(edgeColor);
                        }

                    } else {
                        // Just make sure it's taken off the list of "exes".
                        theNode = theEdge.getTo();
                        formerEdges.remove(entryId);
                    }

                    if (entryId.equals(upPeerStr)) {

                        if (!theEdge.getColor().equals(upColor)) {
                            // Fix that edge.
                            theEdge.setLength(2 * Edge.DEFAULT_LENGTH);
                            theEdge.setColor(upColor);
                        }

                        // Upgrade to alledged rdv if needed.
                        if (theNode.getType() != Node.TYPE_RECTANGLE) {
                            theNode.setType(Node.TYPE_ROUNDRECT);

                            // We need to make sure it is itself properly anchored; only
                            // actual rdvs are created anchored by default.
                            if (isTopNode(theNode)) {
                                if (tgPanel.findEdge(anchor, theNode) == null) {
                                    Edge e = tgPanel.addEdge(anchor, theNode, 2 * Edge.DEFAULT_LENGTH);
                                    e.setColor(dimColor);
                                }
                            }

                            if (theNode.getLabel().equals("?")) {
                                nameRdvNode(theNode);
                            }
                        }

                        // theNode is now the respondent's upPeer. Would it relieve
                        // the respondant from being its cluster's anchorage ?

                        Edge anchorEdge = tgPanel.findEdge(anchor, r);

                        if (anchorEdge != null) {
                            tgPanel.deleteEdge(anchorEdge);
                        }
                    } else if (entryId.equals(downPeerStr)) {

                        if (!theEdge.getColor().equals(downColor)) {
                            // Fix that edge.
                            theEdge.setLength(2 * Edge.DEFAULT_LENGTH);
                            theEdge.setColor(downColor);
                        }

                        // Upgrade to alledged rdv if needed.
                        if (theNode.getType() != Node.TYPE_RECTANGLE) {
                            theNode.setType(Node.TYPE_ROUNDRECT);
                            // No need to anchor, though. We're about to
                            // drop anchorage it if exists anyway.

                            if (theNode.getLabel().equals("?")) {
                                nameRdvNode(theNode);
                            }
                        }

                        // theNode is now the downPeer of the respondant. Does it relieve
                        // theNode from being its cluster's anchorage ?
                        Edge anchorEdge = tgPanel.findEdge(anchor, theNode);

                        if (anchorEdge != null) {
                            tgPanel.deleteEdge(anchorEdge);
                        }
                    } else {
                        if (!theEdge.getColor().equals(edgeColor)) {

                            // Fix that edge now.
                            theEdge.setLength(Edge.DEFAULT_LENGTH);
                            theEdge.setColor(edgeColor);

                            // Although rather unlikely, we have to account for the fact
                            // that this might have been our up or down peer up to now, and
                            // is now reported as a client.
                            // This means that this edge is no-longer sufficient
                            // to guarantee anchorage. We must check if the respondant or
                            // the node becomes its cluster's anchorage.
                            if (isTopNode(r)) {
                                // Make sure it is anchored.
                                if (tgPanel.findEdge(anchor, r) == null) {
                                    Edge e = tgPanel.addEdge(anchor, r, 2 * Edge.DEFAULT_LENGTH);
                                    e.setColor(dimColor);
                                }
                            }
                            if (isTopNode(theNode)) {
                                // Make sure it is anchored.
                                if (tgPanel.findEdge(anchor, theNode) == null) {
                                    Edge e = tgPanel.addEdge(anchor, theNode, 2 * Edge.DEFAULT_LENGTH);
                                    e.setColor(dimColor);
                                }
                            }
                        }
                    }
                }

                // The former nodes that remain are no-longer attached to this respondant.
                // detach them and, if they're free standing now, kill them. Former edges contains
                // only edge TO other nodes.
                for (Object o : formerEdges.values()) {
                    Edge theEdge = (Edge) o;
                    killEdge(theEdge);
                }
                tgPanel.setLocale(r, 10);
            }
        } catch (TGException tge) {
            if (LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, "An exception occurred: ", tge);
            }
        }

        String label = "n=" + (tgPanel.getNodeCount() - 1);
        anchor.setLabel(label);
        setVisible(true);
    }

    Timer timer = new Timer();

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
                        synchronized (lock) {
                            Iterator<String> rdvs = getMIARatings(0, 2);

                            while (rdvs.hasNext()) {
                                String itsId = rdvs.next();
                                Node rdv = tgPanel.findNode(itsId);
                                rdv.setBackColor(goodColor);
                            }
                            rdvs = getMIARatings(2, 5);
                            while (rdvs.hasNext()) {
                                String itsId = rdvs.next();
                                Node rdv = tgPanel.findNode(itsId);
                                rdv.setBackColor(badColor);
                            }
                            Iterator<String> mias = getMIARatings(6, Long.MAX_VALUE);

                            while (mias.hasNext()) {
                                String miaId = mias.next();
                                Node mia = tgPanel.findNode(miaId);
                                rdvStats.remove(miaId);

                                // Kill the edges now. killEdge() deals with the consequences.
                                // We no-longer believe that this is a valid rdv, but
                                // if some other rdv has an edge to it (like it thinks it is
                                // an up or down peer) we must keep showing that. This means
                                // that if there is at least one inbound edge from an rdv,
                                // we keep this node. Just change it to "suspected rdv" with
                                // a rounded rect shape.

                                boolean keepNode = false;
                                Iterator discEdges = mia.getEdges();

                                // Copy the collection. No idea how this iterator implements
                                // remove. And the underlying collection has to be modified.
                                if (discEdges != null) {
                                    ArrayList discEdgesL = new ArrayList();

                                    while (discEdges.hasNext()) {
                                        discEdgesL.add(discEdges.next());
                                    }
                                    discEdges = discEdgesL.iterator();

                                    while (discEdges.hasNext()) {
                                        Edge theEdge = (Edge) discEdges.next();
                                        Node origin = theEdge.getFrom();

                                        if ((mia != origin) && (origin.getType() == Node.TYPE_RECTANGLE)) {
                                            keepNode = true;
                                        } else {
                                            killEdge(theEdge);
                                        }
                                    }
                                }

                                // Finally we may remove this node from the display.
                                if (keepNode) {
                                    mia.setType(Node.TYPE_ROUNDRECT);
                                    mia.setLabel("?");
                                    // The name is now dubious.
                                } else {
                                    tgPanel.deleteNode(mia);
                                    tgPanel.setSelect(anchor);
                                }
                            }
                        }
                        query();
                    }
                };

        timer.schedule(queryTask, 1000 * 5, QueryInterval);
    }


    /**
     * issue a query
     */
    private void query() {

        Message message = new Message();

        message.addMessageElement("jxta", new StringMessageElement(iViewRendezvous.QUERY, netPeerGroup.getPeerID().toString(), null));
        message.addMessageElement("jxta", new StringMessageElement(iViewRendezvous.QUERYDATE, Long.toString(System.currentTimeMillis()), null));
        message.addMessageElement("jxta", new StringMessageElement(iViewRendezvous.QUERYPEERNAME, netPeerGroup.getPeerName(), null));
        message.addMessageElement("jxta", new StringMessageElement(iViewRendezvous.QUERYID, "" + qid++, null));
        try {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Sending a Query ..............");
            }
            synchronized (lock) {
                lastQueryDate = System.currentTimeMillis();
            }
            rendezvous.walk(message, iViewRendezvous.HANDLE, iViewRendezvous.QUERY, 1);
        } catch (Exception io) {
            LOG.log(Level.SEVERE, "Could not generate query.", io);
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
            case RendezvousEvent.BECAMERDV:
                statusDot.setIcon(greenIcon);
                startQueries();
                break;
            default:
                break;
        }
    }


    /**
     * Starts the JXTA Platform This method be called after the platform has
     * been initialized
     */
    private void startJxta() {
        File home = new File(System.getProperty("JXTA_HOME", ".jxta/"));
        home.mkdirs();

        try {
            NetworkConfigurator config = new NetworkConfigurator(NetworkConfigurator.EDGE_NODE, home.toURI());
            if (!config.exists()) {
                config.setPeerID(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID));
                config.setName("JxtaNetMap Viewer");
                config.setPassword(System.getProperty("net.jxta.tls.password", "password"));
                config.setTcpStartPort(9701);
                config.setTcpEndPort(9799);
                config.addRdvSeedingURI(URI.create("http://rdv.jxtahosts.net/cgi-bin/rendezvous.cgi?2"));
                config.addRelaySeedingURI(URI.create("http://rdv.jxtahosts.net/cgi-bin/relays.cgi?2"));
                config.save();
            } else {
                config.load();
            }

            NetPeerGroupFactory npgf = new NetPeerGroupFactory(config.getPlatformConfig(), home.toURI());
            // create, and Start the default jxta NetPeerGroup
            netPeerGroup = npgf.getInterface();
            netPeerGroup.startApp(null);

            MembershipService membership = netPeerGroup.getMembershipService();
            Credential cred = membership.getDefaultCredential();
            if (null == cred) {
                AuthenticationCredential authCred = new AuthenticationCredential(netPeerGroup, "StringAuthentication", null);

                StringAuthenticator auth = null;
                try {
                    auth = (StringAuthenticator) membership.apply(authCred);
                } catch (Exception failed) {
                    ;
                }

                if (null != auth) {
                    auth.setAuth1_KeyStorePassword(System.getProperty("net.jxta.tls.password", "password").toCharArray());
                    auth.setAuth2Identity(netPeerGroup.getPeerID());
                    auth.setAuth3_IdentityPassword(System.getProperty("net.jxta.tls.password", "password").toCharArray());
                    if (auth.isReadyForJoin()) {
                        membership.join(auth);
                    }
                }
            }

            myid = netPeerGroup.getPeerID();
            discovery = netPeerGroup.getDiscoveryService();
            rendezvous = netPeerGroup.getRendezVousService();
            endpoint = netPeerGroup.getEndpointService();
            rendezvous.addListener(this);
            endpoint.addIncomingMessageListener(this, iViewRendezvous.HANDLE, iViewRendezvous.QUERY);
            pipeAdv = iViewRendezvous.createPipeAdv(iViewRendezvous.pipeID);
            pipeService = netPeerGroup.getPipeService();
            inputPipe = pipeService.createInputPipe(pipeAdv, this);
            if (rendezvous.isConnectedToRendezVous()) {
                statusDot.setIcon(greenIcon);
                startQueries();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failure starting JXTA", e);
        }
    }


    /**
     * {@inheritDoc}
     */
    public void processIncomingMessage(Message message,
                                       EndpointAddress srcAddr,
                                       EndpointAddress dstAddr) {

        List<RdvAdvertisement> list = rendezvous.getLocalWalkView();
        MessageElement element = message.getMessageElement("jxta", iViewRendezvous.QUERY);

        if (element != null) {
            if (LOG.isLoggable(Level.FINE)) {
                String msg = "Recevied a query from : " +
                        message.getMessageElement("jxta", iViewRendezvous.QUERYPEERNAME).toString() + ", QID: " +
                        message.getMessageElement("jxta", iViewRendezvous.QUERYID).toString();
                LOG.fine(msg);
            }
        }
    }

    /**
     * {@inheritDoc}
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
                    LOG.fine("Received a Response from :" + response.getName());
                }
                draw(response);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failure handling pipe message event", e);
            return;
        }
    }

    /**
     * Return the TGPanel used with this JxtaNetMap.
     *
     * @return The tGPanel value
     */
    public TGPanel getTGPanel() {
        return tgPanel;
    }

    // navigation .................

    /**
     * Return the HVScroll used with this JxtaNetMap.
     *
     * @return The hVScroll value
     */
    public HVScroll getHVScroll() {
        return hvScroll;
    }


    /**
     * Sets the horizontal offset to p.x, and the vertical offset to p.y given
     * a Point <tt>p<tt>.
     *
     * @param p The new offset value
     */
    public void setOffset(Point p) {
        hvScroll.setOffset(p);
    }


    /**
     * Return the horizontal and vertical offset position as a Point.
     *
     * @return The offset value
     */
    public Point getOffset() {
        return hvScroll.getOffset();
    }

    // rotation ...................

    /**
     * Return the RotateScroll used with this JxtaNetMap.
     *
     * @return The rotateScroll value
     */
    public RotateScroll getRotateScroll() {
        return rotateScroll;
    }


    /**
     * Set the rotation angle of this JxtaNetMap (allowable values between 0 to
     * 359).
     *
     * @param angle The new rotationAngle value
     */
    public void setRotationAngle(int angle) {
        rotateScroll.setRotationAngle(angle);
    }


    /**
     * Return the rotation angle of this JxtaNetMap.
     *
     * @return The rotationAngle value
     */
    public int getRotationAngle() {
        return rotateScroll.getRotationAngle();
    }

    // locality ...................

    /**
     * Return the LocalityScroll used with this JxtaNetMap.
     *
     * @return The localityScroll value
     */
    public LocalityScroll getLocalityScroll() {
        return localityScroll;
    }


    /**
     * Set the locality radius of this TGScrollPane (allowable values between 0
     * to 4, or LocalityUtils.INFINITE_LOCALITY_RADIUS).
     *
     * @param radius The new localityRadius value
     */
    public void setLocalityRadius(int radius) {
        localityScroll.setLocalityRadius(radius);
    }


    /**
     * Return the locality radius of this JxtaNetMap.
     *
     * @return The localityRadius value
     */
    public int getLocalityRadius() {
        return localityScroll.getLocalityRadius();
    }

    // zoom .......................

    /**
     * Return the ZoomScroll used with this JxtaNetMap.
     *
     * @return The zoomScroll value
     */
    public ZoomScroll getZoomScroll() {
        return zoomScroll;
    }


    /**
     * Set the zoom value of this JxtaNetMap (allowable values between -100 to
     * 100).
     *
     * @param zoomValue The new zoomValue value
     */
    public void setZoomValue(int zoomValue) {
        zoomScroll.setZoomValue(zoomValue);
    }


    /**
     * Return the zoom value of this JxtaNetMap.
     *
     * @return The zoomValue value
     */
    public int getZoomValue() {
        return zoomScroll.getZoomValue();
    }


    /**
     * Gets the gLPopup attribute of the JxtaNetMap object
     *
     * @return The gLPopup value
     */
    public JPopupMenu getGLPopup() {
        return glPopup;
    }


    /**
     * Builds the lens
     */
    public void buildLens() {
        tgLensSet.addLens(hvScroll.getLens());
        tgLensSet.addLens(zoomScroll.getLens());
        // tgLensSet.addLens(hyperScroll.getLens());
        tgLensSet.addLens(rotateScroll.getLens());
        tgLensSet.addLens(tgPanel.getAdjustOriginLens());
    }

    /**
     * creates the panel
     */
    public void buildPanel() {
        final JScrollBar horizontalSB = hvScroll.getHorizontalSB();
        final JScrollBar verticalSB = hvScroll.getVerticalSB();
        final JScrollBar zoomSB = zoomScroll.getZoomSB();
        final JScrollBar rotateSB = rotateScroll.getRotateSB();
        final JScrollBar localitySB = localityScroll.getLocalitySB();

        setLayout(new BorderLayout());
        JPanel scrollPanel = new JPanel();

        greenImage = getToolkit().getImage(getClass().getResource(greendot));
        redImage = getToolkit().getImage(getClass().getResource(reddot));
        redIcon = new ImageIcon(redImage);
        greenIcon = new ImageIcon(greenImage);
        statusDot = new JLabel();

        scrollPanel.setBackground(defaultColor);
        scrollPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        JPanel modeSelectPanel = new JPanel();

        modeSelectPanel.setBackground(defaultColor);
        modeSelectPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));

        AbstractAction navigateAction =
                new AbstractAction("Navigate") {
                    public void actionPerformed(ActionEvent e) {
                        tgUIManager.activate("Navigate");
                    }
                };

        AbstractAction editAction =
                new AbstractAction("Edit") {
                    public void actionPerformed(ActionEvent e) {
                        tgUIManager.activate("Edit");
                    }
                };

        JRadioButton rbNavigate = new JRadioButton(navigateAction);

        rbNavigate.setBackground(defaultColor);
        rbNavigate.setSelected(true);
        JRadioButton rbEdit = new JRadioButton(editAction);

        rbEdit.setBackground(defaultColor);
        ButtonGroup bg = new ButtonGroup();

        bg.add(rbNavigate);
        bg.add(rbEdit);
        statusDot.setIcon(redIcon);
        modeSelectPanel.add(statusDot);
        modeSelectPanel.add(rbNavigate);
        modeSelectPanel.add(rbEdit);
        rbEdit.setEnabled(false);
        final JPanel topPanel = new JPanel();

        topPanel.setBackground(defaultColor);
        topPanel.setLayout(new GridBagLayout());
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.weightx = 0;
        c.insets = new Insets(0, 10, 0, 10);
        topPanel.add(modeSelectPanel, c);
        c.insets = new Insets(0, 0, 0, 0);
        c.gridx = 1;
        c.weightx = 1;

        scrollBarHash.put(zoomLabel, zoomSB);
        scrollBarHash.put(rotateLabel, rotateSB);
        scrollBarHash.put(localityLabel, localitySB);

        JPanel scrollselect = scrollSelectPanel(new String[]{zoomLabel, rotateLabel, localityLabel});

        scrollselect.setBackground(defaultColor);
        topPanel.add(scrollselect, c);

        add(topPanel, BorderLayout.NORTH);

        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 1;
        c.weighty = 1;
        scrollPanel.add(tgPanel, c);

        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 0;
        c.weighty = 0;
        scrollPanel.add(verticalSB, c);

        c.gridx = 0;
        c.gridy = 2;
        scrollPanel.add(horizontalSB, c);

        add(scrollPanel, BorderLayout.CENTER);

        glPopup = new JPopupMenu();
        glPopup.setBackground(defaultColor);

        JMenuItem menuItem = new JMenuItem("Toggle Controls");
        ActionListener toggleControlsAction =
                new ActionListener() {
                    boolean controlsVisible = true;


                    public void actionPerformed(ActionEvent e) {
                        controlsVisible = !controlsVisible;
                        horizontalSB.setVisible(controlsVisible);
                        verticalSB.setVisible(controlsVisible);
                        topPanel.setVisible(controlsVisible);
                    }
                };

        menuItem.addActionListener(toggleControlsAction);
        glPopup.add(menuItem);
    }


    /**
     * builds the scroll panel
     *
     * @param scrollBarNames array of scroll bar names
     * @return the panel
     */
    protected JPanel scrollSelectPanel(String[] scrollBarNames) {
        final JComboBox scrollCombo = new JComboBox(scrollBarNames);

        scrollCombo.setBackground(defaultColor);
        scrollCombo.setPreferredSize(new Dimension(80, 20));
        scrollCombo.setSelectedIndex(0);
        final JScrollBar initialSB = (JScrollBar) scrollBarHash.get(scrollBarNames[0]);

        scrollCombo.addActionListener(
                new ActionListener() {
                    JScrollBar currentSB = initialSB;


                    public void actionPerformed(ActionEvent e) {
                        JScrollBar selectedSB = (JScrollBar) scrollBarHash.get((String) scrollCombo.getSelectedItem());

                        if (currentSB != null) {
                            currentSB.setVisible(false);
                        }
                        if (selectedSB != null) {
                            selectedSB.setVisible(true);
                        }
                        currentSB = selectedSB;
                    }
                }
        );

        final JPanel sbp = new JPanel(new GridBagLayout());

        sbp.setBackground(defaultColor);
        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        sbp.add(scrollCombo, c);
        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 10, 0, 17);
        c.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < scrollBarNames.length; i++) {
            JScrollBar sb = (JScrollBar) scrollBarHash.get(scrollBarNames[i]);

            if (sb == null) {
                continue;
            }
            if (i != 0) {
                sb.setVisible(false);
            }
            // sb.setMinimumSize(new Dimension(200,17));
            sbp.add(sb, c);
        }
        return sbp;
    }

    /**
     * Adds a feature to the UIs attribute of the JxtaNetMap object
     */
    public void addUIs() {
        tgUIManager = new TGUIManager();
        GLEditUI editUI = new GLEditUI(this);
        GLNavigateUI navigateUI = new GLNavigateUI(this);

        tgUIManager.addUI(editUI, "Edit");
        tgUIManager.addUI(navigateUI, "Navigate");
        tgUIManager.activate("Navigate");
    }


    /**
     * The main program for the JxtaNetMap class
     *
     * @param args The command line arguments
     */
    public static void main(String[] args) {

        Thread.currentThread().setName("JxtaNetMap.main()");

        JFrame frame = new JFrame("JXTA iView");
        JxtaNetMap jxv = new JxtaNetMap();

        jxv.startJxta();
        frame.addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        System.exit(0);
                    }
                }
        );

        frame.getContentPane().add("Center", jxv);
        frame.setSize(500, 500);
        frame.setVisible(true);
    }
}

