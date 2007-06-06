/*
 *
 * $Id: JxtaNetmapViewer.java,v 1.7 2007/05/14 17:39:30 bondolo Exp $
 *
 * Copyright (c) 2006 Sun Microsystems, Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *       Sun Microsystems, Inc. for Project JXTA."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA"
 *    must not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact Project JXTA at http://www.jxta.org.
 *
 * 5. Products derived from this software may not be called "JXTA",
 *    nor may "JXTA" appear in their name, without prior written
 *    permission of Sun.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL SUN MICROSYSTEMS OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of Project JXTA.  For more
 * information on Project JXTA, please see
 * <http://www.jxta.org/>.
 *
 * This license is based on the BSD license adopted by the Apache Foundation.
 */
package net.jxta.netmap;

import net.jxta.discovery.DiscoveryService;
import net.jxta.document.StructuredDocument;
import net.jxta.document.StructuredDocumentFactory;
import net.jxta.document.XMLDocument;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.PipeMsgEvent;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.pipe.PipeService;
import net.jxta.platform.NetworkManager;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.rendezvous.RendezVousService;
import net.jxta.rendezvous.RendezvousEvent;
import net.jxta.rendezvous.RendezvousListener;
import prefuse.Constants;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.ActionList;
import prefuse.action.RepaintAction;
import prefuse.action.assignment.ColorAction;
import prefuse.action.assignment.DataColorAction;
import prefuse.action.layout.Layout;
import prefuse.action.layout.graph.ForceDirectedLayout;
import prefuse.activity.Activity;
import prefuse.controls.ControlAdapter;
import prefuse.controls.PanControl;
import prefuse.controls.ToolTipControl;
import prefuse.controls.ZoomControl;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.data.Schema;
import prefuse.data.Table;
import prefuse.data.expression.AndPredicate;
import prefuse.data.expression.Predicate;
import prefuse.data.expression.parser.ExpressionParser;
import prefuse.data.query.SearchQueryBinding;
import prefuse.render.*;
import prefuse.render.Renderer;
import prefuse.util.*;
import prefuse.util.ui.JFastLabel;
import prefuse.util.ui.JSearchPanel;
import prefuse.util.ui.UILib;
import prefuse.visual.*;
import prefuse.visual.expression.InGroupPredicate;
import rendezvous.iViewRendezvous;
import rendezvous.protocol.iViewMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class JxtaNetmapViewer extends Display implements PipeMsgListener, RendezvousListener {

    /**
     * Logger
     */
    private final static Logger LOG = Logger.getLogger(JxtaNetmapViewer.class.getName());

    private final static String AGGR = "aggregates";
    private final static String EDGES = "graph.edges";
    private final static String GRAPH = "graph";
    private final static String NODES = "graph.nodes";
    private final static String greendot = "/resources/green32.png";
    private final static String reddot = "/resources/red32.png";
    private static long QueryInterval = 30 * 1000;
    private static final String label = "label";

    // create data description of labels, setting colors, fonts ahead of time
    private static final Schema LABEL_SCHEMA = PrefuseLib.getVisualItemSchema();

    static {
        LABEL_SCHEMA.setDefault(VisualItem.INTERACTIVE, false);
        LABEL_SCHEMA.setDefault(VisualItem.TEXTCOLOR, ColorLib.gray(200));
        LABEL_SCHEMA.setDefault(VisualItem.FONT, FontLib.getFont("Tahoma", 16));
    }

    private static JxtaNetmapViewer instance = null;

    private SearchQueryBinding searchQ;
    private Graph graph;
    private Table ntbl;
    private Table etbl;
    private VisualGraph vg;
    private AggregateTable table;

    /**
     * the netpeergroup
     */
    private PeerGroup netPeerGroup = null;
    private DiscoveryService discovery = null;
    private RendezVousService rendezvous = null;
    private PipeService pipeService = null;
    private PipeAdvertisement pipeAdv = null;
    private InputPipe inputPipe;
    private ID myid;
    private Node anchor = null;
    private long qid = 0;
    private final Object lock = new String("Node Lock");
    private final Random random = new Random();
    private final Map<String,RendezvousStats> rdvStats = new HashMap<String,RendezvousStats>();

    private long lastQueryDate = 0;

    private long latestRespondedQueryDate = 0;
    private long earliestResponseDate = 0;
    private boolean timerTaskStarted = false;
    private Image greenImage;
    private Image redImage;
    private Icon redIcon;
    private Icon greenIcon;
    private JLabel statusDot;

    private Timer timer = new Timer("Query Timer");

    private Map<ID, RendezvousNode> rdvnodes = new HashMap<ID, RendezvousNode>();
    private int nodeCount = 0;
    private ToolTipControl ttc = new ToolTipControl("tooltip");

    /**
     * Constructor for the JxtaNetmapViewer object
     */
    public JxtaNetmapViewer() {
        // initialize display and data
        super(new Visualization());
        // set up the renderers
        // draw the nodes as basic shapes
        Renderer nodeR = new ShapeRenderer(20);
        // draw aggregates as polygons with curved edges
        Renderer polyR = new PolygonRenderer(Constants.POLY_TYPE_CURVE);
        ((PolygonRenderer) polyR).setCurveSlack(0.15f);

        DefaultRendererFactory drf = new DefaultRendererFactory();

        drf.setDefaultRenderer(nodeR);
        drf.add("ingroup('aggregates')", polyR);
        LabelRenderer labelr = new LabelRenderer(label);
        labelr.setRoundedCorner(8, 8);
        drf.add(new InGroupPredicate(label), labelr);

        m_vis.setRendererFactory(drf);

        graph = new Graph();
        ntbl = graph.getNodeTable();
        etbl = graph.getEdgeTable();

        ntbl.addColumn("color", int.class);
        ntbl.addColumn("label", String.class);
        ntbl.addColumn("pid", String.class);
        ntbl.addColumn("tooltip", String.class);
        ntbl.addColumn("id", int.class);
        ntbl.addColumn("rdvid", int.class);
        etbl.addColumn("label", String.class);
        etbl.addColumn("color", int.class);
        etbl.addColumn("pid", String.class);
        etbl.addColumn("tooltip", String.class);
        etbl.addColumn("id", int.class);
        etbl.addColumn("rdvid", int.class);
        anchor = graph.addNode();
        anchor.setInt("id", 0);
        anchor.setInt("color", 0);
        anchor.setString(label, "n=" + nodeCount);
        anchor.setString("tooltip", "Total node count " + nodeCount);
        vg = m_vis.addGraph(GRAPH, graph);
        m_vis.setInteractive(EDGES, null, false);
        m_vis.setValue(NODES, null, VisualItem.SHAPE, Constants.SHAPE_ELLIPSE);

        table = m_vis.addAggregates(AGGR);
        table.addColumn(VisualItem.POLYGON, float[].class);
        table.addColumn("id", int.class);
        table.addColumn("pid", String.class);
        table.addColumn("color", int.class);
        table.addColumn(label, String.class);

        // set up the visual operators
        // first set up all the color actions
        ColorAction nStroke = new ColorAction(NODES, VisualItem.STROKECOLOR);
        nStroke.setDefaultColor(ColorLib.gray(100));
        //nStroke.add("_hover", ColorLib.gray(50));

        ColorAction nFill = new ColorAction(NODES, VisualItem.FILLCOLOR);
        //nFill.setDefaultColor(ColorLib.gray(255));
        nFill.setDefaultColor(ColorLib.blue(255));
        nFill.add("_hover", ColorLib.blue(200));

        ColorAction nEdges = new ColorAction(EDGES, VisualItem.STROKECOLOR);
        nEdges.setDefaultColor(ColorLib.gray(100));

        ColorAction aStroke = new ColorAction(AGGR, VisualItem.STROKECOLOR);
        aStroke.setDefaultColor(ColorLib.gray(200));
        aStroke.add("_hover", ColorLib.rgb(255, 100, 100));

        ColorAction textColor = new ColorAction(NODES, VisualItem.TEXTCOLOR);
        textColor.setDefaultColor(ColorLib.gray(200));

        ColorAction labelColor = new ColorAction(label, VisualItem.TEXTCOLOR);
        labelColor.setDefaultColor(ColorLib.gray(80));
        int[] palette = new int[]{
                ColorLib.rgba(255, 200, 200, 150),
                ColorLib.rgba(200, 255, 200, 150),
                ColorLib.rgba(200, 200, 255, 150),
                ColorLib.rgba(153, 153, 255, 150),
                ColorLib.rgba(204, 255, 204, 150),
                ColorLib.rgba(0, 63, 81, 150),
                ColorLib.rgba(192, 231, 179, 150),
                ColorLib.rgba(185, 185, 219, 150)
        };
        ColorAction aFill = new DataColorAction(AGGR, "color",
                Constants.NOMINAL,
                VisualItem.FILLCOLOR,
                palette);

        // bundle the color actions
        ActionList colors = new ActionList();
        colors.add(nStroke);
        colors.add(nFill);
        colors.add(nEdges);
        colors.add(aStroke);
        colors.add(aFill);
        colors.add(labelColor);
        colors.add(textColor);

        // now create the main layout routine
        ActionList layout = new ActionList(Activity.INFINITY);
        layout.add(colors);
        layout.add(new LabelLayout(label));
        layout.add(new ForceDirectedLayout(GRAPH, true));
        layout.add(new AggregateLayout(AGGR));
        layout.add(new RepaintAction());
        m_vis.putAction("textColor", textColor);
        m_vis.putAction("layout", layout);

        // set up the display
        setSize(700, 600);
        pan(250, 250);
        setHighQuality(true);
        addControlListener(new AggregateDragControl());
        addControlListener(ttc);
        addControlListener(new ZoomControl());
        addControlListener(new PanControl());
        searchQ = new SearchQueryBinding(ntbl, "label");
        SearchQueryBinding searchEdge = new SearchQueryBinding(graph.getNodeTable(), "label");
        AndPredicate filter = new AndPredicate(searchQ.getPredicate());
        filter.add(new SearchQueryBinding(graph.getNodeTable(), "pid").getPredicate());
        filter.add(new SearchQueryBinding(graph.getEdgeTable(), "label").getPredicate());
        filter.add(new SearchQueryBinding(graph.getEdgeTable(), "pid").getPredicate());

        m_vis.addFocusGroup(Visualization.SEARCH_ITEMS, searchQ.getSearchSet());
        searchQ.getPredicate().addExpressionListener(new UpdateListener() {
            public void update(Object src) {
                m_vis.cancel("animatePaint");
                m_vis.run("colors");
                m_vis.run("animatePaint");
            }
        });

        Predicate labelP = (Predicate) ExpressionParser.parse("true");
        m_vis.addDecorators(label, NODES, labelP, LABEL_SCHEMA);
        // set things running
        m_vis.run("layout");

        greenImage = getToolkit().getImage(getClass().getResource(greendot));
        redImage = getToolkit().getImage(getClass().getResource(reddot));
        redIcon = new ImageIcon(redImage);
        greenIcon = new ImageIcon(greenImage);
        statusDot = new JLabel();
        statusDot.setIcon(redIcon);

    }

    private void drawM() {
        iViewMessage response = new iViewMessage();
        response.setSrcID(netPeerGroup.getPeerID());
        response.setName(netPeerGroup.getPeerName());
        long freeHeap = Runtime.getRuntime().freeMemory();
        long totalHeap = Runtime.getRuntime().totalMemory();
        long maxHeap = Runtime.getRuntime().maxMemory();
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        if (group.getParent() != null) {
            group = group.getParent();
        }
        long threadNb = group.activeCount();

        response.setFreeHeap(freeHeap);
        response.setTotalHeap(totalHeap);
        response.setMaxHeap(maxHeap);
        response.setThreadNb(threadNb);
        response.setUptime(100000);
        response.setMaxNoClients(1);
        response.add(new iViewMessage.Entry(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID), "edge node0"));
        response.add(new iViewMessage.Entry(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID), "edge node1"));
        response.add(new iViewMessage.Entry(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID), "edge node2"));
        response.add(new iViewMessage.Entry(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID), "edge node3"));
        response.add(new iViewMessage.Entry(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID), "edge node4"));
        response.add(new iViewMessage.Entry(IDFactory.newPeerID(PeerGroupID.defaultNetPeerGroupID), "edge node5"));
        draw(response);

    }

    public static JxtaNetmapViewer getInstance() {
        if (instance == null) {
            instance = new JxtaNetmapViewer();
        }
        return instance;
    }

    public SearchQueryBinding getSearchQuery() {
        return searchQ;
    }

    /**
     * Creates the main frame
     *
     * @return main frame
     */
    public static JFrame viewer(NetworkManager netManager) {
        instance = JxtaNetmapViewer.getInstance();

        JFrame frame = new JFrame("JXTA network visualizer");
        JSearchPanel search = instance.getSearchQuery().createSearchPanel();
        search.setLabelText("Search for node by name or PeerID");
        search.setShowResultCount(true);
        search.setBorder(BorderFactory.createEmptyBorder(5, 5, 4, 0));
        search.setFont(FontLib.getFont("Tahoma", Font.PLAIN, 11));
        final JFastLabel title = new JFastLabel("                    ");

        Box box = UILib.getBox(new Component[]{title, search}, true, 20, 3, 0);
        UILib.setColor(box, Color.WHITE, Color.BLACK);
        box.add(instance.statusDot);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(instance, BorderLayout.CENTER);
        panel.add(box, BorderLayout.SOUTH);

        frame.getContentPane().add(panel);

        instance.startJxta(netManager);
        //instance.drawM();

        return frame;
    }

    /**
     * Set label positions. Labels are assumed to be DecoratorItem instances,
     * decorating their respective nodes. The layout simply gets the bounds
     * of the decorated node and assigns the label coordinates to the center
     * of those bounds.
     */
    public static class LabelLayout extends Layout {
        public LabelLayout(String group) {
            super(group);
        }

        public void run(double frac) {
            Iterator iter = m_vis.items(m_group);
            while (iter.hasNext()) {
                DecoratorItem item = (DecoratorItem) iter.next();
                VisualItem node = item.getDecoratedItem();
                Rectangle2D bounds = node.getBounds();
                setX(item, null, bounds.getCenterX());
                setY(item, null, bounds.getCenterY());
            }
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
    public Iterator getMIARatings(long low, long high) {
        List<String> mias = new ArrayList<String>();
        for (String key : rdvStats.keySet()) {
            long rating = getMIARating(key);

            if (rating >= low && rating <= high) {
                mias.add(key);
            }
        }
        return mias.iterator();
    }

    private void colorDupes(String theId) {
    }

    int num = 1;

    private RendezvousNode getRdvnode(ID id) {
        RendezvousNode rdvNode = rdvnodes.get(id);
        if (rdvNode == null) {
            Node node = graph.addNode();
            node.setInt("id", num);

            //m_vis.setInteractive(EDGES, null, false);
            m_vis.setValue(NODES, null, VisualItem.SHAPE,
                    Constants.SHAPE_ELLIPSE);
            m_vis.setValue(EDGES, null, VisualItem.SHAPE,
                    Constants.SHAPE_ELLIPSE);
            AggregateItem aitem = (AggregateItem) table.addItem();
            aitem.setInt("id", num);
            aitem.setInt("color", random.nextInt(8));
            rdvNode = new RendezvousNode(id, node, num, table, aitem);
            num++;
            rdvnodes.put(id, rdvNode);
        }
        nodeCount++;
        return rdvNode;

    }

    private void draw(iViewMessage view) {
        Node r = null;

        PeerID id = (PeerID) view.getSrcID();
        String name = null;

        if (view.getQueryDate() != -1) {
            updateStats(id.toString(), view.getQueryDate());
        } else {
            updateStats(id.toString());
        }

        RendezvousNode rdvNode = getRdvnode(id);

        if (view.getThreadNb() != -1) {
            name = view.getName() + " up:" + (view.getUptime() / 60000) + "m th:" + view.getThreadNb() + " used:"
                    + ((view.getTotalHeap() - view.getFreeHeap()) / (1024 * 1024)) + "M heap Total/Max:"
                    + (view.getTotalHeap() / (1024 * 1024)) + "M/" + (view.getMaxHeap() / (1024 * 1024)) + "M MAX Clients :" + view.getMaxNoClients();
        } else {
            name = view.getName();
            System.out.println("Got a message from :" + name);
        }
        long lagtime = getRdvLagTime(id.toString());

        if (lagtime < 0) {
            name += " lag: N/A";
        } else {
            name += " lag:" + lagtime + "ms";
        }
        rdvNode.node.setString(label, view.getName());
        rdvNode.node.setString("tooltip", name);

        List <iViewMessage.Entry> entries = view.getEntries();

        rdvNode.upPeer = view.getUpPeer();
        rdvNode.downPeer = view.getDownPeer();

        RendezvousNode uprdvNode = getRdvnode(rdvNode.upPeer);
        RendezvousNode dnrdvNode = getRdvnode(rdvNode.downPeer);

        graph.addEdge(rdvNode.node, uprdvNode.node);
        graph.addEdge(rdvNode.node, dnrdvNode.node);

        String upPeerStr = rdvNode.upPeer.toString();
        String downPeerStr = rdvNode.downPeer.toString();

        if (!rdvNode.upPeer.equals(ID.nullID)) {
            entries.add(new iViewMessage.Entry(rdvNode.upPeer, "?"));
        }
        if (!rdvNode.downPeer.equals(ID.nullID)) {
            entries.add(new iViewMessage.Entry(rdvNode.downPeer, "?"));
        }

        for (rendezvous.protocol.iViewMessage.Entry entry : entries) {
            String ename = entry.name;
            if (name.length() > 255) {
                ename = entry.id.toString();
            }

            Node theEdge = rdvNode.edges.get(entry.id);

            if (theEdge == null) {
                Node edge = graph.addNode();
                edge.setString(label, ename);
                edge.setString("tooltip", ename);
                edge.setInt("rdvid", rdvNode.node.getInt("id"));
                graph.addEdge(rdvNode.node, edge);
                rdvNode.edges.put(entry.id, edge);
                nodeCount++;
            }
            Iterator nodes = vg.nodes();
            while (nodes.hasNext()) {
                VisualItem item = (VisualItem) nodes.next();
                if (((Node) item).canGetInt("rdvid") &&
                        ((Node) item).getInt("id") == rdvNode.node.getInt("id") ||
                        ((Node) item).getInt("rdvid") == rdvNode.node.getInt("id")) {
                    item.setSize(1.5);
                    rdvNode.aggNode.addItem(item);
                }
            }
        }

        String countLabel = "n=" + nodeCount;
        anchor.setString(label, countLabel);
        anchor.setString("tooltip", "Total node count " + nodeCount);

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
                        synchronized (lock) {
                            Iterator rdvs = getMIARatings(0, 2);

                            while (rdvs.hasNext()) {
                                String itsId = (String) rdvs.next();
                                //Node rdv = tgPanel.findNode(itsId);
                                //rdv.setBackColor(goodColor);
                            }
                            rdvs = getMIARatings(2, 5);
                            while (rdvs.hasNext()) {
                                String itsId = (String) rdvs.next();
                                //Node rdv = tgPanel.findNode(itsId);
                                //rdv.setBackColor(badColor);
                            }
                            Iterator mias = getMIARatings(6, Long.MAX_VALUE);

                            while (mias.hasNext()) {
                                String miaId = (String) mias.next();
                                // Node mia = tgPanel.findNode(miaId);
                                rdvStats.remove(miaId);

                                // Kill the edges now. killEdge() deals with the consequences.
                                // We no-longer believe that this is a valid rdv, but
                                // if some other rdv has an edge to it (like it thinks it is
                                // an up or down peer) we must keep showing that. This means
                                // that if there is at least one inbound edge from an rdv,
                                // we keep this node. Just change it to "suspected rdv" with
                                // a rounded rect shape.

                                boolean keepNode = false;
                                // Iterator discEdges = mia.getEdges();

                                // Copy the collection. No idea how this iterator implements
                                // remove. And the underlying collection has to be modified.
                                /*
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
                                */

                                // Finally we may remove this node from the display.
                                if (keepNode) {
                                    //mia.setType(Node.TYPE_ROUNDRECT);
                                    //mia.setLabel("?");
                                    // The name is now dubious.
                                } else {
                                    //tgPanel.deleteNode(mia);
                                    // tgPanel.setSelect(anchor);
                                }
                            }
                        }
                        query();
                    }
                };
        timer.purge();
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
            case RendezvousEvent.RDVDISCONNECT:
            case RendezvousEvent.CLIENTDISCONNECT:
            case RendezvousEvent.RDVFAILED:
                statusDot.setIcon(redIcon);
                break;
            default:
                break;
        }
    }

    /**
     * Starts the JXTA Platform This method be called after the platform has
     * been initialized
     */
    private void startJxta(NetworkManager netManager) {
        netPeerGroup = netManager.getNetPeerGroup();
        myid = netPeerGroup.getPeerID();
        discovery = netPeerGroup.getDiscoveryService();
        rendezvous = netPeerGroup.getRendezVousService();
        rendezvous.addListener(this);
        pipeAdv = iViewRendezvous.createPipeAdv(iViewRendezvous.pipeID);
        pipeService = netPeerGroup.getPipeService();
        try {
            inputPipe = pipeService.createInputPipe(pipeAdv, this);
        } catch (IOException io) {
            io.printStackTrace();
        }
        if (rendezvous.isConnectedToRendezVous()) {
            statusDot.setIcon(greenIcon);
            startQueries();
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
                    LOG.fine("Received a Response from :" +
                            response.getName());
                }
                draw(response);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failure handling pipe message event", e);
        }
    }

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

}
// end of class JxtaNetmapViewer

/**
 * Layout algorithm that computes a convex hull surrounding aggregate items and
 * saves it in the "_polygon" field.
 */
class AggregateLayout extends Layout {

    // convex hull pixel margin
    private int m_margin = 5;
    // buffer for computing convex hulls
    private double[] m_pts;


    /**
     * Constructor for the AggregateLayout object
     *
     * @param aggrGroup Description of the Parameter
     */
    public AggregateLayout(String aggrGroup) {
        super(aggrGroup);
    }


    public void run(double frac) {

        AggregateTable aggr = (AggregateTable) m_vis.getGroup(m_group);
        // do we have any  to process?
        if (aggr == null) {
            return;
        }
        int num = aggr.getTupleCount();
        if (num == 0) {
            return;
        }

        // update buffers
        int maxsz = 0;
        for (Iterator aggrs = aggr.tuples(); aggrs.hasNext();) {
            maxsz = Math.max(maxsz, 8 * 4 *
                    ((AggregateItem) aggrs.next()).getAggregateSize());
        }
        if (m_pts == null || maxsz > m_pts.length) {
            m_pts = new double[maxsz];
        }

        // compute and assign convex hull for each aggregate
        Iterator aggrs = m_vis.visibleItems(m_group);
        while (aggrs.hasNext()) {
            AggregateItem aitem = (AggregateItem) aggrs.next();

            int idx = 0;
            if (aitem.getAggregateSize() == 0) {
                continue;
            }
            VisualItem item = null;
            Iterator iter = aitem.items();
            while (iter.hasNext()) {
                item = (VisualItem) iter.next();
                if (item.isVisible()) {
                    addPoint(m_pts, idx, item, m_margin);
                    idx += 2 * 4;
                }
            }
            // if no aggregates are visible, do nothing
            if (idx == 0) {
                continue;
            }

            // compute convex hull
            double[] nhull = GraphicsLib.convexHull(m_pts, idx);

            // prepare viz attribute array
            float[] fhull = (float[]) aitem.get(VisualItem.POLYGON);
            if (fhull == null || fhull.length < nhull.length) {
                fhull = new float[nhull.length];
            } else if (fhull.length > nhull.length) {
                fhull[nhull.length] = Float.NaN;
            }

            // copy hull values
            for (int j = 0; j < nhull.length; j++) {
                fhull[j] = (float) nhull[j];
            }
            aitem.set(VisualItem.POLYGON, fhull);
            aitem.setValidated(false);
            // force invalidation
        }
    }


    /**
     * Adds a feature to the Point attribute of the AggregateLayout class
     *
     * @param pts    The feature to be added to the Point attribute
     * @param idx    The feature to be added to the Point attribute
     * @param item   The feature to be added to the Point attribute
     * @param growth The feature to be added to the Point attribute
     */
    private static void addPoint(double[] pts, int idx,
                                 VisualItem item, int growth) {
        Rectangle2D b = item.getBounds();
        double minX = (b.getMinX()) - growth;
        double minY = (b.getMinY()) - growth;
        double maxX = (b.getMaxX()) + growth;
        double maxY = (b.getMaxY()) + growth;
        pts[idx] = minX;
        pts[idx + 1] = minY;
        pts[idx + 2] = minX;
        pts[idx + 3] = maxY;
        pts[idx + 4] = maxX;
        pts[idx + 5] = minY;
        pts[idx + 6] = maxX;
        pts[idx + 7] = maxY;
    }

}
// end of class AggregateLayout

/**
 * Interactive drag control that is "aggregate-aware"
 */
class AggregateDragControl extends ControlAdapter {


    /**
     * Description of the Field
     */
    protected Point2D down = new Point2D.Double();
    /**
     * Description of the Field
     */
    protected boolean dragged;
    /**
     * Description of the Field
     */
    protected Point2D temp = new Point2D.Double();

    private VisualItem activeItem;


    /**
     * Creates a new drag control that issues repaint requests as an item is
     * dragged.
     */
    public AggregateDragControl() {
    }


    /**
     * @param item Description of the Parameter
     * @param e    Description of the Parameter
     * @see prefuse.controls.Control#itemDragged(prefuse.visual.VisualItem,
     *java.awt.event.MouseEvent)
     */
    public void itemDragged(VisualItem item, MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        dragged = true;
        Display d = (Display) e.getComponent();
        d.getAbsoluteCoordinate(e.getPoint(), temp);
        double dx = temp.getX() - down.getX();
        double dy = temp.getY() - down.getY();

        move(item, dx, dy);

        down.setLocation(temp);
    }


    /**
     * @param item Description of the Parameter
     * @param e    Description of the Parameter
     * @see prefuse.controls.Control#itemEntered(prefuse.visual.VisualItem,
     *java.awt.event.MouseEvent)
     */
    public void itemEntered(VisualItem item, MouseEvent e) {
        Display d = (Display) e.getSource();
        d.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        activeItem = item;
        if (!(item instanceof AggregateItem)) {
            setFixed(item, true);
        }
    }


    /**
     * @param item Description of the Parameter
     * @param e    Description of the Parameter
     * @see prefuse.controls.Control#itemExited(prefuse.visual.VisualItem,
     *java.awt.event.MouseEvent)
     */
    public void itemExited(VisualItem item, MouseEvent e) {
        if (activeItem == item) {
            activeItem = null;
            setFixed(item, false);
        }
        Display d = (Display) e.getSource();
        d.setCursor(Cursor.getDefaultCursor());
    }


    /**
     * @param item Description of the Parameter
     * @param e    Description of the Parameter
     * @see prefuse.controls.Control#itemPressed(prefuse.visual.VisualItem,
     *java.awt.event.MouseEvent)
     */
    public void itemPressed(VisualItem item, MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        dragged = false;
        Display d = (Display) e.getComponent();
        d.getAbsoluteCoordinate(e.getPoint(), down);
        if (item instanceof AggregateItem) {
            setFixed(item, true);
        }
    }


    /**
     * @param item Description of the Parameter
     * @param e    Description of the Parameter
     * @see prefuse.controls.Control#itemReleased(prefuse.visual.VisualItem,
     *java.awt.event.MouseEvent)
     */
    public void itemReleased(VisualItem item, MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        if (dragged) {
            activeItem = null;
            setFixed(item, false);
            dragged = false;
        }
    }


    /**
     * Sets the fixed attribute of the AggregateDragControl class
     *
     * @param item  The new fixed value
     * @param fixed The new fixed value
     */
    protected static void setFixed(VisualItem item, boolean fixed) {
        if (item instanceof AggregateItem) {
            Iterator items = ((AggregateItem) item).items();
            while (items.hasNext()) {
                setFixed((VisualItem) items.next(), fixed);
            }
        } else {
            item.setFixed(fixed);
        }
    }


    /**
     * Description of the Method
     *
     * @param item Description of the Parameter
     * @param dx   Description of the Parameter
     * @param dy   Description of the Parameter
     */
    protected static void move(VisualItem item, double dx, double dy) {
        if (item instanceof AggregateItem) {
            Iterator items = ((AggregateItem) item).items();
            while (items.hasNext()) {
                move((VisualItem) items.next(), dx, dy);
            }
        } else {
            double x = item.getX();
            double y = item.getY();
            item.setStartX(x);
            item.setStartY(y);
            item.setX(x + dx);
            item.setY(y + dy);
            item.setEndX(x + dx);
            item.setEndY(y + dy);
        }
    }

    /**
     * The main program for the JxtaNetmapViewer class
     *
     * @param argv The command line arguments
     */
    public static void main(String[] argv) {
        String jxta_home = System.getProperty("JXTA_HOME", ".jxta");
        File home = new File(jxta_home);

        try {
            NetworkManager manager = new NetworkManager(NetworkManager.ConfigMode.EDGE, "Jxta Netmap Viewer", home.toURI());
            manager.startNetwork();

            JFrame viewer = JxtaNetmapViewer.viewer(manager);
            viewer.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            viewer.pack();
            viewer.setVisible(true);
        } catch (Throwable failed) {
            System.err.flush();
            failed.printStackTrace(System.err);
        }
    }
}