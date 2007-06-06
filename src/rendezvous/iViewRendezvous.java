/*
 *  Copyright (c) 2001-2006 Sun Microsystems, Inc.  All rights
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
 *  $Id: iViewRendezvous.java,v 1.26 2007/02/06 18:27:52 hamada Exp $
 */
package rendezvous;

import net.jxta.document.AdvertisementFactory;
import net.jxta.document.MimeMediaType;
import net.jxta.document.XMLDocument;
import net.jxta.endpoint.*;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.impl.rendezvous.RendezVousServiceInterface;
import net.jxta.impl.rendezvous.rpv.PeerView;
import net.jxta.impl.rendezvous.rpv.PeerViewElement;
import net.jxta.peer.PeerID;
import net.jxta.pipe.OutputPipe;
import net.jxta.pipe.PipeID;
import net.jxta.pipe.PipeService;
import net.jxta.protocol.PipeAdvertisement;
import rendezvous.protocol.iViewMessage;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is a very simple application, which can be used as an app to run a
 * rendezvous.
 */

public class iViewRendezvous extends Rendezvous implements EndpointListener, Runnable {
    /**
     * The Log4J debugging category.
     */
    private final static Logger LOG = Logger.getLogger(iViewRendezvous.class.getName());
    public static final String HANDLE = "iView";
    public static final String QUERY = "iViewQuery";
    public static final String QUERYDATE = "iViewQueryDate";
    public static final String QUERYPEERNAME = "PeerName";
    public static final String QUERYID = "QID";
    public static String pipeID = "urn:jxta:uuid-59616261646162614E50472050325033DCD44908E42B4EF790A4B9715E5AE29904";
    private PipeAdvertisement pipeAdv = null;
    private PipeService pipeService = null;
    private OutputPipe broadcastPipe = null;
    private boolean stopped = false;
    private static final int timeout = 10000;
    private final long startDate = System.currentTimeMillis();

    // The main thread will keep running and keep holding a reference
    // to JXTA until the quit command comes. When a quit command is
    // received, *this* application will clear all references to JXTA
    // and terminate
    public iViewRendezvous(String name, URI home) {
        super(name, home);

        if (!endpoint.addIncomingMessageListener(this, HANDLE, QUERY)) {
            LOG.severe("FATAL ERROR : Could not install propagate listener");
            System.exit(1);
        }

        pipeService = netPeerGroup.getPipeService();
        pipeAdv = createPipeAdv(pipeID);

        try {
            broadcastPipe = pipeService.createOutputPipe(pipeAdv, 1);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        Thread thread = new Thread(this, "iView reporter Thread : " + timeout);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * given a pipeid, returns a pipe advertisement
     *
     * @param pipeid Pipe ID
     * @return a pipe advertisement
     */
    public static PipeAdvertisement createPipeAdv(String pipeid) {
        PipeAdvertisement pipeAdv = null;
        try {
            URI uri = new URI(pipeid);
            PipeID pID = (PipeID) IDFactory.fromURI(uri);
            // create the pipe advertisement, to be used in creating the pipe
            pipeAdv = (PipeAdvertisement) AdvertisementFactory.newAdvertisement(PipeAdvertisement.getAdvertisementType());
            pipeAdv.setPipeID(pID);
            pipeAdv.setType(PipeService.PropagateType);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return pipeAdv;
    }

    private void respond(PeerID peerid) {

        Message msg = new Message();
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
        response.setUptime(System.currentTimeMillis() - startDate);
        response.setMaxNoClients(maxNumberOfClients);

        // This is a violation, but there is no existing alternative that does
        // not make assumptions on the impl. When getPeerView is removed,
        // we'll need getUp/DownPeer at the API.
        PeerView pv = ((RendezVousServiceInterface) rendezvous).getPeerView();
        PeerViewElement uppe = pv.getUpPeer();
        PeerViewElement downpe = pv.getDownPeer();
        if (uppe != null) {
            response.setUpPeer(uppe.getRdvAdvertisement().getPeerID());
        }

        if (downpe != null) {
            response.setDownPeer(downpe.getRdvAdvertisement().getPeerID());
        }

        for (Object o : pv.getView()) {
            PeerViewElement pve = (PeerViewElement) o;
            iViewMessage.Entry entry = new
                    iViewMessage.Entry(pve.getRdvAdvertisement().getPeerID(),
                    pve.getRdvAdvertisement().getName());
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Adding entry :" + pve.getRdvAdvertisement().getPeerID());
            }
            response.addPVE(entry);
        }

        Enumeration<ID> en = rendezvous.getConnectedPeers();

        while (en.hasMoreElements()) {
            ID id = en.nextElement();
            String name = idToName(id.toString());
            if (name != null && name.length() > 200) {
                name = id.toString();
            }
            iViewMessage.Entry entry = new iViewMessage.Entry(id, name);
            response.add(entry);
        }
        try {
            msg.addMessageElement("jxta", new TextDocumentMessageElement(HANDLE,
                    (XMLDocument) response.getDocument(MimeMediaType.XMLUTF8), null));
            //System.out.println("Sending :\n"+response.toString());
            send(peerid, msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void send(PeerID peerid, Message msg) {
        try {
            if (peerid != null) {
                // Unicast datagram
                // create a op pipe to the destination peer
                OutputPipe op = pipeService.createOutputPipe(pipeAdv,
                        Collections.singleton(peerid), 1);
                op.send(msg);
                op.close();
            } else {
                broadcastPipe.send(msg);
            }
        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void processIncomingMessage(Message message,
                                       EndpointAddress srcAddr,
                                       EndpointAddress dstAddr) {

        List list = rendezvous.getLocalWalkView();
        MessageElement element = message.getMessageElement("jxta", QUERY);

        if (element != null) {
            if (LOG.isLoggable(Level.FINE)) {
                StringBuilder msg = new StringBuilder("Recevied a query from : ");

                msg.append(message.getMessageElement("jxta", QUERYPEERNAME).toString());
                msg.append(" QID: ");
                msg.append(message.getMessageElement("jxta", QUERYID).toString());

                LOG.fine(msg.toString());
            }

            try {
                String strPID = element.toString();
                try {
                    URI uri = new URI(strPID);
                    PeerID pID = (PeerID) IDFactory.fromURI(uri);
                    respond(pID);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                int ttl = list.size() + 1;
                //Walk the message from here on.
                rendezvous.walk(message.clone(), HANDLE, QUERY, ttl);

                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Walking a message with a pv size of :" + ttl);
                    /*
                    Iterator it = list.iterator();
                    while (it.hasNext()) {
                        LOG.debug("RDV :"+ ((RdvAdvertisement)it.next()).getName());
                }
                    */
                }
            } catch (Throwable t) {
                t.printStackTrace(System.err);
            }
        }
    }

    public void run() {
        while (!stopped) {
            try {
                synchronized (this) {
                    wait(timeout);
                }
                respond(null);
            } catch (InterruptedException ie) {
                Thread.interrupted();
            }
        }
    }

    public static void main(String args[]) {
        String jxta_home = System.getProperty("JXTA_HOME", ".jxta");
        File home = new File(jxta_home);

        Thread.currentThread().setName(iViewRendezvous.class.getName() + ".main()");

        String hostname = "iViewRendezvous";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException uhe) {
            //ignored
        }

        iViewRendezvous rend = new iViewRendezvous(hostname, home.toURI());
        rend.waitForQuit();
        rend.stopped = true;
        rend.releaseJxta();
        System.exit(0);
    }
}
