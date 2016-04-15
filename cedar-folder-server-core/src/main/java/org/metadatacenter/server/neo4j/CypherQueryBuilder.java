package org.metadatacenter.server.neo4j;

public class CypherQueryBuilder {

  public static final String RELATION_HAS_SUBFOLDER = "HAS_SUBFOLDER";

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
    sb.append("pav_createdOn: {createdOn}").append(",");
    sb.append("pav_lastUpdatedOn: {lastUpdatedOn}").append(",");
    sb.append("pav_createdBy: {createdBy}").append(",");
    sb.append("cedar_lastUpdatedBy: {lastUpdatedBy}");
    sb.append("}");
    sb.append(")");
    return sb.toString();
  }

  public static String createFolderAsChildOfId() {
    StringBuilder sb = new StringBuilder();
    sb.append("MATCH (parent:Folder {id:{parentId} })");
    sb.append(CypherQueryBuilder.createFolder("child"));
    sb.append("CREATE");
    sb.append("(parent)-[:").append(RELATION_HAS_SUBFOLDER).append("]->(child)");
    sb.append("RETURN child");
    return sb.toString();
  }

  public static String getFolderLookupQuery(int cnt) {
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
      sb.append("-[:").append(RELATION_HAS_SUBFOLDER).append("]->");
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
    sb.append("-[:").append(RELATION_HAS_SUBFOLDER).append("]->");
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
}
