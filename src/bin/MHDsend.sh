#!/bin/sh
# -------------------------------------------------------------------------
# MHDSEND  Launcher
# -------------------------------------------------------------------------

MAIN_CLASS=kr.irm.fhir.MHDsend

DIRNAME="`dirname "$0"`"

# Setup $MHDSEND_HOME
if [ "x$MHDSEND_HOME" = "x" ]; then
    MHDSEND_HOME=`cd "$DIRNAME"/..; pwd`
fi

# Setup the JVM
if [ "x$JAVA_HOME" != "x" ]; then
    JAVA=$JAVA_HOME/bin/java
else
    JAVA="java"
fi

# Setup the classpath
CP="$MHDSEND_HOME/etc/MHDsend/"
for s in $MHDSEND_HOME/lib/*.jar
do
	CP="$CP:$s"
done

# Execute the JVM

exec $JAVA $JAVA_OPTS -cp "$CP" $MAIN_CLASS "$@"