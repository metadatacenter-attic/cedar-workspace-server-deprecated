package org.metadatacenter.server.neo4j;

import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.model.folderserver.CedarFSResource;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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

  // Expose methods of Neo4JProxy
  // Convert folderId from URL style into internal representation
  // Other resource ids should not be converted
  // Add user info to calls
  private String getFolderUUID(String folderId) {
    return neo4JProxy.getFolderUUID(folderId);
  }

  public CedarFSFolder findFolderById(String folderURL) {
    return neo4JProxy.findFolderById(getFolderUUID(folderURL));
  }

  public CedarFSResource findResourceById(String resourceURL) {
    return neo4JProxy.findResourceById(resourceURL);
  }

  public CedarFSFolder createFolderAsChildOfId(String parentFolderURL, String name, String description) {
    return neo4JProxy.createFolderAsChildOfId(getFolderUUID(parentFolderURL), name, description, getUserId(), null);
  }

  public CedarFSFolder createFolderAsChildOfId(String parentFolderURL, String name, String description, Map<String,
      Object> extraProperties) {
    return neo4JProxy.createFolderAsChildOfId(getFolderUUID(parentFolderURL), name, description, getUserId(),
        extraProperties);
  }

  public CedarFSResource createResourceAsChildOfId(String parentFolderURL, String childURL, CedarNodeType
      resourceType, String name, String description) {
    return createResourceAsChildOfId(parentFolderURL, childURL, resourceType, name, description, null);
  }

  public CedarFSResource createResourceAsChildOfId(String parentFolderURL, String childURL, CedarNodeType
      resourceType, String name, String description, Map<String, Object> extraProperties) {
    return neo4JProxy.createResourceAsChildOfId(getFolderUUID(parentFolderURL), childURL, resourceType, name,
        description, getUserId(), extraProperties);
  }

  public CedarFSFolder updateFolderById(String folderURL, Map<String, String> updateFields) {
    return neo4JProxy.updateFolderById(getFolderUUID(folderURL), updateFields, getUserId());
  }

  public CedarFSResource updateResourceById(String resourceURL, CedarNodeType resourceType, Map<String,
      String> updateFields) {
    return neo4JProxy.updateResourceById(resourceURL, updateFields, getUserId());
  }

  public boolean deleteFolderById(String folderURL) {
    return neo4JProxy.deleteFolderById(getFolderUUID(folderURL));
  }

  public boolean deleteResourceById(String resourceURL, CedarNodeType resourceType) {
    return neo4JProxy.deleteResourceById(resourceURL);
  }

  public CedarFSFolder findFolderByPath(String path) {
    return neo4JProxy.findFolderByPath(path);
  }

  public CedarFSFolder findFolderByParentIdAndName(CedarFSFolder parentFolder, String name) {
    return neo4JProxy.findFolderByParentIdAndName(getFolderUUID(parentFolder.getId()), name);
  }

  public List<CedarFSFolder> findFolderPathByPath(String path) {
    return neo4JProxy.findFolderPathByPath(path);
  }


  public List<CedarFSFolder> findFolderPath(CedarFSFolder folder) {
    if (folder.isRoot()) {
      List<CedarFSFolder> pathInfo = new ArrayList<>();
      pathInfo.add(folder);
      return pathInfo;
    } else {
      return neo4JProxy.findFolderPathById(getFolderUUID(folder.getId()));
    }
  }

  public List<CedarFSNode> findFolderContents(String folderURL, List<CedarNodeType> resourceTypeList, int
      limit, int offset, List<String> sortList) {
    return neo4JProxy.findFolderContents(getFolderUUID(folderURL), resourceTypeList, limit, offset, sortList);
  }

  public long findFolderContentsCount(String folderURL) {
    return neo4JProxy.findFolderContentsCount(getFolderUUID(folderURL));
  }

  public long findFolderContentsCount(String folderURL, List<CedarNodeType> resourceTypeList) {
    return neo4JProxy.findFolderContentsFilteredCount(getFolderUUID(folderURL), resourceTypeList);
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
      Map<String, Object> extraParams = new HashMap<>();
      extraParams.put("isSystem", true);
      usersFolder = createFolderAsChildOfId(rootFolderURL, pathUtil.extractName(config.getUsersFolderPath()), config
          .getUsersFolderDescription(), extraParams);
    }
    CedarFSFolder lostAndFoundFolder = findFolderByPath(config.getLostAndFoundFolderPath());
    if (lostAndFoundFolder == null) {
      Map<String, Object> extraParams = new HashMap<>();
      extraParams.put("isSystem", true);
      lostAndFoundFolder = createFolderAsChildOfId(rootFolderURL, pathUtil.extractName(config
          .getLostAndFoundFolderPath()), config.getLostAndFoundFolderDescription(), extraParams);
    }
  }

  public void ensureUserHomeExists() {
    Neo4jConfig config = neo4JProxy.getConfig();
    IPathUtil pathUtil = neo4JProxy.getPathUtil();
    String userHomePath = config.getUsersFolderPath() + pathUtil.getSeparator() + cu.getUserId();
    CedarFSFolder currentUserHomeFolder = findFolderByPath(userHomePath);
    if (currentUserHomeFolder == null) {
      CedarFSFolder usersFolder = findFolderByPath(config.getUsersFolderPath());
      // usersFolder should not be null at this point. If it is, we let the NPE to be thrown
      Map<String, Object> extraParams = new HashMap<>();
      extraParams.put("isUserHome", true);
      extraParams.put("isSystem", true);
      currentUserHomeFolder = createFolderAsChildOfId(usersFolder.getId(), cu.getUserId(), "", extraParams);
    }
  }

  public void addPathAndParentId(CedarFSFolder folder) {
    if (folder != null) {
      List<CedarFSFolder> path = findFolderPath(folder);
      if (path != null) {
        folder.setPath(getPathString(path));
        folder.setParentPath(getParentPathString(path));
        folder.setParentFolderId(getParentId(path));
      }
    }
  }

  public void addPathAndParentId(CedarFSResource resource) {
    if (resource != null) {
      List<CedarFSNode> path = neo4JProxy.findNodePathById(resource.getId());
      if (path != null) {
        resource.setPath(getPathString(path));
        resource.setParentPath(getParentPathString(path));
        resource.setParentFolderId(getParentId(path));
      }
    }
  }

  private String getParentId(List<? extends CedarFSNode> path) {
    if (path != null) {
      if (path.size() > 1) {
        return path.get(path.size() - 2).getId();
      }
    }
    return null;
  }

  private String getParentPathString(List<? extends CedarFSNode> path) {
    List<CedarFSNode> p = new ArrayList<>();
    p.addAll(path);
    if (path.size() > 0) {
      p.remove(p.size() - 1);
    } else {
      return null;
    }
    return getPathString(p);
  }

  private String getPathString(List<? extends CedarFSNode> path) {
    StringBuilder sb = new StringBuilder();
    boolean addSeparator = false;
    for (CedarFSNode node : path) {
      if (addSeparator) {
        sb.append(neo4JProxy.getPathUtil().getSeparator());
      }
      if (node instanceof CedarFSFolder) {
        if (!((CedarFSFolder) node).isRoot()) {
          addSeparator = true;
        }
      }
      sb.append(node.getName());
    }
    return sb.length() == 0 ? null : sb.toString();
  }
}
