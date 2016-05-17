package utils;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.metadatacenter.constant.ConfigConstants;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.Play;

import java.io.IOException;

import static org.metadatacenter.constant.ConfigConstants.*;

public class DataServices {

  private static DataServices instance = new DataServices();
  private static UserService userService;
  private static Neo4JProxy neo4JProxy;
  private static Logger log = LoggerFactory.getLogger(DataServices.class);


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
    nc.setLostAndFoundFolderPath(config.getString(NEO4J_FOLDERS_LOSTANDFOUND_PATH));
    nc.setLostAndFoundFolderDescription(config.getString(NEO4J_FOLDERS_LOSTANDFOUND_DESCRIPTION));

    String folderIdPrefix = config.getString(ConfigConstants.LINKED_DATA_ID_PATH_BASE) + config.getString
        (ConfigConstants.LINKED_DATA_ID_PATH_SUFFIX_FOLDERS);
    neo4JProxy = new Neo4JProxy(nc, folderIdPrefix);

    CedarUser adminUser = null;
    String userId = null;
    try {
      userId = config.getString(USER_ADMIN_USER_UUID);
      adminUser = userService.findUser(userId);
    } catch (Exception ex) {
      log.error("Error while loading admin user for id:" + userId + ":");
    }
    if (adminUser == null) {
      log.warn("Admin user not found for id:" + userId + ":");
    }

    Neo4JUserSession neo4JSession = getNeo4JSession(adminUser, false);
    neo4JSession.ensureGlobalObjectsExists();
  }

  public UserService getUserService() {
    return userService;
  }

  public Neo4JUserSession getNeo4JSession(CedarUser cu) {
    return getNeo4JSession(cu, true);
  }

  private Neo4JUserSession getNeo4JSession(CedarUser cu, boolean createHome) {
    Configuration config = Play.application().configuration();
    Neo4JUserSession neo4JSession = Neo4JUserSession.get(neo4JProxy, cu, config.getString(USER_DATA_ID_PATH_BASE));
    if (createHome) {
      neo4JSession.ensureUserHomeExists();
    }
    return neo4JSession;
  }


}