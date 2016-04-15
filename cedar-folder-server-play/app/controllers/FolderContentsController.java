package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.metadatacenter.model.CedarFolder;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.request.ResourceListRequest;
import org.metadatacenter.model.response.ResourceListResponse;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
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

  final static List<String> knownSortKeys;
  public static final String DEFAULT_SORT = "name";

  static {
    knownSortKeys = new ArrayList<>();
    knownSortKeys.add("name");
    knownSortKeys.add("createdOn");
    knownSortKeys.add("lastUpdatedOn");
  }

  public static Result findFolderContents(F.Option<String> pathParam, F.Option<String> resourceTypes, F.Option<String>
      sort, F.Option<Integer> limitParam, F.Option<Integer> offsetParam) {
    try {
      IAuthRequest frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      Authorization.mustHavePermission(frontendRequest, CedarPermission.JUST_AUTHORIZED);

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

      Neo4JProxy neo4JProxy = DataServices.getInstance().getNeo4JProxy();

      String normalizedPath = neo4JProxy.normalizePath(path);
      if (!normalizedPath.equals(path)) {
        throw new IllegalArgumentException("Do not pass trailing / for paths!");
      }

      CedarFolder folder = neo4JProxy.findFolderByPath(path);
      if (folder == null) {
        return notFound();
      }

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
        throw new IllegalArgumentException("You must pass in resource_types as a comma separatred list!");
      }

      List<String> resourceTypeStringList = Arrays.asList(StringUtils.split(resourceTypesString, ","));
      List<CedarResourceType> resourceTypeList = new ArrayList<>();
      for (String rt : resourceTypeStringList) {
        CedarResourceType crt = CedarResourceType.forValue(rt);
        if (crt == null) {
          throw new IllegalArgumentException("You passed an illegal sort type:'" + rt + "'. The allowed values are:" +
              CedarResourceType.values());
        } else {
          resourceTypeList.add(crt);
        }
      }

      ResourceListResponse r = new ResourceListResponse();

      ResourceListRequest req = new ResourceListRequest();
      req.setPath(path);
      req.setResourceTypes(resourceTypeList);
      req.setLimit(limit);
      req.setOffset(offset);
      req.setSort(sortList);

      r.setRequest(req);

      List<CedarResource> resources = neo4JProxy.findFolderContents(folder, resourceTypeList, limit, offset,
          sortList);

      long total = resources.size();

      r.setTotalCount(total);
      r.setCurrentOffset(offset);

      r.setResources(resources);

      F.Option<Integer> none = new F.None<>();
      String absoluteUrl = routes.FolderContentsController.findFolderContents(pathParam, resourceTypes, sort, none,
          none)
          .absoluteURL(request());

      r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, limit, offset));

      ObjectMapper mapper = new ObjectMapper();
      JsonNode resp = mapper.valueToTree(r);
      return ok(resp);

    } catch (IllegalArgumentException e) {
      play.Logger.error("Illegal argument while creating the folder", e);
      return badRequestWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while creating the folder", e);
      return internalServerErrorWithError(e);
    }

  }

}
