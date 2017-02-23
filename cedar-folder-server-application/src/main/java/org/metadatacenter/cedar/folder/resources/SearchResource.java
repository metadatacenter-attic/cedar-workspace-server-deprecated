package org.metadatacenter.cedar.folder.resources;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListQueryTypeDetector;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.util.http.CedarURIBuilder;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.PagedSortedTypedSearchQuery;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
                               @QueryParam(QP_SORT) Optional<String> sortParam,
                               @QueryParam(QP_LIMIT) Optional<Integer> limitParam,
                               @QueryParam(QP_OFFSET) Optional<Integer> offsetParam,
                               @QueryParam(QP_SHARING) Optional<String> sharing) throws CedarException {

    CedarRequestContext c = CedarRequestContextFactory.fromRequest(request);
    c.must(c.user()).be(LoggedIn);

    PagedSortedTypedSearchQuery pagedSearchQuery = new PagedSortedTypedSearchQuery(
        cedarConfig.getFolderRESTAPI().getPagination())
        .q(q)
        .resourceTypes(resourceTypes)
        .derivedFromId(derivedFromId)
        .sort(sortParam)
        .limit(limitParam)
        .offset(offsetParam);
    pagedSearchQuery.validate();

    int limit = pagedSearchQuery.getLimit();
    int offset = pagedSearchQuery.getOffset();
    List<String> sortList = pagedSearchQuery.getSortList();
    List<CedarNodeType> nodeTypeList = pagedSearchQuery.getNodeTypeList();

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
    } else {
      throw new CedarProcessingException("Search type not supported by folder server")
          .parameter("resolvedSearchType", nlqt.getValue());
    }

    r.setTotalCount(total);
    r.setCurrentOffset(offset);

    r.setResources(resources);

    CedarURIBuilder builder = new CedarURIBuilder(uriInfo)
        .queryParam(QP_RESOURCE_TYPES, resourceTypes)
        .queryParam(QP_SORT, sortParam)
        .queryParam(QP_LIMIT, limitParam)
        .queryParam(QP_OFFSET, offsetParam);

    String absoluteUrl = builder.build().toString();

    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, limit, offset));

    return Response.ok().entity(r).build();
  }

}