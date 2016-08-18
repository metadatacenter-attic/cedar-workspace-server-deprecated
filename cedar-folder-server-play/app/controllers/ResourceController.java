package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.model.folderserver.CedarFSResource;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FSNodeListResponse;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.IAuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.parameter.ParameterUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.F;
import play.mvc.Result;
import utils.DataServices;

import java.util.*;

public class ResourceController extends AbstractFolderServerController {
  private static Logger log = LoggerFactory.getLogger(ResourceController.class);

  final static List<String> knownSortKeys;
  public static final String DEFAULT_SORT;

  static {
    DEFAULT_SORT = "name";
    knownSortKeys = new ArrayList<>();
    knownSortKeys.add("name");
    knownSortKeys.add("createdOnTS");
    knownSortKeys.add("lastUpdatedOnTS");
  }

  public static Result createResource() {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.LOGGED_IN);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while creating the resource", e);
      return forbiddenWithError(e);
    }

    try {
      JsonNode creationRequest = request().body().asJson();
      if (creationRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      // get parentId
      String parentId = ParameterUtil.getStringOrThrowError(creationRequest, "parentId",
          "You must supply the parentId of the new resource!");

      // get id
      String id = ParameterUtil.getStringOrThrowError(creationRequest, "id",
          "You must supply the id of the new resource!");

      // get name
      String name = ParameterUtil.getStringOrThrowError(creationRequest, "name",
          "You must supply the name of the new resource!");

      // get resourceType parameter
      String nodeTypeString = ParameterUtil.getStringOrThrowError(creationRequest, "nodeType",
          "You must supply the nodeType of the new resource!");

      CedarNodeType nodeType = CedarNodeType.forValue(nodeTypeString);
      if (nodeType == null) {
        StringBuilder sb = new StringBuilder();
        Arrays.asList(CedarNodeType.values()).forEach(crt -> sb.append(crt.getValue()).append(","));
        throw new IllegalArgumentException("The supplied node type is invalid! It should be one of:" + sb
            .toString());
      }


      String description = "";
      // let's not read resource description for instances
      if (nodeType != CedarNodeType.INSTANCE) {
        // get description
        description = ParameterUtil.getStringOrThrowError(creationRequest, "description",
            "You must supply the description of the new resource!");
      }

      // check existence of parent folder
      CedarFSResource newResource = null;
      CedarFSFolder parentFolder = neoSession.findFolderById(parentId);

      String candidatePath = null;
      if (parentFolder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("parentId", parentId);
        return badRequest(generateErrorDescription("parentNotPresent",
            "The parent folder is not present:" + parentId, errorParams));
      } else {
        // Later we will guarantee some kind of uniqueness for the resource names
        // Currently we allow duplicate names, the id is the PK
        NodeLabel nodeLabel = NodeLabel.forCedarNodeType(nodeType);
        newResource = neoSession.createResourceAsChildOfId(parentId, id, nodeType, name, description, nodeLabel);
      }

      if (newResource != null) {
        JsonNode createdResource = JsonMapper.MAPPER.valueToTree(newResource);
        String absoluteUrl = routes.ResourceController.findResource(id).absoluteURL(request());
        response().setHeader(HttpConstants.HTTP_HEADER_LOCATION, absoluteUrl);
        return created(createdResource);
      } else {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("parentId", parentId);
        errorParams.put("id", id);
        errorParams.put("resourceType", nodeTypeString);
        return badRequest(generateErrorDescription("resourceNotCreated",
            "The resource was not created:" + id, errorParams));
      }
    } catch (IllegalArgumentException e) {
      play.Logger.error("Illegal argument while creating the resource", e);
      return badRequestWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while creating the resource", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result findAllNodes(F.Option<String> sortParam, F.Option<Integer> limitParam, F.Option<Integer>
      offsetParam) {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.LOGGED_IN);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while retrieving all resources", e);
      return forbiddenWithError(e);
    }

    F.Option<Integer> none = new F.None<>();
    String absoluteUrl = routes.ResourceController.findAllNodes(sortParam, none, none).absoluteURL(request());

    // TODO : set default values for input parameters from config
    int limit = 50;
    int maxAllowedLimit = 50000;
    int offset = 0;

    // Input parameter validation: 'limit'
    if (limitParam.isDefined()) {
      if (limitParam.get() <= 0) {
        throw new IllegalArgumentException("You should specify a positive limit!");
      } else if (limitParam.get() > maxAllowedLimit) {
        throw new IllegalArgumentException("You should specify a limit smaller than " + maxAllowedLimit + "!");
      }
      limit = limitParam.get();
    }
    // Input parameter validation: 'offset'
    if (offsetParam.isDefined()) {
      if (offsetParam.get() < 0) {
        throw new IllegalArgumentException("You should specify a positive or zero offset!");
      }
      offset = offsetParam.get();
    }
    // Input parameter validation: 'sort'
    List<String> sortList = new ArrayList<>();
    if (sortParam.isDefined()) {
      sortList = Arrays.asList(sortParam.get().split("\\s*,\\s*"));
      for (String s : sortList) {
        if (!knownSortKeys.contains(s) && !knownSortKeys.contains("-" + s)) {
          throw new IllegalArgumentException("You passed an illegal sort type: '" + s + "'. The allowed values are:" +
              knownSortKeys);
        }
      }
    } else {
      sortList.add(DEFAULT_SORT);
    }

    Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

    // Retrieve all resources
    List<CedarFSNode> resources = neoSession.findAllNodes(limit, offset, sortList);

    // Build response
    FSNodeListResponse r = new FSNodeListResponse();
    NodeListRequest req = new NodeListRequest();
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);
    r.setRequest(req);
    long total = neoSession.findAllNodesCount();
    r.setTotalCount(total);
    r.setCurrentOffset(offset);
    r.setResources(resources);
    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, limit, offset));
    JsonNode resp = JsonMapper.MAPPER.valueToTree(r);
    return ok(resp);
  }

  public static Result findResource(String resourceId) {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.LOGGED_IN);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while reading the resource", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      CedarFSResource resource = neoSession.findResourceById(resourceId);
      if (resource == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", resourceId);
        return notFound(generateErrorDescription("resourceNotFound",
            "The resource can not be found by id:" + resourceId, errorParams));
      } else {
        neoSession.addPathAndParentId(resource);
        JsonNode folderNode = JsonMapper.MAPPER.valueToTree(resource);
        return ok(folderNode);
      }
    } catch (Exception e) {
      play.Logger.error("Error while getting the resource", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result updateResource(String resourceId) {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.LOGGED_IN);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while updating the resource", e);
      return forbiddenWithError(e);
    }

    try {
      JsonNode folderUpdateRequest = request().body().asJson();
      if (folderUpdateRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      String name = null;
      JsonNode nameNode = folderUpdateRequest.get("name");
      if (nameNode != null) {
        name = nameNode.asText();
        if (name != null) {
          name = name.trim();
        }
      }

      String description = null;
      JsonNode descriptionNode = folderUpdateRequest.get("description");
      if (descriptionNode != null) {
        description = descriptionNode.asText();
        if (description != null) {
          description = description.trim();
        }
      }

      if ((name == null || name.isEmpty()) && (description == null || description.isEmpty())) {
        throw new IllegalArgumentException("You must supply the new description or the new name of the resource!");
      }

      CedarFSResource resource = neoSession.findResourceById(resourceId);
      if (resource == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", resourceId);
        return notFound(generateErrorDescription("resourceNotFound",
            "The resource can not be found by id:" + resourceId, errorParams));
      } else {
        Map<String, String> updateFields = new HashMap<>();
        if (description != null) {
          updateFields.put("description", description);
        }
        if (name != null) {
          updateFields.put("name", name);
        }
        //TODO: fix this
        CedarFSResource updatedFolder = neoSession.updateResourceById(resourceId, CedarNodeType.ELEMENT, updateFields);
        if (updatedFolder == null) {
          return notFound();
        } else {
          JsonNode updatedFolderNode = JsonMapper.MAPPER.valueToTree(updatedFolder);
          return ok(updatedFolderNode);
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while deleting the resource", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result deleteResource(String resourceId) {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.LOGGED_IN);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while deleting the resource", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      CedarFSResource resource = neoSession.findResourceById(resourceId);
      if (resource == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", resourceId);
        return notFound(generateErrorDescription("resourceNotFound",
            "The resource can not be found by id:" + resourceId, errorParams));
      } else {
        boolean deleted = neoSession.deleteResourceById(resourceId, CedarNodeType.ELEMENT);
        if (deleted) {
          return noContent();
        } else {
          ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
          errorParams.put("id", resourceId);
          return internalServerErrorWithError(generateErrorDescription("resourceNotDeleted",
              "The resource can not be delete by id:" + resourceId, errorParams));
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while deleting the resource", e);
      return internalServerErrorWithError(e);
    }
  }

}
