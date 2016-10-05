package utils;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;

public class DataServices {

  private static DataServices instance = new DataServices();
  private static UserService userService;
  private static Neo4JProxy neo4JProxy;
  private static CedarConfig cedarConfig;


  public static DataServices getInstance() {
    return instance;
  }

  private DataServices() {
    cedarConfig = CedarConfig.getInstance();
    userService = new UserServiceMongoDB(cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoCollectionName(CedarNodeType.USER));

    neo4JProxy = new Neo4JProxy(Neo4jConfig.fromCedarConfig(cedarConfig),
        cedarConfig.getLinkedDataConfig().getBase(),
        cedarConfig.getLinkedDataConfig().getUsersBase());
  }

  public UserService getUserService() {
    return userService;
  }

  public Neo4JUserSession getNeo4JSession(CedarUser currentUser) {
    return Neo4JUserSession.get(neo4JProxy, userService, currentUser, true);
  }
}