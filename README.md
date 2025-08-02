mvn deploy:upload-changes \
  -DsvnRevisions=100:105 \
  -DremoteHost=a.server.com \
  -DremoteUser=username \
  -DprivateKeyPath=~/.ssh/id_rsa \
  -DremotePath=/opt/tomcat/webapps/myapp/
