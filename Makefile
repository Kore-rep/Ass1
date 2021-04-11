JAVAC = /usr/bin/javac
.SUFFIXES: .java .class

SRCDIR = src
BINDIR = bin
DOCDIR = doc

CLASSES = Hashing.class Message.class Server.class Client.class

CLASS_FILES=$(CLASSES:%.class=$(BINDIR)/%.class)

$(BINDIR)/%.class:$(SRCDIR)/%.java
	$(JAVAC) -d $(BINDIR)/ -cp $(BINDIR) $<

default: $(CLASS_FILES)

clean:
	rm $(BINDIR)/*.class

server:
	java -cp bin Server

client:
	java -cp bin Client

docs:
	javadoc -private -d $(DOCDIR) src/*.java
