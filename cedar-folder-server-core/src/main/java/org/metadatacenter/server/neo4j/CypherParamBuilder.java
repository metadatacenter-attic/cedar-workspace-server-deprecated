package org.metadatacenter.server.neo4j;

import org.apache.commons.lang3.StringUtils;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.model.folderserver.CedarFolder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CypherParamBuilder {

  private CypherParamBuilder() {
  }

  public static Map<String, String> createFolder(String parentId, String name, String description, String createdBy) {
    String folderId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    Map<String, String> params = new HashMap<>();
    params.put("parentId", parentId);
    params.put("id", folderId);
    params.put("name", name);
    params.put("description", description);
    params.put("createdOn", nowString);
    params.put("lastUpdatedOn", nowString);
    params.put("createdBy", createdBy);
    params.put("lastUpdatedBy", createdBy);
    return params;
  }

  public static Map<String, String> getFolderLookupByDepthParameters(Neo4JProxy proxy, String path) {
    String normalizedPath = proxy.getPathUtil().normalizePath(path);
    String[] parts = StringUtils.split(normalizedPath, proxy.getPathUtil().getSeparator());
    Map<String, String> folderNames = new HashMap<>();
    folderNames.put("f0", proxy.getPathUtil().getRootPath());
    for (int i = 0; i < parts.length; i++) {
      folderNames.put("f" + (i + 1), parts[i]);
    }
    return folderNames;
  }

  public static Map<String, String> getFolderContentsLookupParameters(CedarFolder folder) {
    Map<String, String> params = new HashMap<>();
    params.put("id", folder.getId());
    return params;
  }

  public static Map<String, String> getFolderById(String folderId) {
    Map<String, String> params = new HashMap<>();
    params.put("id", folderId);
    return params;
  }

  public static Map<String, String> deleteFolderById(String folderId) {
    Map<String, String> params = new HashMap<>();
    params.put("id", folderId);
    return params;
  }

  public static Map<String, String> updateFolderById(String folderId, Map<String, String> updateFields, String
      updatedBy) {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    Map<String, String> params = new HashMap<>();
    params.put("lastUpdatedOn", nowString);
    params.put("lastUpdatedBy", updatedBy);
    params.put("id", folderId);
    params.putAll(updateFields);
    return params;
  }

  public static Map<String, String> getFolderLookupByIDParameters(Neo4JProxy proxy, String id) {
    Map<String, String> params = new HashMap<>();
    params.put("name", proxy.getPathUtil().getRootPath());
    params.put("id", id);
    return params;
  }
}
