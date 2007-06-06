/*
 *  Copyright (c) 2004 Sun Microsystems, Inc.  All rights
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
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of Project JXTA.  For more
 *  information on Project JXTA, please see
 *  <http://www.jxta.org/>.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation.
 *
 *  $Id: iViewMessage.java,v 1.17 2007/02/06 18:27:52 hamada Exp $
 */
package rendezvous.protocol;

import net.jxta.document.*;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A HealthAdvertisement is described as follows <p/>
 * <p/>
 * <pre>
 * &lt;?xml version="1.0"?>
 * &lt;!DOCTYPE jxta:iView>
 * &lt;jxta:System xmlns:jxta="http://jxta.org">
 *   &lt;src> src
 *   &lt;id name=node name>id&lt;/id>
 * &lt;/jxta:iView>
 * </pre>
 */
public class iViewMessage {
    private final static Logger LOG = Logger.getLogger(iViewMessage.class.getName());
    private final Set<Entry> entries = new HashSet<Entry>();
    private final Set<Entry> peerview = new HashSet<Entry>();
    private ID srcID = ID.nullID;
    private ID upPeer = ID.nullID;
    private ID downPeer = ID.nullID;
    private String name = null;
    private long maxHeap = -1;
    private long totalHeap = -1;
    private long freeHeap = -1;
    private long threadNb = -1;
    private long uptime = -1;
    private long queryDate = -1;
    private int maxNumberOfClients = -1;

    private final static String entryTag = "Entry";
    private final static String pveTag = "PVE";
    private final static String upTag = "UP";
    private final static String downTag = "DOWN";
    private final static String srcTag = "src";
    private final static String nameTag = "Name";
    private final static String vitalsTag = "vitals";
    private final static String maxHeapTag = "maxHeap";
    private final static String totalHeapTag = "totalHeap";
    private final static String freeHeapTag = "freeHeap";
    private final static String threadNbTag = "threadNb";
    private final static String uptimeTag = "uptime";
    private final static String queryDateTag = "queryDate";
    private final static String MAXCLIENTSTag = "maxnoc";

    /**
     * Default Constructor
     */
    public iViewMessage() {
    }

    /**
     * Constructor for the iViewMessage object
     *
     * @param stream message stream
     * @throws IOException if an io error occurs
     */
    public iViewMessage(InputStream stream) throws IOException {
        this(StructuredDocumentFactory.newStructuredDocument(
                MimeMediaType.XMLUTF8, stream));
    }

    /**
     * Constructor for the HealthMessage object
     *
     * @param srcID
     * @param entries
     * @param peerview the current peerview
     */
    public iViewMessage(ID srcID, Collection<Entry> entries, Collection<Entry> peerview) {
        this.srcID = srcID;
        this.entries.addAll(entries);
        this.peerview.addAll(peerview);
    }

    /**
     * Construct from a StructuredDocument
     *
     * @param root root element
     */
    public iViewMessage(Element root) {
        XMLElement doc = (XMLElement) root;

        if (!getAdvertisementType().equals(doc.getName())) {
            throw new IllegalArgumentException("Could not construct : " +
                    getClass().getName() + "from doc containing a " + doc.getName());
        }
        initialize(doc);
    }

    /**
     * sets the entries list
     *
     * @param list The new entries value
     */
    public void setEntries(Collection<Entry> list) {
        this.entries.clear();
        this.entries.addAll(list);
    }

    /**
     * Adds a peer entry
     *
     * @param entry to add
     */
    public void add(Entry entry) {
        entries.add(entry);
    }

    /**
     * removes  a peer entry
     *
     * @param entry to remove
     */
    public void remove(Entry entry) {
        entries.remove(entry);
    }

    /**
     * sets the entries list
     *
     * @param list The new entries value
     */
    public void setPeerView(Collection<Entry> list) {
        this.peerview.clear();
        this.peerview.addAll(list);
    }

    /**
     * Adds a PVE entry
     *
     * @param entry to add to the PVE list
     */
    public void addPVE(Entry entry) {
        peerview.add(entry);
    }

    /**
     * Removes a PVE entry
     *
     * @param entry to remove from the PVE list
     */
    public void removePVE(Entry entry) {
        peerview.remove(entry);
    }

    /**
     * sets the unique id
     *
     * @param id The id
     */
    public void setSrcID(ID id) {
        this.srcID = (id == null ? ID.nullID : id);
    }

    /**
     * Sets the name attribute of the iViewMessage object
     *
     * @param name The new name value
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the number of threads
     *
     * @param threadNb number of threads
     */
    public void setThreadNb(long threadNb) {
        this.threadNb = threadNb;
    }

    /**
     * Sets the maxHeap attribute of the iViewMessage object
     *
     * @param maxHeap The new maxHeap value
     */
    public void setMaxHeap(long maxHeap) {
        this.maxHeap = maxHeap;
    }

    /**
     * Sets the totalHeap attribute of the iViewMessage object
     *
     * @param totalHeap The new totalHeap value
     */
    public void setTotalHeap(long totalHeap) {
        this.totalHeap = totalHeap;
    }

    /**
     * Sets the freeHeap attribute of the iViewMessage object
     *
     * @param freeHeap The new freeHeap value
     */
    public void setFreeHeap(long freeHeap) {
        this.freeHeap = freeHeap;
    }

    /**
     * Sets the uptime attribute of the iViewMessage object
     *
     * @param uptime The new uptime value
     */
    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    /**
     * Sets the queryDate attribute of the iViewMessage object
     *
     * @param date The new queryDate value
     */
    public void setQueryDate(long date) {
        this.queryDate = date;
    }

    /**
     * Set the UpPeer value.
     *
     * @param upPeer The new UpPeer value.
     */
    public void setUpPeer(ID upPeer) {
        this.upPeer = upPeer;
    }

    /**
     * Set the DownPeer value.
     *
     * @param downPeer The new DownPeer value.
     */
    public void setDownPeer(ID downPeer) {
        this.downPeer = downPeer;
    }

    /**
     * Sets the maxNumberOfClients attribute of the iViewMessage object
     *
     * @param maxNumberOfClients The new maxNumberOfClients value
     */
    public void setMaxNoClients(int maxNumberOfClients) {
        this.maxNumberOfClients = maxNumberOfClients;
    }

    /**
     * gets the entries list
     *
     * @return List The List containing Entries
     */
    public List<Entry> getEntries() {
        return new ArrayList<Entry>(entries);
    }

    /**
     * gets the src id
     *
     * @return id The id
     */
    public ID getSrcID() {
        return srcID;
    }

    /**
     * gets the src name
     *
     * @return The src name
     */
    public String getName() {
        if (name != null) {
            return name;
        } else {
            return "anon";
        }
    }

    /**
     * Gets the threadNb attribute of the iViewMessage object
     *
     * @return The threadNb value
     */
    public long getThreadNb() {
        return threadNb;
    }

    /**
     * Gets the maxHeap attribute of the iViewMessage object
     *
     * @return The maxHeap value
     */
    public long getMaxHeap() {
        return maxHeap;
    }

    /**
     * Gets the totalHeap attribute of the iViewMessage object
     *
     * @return The totalHeap value
     */
    public long getTotalHeap() {
        return totalHeap;
    }

    /**
     * Gets the freeHeap attribute of the iViewMessage object
     *
     * @return The freeHeap value
     */
    public long getFreeHeap() {
        return freeHeap;
    }

    /**
     * Gets the uptime attribute of the iViewMessage object
     *
     * @return The uptime value
     */
    public long getUptime() {
        return uptime;
    }

    /**
     * Gets the queryDate attribute of the iViewMessage object
     *
     * @return The queryDate value
     */
    public long getQueryDate() {
        return queryDate;
    }

    /**
     * Get the UpPeer value.
     *
     * @return the UpPeer value.
     */
    public ID getUpPeer() {
        return upPeer;
    }

    /**
     * Get the DownPeer value.
     *
     * @return the DownPeer value.
     */
    public ID getDownPeer() {
        return downPeer;
    }

    public int getMaxNoClients() {
        return maxNumberOfClients;
    }

    /**
     * {@inheritDoc}
     *
     * @param asMimeType mime type encoding
     * @return The document value
     */
    public Document getDocument(MimeMediaType asMimeType) {
        StructuredDocument adv =
                StructuredDocumentFactory.newStructuredDocument(asMimeType, getAdvertisementType());
        if (adv instanceof XMLDocument) {
            ((XMLDocument) adv).addAttribute("xmlns:jxta", "http://jxta.org");
        }
        Element e;
        e = adv.createElement(srcTag, getSrcID().toString());
        adv.appendChild(e);
        e = adv.createElement(nameTag, getName());
        adv.appendChild(e);
        e = adv.createElement(queryDateTag, "" + getQueryDate());
        adv.appendChild(e);
        e = adv.createElement(upTag, getUpPeer().toString());
        adv.appendChild(e);
        e = adv.createElement(downTag, getDownPeer().toString());
        adv.appendChild(e);
        e = adv.createElement(vitalsTag);
        adv.appendChild(e);
        ((Attributable) e).addAttribute(threadNbTag, "" + threadNb);
        ((Attributable) e).addAttribute(maxHeapTag, "" + maxHeap);
        ((Attributable) e).addAttribute(totalHeapTag, "" + totalHeap);
        ((Attributable) e).addAttribute(freeHeapTag, "" + freeHeap);
        ((Attributable) e).addAttribute(uptimeTag, "" + uptime);
        ((Attributable) e).addAttribute(MAXCLIENTSTag, "" + maxNumberOfClients);

        for (Object entry1 : entries) {
            Entry entry = (Entry) entry1;
            if (entry.id == null) {
                //skip bad entries
                continue;
            }
            String name = "unknown";
            if (entry.name != null) {
                name = entry.name;
            }
            e = adv.createElement(entryTag, entry.id.toString());
            adv.appendChild(e);
            ((Attributable) e).addAttribute(nameTag, name);
        }

        for (Object aPeerview : peerview) {
            Entry entry = (Entry) aPeerview;
            if (entry.id == null && entry.name == null) {
                //skip bad entries
                continue;
            }
            e = adv.createElement(pveTag, entry.id.toString());
            adv.appendChild(e);
            if (entry.name != null) {
                ((Attributable) e).addAttribute(nameTag, entry.name);
            }
        }

        return adv;
    }


    /**
     * given an element, read in the value of a given attribute name
     *
     * @param elem     the Attributable
     * @param attrName attribute name
     * @return parses the value into a long, return a -1 on error
     */
    private long readLongAttr(Attributable elem, String attrName) {
        Attribute attr = elem.getAttribute(attrName);
        long res = -1;
        if (attr != null) {
            String asStr = attr.getValue();
            try {
                res = Long.parseLong(asStr);
            } catch (NumberFormatException nfe) {
                //ignored
            }
        }
        return res;
    }

    /**
     * given an element, read in the value of a given attribute name
     *
     * @param elem     the Attributable
     * @param attrName attribute name
     * @return parses the value into a long, return a -1 on error
     */
    private int readIntAttr(Attributable elem, String attrName) {
        Attribute attr = elem.getAttribute(attrName);
        int res = -1;
        if (attr != null) {
            String asStr = attr.getValue();
            try {
                res = Integer.parseInt(asStr);
            } catch (NumberFormatException nfe) {
                //ignored
            }
        }
        return res;
    }

    /**
     * Process an individual element from the document.
     *
     * @param doc
     */
    protected void initialize(XMLElement doc) {

        Enumeration elements = doc.getChildren();
        while (elements.hasMoreElements()) {
            XMLElement elem = (XMLElement) elements.nextElement();
            if (elem.getName().equals(srcTag)) {
                try {
                    URI id = new URI(elem.getTextValue());
                    setSrcID(IDFactory.fromURI(id));
                } catch (URISyntaxException badID) {
                    throw new IllegalArgumentException("unknown ID format in advertisement: " + elem.getTextValue());
                } catch (ClassCastException badID) {
                    throw new IllegalArgumentException("Id is not a known id type: " + elem.getTextValue());
                }
                continue;
            }
            if (elem.getName().equals(upTag)) {
                try {
                    URI id = new URI(elem.getTextValue());
                    setUpPeer(IDFactory.fromURI(id));
                } catch (URISyntaxException badID) {
                    throw new IllegalArgumentException("Unknown ID format in advertisement: " + elem.getTextValue());
                } catch (ClassCastException badID) {
                    throw new IllegalArgumentException("ID is not a known id type: " + elem.getTextValue());
                }
                continue;
            }
            if (elem.getName().equals(downTag)) {
                try {
                    URI id = new URI(elem.getTextValue());
                    setDownPeer(IDFactory.fromURI(id));
                } catch (URISyntaxException badID) {
                    throw new IllegalArgumentException("unknown ID format in advertisement: " + elem.getTextValue());
                } catch (ClassCastException badID) {
                    throw new IllegalArgumentException("Id is not a known id type: " + elem.getTextValue());
                }
                continue;
            }

            if (elem.getName().equals(nameTag)) {
                name = elem.getTextValue();
                continue;
            }

            if (elem.getName().equals(queryDateTag)) {
                try {
                    queryDate = Long.parseLong(elem.getTextValue());
                } catch (NumberFormatException nfe) {
                    //ignored
                }
                continue;
            }

            if (elem.getName().equals(vitalsTag)) {
                threadNb = readLongAttr(elem, threadNbTag);
                maxHeap = readLongAttr(elem, maxHeapTag);
                totalHeap = readLongAttr(elem, totalHeapTag);
                freeHeap = readLongAttr(elem, freeHeapTag);
                uptime = readLongAttr(elem, uptimeTag);
                maxNumberOfClients = readIntAttr(elem, MAXCLIENTSTag);
                continue;
            }

            if (elem.getName().equals(entryTag)) {
                String name = "NA";
                Attribute nameAttr = (elem).getAttribute(nameTag);
                if (nameAttr != null) {
                    name = nameAttr.getValue();
                }
                ID pid = ID.nullID;
                try {
                    URI id = new URI(elem.getTextValue());
                    pid = IDFactory.fromURI(id);
                } catch (URISyntaxException badID) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "Error parsing PeerID :" + elem.getTextValue(), badID);
                    }
                } catch (ClassCastException badID) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "Error parsing PeerID :" + elem.getTextValue(), badID);
                    }
                }
                Entry entry = new Entry(pid, name);
                add(entry);
            }

            if (elem.getName().equals(pveTag)) {
                String name = "NA";
                Attribute nameAttr = ((elem).getAttribute(nameTag));
                if (nameAttr != null) {
                    name = nameAttr.getValue();
                }
                ID pid = ID.nullID;
                try {
                    URI id = new URI(elem.getTextValue());
                    pid = IDFactory.fromURI(id);
                } catch (URISyntaxException badID) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "Error parsing PeerID :" + elem.getTextValue(), badID);
                    }
                } catch (ClassCastException badID) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.log(Level.FINE, "Error parsing PeerID :" + elem.getTextValue(), badID);
                    }
                }
                Entry entry = new Entry(pid, name);
                addPVE(entry);
            }
        }
    }

    /**
     * returns the document string representation of this object
     *
     * @return String representation of the of this message type
     */
    public String toString() {

        try {
            XMLDocument doc = (XMLDocument) getDocument(MimeMediaType.XMLUTF8);
            return doc.toString();
        } catch (Throwable e) {
            if (e instanceof Error) {
                throw (Error) e;
            } else if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new UndeclaredThrowableException(e);
            }
        }
    }

    /**
     * All messages have a type (in xml this is &#0033;doctype) which
     * identifies the message
     *
     * @return String "jxta:iView"
     */
    public static String getAdvertisementType() {
        return "jxta:iView";
    }

    /**
     * Entries class
     */
    public final static class Entry {
        /**
         * Entry ID entry id
         */
        public final ID id;
        /**
         * Entry name
         */
        public final String name;

        /**
         * Creates a Entry with id and name
         *
         * @param id   id
         * @param name node name
         */

        public Entry(ID id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * {@inheritDoc}
         */
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj instanceof Entry) {
                Entry asEntry = (Entry) obj;
                return (null == id) && (null == asEntry.id) || null != id && id.equals(asEntry.id);
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public int hashCode() {
            return id.hashCode();
        }
    }

    /**
     * main Method
     *
     * @param args command line arguments. non defined
     */
    public static void main(String args[]) {
        String peerid = "urn:jxta:uuid-59616261646162614A78746150325033B63B09AE1A97431DBB7F8113445EA3F803";
        try {
            URI uri = new URI(peerid);
            ID pID = IDFactory.fromURI(uri);
            List <Entry> list = new ArrayList<Entry>(5);
            for (int i = 0; i < 5; i++) {
                Entry entry = new Entry(pID, "Name" + i);
                list.add(entry);
            }
            iViewMessage iv = new iViewMessage(pID, list, Collections.EMPTY_LIST);
            iv.setName("test");
            System.out.print(iv.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

