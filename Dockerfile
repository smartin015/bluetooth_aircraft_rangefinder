FROM ubuntu:18.04

RUN apt-get update

RUN apt-get install -y \
    cmake git wget libncurses-dev \
    flex bison gperf python python-pip \
    python-setuptools python-serial \
    python-click python-cryptography \
    python-future python-pyparsing \
    python-pyelftools cmake ninja-build \
    ccache

RUN wget https://dl.espressif.com/dl/xtensa-esp32-elf-linux64-1.22.0-80-g6c4433a-5.2.0.tar.gz  -P /tmp
RUN mkdir -p /esp && cd /esp && tar -xzf /tmp/xtensa-esp32-elf-linux64-1.22.0-80-g6c4433a-5.2.0.tar.gz

ENV PATH "/esp/xtensa-esp32-elf/bin:$PATH"
ENV ADF_PATH /esp-adf
ENV IDF_PATH /esp-adf/esp-idf

RUN git clone --recursive https://github.com/espressif/esp-adf.git
RUN python -m pip install --user -r $IDF_PATH/requirements.txt
RUN cd /esp-adf && git submodule update --init --recursive

RUN rm -rf /var/lib/apt/lists/*
