package org.metadatacenter.server.neo4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.constant.HttpConnectionConstants;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.model.folderserver.CedarFSResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class Neo4JProxy {


  private Neo4jConfig config;
  private String folderIdPrefix;
  private IPathUtil pathUtil;
  private static ObjectMapper MAPPER = new ObjectMapper();

  private static Logger log = LoggerFactory.getLogger(Neo4JProxy.class);


  public Neo4JProxy(Neo4jConfig config, String folderIdPrefix) {
    this.config = config;
    this.folderIdPrefix = folderIdPrefix;
    this.pathUtil = new Neo4JPathUtil(config);
  }

  IPathUtil getPathUtil() {
    return pathUtil;
  }

  Neo4jConfig getConfig() {
    return config;
  }

  private JsonNode executeCypherQueryAndCommit(CypherQuery query) {
    return executeCypherQueriesAndCommit(Arrays.asList(query));
  }

  private JsonNode executeCypherQueriesAndCommit(List<CypherQuery> queries) {
    System.out.println("Execute cypher queries --------------------------:");
    List<Map<String, Object>> statements = new ArrayList<>();
    for (CypherQuery q : queries) {
      if (q instanceof CypherQueryWithParameters) {
        CypherQueryWithParameters qp = (CypherQueryWithParameters) q;
        System.out.println("Query with parameters:");
        System.out.println("  q: " + qp.getQuery());
        System.out.println("  p: " + qp.getParameters());
        Map<String, Object> statement = new HashMap<>();
        statement.put("statement", qp.getQuery());
        statement.put("parameters", qp.getParameters());
        statements.add(statement);
      } else if (q instanceof CypherQueryLiteral) {
        System.out.println("Query literal:");
        System.out.println("  q: " + q.getQuery());
        CypherQueryLiteral qp = (CypherQueryLiteral) q;
        Map<String, Object> statement = new HashMap<>();
        statement.put("statement", qp.getQuery());
        statements.add(statement);
      }
    }

    Map<String, Object> body = new HashMap<>();
    body.put("statements", statements);

    String requestBody = null;
    try {
      requestBody = MAPPER.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      log.error("Error serializing cypher queries", e);
    }

    try {
      HttpResponse response = Request.Post(config.getTransactionUrl())
          .addHeader(HttpConstants.HTTP_HEADER_AUTHORIZATION, config.getAuthString())
          .addHeader(HttpConstants.HTTP_HEADER_ACCEPT, HttpConstants.CONTENT_TYPE_APPLICATION_JSON)
          .addHeader("X-stream", "true")
          .bodyString(requestBody, ContentType.APPLICATION_JSON)
          .connectTimeout(HttpConnectionConstants.CONNECTION_TIMEOUT)
          .socketTimeout(HttpConnectionConstants.SOCKET_TIMEOUT)
          .execute()
          .returnResponse();

      int statusCode = response.getStatusLine().getStatusCode();
      String responseAsString = EntityUtils.toString(response.getEntity());
      if (statusCode == HttpConstants.OK) {
        return MAPPER.readTree(responseAsString);
      } else {
        return null;
      }

    } catch (IOException ex) {
      log.error("Error while reading user details from Keycloak", ex);
      ex.printStackTrace();
    }
    return null;
  }

  List<CedarFSFolder> findFolderPathByPath(String path) {
    List<CedarFSFolder> pathList = new ArrayList<>();
    int cnt = getPathUtil().getPathDepth(path);
    String cypher = CypherQueryBuilder.getFolderLookupQueryByDepth(cnt);
    Map<String, Object> params = CypherParamBuilder.getFolderLookupByDepthParameters(pathUtil, path);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode pathListJsonNode = jsonNode.at("/results/0/data/0/row");
    if (pathListJsonNode != null && !pathListJsonNode.isMissingNode()) {
      pathListJsonNode.forEach(f -> {
        CedarFSFolder cf = buildFolder(f);
        if (cf != null) {
          pathList.add(cf);
        }
      });
    }
    return pathList;
  }

  List<CedarFSFolder> findFolderPathById(String id) {
    List<CedarFSFolder> pathList = new ArrayList<>();
    String cypher = CypherQueryBuilder.getFolderLookupQueryById();
    Map<String, Object> params = CypherParamBuilder.getFolderLookupByIDParameters(pathUtil, id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode pathListJsonNode = jsonNode.at("/results/0/data/0/row/0");
    if (pathListJsonNode != null && !pathListJsonNode.isMissingNode()) {
      pathListJsonNode.forEach(f -> {
        // relationships are also included, filter them out
        Map pathElement = buildMap(f);
        if (pathElement != null && !pathElement.isEmpty()) {
          CedarFSFolder cf = buildFolder(f);
          if (cf != null) {
            pathList.add(cf);
          }
        }
      });
    }
    return pathList;
  }

  CedarFSFolder findFolderByPath(String path) {
    List<CedarFSFolder> folderPath = findFolderPathByPath(path);
    if (folderPath != null && folderPath.size() > 0) {
      CedarFSFolder folder = folderPath.get(folderPath.size() - 1);
      return folder;
    }
    return null;
  }

  CedarFSFolder findFolderById(String folderUUID) {
    String cypher = CypherQueryBuilder.getFolderById();
    Map<String, Object> params = CypherParamBuilder.getFolderById(folderUUID);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode folderNode = jsonNode.at("/results/0/data/0/row/0");
    return buildFolder(folderNode);
  }

  CedarFSResource findResourceById(String resourceURL) {
    String cypher = CypherQueryBuilder.getResourceById();
    Map<String, Object> params = CypherParamBuilder.getResourceById(resourceURL);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode resourceNode = jsonNode.at("/results/0/data/0/row/0");
    return buildResource(resourceNode);
  }

  CedarFSFolder createRootFolder(String creatorId) {
    Map<String, Object> extraParams = new HashMap<>();
    extraParams.put("isRoot", true);
    String cypher = CypherQueryBuilder.createRootFolder(extraParams);
    Map<String, Object> params = CypherParamBuilder.createFolder(null, config.getRootFolderPath(), config
        .getRootFolderDescription(), creatorId, extraParams);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode rootNode = jsonNode.at("/results/0/data/0/row/0");
    return buildFolder(rootNode);
  }

  CedarFSFolder createFolderAsChildOfId(String parentId, String name, String description, String creatorId, Map<String,
      Object> extraProperties) {
    String cypher = CypherQueryBuilder.createFolderAsChildOfId(extraProperties);
    Map<String, Object> params = CypherParamBuilder.createFolder(parentId, name, description, creatorId, extraProperties);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode newNode = jsonNode.at("/results/0/data/0/row/0");
    return buildFolder(newNode);
  }


  CedarFSResource createResourceAsChildOfId(String parentId, CedarNodeType resourceType, String name, String
      description, String creatorId, Map<String, Object> extraProperties) {
    String cypher = CypherQueryBuilder.createResourceAsChildOfId(extraProperties);
    Map<String, Object> params = CypherParamBuilder.createResource(parentId, resourceType, name, description,
        creatorId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode newNode = jsonNode.at("/results/0/data/0/row/0");
    return buildResource(newNode);
  }

  List<CedarFSNode> findFolderContents(String folderId, Collection<CedarNodeType> resourceTypes, int
      limit, int offset, List<String> sortList) {
    List<CedarFSNode> resources = new ArrayList<>();

    String cypher = CypherQueryBuilder.getFolderContentsLookupQuery();
    Map<String, Object> params = CypherParamBuilder.getFolderContentsLookupParameters(folderId, resourceTypes, limit,
        offset);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode resourceListJsonNode = jsonNode.at("/results/0/data");
    if (resourceListJsonNode != null && !resourceListJsonNode.isMissingNode()) {
      resourceListJsonNode.forEach(f -> {
        JsonNode nodeNode = f.at("/row/0");
        CedarFSNode cf = buildNode(nodeNode);
        if (cf != null) {
          resources.add(cf);
        }
      });
    }
    return resources;
  }

  boolean deleteFolderById(String folderId) {
    String cypher = CypherQueryBuilder.deleteFolderById();
    Map<String, Object> params = CypherParamBuilder.deleteFolderById(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode errorsNode = jsonNode.at("/errors");
    if (errorsNode.size() != 0) {
      JsonNode error = errorsNode.path(0);
      log.warn("Error while deleting folder:", error);
    }
    return errorsNode.size() == 0;
  }

  boolean deleteResourceById(String resourceURL) {
    String cypher = CypherQueryBuilder.deleteResourceById();
    Map<String, Object> params = CypherParamBuilder.deleteResourceById(resourceURL);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode errorsNode = jsonNode.at("/errors");
    if (errorsNode.size() != 0) {
      JsonNode error = errorsNode.path(0);
      log.warn("Error while deleting resource:", error);
    }
    return errorsNode.size() == 0;
  }

  CedarFSFolder updateFolderById(String folderId, Map<String, String> updateFields, String updatedBy) {
    String cypher = CypherQueryBuilder.updateFolderById(updateFields);
    Map<String, Object> params = CypherParamBuilder.updateFolderById(folderId, updateFields, updatedBy);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode updatedNode = jsonNode.at("/results/0/data/0/row/0");
    return buildFolder(updatedNode);
  }

  CedarFSResource updateResourceById(String resourceURL, Map<String, String> updateFields, String updatedBy) {
    String cypher = CypherQueryBuilder.updateResourceById(updateFields);
    Map<String, Object> params = CypherParamBuilder.updateResourceById(resourceURL, updateFields, updatedBy);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode updatedNode = jsonNode.at("/results/0/data/0/row/0");
    return buildResource(updatedNode);
  }

  void convertNeo4JValues(CedarFSNode r) {
    r.setId(folderIdPrefix + r.getId());
  }

  String getFolderUUID(String folderId) {
    if (folderId != null && folderId.startsWith(folderIdPrefix)) {
      return folderId.substring(folderIdPrefix.length());
    } else {
      return null;
    }
  }

  //TODO: fix this, we need to handle the prefixes based on the type
  //TODO: REALLY IMPORTANT
  String getResourceUUID(String folderId, CedarNodeType resourceType) {
    if (folderId != null && folderId.startsWith(folderIdPrefix)) {
      return folderId.substring(folderIdPrefix.length());
    } else {
      return null;
    }
  }

  private CedarFSFolder buildFolder(JsonNode f) {
    CedarFSFolder cf = null;
    if (f != null && !f.isMissingNode()) {
      try {
        cf = MAPPER.treeToValue(f, CedarFSFolder.class);
      } catch (JsonProcessingException e) {
        log.error("Error deserializing folder", e);
      }
      convertNeo4JValues(cf);
    }
    return cf;
  }

  private CedarFSNode buildNode(JsonNode f) {
    CedarFSNode cf = null;
    if (f != null && !f.isMissingNode()) {
      try {
        cf = MAPPER.treeToValue(f, CedarFSNode.class);
      } catch (JsonProcessingException e) {
        log.error("Error deserializing node", e);
      }
      convertNeo4JValues(cf);
    }
    return cf;
  }

  private CedarFSResource buildResource(JsonNode r) {
    CedarFSResource cr = null;
    if (r != null && !r.isMissingNode()) {
      try {
        cr = MAPPER.treeToValue(r, CedarFSResource.class);
      } catch (JsonProcessingException e) {
        log.error("Error deserializing resource", e);
      }
      convertNeo4JValues(cr);
    }
    return cr;
  }

  private Map buildMap(JsonNode f) {
    Map map = null;
    if (f != null && !f.isMissingNode()) {
      try {
        map = MAPPER.treeToValue(f, Map.class);
      } catch (JsonProcessingException e) {
        log.error("Error deserializing map", e);
      }
    }
    return map;
  }

}
