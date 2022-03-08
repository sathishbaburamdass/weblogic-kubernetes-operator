// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResouceByPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test implicit upgrade of domain resource with auximage in v8 format")
@IntegrationTest
@Disabled("Temporarily disabled due to auxiliary image 4.0 changes.")
class ItAuxDomainImplicitUpgrde {
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static LoggingFacade logger = null;
  private String domainUid = "domain1";
  private static String miiAuxiliaryImage = MII_AUXILIARY_IMAGE_NAME + "-upg";
  private final int replicaCount = 2;
  private static String adminSecretName;
  private static String encryptionSecretName;

  /**
   * Install Operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        ENCRYPION_USERNAME_DEFAULT, ENCRYPION_PASSWORD_DEFAULT);

    // build app
    assertTrue(buildAppArchive(defaultAppParams()
            .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
            .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));
  }

  /**
   * Create a domain resource with auxiliary image.
   * Use an domain.yaml file with API Version expliciltly set to v8.
   * Use the v8 style auxililiary configuration supported in WKO v3.3.x
   * Start the Operator with latest version
   * Here the webhook infra started in Operator namespace should implicitly 
   *  upgrade the domain resource to native k8s format with initContainer 
   *  configuration in ServerPod section and start the domain 
   */
  @Test
  @DisplayName("Test to implicit upgrade of v8 version of AuxDomain with webhook")
  void testImplicitAuxV8DomainUpgrade() {
    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    List<String> archiveList = Collections.singletonList(ARCHIVE_DIR + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/model.jms2.yaml");

    // create auxiliary image using imagetool command if does not exists
    logger.info("creating auxiliary image {0}:{1} using imagetool.sh ", miiAuxiliaryImage, MII_BASIC_IMAGE_TAG);
    testUntil(
          withStandardRetryPolicy,
          createAuxiliaryImage(miiAuxiliaryImage, modelList, archiveList),
          logger,
          "createAuxImage to be successful");

    // push auxiliary image to repo for multi node cluster
    logger.info("docker push image {0}:{1} to registry {2}", miiAuxiliaryImage, MII_BASIC_IMAGE_TAG,
        DOMAIN_IMAGES_REPO);
    dockerLoginAndPushImageToRegistry(miiAuxiliaryImage + ":" + MII_BASIC_IMAGE_TAG);
    String auxImage = miiAuxiliaryImage + ":" + MII_BASIC_IMAGE_TAG;
    Map<String, String> templateMap  = new HashMap();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("AUX_IMAGE", auxImage);
    templateMap.put("BASE_IMAGE", WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    templateMap.put("API_VERSION", "v8");
    Path srcDomainFile = Paths.get(RESOURCE_DIR,
        "upgrade", "auxiliary.domain.template.v8.yaml");
    Path targetDomainFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcDomainFile.toString(),
        "domain.yaml", templateMap));
    logger.info("Generated Domain Resource file {0}", targetDomainFile);

    // run kubectl to create the domain
    logger.info("Run kubectl to create the domain");
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl apply -f "
            + Paths.get(WORK_DIR + "/domain.yaml").toString());
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, "v9", domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);

    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";
    
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    StringBuffer getDomain = new StringBuffer("kubectl get domain ");
    getDomain.append(domainUid);
    getDomain.append(" -n " + domainNamespace);
    getDomain.append(" -o yaml | grep compatibility-mode-operator");
    // Get the domain in yaml format
    ExecResult dresult = assertDoesNotThrow(
                 () -> exec(new String(getDomain), true));
    logger.info("Get domain command {0}", getDomain.toString());
    logger.info("kubectl get domain returned {0}", dresult.toString());
    assertTrue(dresult.stdout().contains("compatibility-mode-operator"), "Failed to implicitly upgrade v8 aux domain");
    
    //create router for admin service on OKD
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodName, adminSvcExtHost);
    // check the sample app is accessible from managed servers
    logger.info("Check and wait for the sample application to become ready");
    for (int i = 1; i <= replicaCount; i++) {
      int index = i;
      testUntil(withStandardRetryPolicy,
          () -> appAccessibleInPod(domainNamespace, managedServerPrefix + index, "8001",
              "sample-war/index.jsp", MII_APP_RESPONSE_V1 + index),
          logger,
          "application {0} is running on pod {1} in namespace {2}",
          "sample-war",
          managedServerPrefix + index,
          domainNamespace);
    }
  }

  /**
   * Cleanup images.
   */
  public void tearDownAll() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
      // delete images
      if (miiAuxiliaryImage != null) {
        deleteImage(miiAuxiliaryImage);
      }
    }
  }

  /**
   * Check Configured JMS Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  private static void checkConfiguredJMSresouce(String domainNamespace, String adminServerPodName,
                                               String adminSvcExtHost) {
    verifyConfiguredSystemResource(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JMSSystemResources", "TestClusterJmsModule2", "200");
  }

  /**
   * Check Configured JDBC Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  public static void checkConfiguredJDBCresouce(String domainNamespace, String adminServerPodName,
                                                String adminSvcExtHost) {

    verifyConfiguredSystemResouceByPath(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JDBCSystemResources/TestDataSource/JDBCResource/JDBCDriverParams",
        "jdbc:oracle:thin:@\\/\\/xxx.xxx.x.xxx:1521\\/ORCLCDB");
  }
}
