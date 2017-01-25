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
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListQueryTypeDetector;
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

import static org.metadatacenter.constant.CedarQueryParameters.*;
import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource extends AbstractFolderServerResource {

  public SearchResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("/search")
  public Response sharedWithMe(@QueryParam(QP_Q) Optional<String> q,
                               @QueryParam(QP_RESOURCE_TYPES) Optional<String> resourceTypes,
                               @QueryParam(QP_DERIVED_FROM_ID) Optional<String> derivedFromId,
                               @QueryParam(QP_SORT) Optional<String> sort,
                               @QueryParam(QP_LIMIT) Optional<Integer> limitParam,
                               @QueryParam(QP_OFFSET) Optional<Integer> offsetParam,
                               @QueryParam(QP_SHARING) Optional<String> sharing) throws CedarException {

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
    req.setQ(q.orElse(null));
    req.setDerivedFromId(derivedFromId.orElse(null));

    r.setRequest(req);

    NodeListQueryType nlqt = NodeListQueryTypeDetector.detect(q, derivedFromId, sharing);
    r.setNodeListQueryType(nlqt);

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    List<FolderServerNode> resources = null;
    long total = 0;

    if (nlqt == NodeListQueryType.VIEW_SHARED_WITH_ME) {
      resources = folderSession.viewSharedWithMe(nodeTypeList, limit, offset, sortList);
      total = folderSession.viewSharedWithMeCount(nodeTypeList);
    } else if (nlqt == NodeListQueryType.VIEW_ALL) {
      resources = folderSession.viewAll(nodeTypeList, limit, offset, sortList);
      total = folderSession.viewAllCount(nodeTypeList);
    }


    r.setTotalCount(total);
    r.setCurrentOffset(offset);

    r.setResources(resources);

    //TODO: indicate, that this is a view, not a real path
    //TODO: we should do the same for the search results as well
    //r.setPathInfo(null);

    //TODO: document why it is useful
    CedarURIBuilder builder = new CedarURIBuilder(uriInfo)
        .queryParam(QP_RESOURCE_TYPES, resourceTypes)
        .queryParam(QP_SORT, sort)
        .queryParam(QP_LIMIT, limitParam)
        .queryParam(QP_OFFSET, offsetParam);

    String absoluteUrl = builder.build().toString();

    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, limit, offset));

    return Response.ok().entity(r).build();
  }

}