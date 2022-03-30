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

import java.io.*;
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

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param //namespaces injected by JUnit
   */
  /*@BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    new Command()
            .withParams(new CommandParams()
                    .command("kubectl create ns inside-initall"))
            .execute();

  }*/

  @Test
  @DisplayName("Create FMW Dynamic Domain in PV")
  void testFmwDynamicDomainInPV() throws IOException {

    new Command()
            .withParams(new CommandParams()
                    .command("helm version --short"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("rm -rf /home/opc/intg-test/workspace && mkdir -p /home/opc/intg-test/workspace/FMW-DockerImages && mkdir -p /home/opc/intg-test/workspace/weblogic-kubernetes-operator && chmod -R 777 /home/opc/intg-test/workspace && rm -rf /scratch/u01/DockerVolume/domains/soa-domain/*"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("GIT_SSH_COMMAND='ssh -i /home/opc/intg-test/id_rsa_github -o IdentitiesOnly=yes' git clone git@orahub.oci.oraclecorp.com:paascicd/FMW-DockerImages.git /home/opc/intg-test/workspace/FMW-DockerImages"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("git clone https://github.com/oracle/weblogic-kubernetes-operator.git /home/opc/intg-test/workspace/weblogic-kubernetes-operator"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("cd /home/opc/intg-test/workspace && mv -f FMW-DockerImages fmwsamples_bkup && mkdir /home/opc/intg-test/workspace/fmwsamples && cd fmwsamples && mkdir -p OracleSOASuite/kubernetes/3.3.0"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("cd /home/opc/intg-test/workspace/fmwsamples && cp -rf /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/README.md /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/charts /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/common /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/create-kubernetes-secrets /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/create-oracle-db-service /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/create-rcu-credentials /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/create-rcu-schema /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/create-soa-domain /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/create-weblogic-domain-credentials /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/create-weblogic-domain-pv-pvc /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/delete-domain /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/domain-lifecycle /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/elasticsearch-and-kibana /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/imagetool-scripts /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/logging-services /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/monitoring-service /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/rest /home/opc/intg-test/workspace/fmwsamples_bkup/OracleSOASuite/kubernetes/3.3.0/scaling OracleSOASuite/kubernetes/3.3.0/"))
            .execute();
    //update create-pv-pvc-inputs.yaml
    pv_pvc_util();

    //clear previous run namespaces
    new Command()
            .withParams(new CommandParams()
                    .command("kubectl delete clusterrolebinding crb-default-sa-soa-opns -n soa-opns && kubectl delete crd domains.weblogic.oracle && kubectl delete ns soa-opns && kubectl delete ns soa-domain"))
            .execute();

    //create ns & cluster bindings
    new Command()
            .withParams(new CommandParams()
                    .command("kubectl create ns soa-opns && kubectl create ns soa-domain && kubectl create clusterrolebinding crb-default-sa-soa-opns --clusterrole=cluster-admin --serviceaccount=soa-opns:default"))
            .execute();

    //install operator
    new Command()
            .withParams(new CommandParams()
                    .command("helm install op-intg-test /home/opc/intg-test/workspace/fmwsamples/OracleSOASuite/kubernetes/3.3.0/charts/weblogic-operator --namespace soa-opns --set serviceAccount=default --set 'domainNamespaces={}' --set image=ghcr.io/oracle/weblogic-kubernetes-operator:3.3.0 --wait"))
            .execute();


    new Command()
            .withParams(new CommandParams()
                    .command("kubectl get pods -n soa-opns"))
            .execute();
    //weblogic / rcu creds
    new Command().withParams(new CommandParams()
            .command("cd /home/opc/intg-test/workspace/fmwsamples/ && OracleSOASuite/kubernetes/3.3.0/create-weblogic-domain-credentials/create-weblogic-credentials.sh -u weblogic -p welcome1 -n soa-domain -d soainfra && OracleSOASuite/kubernetes/3.3.0/create-rcu-credentials/create-rcu-credentials.sh -u soainfra -p Welcome1 -a sys -q Oradoc_db1 -d soainfra -n soa-domain")).execute();
    //prepare pv-pvc yaml file

    new Command().withParams(new CommandParams()
            .command("cd /home/opc/intg-test/workspace/fmwsamples/ && ./OracleSOASuite/kubernetes/3.3.0/create-weblogic-domain-pv-pvc/create-pv-pvc.sh -i OracleSOASuite/kubernetes/3.3.0/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml -o script-output-directory && cp script-output-directory/pv-pvcs/soainfra-soa-domain-pv.yaml . && cp script-output-directory/pv-pvcs/soainfra-soa-domain-pvc.yaml .")).execute();
    //create pv pvc
    new Command().withParams(new CommandParams()
            .command("cd /home/opc/intg-test/workspace/fmwsamples && kubectl apply -f soainfra-soa-domain-pv.yaml && kubectl apply -f soainfra-soa-domain-pvc.yaml")).execute();

    /*try {
      MINUTES.sleep(300);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }*/
  }

  public void pv_pvc_util() throws IOException {
    String pv_pvc = "---\n" +
            "version: create-soainfra-pv-pvc-inputs-v1\n" +
            "baseName: soa-domain\n" +
            "domainUID: soainfra\n" +
            "namespace: soa-domain\n" +
            "weblogicDomainStorageType: HOST_PATH\n" +
            "weblogicDomainStoragePath: /scratch/u01/DockerVolume/domains/soa-domain\n" +
            "weblogicDomainStorageReclaimPolicy: Recycle\n" +
            "weblogicDomainStorageSize: 10Gi";
    File file=new File("/home/opc/intg-test/workspace/fmwsamples/OracleSOASuite/kubernetes/3.3.0/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml");
    DataOutputStream outstream= new DataOutputStream(new FileOutputStream(file,false));
    outstream.write(pv_pvc.getBytes());
    outstream.close();
  }
}