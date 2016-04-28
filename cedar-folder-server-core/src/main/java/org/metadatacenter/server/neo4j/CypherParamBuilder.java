package org.metadatacenter.server.neo4j;

import org.apache.commons.lang3.StringUtils;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;

import java.time.Instant;
import java.util.*;

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


  public static Map<String, Object> createFolder(String parentId, String name, String description, String createdBy) {
    return createFolder(parentId, name, description, createdBy, null);
  }

  public static Map<String, Object> createFolder(String parentId, String name, String description, String createdBy,
                                                 Map<String, Object> extraProperties) {
    String nodeId = UUID.randomUUID().toString();
    return createNode(parentId, nodeId, CedarNodeType.FOLDER, name, description, createdBy, extraProperties);
  }

  public static Map<String, Object> createResource(String parentId, String childURL, CedarNodeType resourceType,
                                                   String name, String description, String createdBy) {
    return createResource(parentId, childURL, resourceType, name, description, createdBy, null);
  }

  public static Map<String, Object> createResource(String parentId, String childURL, CedarNodeType resourceType,
                                                   String name, String description, String createdBy, Map<String,
      Object> extraProperties) {
    return createNode(parentId, childURL, resourceType, name, description, createdBy, extraProperties);
  }

  private static Map<String, Object> createNode(String parentId, String childId, CedarNodeType resourceType, String
      name, String description, String createdBy, Map<String, Object> extraProperties) {

    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    String nowTSString = String.valueOf(now.getEpochSecond());
    Map<String, Object> params = new HashMap<>();
    params.put(PARENT_ID, parentId);
    params.put(ID, childId);
    params.put(NAME, name);
    params.put(DESCRIPTION, description);
    params.put(CREATED_BY, createdBy);
    params.put(CREATED_ON, nowString);
    params.put(CREATED_ON_TS, nowTSString);
    params.put(LAST_UPDATED_BY, createdBy);
    params.put(LAST_UPDATED_ON, nowString);
    params.put(LAST_UPDATED_ON_TS, nowTSString);
    params.put(RESOURCE_TYPE, resourceType.getValue());
    if (extraProperties != null && !extraProperties.isEmpty()) {
      extraProperties.forEach((key, value) -> params.put(key, value));
    }
    return params;
  }

  public static Map<String, Object> getFolderLookupByDepthParameters(IPathUtil pathUtil, String path) {
    String normalizedPath = pathUtil.normalizePath(path);
    String[] parts = StringUtils.split(normalizedPath, pathUtil.getSeparator());
    Map<String, Object> folderNames = new HashMap<>();
    folderNames.put(getFolderAlias(0), pathUtil.getRootPath());
    for (int i = 0; i < parts.length; i++) {
      folderNames.put(getFolderAlias(i + 1), parts[i]);
    }
    return folderNames;
  }

  public static Map<String, Object> getFolderContentsLookupParameters(String folderId, Collection<CedarNodeType>
      resourceTypes, int limit, int offset) {
    Map<String, Object> params = new HashMap<>();
    params.put(ID, folderId);
    List<String> rtl = new ArrayList<>();
    resourceTypes.forEach(cnt -> rtl.add(cnt.getValue()));
    params.put("resourceTypeList", rtl);
    params.put("limit", limit);
    params.put("offset", offset);
    return params;
  }

  public static Map<String, Object> getFolderContentsCountParameters(String folderId, Collection<CedarNodeType>
      resourceTypes) {
    Map<String, Object> params = new HashMap<>();
    params.put(ID, folderId);
    List<String> rtl = new ArrayList<>();
    resourceTypes.forEach(cnt -> rtl.add(cnt.getValue()));
    params.put("resourceTypeList", rtl);
    return params;
  }

  public static Map<String, Object> getFolderById(String folderId) {
    Map<String, Object> params = new HashMap<>();
    params.put(ID, folderId);
    return params;
  }

  public static Map<String, Object> getResourceById(String resourceURL) {
    Map<String, Object> params = new HashMap<>();
    params.put(ID, resourceURL);
    return params;
  }

  public static Map<String, Object> deleteFolderById(String folderId) {
    Map<String, Object> params = new HashMap<>();
    params.put(ID, folderId);
    return params;
  }

  public static Map<String, Object> deleteResourceById(String resourceURL) {
    Map<String, Object> params = new HashMap<>();
    params.put(ID, resourceURL);
    return params;
  }

  public static Map<String, Object> updateFolderById(String folderId, Map<String, String> updateFields, String
      updatedBy) {
    return updateNodeById(folderId, updateFields, updatedBy);
  }

  public static Map<String, Object> updateResourceById(String resourceURL, Map<String, String> updateFields, String
      updatedBy) {
    return updateNodeById(resourceURL, updateFields, updatedBy);
  }

  private static Map<String, Object> updateNodeById(String nodeId, Map<String, String> updateFields, String
      updatedBy) {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    String nowTSString = String.valueOf(now.getEpochSecond());
    Map<String, Object> params = new HashMap<>();
    params.put(LAST_UPDATED_BY, updatedBy);
    params.put(LAST_UPDATED_ON, nowString);
    params.put(LAST_UPDATED_ON_TS, nowTSString);
    params.put(ID, nodeId);
    params.putAll(updateFields);
    return params;
  }

  public static Map<String, Object> getFolderLookupByIDParameters(IPathUtil pathUtil, String id) {
    Map<String, Object> params = new HashMap<>();
    params.put(NAME, pathUtil.getRootPath());
    params.put(ID, id);
    return params;
  }

  public static Map<String, Object> getFolderByParentIdAndName(String parentId, String name) {
    Map<String, Object> params = new HashMap<>();
    params.put(ID, parentId);
    params.put(NAME, name);
    return params;
  }
}
