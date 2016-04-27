package org.metadatacenter.server.neo4j;

import java.util.HashMap;
import java.util.Map;

import static org.metadatacenter.server.neo4j.Neo4JFields.*;

public class CypherQueryBuilder {

  public static final String RELATION_CONTAINS = "CONTAINS";
  public static final String LABEL_FOLDER = "Folder";
  public static final String LABEL_RESOURCE = "Resource";

  private CypherQueryBuilder() {
  }

  private static String buildCreateAssignment(String propertyName) {
    StringBuilder sb = new StringBuilder();
    sb.append(propertyName).append(": {").append(propertyName).append("}");
    return sb.toString();
  }

  private static String buildUpdateAssignment(String propertyName) {
    StringBuilder sb = new StringBuilder();
    sb.append(propertyName).append("= {").append(propertyName).append("}");
    return sb.toString();
  }

  public static String createRootFolder(Map<String, Object> extraParams) {
    StringBuilder sb = new StringBuilder();
    sb.append(createFolder("root", extraParams));
    sb.append("RETURN root");
    return sb.toString();
  }

  public static String createFolder(String folderAlias) {
    return createFolder(folderAlias, null);
  }

  public static String createFolder(String folderAlias, Map<String, Object> extraProperties) {
    return createNode(folderAlias, LABEL_FOLDER, extraProperties);
  }

  public static String createResource(String resourceAlias) {
    return createResource(resourceAlias, null);
  }

  public static String createResource(String resourceAlias, Map<String, Object> extraProperties) {
    return createNode(resourceAlias, LABEL_RESOURCE, extraProperties);
  }

  private static String createNode(String nodeAlias, String nodeLabel, Map<String, Object> extraProperties) {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE (");
    sb.append(nodeAlias).append(":").append(nodeLabel).append(" {");
    sb.append(buildCreateAssignment(ID)).append(",");
    sb.append(buildCreateAssignment(NAME)).append(",");
    sb.append(buildCreateAssignment(DESCRIPTION)).append(",");
    sb.append(buildCreateAssignment(CREATED_BY)).append(",");
    sb.append(buildCreateAssignment(CREATED_ON)).append(",");
    sb.append(buildCreateAssignment(CREATED_ON_TS)).append(",");
    sb.append(buildCreateAssignment(LAST_UPDATED_BY)).append(",");
    sb.append(buildCreateAssignment(LAST_UPDATED_ON)).append(",");
    sb.append(buildCreateAssignment(LAST_UPDATED_ON_TS)).append(",");
    if (extraProperties != null && !extraProperties.isEmpty()) {
      extraProperties.forEach((key, value) -> sb.append(buildCreateAssignment(key)).append(","));
    }
    sb.append(buildCreateAssignment(RESOURCE_TYPE));
    sb.append("}");
    sb.append(")");
    return sb.toString();
  }


  public static String createFolderAsChildOfId(Map<String, Object> extraProperties) {
    return createNodeAsChildOfId(LABEL_FOLDER, extraProperties);
  }

  public static String createResourceAsChildOfId(Map<String, Object> extraProperties) {
    return createNodeAsChildOfId(LABEL_RESOURCE, extraProperties);
  }

  private static String createNodeAsChildOfId(String childNodeLabel, Map<String, Object> extraProperties) {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (parent:").append(LABEL_FOLDER).append(" {id:{parentId} })");
    sb.append(CypherQueryBuilder.createNode("child", childNodeLabel, extraProperties));
    sb.append("CREATE");
    sb.append("(parent)-[:").append(RELATION_CONTAINS).append("]->(child)");
    sb.append("RETURN child");
    return sb.toString();
  }

  public static String getFolderLookupQueryByDepth(int cnt) {
    StringBuilder sb = new StringBuilder();
    if (cnt >= 1) {
      sb.append("MATCH (f0:").append(LABEL_FOLDER).append(" {name:{f0} })");
    }
    for (int i = 2; i <= cnt; i++) {
      String parentAlias = "f" + (i - 2);
      String childAlias = "f" + (i - 1);
      sb.append("MATCH (");
      sb.append(childAlias);
      sb.append(":").append(LABEL_FOLDER).append(" {name:{");
      sb.append(childAlias);
      sb.append("} })");

      sb.append("MATCH (");
      sb.append(parentAlias);
      sb.append(")");
      sb.append("-[:").append(RELATION_CONTAINS).append("]->");
      sb.append("(");
      sb.append(childAlias);
      sb.append(")");

    }
    sb.append("RETURN *");
    return sb.toString();
  }

  public static String getFolderContentsLookupQuery() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (parent:").append(LABEL_FOLDER).append(" {id:{id} })");
    sb.append("MATCH (child)");
    sb.append("MATCH (parent)");
    sb.append("-[:").append(RELATION_CONTAINS).append("]->");
    sb.append("(child)");
    sb.append("WHERE child.resourceType in {resourceTypeList}");
    sb.append("RETURN child");
    sb.append(" SKIP {offset}");
    sb.append(" LIMIT {limit}");
    return sb.toString();
  }

  public static String getFolderContentsCountQuery() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (parent:").append(LABEL_FOLDER).append(" {id:{id} })");
    sb.append("MATCH (child)");
    sb.append("MATCH (parent)");
    sb.append("-[:").append(RELATION_CONTAINS).append("]->");
    sb.append("(child)");
    sb.append("WHERE child.resourceType in {resourceTypeList}");
    sb.append("RETURN count(child)");
    return sb.toString();
  }

  public static String getFolderById() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (folder:").append(LABEL_FOLDER).append(" {id:{id} })");
    sb.append("RETURN folder");
    return sb.toString();
  }

  public static String getResourceById() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (resource:").append(LABEL_RESOURCE).append(" {id:{id} })");
    sb.append("RETURN resource");
    return sb.toString();
  }

  public static String deleteFolderById() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (folder:").append(LABEL_FOLDER).append(" {id:{id} })");
    sb.append("DETACH DELETE folder");
    return sb.toString();
  }

  public static String deleteResourceById() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (resource:").append(LABEL_RESOURCE).append(" {id:{id} })");
    sb.append("DETACH DELETE resource");
    return sb.toString();
  }

  public static String updateFolderById(Map<String, String> updateFields) {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (folder:").append(LABEL_FOLDER).append(" {id:{id} })");
    sb.append("SET folder.lastUpdatedBy= {lastUpdatedBy}");
    sb.append("SET folder.lastUpdatedOn= {lastUpdatedOn}");
    sb.append("SET folder.lastUpdatedOnTS= {lastUpdatedOnTS}");
    for (String propertyName : updateFields.keySet()) {
      sb.append("SET folder.").append(buildUpdateAssignment(propertyName));
    }
    sb.append("RETURN folder");
    return sb.toString();
  }

  public static String updateResourceById(Map<String, String> updateFields) {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (resource:").append(LABEL_RESOURCE).append(" {id:{id} })");
    sb.append("SET resource.lastUpdatedBy= {lastUpdatedBy}");
    sb.append("SET resource.lastUpdatedOn= {lastUpdatedOn}");
    sb.append("SET resource.lastUpdatedOnTS= {lastUpdatedOnTS}");
    for (String propertyName : updateFields.keySet()) {
      sb.append("SET resource.").append(buildUpdateAssignment(propertyName));
    }
    sb.append("RETURN resource");
    return sb.toString();
  }

  public static String getFolderLookupQueryById() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH");
    sb.append("(root:").append(LABEL_FOLDER).append(" {name:{name} })").append(",");
    sb.append("(current:").append(LABEL_FOLDER).append(" {id:{id} })").append(",");
    sb.append("path=shortestPath((root)-[:").append(RELATION_CONTAINS).append("*]->(current))");
    sb.append("RETURN path");
    return sb.toString();
  }
}
