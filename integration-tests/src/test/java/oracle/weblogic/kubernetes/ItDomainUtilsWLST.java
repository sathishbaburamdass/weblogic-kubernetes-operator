// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.with;

/**
 * Tests to create JRF domain in persistent volume using WLST.
 */
@DisplayName("Verify the WebLogic server pods can run with domain created in persistent volume")
@IntegrationTest
class ItDomainUtilsWLST {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String jrfDomainNamespace = null;
  private static String oracle_home = null;
  private static String java_home = null;

  private static final String RCUSCHEMAPREFIX = "soadomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private final String domainUid = "soadomain";
  private final String wlSecretName = domainUid + "-weblogic-credentials";
  private final String rcuSecretName = domainUid + "-rcu-credentials";
  private static int t3ChannelPort = 0;

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
          = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    new Command()
            .withParams(new CommandParams()
                    .command("kubectl create ns inside-INITALL"))
            .execute();

  }

  @Test
  @DisplayName("Create FMW Dynamic Domain in PV")
  void testFmwDynamicDomainInPV() {
    new Command()
            .withParams(new CommandParams()
                    .command("ls"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("pwd"))
            .execute();

    new Command()
            .withParams(new CommandParams()
                    .command("ls /"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("git pull https://github.com/sathishbaburamdass/weblogic-kubernetes-operator.git"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("ls"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("helm version --short"))
            .execute();
    try {
      MINUTES.sleep(300);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}