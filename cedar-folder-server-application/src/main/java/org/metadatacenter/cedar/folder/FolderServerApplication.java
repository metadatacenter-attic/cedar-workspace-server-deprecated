package org.metadatacenter.cedar.folder;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.cedar.folder.health.FolderServerHealthCheck;
import org.metadatacenter.cedar.folder.resources.*;
import org.metadatacenter.cedar.util.dw.CedarDropwizardApplicationUtil;
import org.metadatacenter.config.CedarConfig;

public class FolderServerApplication extends Application<FolderServerConfiguration> {

  private static CedarConfig cedarConfig;

  public static void main(String[] args) throws Exception {
    new FolderServerApplication().run(args);
  }

  @Override
  public String getName() {
    return "folder-server";
  }

  @Override
  public void initialize(Bootstrap<FolderServerConfiguration> bootstrap) {
    CedarDropwizardApplicationUtil.setupKeycloak();

    cedarConfig = CedarConfig.getInstance();
  }

  @Override
  public void run(FolderServerConfiguration configuration, Environment environment) {
    final IndexResource index = new IndexResource();
    environment.jersey().register(index);

    environment.jersey().register(new AccessibleNodesResource(cedarConfig));
    environment.jersey().register(new CommandResource(cedarConfig));
    environment.jersey().register(new FolderContentsResource(cedarConfig));
    environment.jersey().register(new FoldersResource(cedarConfig));
    environment.jersey().register(new NodesResource(cedarConfig));
    environment.jersey().register(new ResourcesResource(cedarConfig));
    environment.jersey().register(new SharedWithMeResource(cedarConfig));
    environment.jersey().register(new UsersResource(cedarConfig));

    final FolderServerHealthCheck healthCheck = new FolderServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);

    CedarDropwizardApplicationUtil.setupEnvironment(environment);
  }
}
