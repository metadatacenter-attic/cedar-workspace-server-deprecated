package org.metadatacenter.server.neo4j;

import org.apache.commons.lang3.StringUtils;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.metadatacenter.server.neo4j.Neo4JFields.*;

public class CypherParamBuilder {

  private CypherParamBuilder() {
  }

  private static String getFolderAlias(int i) {
    StringBuilder sb = new StringBuilder();
    sb.append(FOLDER_ALIAS_PREFIX);
    sb.append(i);
    return sb.toString();
  }


  public static Map<String, String> createFolder(String parentId, String name, String description, String createdBy) {
    return createNode(parentId, CedarNodeType.FOLDER, name, description, createdBy);
  }

  public static Map<String, String> createResource(String parentId, CedarNodeType resourceType, String name,
                                                   String description, String createdBy) {
    return createNode(parentId, resourceType, name, description, createdBy);
  }

  private static Map<String, String> createNode(String parentId, CedarNodeType resourceType, String name,
                                                String description, String createdBy) {
    String nodeId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    String nowTSString = String.valueOf(now.getEpochSecond());
    Map<String, String> params = new HashMap<>();
    params.put(PARENT_ID, parentId);
    params.put(ID, nodeId);
    params.put(NAME, name);
    params.put(DESCRIPTION, description);
    params.put(CREATED_BY, createdBy);
    params.put(CREATED_ON, nowString);
    params.put(CREATED_ON_TS, nowTSString);
    params.put(LAST_UPDATED_BY, createdBy);
    params.put(LAST_UPDATED_ON, nowString);
    params.put(LAST_UPDATED_ON_TS, nowTSString);
    params.put(RESOURCE_TYPE, resourceType.getValue());
    return params;
  }

  public static Map<String, String> getFolderLookupByDepthParameters(IPathUtil pathUtil, String path) {
    String normalizedPath = pathUtil.normalizePath(path);
    String[] parts = StringUtils.split(normalizedPath, pathUtil.getSeparator());
    Map<String, String> folderNames = new HashMap<>();
    folderNames.put(getFolderAlias(0), pathUtil.getRootPath());
    for (int i = 0; i < parts.length; i++) {
      folderNames.put(getFolderAlias(i + 1), parts[i]);
    }
    return folderNames;
  }

  public static Map<String, String> getFolderContentsLookupParameters(String folderId) {
    Map<String, String> params = new HashMap<>();
    params.put(ID, folderId);
    return params;
  }

  public static Map<String, String> getFolderById(String folderId) {
    Map<String, String> params = new HashMap<>();
    params.put(ID, folderId);
    return params;
  }

  public static Map<String, String> getResourceById(String resourceURL) {
    Map<String, String> params = new HashMap<>();
    params.put(ID, resourceURL);
    return params;
  }

  public static Map<String, String> deleteFolderById(String folderId) {
    Map<String, String> params = new HashMap<>();
    params.put(ID, folderId);
    return params;
  }

  public static Map<String, String> deleteResourceById(String resourceURL) {
    Map<String, String> params = new HashMap<>();
    params.put(ID, resourceURL);
    return params;
  }

  public static Map<String, String> updateFolderById(String folderId, Map<String, String> updateFields, String
      updatedBy) {
    return updateNodeById(folderId, updateFields, updatedBy);
  }

  public static Map<String, String> updateResourceById(String resourceURL, Map<String, String> updateFields, String
      updatedBy) {
    return updateNodeById(resourceURL, updateFields, updatedBy);
  }

  private static Map<String, String> updateNodeById(String nodeId, Map<String, String> updateFields, String
      updatedBy) {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    String nowTSString = String.valueOf(now.getEpochSecond());
    Map<String, String> params = new HashMap<>();
    params.put(LAST_UPDATED_BY, updatedBy);
    params.put(LAST_UPDATED_ON, nowString);
    params.put(LAST_UPDATED_ON_TS, nowTSString);
    params.put(ID, nodeId);
    params.putAll(updateFields);
    return params;
  }

  public static Map<String, String> getFolderLookupByIDParameters(IPathUtil pathUtil, String id) {
    Map<String, String> params = new HashMap<>();
    params.put(NAME, pathUtil.getRootPath());
    params.put(ID, id);
    return params;
  }
}
