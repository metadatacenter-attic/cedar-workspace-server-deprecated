package org.metadatacenter.cedar.folder;

import io.dropwizard.setup.Environment;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.folder.health.FolderServerHealthCheck;
import org.metadatacenter.cedar.folder.resources.*;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplication;
import org.metadatacenter.model.ServerName;

public class FolderServerApplication extends CedarMicroserviceApplication<FolderServerConfiguration> {

  public static void main(String[] args) throws Exception {
    new FolderServerApplication().run(args);
  }

  @Override
  protected ServerName getServerName() {
    return ServerName.FOLDER;
  }

  @Override
  public void initializeApp() {
    CedarDataServices.initializeFolderServices(cedarConfig);
  }

  @Override
  public void runApp(FolderServerConfiguration configuration, Environment environment) {
    final IndexResource index = new IndexResource();
    environment.jersey().register(index);

    environment.jersey().register(new CommandResource(cedarConfig));
    environment.jersey().register(new FolderContentsResource(cedarConfig));
    environment.jersey().register(new FoldersResource(cedarConfig));
    environment.jersey().register(new NodesResource(cedarConfig));
    environment.jersey().register(new ResourcesResource(cedarConfig));
    environment.jersey().register(new SearchResource(cedarConfig));
    environment.jersey().register(new UsersResource(cedarConfig));

    final FolderServerHealthCheck healthCheck = new FolderServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);
  }
}
