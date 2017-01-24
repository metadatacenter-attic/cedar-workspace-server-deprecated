package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.commons.lang3.StringUtils;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.CedarQueryParameters;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.FolderContentSortOptions;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.http.LinkHeaderUtil;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.constant.CedarQueryParameters.*;
import static org.metadatacenter.constant.CedarQueryParameters.QP_RESOURCE_TYPES;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;


@Path("/folders")
@Produces(MediaType.APPLICATION_JSON)
public class FolderContentsResource extends AbstractFolderServerResource {

  public FolderContentsResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/contents")
  public Response findFolderContentsByPath(@QueryParam(QP_PATH) Optional<String> pathParam,
                                           @QueryParam(QP_RESOURCE_TYPES) Optional<String> resourceTypes,
                                           @QueryParam(QP_SORT) Optional<String> sort,
                                           @QueryParam(QP_LIMIT) Optional<Integer> limitParam,
                                           @QueryParam(QP_OFFSET) Optional<Integer> offsetParam) throws
      CedarException {

    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    // Test path
    String path = null;
    if (pathParam.isPresent()) {
      path = pathParam.get();
    }
    if (path != null) {
      path = path.trim();
    }

    if (path == null || path.length() == 0) {
      throw new IllegalArgumentException("You need to specify path as a request parameter!");
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    String normalizedPath = folderSession.normalizePath(path);
    if (!normalizedPath.equals(path)) {
      throw new IllegalArgumentException("The path is not in normalized form!");
    }

    FolderServerFolder folder = folderSession.findFolderByPath(path);
    if (folder == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    UriBuilder builder = uriInfo.getAbsolutePathBuilder();
    URI absoluteURI = builder.queryParam(QP_PATH, pathParam)
        .queryParam(QP_RESOURCE_TYPES, resourceTypes)
        .queryParam(QP_SORT, sort)
        .build();

    List<FolderServerFolder> pathInfo = folderSession.findFolderPathByPath(path);

    return findFolderContents(folderSession, folder, absoluteURI.toString(), pathInfo, resourceTypes, sort, limitParam,
        offsetParam);
  }

  @GET
  @Timed
  @Path("/{id}/contents")
  public Response findFolderContentsById(@PathParam(PP_ID) String id,
                                         @QueryParam(QP_RESOURCE_TYPES) Optional<String> resourceTypes,
                                         @QueryParam(QP_SORT) Optional<String> sort,
                                         @QueryParam(QP_LIMIT) Optional<Integer> limitParam,
                                         @QueryParam(QP_OFFSET) Optional<Integer> offsetParam) throws
      CedarException {
    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    if (id != null) {
      id = id.trim();
    }

    if (id == null || id.length() == 0) {
      throw new IllegalArgumentException("You need to specify id as a request parameter!");
    }

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    FolderServerFolder folder = folderSession.findFolderById(id);
    if (folder == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    UriBuilder builder = uriInfo.getAbsolutePathBuilder();
    URI absoluteURI = builder.path(CedarUrlUtil.urlEncode(id))
        .queryParam(QP_RESOURCE_TYPES, resourceTypes)
        .queryParam(QP_SORT, sort)
        .build();

    List<FolderServerFolder> pathInfo = folderSession.findFolderPath(folder);

    return findFolderContents(folderSession, folder, absoluteURI.toString(), pathInfo, resourceTypes, sort, limitParam,
        offsetParam);
  }


  private Response findFolderContents(FolderServiceSession folderSession, FolderServerFolder folder, String
      absoluteUrl, List<FolderServerFolder> pathInfo, Optional<String> resourceTypes, Optional<String> sort,
                                      Optional<Integer> limitParam, Optional<Integer> offsetParam) throws
      CedarException {

    // Test limit
    // TODO : set defaults from config here
    int limit = 50; // set default
    if (limitParam.isPresent()) {
      if (limitParam.get() <= 0) {
        throw new IllegalArgumentException("You should specify a positive limit!");
      } else if (limitParam.get() > 100) {
        throw new IllegalArgumentException("You should specify a limit smaller than 100!");
      }
      limit = limitParam.get();
    }

    // Test offset
    int offset = 0;
    if (offsetParam.isPresent()) {
      if (offsetParam.get() < 0) {
        throw new IllegalArgumentException("You should specify a positive or zero offset!");
      }
      offset = offsetParam.get();
    }

    // Test sort
    String sortString;
    if (sort.isPresent()) {
      sortString = sort.get();
    } else {
      sortString = FolderContentSortOptions.getDefaultSortField().getName();
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
      if (!FolderContentSortOptions.isKnownField(test)) {
        throw new IllegalArgumentException("You passed an illegal sort type:'" + s + "'. The allowed values are:" +
            FolderContentSortOptions.getKnownFieldNames());
      }
    }

    // Test resourceTypes
    String nodeTypesString = null;
    if (resourceTypes.isPresent()) {
      nodeTypesString = resourceTypes.get();
    }
    if (nodeTypesString != null) {
      nodeTypesString = nodeTypesString.trim();
    }
    if (nodeTypesString == null || nodeTypesString.isEmpty()) {
      throw new IllegalArgumentException("You must pass in resource_types as a comma separated list!");
    }

    List<String> nodeTypeStringList = Arrays.asList(StringUtils.split(nodeTypesString, ","));
    List<CedarNodeType> nodeTypeList = new ArrayList<>();
    for (String rt : nodeTypeStringList) {
      CedarNodeType crt = CedarNodeType.forValue(rt);
      if (crt == null) {
        throw new IllegalArgumentException("You passed an illegal sort type:'" + rt + "'. The allowed values are:" +
            CedarNodeType.values());
      } else {
        nodeTypeList.add(crt);
      }
    }

    FolderServerNodeListResponse r = new FolderServerNodeListResponse();
    r.setNodeListQueryType(NodeListQueryType.FOLDER_CONTENT);

    NodeListRequest req = new NodeListRequest();
    req.setNodeTypes(nodeTypeList);
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);

    r.setRequest(req);

    List<FolderServerNode> resources = folderSession.findFolderContents(folder.getId(), nodeTypeList, limit, offset,
        sortList);

    long total = folderSession.findFolderContentsCount(folder.getId(), nodeTypeList);

    r.setTotalCount(total);
    r.setCurrentOffset(offset);

    r.setResources(resources);

    r.setPathInfo(pathInfo);


    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, limit, offset));

    return Response.ok().entity(r).build();
  }

}