package org.metadatacenter.cedar.workspace.resources;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.VersionServiceSession;
import org.metadatacenter.server.permissions.CurrentUserPermissionUpdaterForWorkspaceFolder;
import org.metadatacenter.server.permissions.CurrentUserPermissionUpdaterForWorkspaceResource;
import org.metadatacenter.server.security.model.auth.FolderWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.auth.NodeWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.auth.ResourceWithCurrentUserPermissions;

public class AbstractFolderServerResource extends CedarMicroserviceResource {

  protected AbstractFolderServerResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  protected void decorateNodeWithCurrentUserPermissions(CedarRequestContext c,
                                                        NodeWithCurrentUserPermissions node) {
    if (node.getType() == CedarNodeType.FOLDER) {
      decorateFolderWithCurrentUserPermissions(c, (FolderWithCurrentUserPermissions) node);
    } else {
      decorateResourceWithCurrentUserPermissions(c, (ResourceWithCurrentUserPermissions) node);
    }
  }

  protected void decorateFolderWithCurrentUserPermissions(CedarRequestContext c,
                                                          FolderWithCurrentUserPermissions folder) {
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);
    CurrentUserPermissionUpdater cupu = CurrentUserPermissionUpdaterForWorkspaceFolder.get(permissionSession, folder);
    cupu.update(folder.getCurrentUserPermissions());
  }

  protected void decorateResourceWithCurrentUserPermissions(CedarRequestContext c,
                                                            ResourceWithCurrentUserPermissions resource) {
    PermissionServiceSession permissionSession = CedarDataServices.getPermissionServiceSession(c);
    VersionServiceSession versionSession = CedarDataServices.getVersionServiceSession(c);
    CurrentUserPermissionUpdater cupu = CurrentUserPermissionUpdaterForWorkspaceResource.get(permissionSession,
        versionSession, cedarConfig, resource);
    cupu.update(resource.getCurrentUserPermissions());

  }
}
