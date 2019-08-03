FROM ubuntu:18.04

RUN apt-get update --fix-missing

RUN apt-get install -y \
    openjdk-8-jre \
    cmake git wget libncurses-dev \
    flex bison gperf python python-pip \
    python-setuptools python-serial \
    python-click python-cryptography \
    python-future python-pyparsing \
    python-pyelftools cmake ninja-build \
    ccache \
    npm \
    nodejs \
    tmux \
    bash \
    libfontconfig \
    tar \
    curl \
    lib32stdc++6 \
    lib32z1 \
    gradle

RUN wget https://dl.espressif.com/dl/xtensa-esp32-elf-linux64-1.22.0-80-g6c4433a-5.2.0.tar.gz  -P /tmp
RUN mkdir -p /esp && cd /esp && tar -xzf /tmp/xtensa-esp32-elf-linux64-1.22.0-80-g6c4433a-5.2.0.tar.gz

ENV PATH "/esp/xtensa-esp32-elf/bin:$PATH"
ENV ADF_PATH /esp-adf
ENV IDF_PATH /esp-adf/esp-idf

RUN git clone --recursive https://github.com/espressif/esp-adf.git
RUN git clone --recursive https://github.com/espressif/esp-iot-solution.git
RUN python -m pip install --user -r $IDF_PATH/requirements.txt
RUN cd /esp-adf && git submodule update --init --recursive

RUN npm install -g cordova webpack-cli
RUN cordova telemetry off

RUN curl http://dl.google.com/android/android-sdk_r24.2-linux.tgz | tar xz -C /usr/local/

# Set JAVA_HOME environment variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Set ANDROID_HOME environment variable
ENV ANDROID_HOME /usr/local/android-sdk-linux

# Add ANDROID_HOME to PATH
RUN export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Manually inject the license for SDK 25 (since it's apparently impossible to do otherwise >.<)
# https://stackoverflow.com/questions/40383323/cant-accept-license-agreement-android-sdk-platform-24/40701596
RUN mkdir "$ANDROID_HOME/licenses"

# Alternate license: 8933bad161af4178b1185d1a37fbf41ea5269c55
RUN echo -e "d56f5187479451eabf01fb78af6dfcb131a6481e" > "$ANDROID_HOME/licenses/android-sdk-license"
RUN ( sleep 5 && while [ 1 ]; do sleep 1; echo y; done ) | /usr/local/android-sdk-linux/tools/android update sdk --no-ui -a --filter 1,2,3,4

RUN rm -rf /var/lib/apt/lists/*
