FROM openjdk:8-slim

RUN apt-get update && apt-get install -y curl

ENV SBT_VERSION 1.0.4
ENV SBT_HOME /usr/local/sbt
ENV PATH ${SBT_HOME}/bin:${PATH}

# Install sbt
RUN curl -sL "https://piccolo.link/sbt-${SBT_VERSION}.tgz" | gunzip | tar -x -C /usr/local && \
    echo -ne "- with sbt $SBT_VERSION\n" >> /root/.built && \
    sbt info

ADD . /bfg
WORKDIR /bfg
RUN sbt bfg/assembly
