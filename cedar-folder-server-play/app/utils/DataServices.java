package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.constant.ConfigConstants;
import org.metadatacenter.model.folderserver.CedarFSFolder;
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

import static org.metadatacenter.constant.ConfigConstants.*;

public class DataServices {

  private static DataServices instance = new DataServices();
  private static UserService userService;
  private static Neo4JProxy neo4JProxy;
  private static ObjectMapper MAPPER = new ObjectMapper();


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
      play.Logger.error("Error while loading admin user for id:" + userId + ":");
    }
    if (adminUser == null) {
      play.Logger.error("Admin user not found for id:" + userId + ":");
      play.Logger.error("Please log into the web application with the cedar-admin user, and then start the FolderServer again!");
      play.Logger.error("Unable to ensure the existence of global objects, exiting!");
      System.exit(-1);
    } else {
      Neo4JUserSession neo4JSession = getNeo4JSession(adminUser, false);
      neo4JSession.ensureGlobalObjectsExists();
    }
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
      System.out.println("cedar-folder-server -> DataServices -> getNeo4JSession -> createHome");
      CedarFSFolder createdFolder = neo4JSession.ensureUserHomeExists();
      System.out.println("cedar-folder-server -> DataServices -> getNeo4JSession -> createdFolder");
      System.out.println(createdFolder);
      // the home folder was just created. Store the ID on the user
      if (createdFolder != null) {
        ObjectNode homeModification = MAPPER.createObjectNode();
        homeModification.put("homeFolderId", createdFolder.getId());
        System.out.println("homeModification: " + homeModification);
        try {
          userService.updateUser(cu.getUserId(), homeModification);
          System.out.println("User updated");
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    return neo4JSession;
  }


}