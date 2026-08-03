FROM iai-artifactory.iai.kit.edu/docker-webis/proof-2/proof-standard:latest

ENV DEBIAN_FRONTEND=noninteractive
# Add base dependencies
ENV PYTHONUNBUFFERED=1
RUN apt update  \
    && apt -y install xorg \
    unzip \
    && \
    apt clean && \
    rm -rf /var/lib/apt/lists/*

# Download the MCR from MathWorks site an install with -mode silent
RUN mkdir /mcr-install && \
    mkdir /opt/mcr && \
    cd /mcr-install && \
    wget --no-check-certificate -q https://ssd.mathworks.com/supportfiles/downloads/R2023b/Release/5/deployment_files/installer/complete/glnxa64/MATLAB_Runtime_R2023b_Update_5_glnxa64.zip && \
    unzip -q MATLAB_Runtime_R2023b_Update_5_glnxa64.zip && \
    rm -f MATLAB_Runtime_R2023b_Update_5_glnxa64.zip && \
    ./install -destinationFolder /opt/mcr -agreeToLicense yes -mode silent && \
    cd / && \
    rm -rf mcr-install

# Configure environment variables for MCR
ENV LD_LIBRARY_PATH /opt/mcr/R2023b/runtime/glnxa64:/opt/mcr/R2023b/bin/glnxa64:/opt/mcr/R2023b/sys/os/glnxa64:/opt/mcr/R2023b/extern/bin/glnxa64

ENV XAPPLRESDIR /etc/X11/app-defaults

# Configure a variable to dump VersionInfo.xml and test the correct installation through GitHub Actions
ENV MCR_MASTER_PATH /opt/mcr/R2023b