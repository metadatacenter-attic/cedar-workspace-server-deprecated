package org.metadatacenter.server.neo4j;

import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.model.folderserver.CedarFSResource;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;
import java.util.Map;

public class Neo4JUserSession {
  private CedarUser cu;
  private Neo4JProxy neo4JProxy;
  private String userIdPrefix;

  public Neo4JUserSession(Neo4JProxy neo4JProxy, CedarUser cu, String userIdPrefix) {
    this.neo4JProxy = neo4JProxy;
    this.cu = cu;
    this.userIdPrefix = userIdPrefix;
  }

  public static Neo4JUserSession get(Neo4JProxy neo4JProxy, CedarUser cu, String userIdPrefix) {
    return new Neo4JUserSession(neo4JProxy, cu, userIdPrefix);
  }

  private String getUserId() {
    // let the NPE, something is really wrong if that happens
    return userIdPrefix + cu.getUserId();
  }

  // Expose methods of PathUtil
  public String sanitizeName(String name) {
    return neo4JProxy.getPathUtil().sanitizeName(name);
  }

  public String normalizePath(String path) {
    return neo4JProxy.getPathUtil().normalizePath(path);
  }

  public String getChildPath(String path, String name) {
    return neo4JProxy.getPathUtil().getChildPath(path, name);
  }

  public String getRootPath() {
    return neo4JProxy.getPathUtil().getRootPath();
  }

  // Expose methods of NEo4JProxy
  // Convert folderId from URL style into internal representation
  // Other resource ids should not be converted
  // Add user info to calls
  private String getFolderUUID(String folderId) {
    return neo4JProxy.getFolderUUID(folderId);
  }

  private String getResourceUUID(String resourceId, CedarNodeType resourceType) {
    return neo4JProxy.getResourceUUID(resourceId, resourceType);
  }

  public CedarFSFolder findFolderById(String folderURL) {
    return neo4JProxy.findFolderById(getFolderUUID(folderURL));
  }

  public CedarFSResource findResourceById(String resourceURL) {
    return neo4JProxy.findResourceById(resourceURL);
  }

  public CedarFSFolder createFolderAsChildOfId(String parentFolderURL, String name, String description) {
    return neo4JProxy.createFolderAsChildOfId(getFolderUUID(parentFolderURL), name, description, getUserId());
  }

  public CedarFSResource createResourceAsChildOfId(String parentFolderURL, CedarNodeType resourceType, String name,
                                                   String description) {
    return neo4JProxy.createResourceAsChildOfId(getFolderUUID(parentFolderURL), resourceType, name, description,
        getUserId());
  }

  public CedarFSFolder updateFolderById(String folderURL, Map<String, String> updateFields) {
    return neo4JProxy.updateFolderById(getFolderUUID(folderURL), updateFields, getUserId());
  }

  public CedarFSResource updateResourceById(String resourceURL, CedarNodeType resourceType, Map<String, String> updateFields) {
    return neo4JProxy.updateResourceById(getResourceUUID(resourceURL, resourceType), updateFields, getUserId());
  }

  public boolean deleteFolderById(String folderURL) {
    return neo4JProxy.deleteFolderById(getFolderUUID(folderURL));
  }

  public boolean deleteResourceById(String resourceURL, CedarNodeType resourceType) {
    return neo4JProxy.deleteResourceById(getResourceUUID(resourceURL, resourceType));
  }

  public CedarFSFolder findFolderByPath(String path) {
    return neo4JProxy.findFolderByPath(path);
  }

  public List<CedarFSFolder> findFolderPathByPath(String path) {
    return neo4JProxy.findFolderPathByPath(path);
  }


  public List<CedarFSFolder> findFolderPathById(String folderURL) {
    return neo4JProxy.findFolderPathById(getFolderUUID(folderURL));
  }

  public List<CedarFSNode> findFolderContents(String folderURL, List<CedarNodeType> resourceTypeList, int
      limit, int offset, List<String> sortList) {
    return neo4JProxy.findFolderContents(getFolderUUID(folderURL), resourceTypeList, limit, offset, sortList);
  }

  public void ensureGlobalObjectsExists() {
    Neo4jConfig config = neo4JProxy.getConfig();
    IPathUtil pathUtil = neo4JProxy.getPathUtil();
    CedarFSFolder rootFolder = findFolderByPath(config.getRootFolderPath());
    String rootFolderURL = null;
    if (rootFolder == null) {
      rootFolder = neo4JProxy.createRootFolder(getUserId());
    }
    if (rootFolder != null) {
      rootFolderURL = rootFolder.getId();
    }
    CedarFSFolder usersFolder = findFolderByPath(config.getUsersFolderPath());
    if (usersFolder == null) {
      usersFolder = createFolderAsChildOfId(rootFolderURL, pathUtil.extractName(config.getUsersFolderPath()), config
          .getUsersFolderDescription());
    }
    CedarFSFolder lostAndFoundFolder = findFolderByPath(config.getLostAndFoundFolderPath());
    if (lostAndFoundFolder == null) {
      lostAndFoundFolder = createFolderAsChildOfId(rootFolderURL, pathUtil.extractName(config
          .getLostAndFoundFolderPath()), config.getLostAndFoundFolderDescription());
    }
  }
}
