FROM quay.io/wildfly/wildfly:31.0.1.Final-jdk17

ENV WILDFLY_HOME=/opt/jboss/wildfly
ENV BITRIX_WEBHOOK_BASE_URL=https://b24-a0p4gq.bitrix24.ru/rest/1/ea3xpoamp12fr371/
ENV BITRIX_CONNECT_TIMEOUT_MS=2000
ENV BITRIX_READ_TIMEOUT_MS=5000

COPY bitrix24-ra/build/distributions/bitrix24-ra.rar ${WILDFLY_HOME}/standalone/deployments/bitrix24-ra.rar
COPY ticket-service/build/libs/ticket-service-0.0.1-SNAPSHOT.war ${WILDFLY_HOME}/standalone/deployments/ticket-service.war

RUN ${WILDFLY_HOME}/bin/add-user.sh -a -u appuser -p apppass -g guest --silent

EXPOSE 8080 9990
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0"]
