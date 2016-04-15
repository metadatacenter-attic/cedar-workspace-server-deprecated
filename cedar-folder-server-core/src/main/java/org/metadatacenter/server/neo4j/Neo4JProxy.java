package org.metadatacenter.server.neo4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.CedarFolder;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.CedarResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Neo4JProxy {

  private static final int connectTimeout = 1000;
  private static final int socketTimeout = 10000;

  private final static String SEPARATOR = "/";
  private final static String FOLDER_NAME_SANITIZER_REGEX = "[^a-zA-Z0-9\\.\\-_' ]";
  private final static Pattern FOLDER_NAME_SANITIZER_PATTERN = Pattern.compile(FOLDER_NAME_SANITIZER_REGEX);

  private Neo4jConfig config;
  private String userIdPrefix;
  private String rootPath;

  private static Logger log = LoggerFactory.getLogger(Neo4JProxy.class);

  public Neo4JProxy(Neo4jConfig config, String userIdPrefix) {
    this.config = config;
    this.userIdPrefix = userIdPrefix;
    this.rootPath = config.getRootFolderPath();
  }

  private JsonNode executeCypherQueryAndCommit(CypherQuery query) {
    return executeCypherQueriesAndCommit(Arrays.asList(query));
  }

  private JsonNode executeCypherQueriesAndCommit(List<CypherQuery> queries) {
    List<Map<String, Object>> statements = new ArrayList<>();
    for (CypherQuery q : queries) {
      if (q instanceof CypherQueryWithParameters) {
        CypherQueryWithParameters qp = (CypherQueryWithParameters) q;
        Map<String, Object> statement = new HashMap<>();
        statement.put("statement", qp.getQuery());
        statement.put("parameters", qp.getParameters());
        statements.add(statement);
      } else if (q instanceof CypherQueryLiteral) {
        CypherQueryLiteral qp = (CypherQueryLiteral) q;
        Map<String, Object> statement = new HashMap<>();
        statement.put("statement", qp.getQuery());
        statements.add(statement);
      }
    }

    Map<String, Object> body = new HashMap<>();
    body.put("statements", statements);

    String requestBody = null;
    ObjectMapper mapper = new ObjectMapper();
    try {
      requestBody = mapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      log.error("Error serializing cypher queries", e);
    }
    //System.out.println("Cypher request:");
    //System.out.println(requestBody);

    try {
      HttpResponse response = Request.Post(config.getTransactionUrl())
          .addHeader(HttpConstants.HTTP_HEADER_AUTHORIZATION, config.getAuthString())
          .addHeader(HttpConstants.HTTP_HEADER_ACCEPT, HttpConstants.CONTENT_TYPE_APPLICATION_JSON)
          .addHeader("X-stream", "true")
          .bodyString(requestBody, ContentType.APPLICATION_JSON)
          .connectTimeout(connectTimeout)
          .socketTimeout(socketTimeout)
          .execute()
          .returnResponse();

      int statusCode = response.getStatusLine().getStatusCode();
      //System.out.println("Status code:" + statusCode);
      String responseAsString = EntityUtils.toString(response.getEntity());
      //System.out.println(responseAsString);
      if (statusCode == HttpConstants.OK) {
        return mapper.readTree(responseAsString);
      } else {
        return null;
      }

    } catch (IOException ex) {
      log.error("Error while reading user details from Keycloak", ex);
      ex.printStackTrace();
    }
    return null;
  }

  public void init() {
    CedarFolder rootFolder = findFolderByPath(config.getRootFolderPath());
    if (rootFolder == null) {
      rootFolder = createRootFolder();
    }
    CedarFolder usersFolder = findFolderByPath(config.getUsersFolderPath());
    if (usersFolder == null) {
      usersFolder = createFolderAsChildOfId(rootFolder.getId(), config.getUsersFolderPath().substring(config
          .getRootFolderPath().length()), config.getUsersFolderDescription(), getUserId(config.getAdminUserUUID()));
    }
  }

  private String getUserId(String userUUID) {
    return userIdPrefix + userUUID;
  }

  public List<CedarFolder> findFolderPath(String path) {
    List<CedarFolder> pathList = new ArrayList<>();
    int cnt = getPathDepth(path);
    String cypher = CypherQueryBuilder.getFolderLookupQuery(cnt);
    Map<String, String> params = CypherParamBuilder.getFolderLookupParameters(this, path);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode pathListJsonNode = jsonNode.path("results").path(0).path("data").path(0).path("row");
    ObjectMapper mapper = new ObjectMapper();
    pathListJsonNode.forEach(f -> {
      try {
        CedarFolder cf = mapper.treeToValue(f, CedarFolder.class);
        pathList.add(cf);
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }
    });
    return pathList;
  }

  public CedarFolder findFolderByPath(String path) {
    List<CedarFolder> folderPath = findFolderPath(path);
    if (folderPath != null && folderPath.size() > 0) {
      CedarFolder folder = folderPath.get(folderPath.size() - 1);
      folder.setPath(normalizePath(path));
      return folder;
    }
    return null;
  }


  public CedarFolder findFolderById(String folderId) {
    String cypher = CypherQueryBuilder.getFolderById();
    Map<String, String> params = CypherParamBuilder.getFolderById(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode folderNode = jsonNode.path("results").path(0).path("data").path(0).path("row").path(0);
    ObjectMapper mapper = new ObjectMapper();
    CedarFolder folder = null;
    try {
      folder = mapper.treeToValue(folderNode, CedarFolder.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return folder;
  }

  public CedarFolder createRootFolder() {
    String cypher = CypherQueryBuilder.createRootFolder();
    Map<String, String> params = CypherParamBuilder.createFolder(null, config.getRootFolderPath(), config
        .getRootFolderDescription(), getUserId(config.getAdminUserUUID()));
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode rootNode = jsonNode.path("results").path(0).path("data").path(0).path("row").path(0);
    ObjectMapper mapper = new ObjectMapper();
    CedarFolder rootFolder = null;
    try {
      rootFolder = mapper.treeToValue(rootNode, CedarFolder.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return rootFolder;
  }

  public CedarFolder createFolderAsChildOfId(String parentId, String name, String description, String creatorId) {
    String cypher = CypherQueryBuilder.createFolderAsChildOfId();
    Map<String, String> params = CypherParamBuilder.createFolder(parentId, name, description, creatorId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode newNode = jsonNode.path("results").path(0).path("data").path(0).path("row").path(0);
    ObjectMapper mapper = new ObjectMapper();
    CedarFolder newFolder = null;
    try {
      newFolder = mapper.treeToValue(newNode, CedarFolder.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    return newFolder;
  }

  public String normalizePath(String path) {
    if (path != null) {
      if (!rootPath.equals(path)) {
        while (path.endsWith(SEPARATOR)) {
          path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith(rootPath)) {
          path = rootPath + path;
        }
        String ds = SEPARATOR + SEPARATOR;
        while (path.indexOf(ds) != -1) {
          path = path.replace(ds, SEPARATOR);
        }
      }
    }
    return path;
  }

  public String sanitizeName(String name) {
    Matcher matcher = FOLDER_NAME_SANITIZER_PATTERN.matcher(name);
    return matcher.replaceAll("_");
  }

  public String extractName(String path) {
    if (path != null) {
      String[] split = StringUtils.split(path, SEPARATOR);
      if (split != null) {
        return split[split.length - 1];
      }
    }
    return null;
  }

  public String getParentPath(String path) {
    if (path != null) {
      String[] split = StringUtils.split(path, SEPARATOR);
      if (split != null) {
        if (split.length > 0) {
          String parent = StringUtils.join(Arrays.copyOf(split, split.length - 1), SEPARATOR);
          return normalizePath(parent);
        }
      } else {
        return rootPath;
      }
    }
    return null;
  }

  public int getPathDepth(String path) {
    String normalizedPath = normalizePath(path);
    return isRoot(normalizedPath) ? 1 : StringUtils.countMatches(normalizedPath, SEPARATOR) + 1;
  }

  public boolean isRoot(String path) {
    String normalizedPath = normalizePath(path);
    return rootPath.equals(normalizedPath);
  }

  public String getSeparator() {
    return SEPARATOR;
  }

  public String getRootPath() {
    return rootPath;
  }

  public List<CedarResource> findFolderContents(CedarFolder folder, Collection<CedarResourceType> resourceTypes, int
      limit,
                                                int offset, List<String> sortList) {
    List<CedarResource> resources = new ArrayList<>();

    String cypher = CypherQueryBuilder.getFolderContentsLookupQuery();
    Map<String, String> params = CypherParamBuilder.getFolderContentsLookupParameters(folder);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode resourceListJsonNode = jsonNode.path("results").path(0).path("data");
    ObjectMapper mapper = new ObjectMapper();
    resourceListJsonNode.forEach(f -> {
      try {
        CedarFolder cf = mapper.treeToValue(f.path("row").path(0), CedarFolder.class);
        resources.add(cf);
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }
    });

    return resources;
  }

  public boolean deleteFolderById(String folderId) {
    String cypher = CypherQueryBuilder.deleteFolderById();
    Map<String, String> params = CypherParamBuilder.deleteFolderById(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode errorsNode = jsonNode.path("errors");
    if (errorsNode.size() != 0) {
      JsonNode error = errorsNode.path(0);
      System.out.println("Error while deleting node:");
      System.out.println(error);
    }
    return errorsNode.size() == 0;
  }
}
