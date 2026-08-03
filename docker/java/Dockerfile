FROM iai-artifactory.iai.kit.edu/docker-webis/proof-2/proof-worker:4.2.1
#FROM proof-worker:local
ENV DEBIAN_FRONTEND=noninteractive
# Add base dependencies
ENV PYTHONUNBUFFERED=1
RUN apt update && apt -y install curl \
    openssl \
    libssl-dev \
    wget \
    make \
    build-essential \
    checkinstall \
    libncursesw5-dev \
    libsqlite3-dev \
    tk-dev \
    libgdbm-dev \
    libc6-dev \
    libbz2-dev \
    #openjdk-21-jdk \
    && apt clean && \
    rm -rf /var/lib/apt/lists/*
