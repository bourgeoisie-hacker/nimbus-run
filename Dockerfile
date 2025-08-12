FROM ubuntu:22.04

# Prevent interactive prompts during install
ENV DEBIAN_FRONTEND=noninteractive

# Install dependencies and OpenJDK 21
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    curl \
    gnupg2 \
    ca-certificates \
    software-properties-common && \
    add-apt-repository ppa:openjdk-r/ppa && \
    apt-get update && \
    apt-get install -y --no-install-recommends openjdk-21-jdk tini && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install yq (v4+) from GitHub releases (Go-based version)
RUN wget https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64 -O /usr/local/bin/yq && \
    chmod +x /usr/local/bin/yq

ADD AutoScaler2/target/ /jars/
ADD scripts/startup.sh /startup.sh

# Use tini for signal handling
ENTRYPOINT ["/usr/bin/tini", "--"]

# Run your app
CMD ["/startup.sh"]
