#
# $Id: Makefile,v 1.19 2007/02/06 18:27:50 hamada Exp $
#


# if need to use a particular JDK set JAVA_HOME in your env.
# if you reference additional libraries they need to be set in the
# CLASSPATH
# 
ifneq ($(JAVA_HOME),)
 JAVAHOMEBIN      = $(JAVA_HOME)/bin/
else
 JAVAHOMEBIN      =
endif

JAVA          = $(JAVAHOMEBIN)java
JAVAC         = $(JAVAHOMEBIN)javac
JAR           = $(JAVAHOMEBIN)jar
CP	      = cp -f
TOP           = $(shell pwd)
SRCS          = $(shell find $(TOP)/src  -name '*.java' -print | grep -v -w 'CVS')
CLASSDIR      = $(TOP)/classes
DIST          = $(TOP)/dist
LIBDIR        = $(PKGDIR)/lib

ifeq ($(PLATFORM),)
  PLATFORM = $(TOP)/../jxta-jxse
endif
ifeq ($(JXTASHELL),)
  JXTASHELL = $(TOP)/../jxse-shell/dist/jxtashell.jar
endif

PLATFORMDIST = $(PLATFORM)/dist
PLATFORMDISTJARS = $(shell find $(PLATFORMDIST) -type f -name '*.jar' -exec printf ':%s' {} \;)
PLATFORMLIB = $(PLATFORM)/lib
PLATFORMLIBJARS = $(shell find $(PLATFORMLIB) -type f -name '*.jar' -exec printf ':%s' {} \;)

ifeq ($(GRAPHJARS),)
  GRAPHJARS=$(shell find $(TOP)/lib -type f -name '*.jar' -exec printf ':%s' {} \;)
endif

ifneq ($(CLASSPATH),)
 JXTACLASSPATH      = $(CLASSPATH):$(CLASSDIR)$(GRAPHJARS)$(PLATFORMDISTJARS)$(PLATFORMLIBJARS)$(JXTAEXTRALIB):$(JXTASHELL)
else
 JXTACLASSPATH      = $(CLASSDIR):$(GRAPHJARS)$(PLATFORMDISTJARS)$(PLATFORMLIBJARS):$(JXTAEXTRALIB):$(JXTASHELL)
endif

ifneq ($(JXTA_HOME),)
 JHOME      = $(JXTA_HOME)
else
 JHOME      = edge
endif

ifeq (true,$(OPTIMIZE))
 JAVACOPT=-O -g:none -source 1.5 -target 1.5
else
 JAVACOPT=-g -deprecation -source 1.5 -target 1.5
endif


PHONIES = all compile jar run monitor ui runp run_test run_server clean clobber help

.PHONY: $(PHONIES)

all: compile

compile: clean
	@if [ '!' -d $(CLASSDIR) ]; then mkdir $(CLASSDIR); fi;
	@$(JAVAC) $(JAVACOPT) -d $(CLASSDIR) -classpath $(JXTACLASSPATH) $(SRCS)
	@$(CP) -rf src/net/jxta/netmap/resources $(CLASSDIR)
	@if [ '!' -d $(DIST) ]; then mkdir $(DIST); fi;
	@echo Creating $(DIST)/netmap.jar
	@cd $(CLASSDIR); $(JAR) -cmf $(TOP)/manifest.mf $(DIST)/netmap.jar  * ;

jar: compile
	@echo Creating $(DIST)/NetMapRendezvous.jar
	@if [ '!' -d $(DIST)/tmp ]; then mkdir $(DIST)/tmp; fi;
	@cd $(DIST)/tmp/; jar -xf ../netmap.jar; jar xf $(TOP)/lib/prefuse.jar; jar xf $(PLATFORM)/lib/bcprov-jdk14.jar; jar xf $(PLATFORMDIST)/jxta.jar; rm -rf META-INF;
	@cd $(DIST)/tmp/;$(JAR) -cmf $(TOP)/viewer.mf $(DIST)/NetMapView.jar  *; cd ..; rm -rf tmp;
run: 
	@$(JAVA) -DJXTA_HOME=$(JHOME) -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) JxtaNetMap
new:
	@$(JAVA) -DJXTA_HOME=$(JHOME) -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) net.jxta.netmap.JxtaNetmapViewer
ma:
	@$(JAVA) -DJXTA_HOME=matrix -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) JxtaNetMap

monitor:
	@$(JAVA) -DJXTA_HOME=$(JHOME) -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) rendezvous.NetworkMonitor

ui:
	@$(JAVA) -DJXTA_HOME=$(JHOME) -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) net.jxta.netmap.NetMapUI

runp:
	@$(JAVA) -DJXTA_HOME=$(JHOME) -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) netmap.NetMapClient

run_server:
	@nohup $(JAVA) -Xss128k -server -Xms64m -Xmx512m -DJXTA_HOME=rendezvous -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) rendezvous.iViewRendezvous &

run_test:
	@if [ '!' -d $(DIST)/tmp ]; then mkdir r1; mkdir r2;mkdir r3;mkdir r3;mkdir r4; touch r1/reconf r2/rouchf r3/reconf r4/reconf; fi;

t1:
	@if [ '!' -d r1 ]; then mkdir r1; touch r1/reconf; fi;
	$(JAVA) -DJXTA_HOME=r1 -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) rendezvous.iViewRendezvous

t2:
	@if [ '!' -d r2 ]; then mkdir r2; touch r2/reconf; fi;
	$(JAVA) -DJXTA_HOME=r2 -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) rendezvous.iViewRendezvous

t3:
	@if [ '!' -d r3 ]; then mkdir r3; touch r3/reconf; fi;
	$(JAVA) -DJXTA_HOME=r3 -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) rendezvous.iViewRendezvous

t4:
	@if [ '!' -d r4 ]; then mkdir r4; touch r4/reconf; fi;
	$(JAVA) -DJXTA_HOME=r4 -Dnet.jxta.tls.password="password" -classpath $(JXTACLASSPATH) rendezvous.iViewRendezvous

clean:
	@rm -rf $(CLASSDIR)
	@rm -rf $(DIST)

clobber: clean

help:
	@echo -n "# Usage : gnumake "
	@for eachtarget in $(PHONIES) ; do echo -n " [$$eachtarget]" ; done
	@echo ""		
