package utils;

import org.metadatacenter.constant.ConfigConstants;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;
import play.Configuration;
import play.Play;

import static org.metadatacenter.constant.ConfigConstants.*;

public class DataServices {

  private static DataServices instance = new DataServices();
  private static UserService userService;
  private static Neo4JProxy neo4JProxy;


  public static DataServices getInstance() {
    return instance;
  }

  private DataServices() {
    Configuration config = Play.application().configuration();
    userService = new UserServiceMongoDB(
        config.getString(MONGODB_DATABASE_NAME),
        config.getString(USERS_COLLECTION_NAME));

    Neo4jConfig nc = new Neo4jConfig();
    nc.setTransactionUrl(config.getString(NEO4J_REST_TRANSACTION_URL));
    nc.setAuthString(config.getString(NEO4J_REST_AUTH_STRING));
    nc.setRootFolderPath(config.getString(NEO4J_FOLDERS_ROOT_PATH));
    nc.setRootFolderDescription(config.getString(NEO4J_FOLDERS_ROOT_DESCRIPTION));
    nc.setUsersFolderPath(config.getString(NEO4J_FOLDERS_USERS_PATH));
    nc.setUsersFolderDescription(config.getString(NEO4J_FOLDERS_USERS_DESCRIPTION));
    nc.setAdminUserUUID(config.getString(NEO4J_ADMIN_USER_UUID));

    String folderIdPrefix = config.getString(ConfigConstants.LINKED_DATA_ID_PATH_BASE) + config.getString
        (ConfigConstants.LINKED_DATA_ID_PATH_SUFFIX_FOLDERS);
    neo4JProxy = new Neo4JProxy(nc, config.getString(USER_DATA_ID_PATH_BASE), folderIdPrefix);
    neo4JProxy.init();
  }

  public UserService getUserService() {
    return userService;
  }

  public Neo4JProxy getNeo4JProxy() {
    return neo4JProxy;
  }
}