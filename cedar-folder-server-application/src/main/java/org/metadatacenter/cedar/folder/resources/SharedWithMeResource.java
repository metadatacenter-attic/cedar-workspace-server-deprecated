package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import org.apache.commons.lang3.StringUtils;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.FolderContentSortOptions;
import org.metadatacenter.util.http.CedarURIBuilder;
import org.metadatacenter.util.http.LinkHeaderUtil;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/view")
@Produces(MediaType.APPLICATION_JSON)
public class SharedWithMeResource extends AbstractFolderServerResource {

  private static final String VIEW_SHARED_WITH_ME = "view/shared-with-me/";

  public SharedWithMeResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/shared-with-me")
  public Response sharedWithMe(@QueryParam("resource_types") Optional<String> resourceTypes,
                                       @QueryParam("sort") Optional<String> sort,
                                       @QueryParam("limit") Optional<Integer> limitParam,
                                       @QueryParam("offset") Optional<Integer> offsetParam) throws
      CedarException {

    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    //TODO: this block is present in 5 places. Unify this.
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
      throw new CedarAssertionException("You must pass in resource_types as a comma separated list!");
    }

    List<String> nodeTypeStringList = Arrays.asList(StringUtils.split(nodeTypesString, ","));
    List<CedarNodeType> nodeTypeList = new ArrayList<>();
    for (String rt : nodeTypeStringList) {
      CedarNodeType crt = CedarNodeType.forValue(rt);
      if (crt == null) {
        throw new CedarAssertionException("You passed an illegal sort type:'" + rt + "'")
            .parameter("sort_type", rt)
            .parameter("allowedValues", CedarNodeType.values());
      } else {
        nodeTypeList.add(crt);
      }
    }

    FolderServerNodeListResponse r = new FolderServerNodeListResponse();

    NodeListRequest req = new NodeListRequest();
    req.setNodeTypes(nodeTypeList);
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);

    r.setRequest(req);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    List<FolderServerNode> resources = folderSession.findSharedWithMe(nodeTypeList, limit, offset,
        sortList);

    long total = folderSession.findSharedWithMeCount(nodeTypeList);

    r.setTotalCount(total);
    r.setCurrentOffset(offset);

    r.setResources(resources);

    //TODO: indicate, that this is a view, nto a real path
    //TODO: we should do the same for the search results as well
    //r.setPathInfo(null);

    //TODO: document why it is useful
    CedarURIBuilder builder = new CedarURIBuilder(uriInfo)
        .queryParam("resource_types", resourceTypes)
        .queryParam("sort", sort)
        .queryParam("limit", limitParam)
        .queryParam("offset", offsetParam);

    String absoluteUrl = builder.build().toString();

    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, limit, offset));

    return Response.ok().entity(r).build();
  }

}