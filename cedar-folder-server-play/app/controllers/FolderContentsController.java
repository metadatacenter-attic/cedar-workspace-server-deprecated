package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FSNodeListResponse;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.IAuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.F;
import play.mvc.Result;
import utils.DataServices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FolderContentsController extends AbstractFolderServerController {
  private static Logger log = LoggerFactory.getLogger(FolderContentsController.class);
  private static ObjectMapper MAPPER = new ObjectMapper();

  final static List<String> knownSortKeys;
  public static final String DEFAULT_SORT;

  static {
    DEFAULT_SORT = "name";
    knownSortKeys = new ArrayList<>();
    knownSortKeys.add("name");
    knownSortKeys.add("createdOnTS");
    knownSortKeys.add("lastUpdatedOnTS");
  }

  public static Result findFolderContentsByPath(F.Option<String> pathParam, F.Option<String> resourceTypes, F
      .Option<String> sort, F.Option<Integer> limitParam, F.Option<Integer> offsetParam) {
    IAuthRequest frontendRequest = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while creating the folder", e);
      return forbiddenWithError(e);
    }

    try {
      // Test path
      String path = null;
      if (pathParam.isDefined()) {
        path = pathParam.get();
      }
      if (path != null) {
        path = path.trim();
      }

      if (path == null || path.length() == 0) {
        throw new IllegalArgumentException("You need to specify path as a request parameter!");
      }

      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(Authorization.getAccountInfo
          (frontendRequest));

      String normalizedPath = neoSession.normalizePath(path);
      if (!normalizedPath.equals(path)) {
        throw new IllegalArgumentException("The path is not in normalized form!");
      }

      CedarFSFolder folder = neoSession.findFolderByPath(path);
      if (folder == null) {
        return notFound();
      }

      F.Option<Integer> none = new F.None<>();
      String absoluteUrl = routes.FolderContentsController.findFolderContentsByPath(pathParam, resourceTypes, sort,
          none,
          none)
          .absoluteURL(request());

      List<CedarFSFolder> pathInfo = neoSession.findFolderPathByPath(path);

      return findFolderContents(neoSession, folder, absoluteUrl, pathInfo, resourceTypes, sort, limitParam,
          offsetParam);

    } catch (IllegalArgumentException e) {
      play.Logger.error("Illegal argument while listing folder contents", e);
      return badRequestWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while listing folder contents", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result findFolderContentsById(String id, F.Option<String> resourceTypes, F.Option<String>
      sort, F.Option<Integer> limitParam, F.Option<Integer> offsetParam) {
    IAuthRequest frontendRequest = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while creating the folder", e);
      return forbiddenWithError(e);
    }

    try {
      if (id != null) {
        id = id.trim();
      }

      if (id == null || id.length() == 0) {
        throw new IllegalArgumentException("You need to specify id as a request parameter!");
      }

      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(Authorization.getAccountInfo
          (frontendRequest));

      CedarFSFolder folder = neoSession.findFolderById(id);
      if (folder == null) {
        return notFound();
      }

      F.Option<Integer> none = new F.None<>();
      String absoluteUrl = routes.FolderContentsController.findFolderContentsById(id, resourceTypes, sort,
          none,
          none)
          .absoluteURL(request());

      List<CedarFSFolder> pathInfo = neoSession.findFolderPath(folder);

      return findFolderContents(neoSession, folder, absoluteUrl, pathInfo, resourceTypes, sort, limitParam,
          offsetParam);

    } catch (IllegalArgumentException e) {
      play.Logger.error("Illegal argument while listing folder contents", e);
      return badRequestWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while listing folder contents", e);
      return internalServerErrorWithError(e);
    }
  }


  private static Result findFolderContents(Neo4JUserSession neoSession, CedarFSFolder folder, String absoluteUrl,
                                           List<CedarFSFolder> pathInfo, F.Option<String> resourceTypes, F
                                               .Option<String> sort, F.Option<Integer> limitParam, F.Option<Integer>
                                               offsetParam) {

    // Test limit
    // TODO : set defaults from config here
    int limit = 50; // set default
    if (limitParam.isDefined()) {
      if (limitParam.get() <= 0) {
        throw new IllegalArgumentException("You should specify a positive limit!");
      } else if (limitParam.get() > 100) {
        throw new IllegalArgumentException("You should specify a limit smaller than 100!");
      }
      limit = limitParam.get();
    }

    // Test offset
    int offset = 0;
    if (offsetParam.isDefined()) {
      if (offsetParam.get() < 0) {
        throw new IllegalArgumentException("You should specify a positive or zero offset!");
      }
      offset = offsetParam.get();
    }

    // Test sort
    String sortString;
    if (sort.isDefined()) {
      sortString = sort.get();
    } else {
      sortString = DEFAULT_SORT;
    }

    if (sortString != null) {
      sortString = sortString.trim();
    }

    List<String> sortList = Arrays.asList(StringUtils.split(sortString, ","));
    for (String s : sortList) {
      String test = s;
      if (s != null && s.startsWith("-")) {
        test = s.substring(1);
      }
      if (!knownSortKeys.contains(test)) {
        throw new IllegalArgumentException("You passed an illegal sort type:'" + s + "'. The allowed values are:" +
            knownSortKeys);
      }
    }

    // Test resourceTypes
    String resourceTypesString = null;
    if (resourceTypes.isDefined()) {
      resourceTypesString = resourceTypes.get();
    }
    if (resourceTypesString != null) {
      resourceTypesString = resourceTypesString.trim();
    }
    if (resourceTypesString == null || resourceTypesString.isEmpty()) {
      throw new IllegalArgumentException("You must pass in resource_types as a comma separated list!");
    }

    List<String> resourceTypeStringList = Arrays.asList(StringUtils.split(resourceTypesString, ","));
    List<CedarNodeType> resourceTypeList = new ArrayList<>();
    for (String rt : resourceTypeStringList) {
      CedarNodeType crt = CedarNodeType.forValue(rt);
      if (crt == null) {
        throw new IllegalArgumentException("You passed an illegal sort type:'" + rt + "'. The allowed values are:" +
            CedarNodeType.values());
      } else {
        resourceTypeList.add(crt);
      }
    }

    FSNodeListResponse r = new FSNodeListResponse();

    NodeListRequest req = new NodeListRequest();
    req.setResourceTypes(resourceTypeList);
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);

    r.setRequest(req);

    List<CedarFSNode> resources = neoSession.findFolderContents(folder.getId(), resourceTypeList, limit, offset,
        sortList);

    long total = neoSession.findFolderContentsCount(folder.getId(), resourceTypeList);

    r.setTotalCount(total);
    r.setCurrentOffset(offset);

    r.setResources(resources);

    r.setPathInfo(pathInfo);


    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, limit, offset));

    JsonNode resp = MAPPER.valueToTree(r);
    return ok(resp);
  }


}
