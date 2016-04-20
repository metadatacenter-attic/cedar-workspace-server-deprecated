package org.metadatacenter.server.neo4j;

import java.util.Map;

public class CypherQueryBuilder {

  public static final String RELATION_CONTAINS = "CONTAINS";

  private CypherQueryBuilder() {
  }

  public static String createRootFolder() {
    StringBuilder sb = new StringBuilder();
    sb.append(createFolder("root"));
    sb.append("RETURN root");
    return sb.toString();
  }

  public static String createFolder(String folderAlias) {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE (");
    sb.append(folderAlias).append(":Folder {");
    sb.append("id: {id}").append(",");
    sb.append("name: {name}").append(",");
    sb.append("description: {description}").append(",");
    sb.append("createdOn: {createdOn}").append(",");
    sb.append("lastUpdatedOn: {lastUpdatedOn}").append(",");
    sb.append("createdBy: {createdBy}").append(",");
    sb.append("lastUpdatedBy: {lastUpdatedBy}");
    sb.append("}");
    sb.append(")");
    return sb.toString();
  }

  public static String createFolderAsChildOfId() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (parent:Folder {id:{parentId} })");
    sb.append(CypherQueryBuilder.createFolder("child"));
    sb.append("CREATE");
    sb.append("(parent)-[:").append(RELATION_CONTAINS).append("]->(child)");
    sb.append("RETURN child");
    return sb.toString();
  }

  public static String getFolderLookupQueryByDepth(int cnt) {
    StringBuilder sb = new StringBuilder();
    if (cnt >= 1) {
      sb.append("MATCH (f0:Folder {name:{f0} })");
    }
    for (int i = 2; i <= cnt; i++) {
      String parentAlias = "f" + (i - 2);
      String childAlias = "f" + (i - 1);
      sb.append("MATCH (");
      sb.append(childAlias);
      sb.append(":Folder {name:{");
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
    sb.append("MATCH (parent:Folder {id:{id} })");
    sb.append("MATCH (child)");
    sb.append("MATCH (parent)");
    sb.append("-[:").append(RELATION_CONTAINS).append("]->");
    sb.append("(child)");
    sb.append("RETURN child");
    return sb.toString();
  }

  public static String getFolderById() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (folder:Folder {id:{id} })");
    sb.append("RETURN folder");
    return sb.toString();
  }

  public static String deleteFolderById() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (folder:Folder {id:{id} })");
    sb.append("DETACH DELETE folder");
    return sb.toString();
  }

  public static String updateFolderById(Map<String, String> updateFields) {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (folder:Folder {id:{id} })");
    sb.append("SET folder.lastUpdatedOn= {lastUpdatedOn}");
    sb.append("SET folder.lastUpdatedBy= {lastUpdatedBy}");
    for (String propertyName : updateFields.keySet()) {
      sb.append("SET folder.").append(propertyName).append("={").append(propertyName).append("}");
    }
    sb.append("RETURN folder");
    return sb.toString();
  }

  public static String getFolderLookupQueryById() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH");
    sb.append("(root:Folder {name:{name} })").append(",");
    sb.append("(current:Folder {id:{id} })").append(",");
    sb.append("path=shortestPath((root)-[:").append(RELATION_CONTAINS).append("*]->(current))");
    sb.append("RETURN path");
    return sb.toString();
  }
}
