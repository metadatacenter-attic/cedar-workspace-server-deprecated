# CEDAR Folder Server

To run the server

    java \
      -Dkeycloak.config.path="$CEDAR_HOME/keycloak.json" \
      -jar $CEDAR_HOME/cedar-folder-server/cedar-folder-server-application/target/cedar-folder-server-application-*.jar \
      server \
      "$CEDAR_HOME/cedar-folder-server/cedar-folder-server-application/config.yml"

To access the application:

[http://localhost:9008/]()

To access the admin port:

[http://localhost:9108/]()