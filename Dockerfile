# Copyright (c) 2017, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# HOW TO BUILD THIS IMAGE
# -----------------------
# Run:
#      $ ./buildDockerImage.sh [-t <image-name>]
#
# -------------------------
FROM ghcr.io/oracle/oraclelinux:8-slim

LABEL "org.opencontainers.image.authors"="Ryan Eberhard <ryan.eberhard@oracle.com>" \
      "org.opencontainers.image.url"="https://github.com/oracle/weblogic-kubernetes-operator" \
      "org.opencontainers.image.source"="https://github.com/oracle/weblogic-kubernetes-operator" \
      "org.opencontainers.image.vendor"="Oracle Corporation" \
      "org.opencontainers.image.title"="Oracle WebLogic Server Kubernetes Operator" \
      "org.opencontainers.image.description"="Oracle WebLogic Server Kubernetes Operator" \
      "org.opencontainers.image.documentation"="https://oracle.github.io/weblogic-kubernetes-operator/"

RUN set -eux; \
    microdnf -y install gzip tar openssl jq; \
    microdnf -y update; \
    microdnf clean all

ENV LANG="en_US.UTF-8" \
    JAVA_HOME="/usr/local/java" \
    PATH="/operator:$JAVA_HOME/bin:$PATH" \
    JAVA_URL="https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_linux-x64_bin.tar.gz"

# Install Java and make the operator run with a non-root user id (1000 is the `oracle` user)
RUN set -eux; \
    curl -fL -o /jdk.tar.gz "$JAVA_URL"; \
    mkdir -p "$JAVA_HOME"; \
    tar --extract --file /jdk.tar.gz --directory "$JAVA_HOME" --strip-components 1; \
    rm /jdk.tar.gz; \
    mkdir /usr/java; \
    ln -sfT "$JAVA_HOME" /usr/java/default; \
    ln -sfT "$JAVA_HOME" /usr/java/latest; \
    rm -Rf "$JAVA_HOME/include" "$JAVA_HOME/jmods"; \
    rm -f "$JAVA_HOME/lib/src.zip"; \
    for bin in "$JAVA_HOME/bin/"*; do \
        base="$(basename "$bin")"; \
        [ ! -e "/usr/bin/$base" ]; \
        alternatives --install "/usr/bin/$base" "$base" "$bin" 20000; \
    done; \
    java -Xshare:dump; \
    useradd -d /operator -M -s /bin/bash -g root -u 1000 oracle; \
    mkdir -m 775 /operator; \
    mkdir -m 775 /deployment; \
    mkdir -m 775 /probes; \
    mkdir -m 775 /logs; \
    mkdir /operator/lib; \
    chown -R oracle:root /operator /deployment /probes /logs

USER oracle

COPY --chown=oracle:root operator/scripts/* /operator/
COPY --chown=oracle:root deployment/scripts/* /deployment/
COPY --chown=oracle:root probes/scripts/* /probes/
COPY --chown=oracle:root operator/target/weblogic-kubernetes-operator.jar /operator/weblogic-kubernetes-operator.jar
COPY --chown=oracle:root operator/target/lib/*.jar /operator/lib/

HEALTHCHECK --interval=1m --timeout=10s \
  CMD /probes/livenessProbe.sh

WORKDIR /deployment/

CMD ["/deployment/operator.sh"]
