FROM  openjdk:8-jre
MAINTAINER bytemecc


VOLUME /home/Lottery
RUN mkdir -p /home/Lottery
WORKDIR /home/Lottery
COPY ./jar/Lottery-API.jar /home/Lottery/app.jar

# ADD Lottery-API.jar /app.jar
RUN bash -c 'touch /home/Lottery/app.jar'
ENTRYPOINT ["java","-jar","/home/Lottery/app.jar"]
