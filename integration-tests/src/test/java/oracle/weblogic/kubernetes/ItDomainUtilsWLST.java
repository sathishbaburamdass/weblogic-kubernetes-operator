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
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
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
  @BeforeAll
  public static void initAll() {
    System.out.println("****----Inside Init All domain deployment via Sample Scripts****----");
    System.out.println("IS UPPERSTACK : "+ TestConstants.IS_UPPERSTACK);
  }

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
                    .command("kubectl delete clusterrolebinding crb-default-sa-soa-opns -n soa-opns && kubectl delete crd domains.weblogic.oracle && kubectl delete ns soa-opns && kubectl delete ns soa-domain && kubectl delete pv soainfra-soa-domain-pv"))
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
    //prepare db
    db_yaml_util();

    new Command().withParams(new CommandParams()
            .command("cd /home/opc/intg-test/workspace/fmwsamples/OracleSOASuite/kubernetes/3.3.0/create-oracle-db-service/common/ && kubectl apply -f oracle.db.yaml -n soa-domain")).execute();
    //check oracledb-0 pod is up

    //checkPodReady("oracledb-0", "oracledb", "soa-domain");
    try {
      MINUTES.sleep(12);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    //prepare rcu
    new Command().withParams(new CommandParams()
            .command("cd /home/opc/intg-test/workspace/fmwsamples/ && ./OracleSOASuite/kubernetes/3.3.0/create-rcu-schema/create-rcu-schema.sh -s soainfra -t soa -d oracledb.soa-domain:1521/oracledbpdb.us.oracle.com -i fmw-paas-sandbox-cert-docker.dockerhub-phx.oci.oraclecorp.com/oracle/soasuite:12.2.1.4-jdk8-ol7-220216.1814 -n soa-domain -q Oradoc_db1 -r Welcome1 -c SOA_PROFILE_TYPE=SMALL,HEALTHCARE_INTEGRATION=NO -l 2000")).execute();
    //ingress
    //new Command().withParams(new CommandParams()
    //            .command("helm install soa-domain-ingress /home/opc/intg-test/workspace/fmwsamples/OracleSOASuite/kubernetes/3.3.0/charts/ingress-per-domain --namespace soa-domain --set type=NGINX --set domainType=soa --set wlsDomain.domainType=soa --set wlsDomain.operatorVersion=3.3.0 --set wlsDomain.domainUID=soainfra --set wlsDomain.productID=soa --set wlsDomain.adminServerName=AdminServer --set wlsDomain.soaClusterName=soa_cluster --set wlsDomain.adminServerPort=7001 --set wlsDomain.soaManagedServerPort=8001 --set wlsDomain.adminServerSSLPort= --set wlsDomain.soaManagedServerSSLPort=8002 --set wlsDomain.osbClusterName= --set wlsDomain.osbManagedServerSSLPort= --set wlsDomain.oamClusterName=soa_cluster --set wlsDomain.oamManagedServerPort=8001 --set wlsDomain.oamManagedServerSSLPort=8002 --set wlsDomain.policyClusterName= --set wlsDomain.policyManagedServerPort= --set wlsDomain.policyManagedServerSSLPort=8002 --set wlsDomain.ibrClusterName= --set wlsDomain.ibrManagedServerPort= --set wlsDomain.ibrManagedServerSSLPort=8002 --set wlsDomain.ucmClusterName= --set wlsDomain.ucmManagedServerPort= --set wlsDomain.ucmManagedServerSSLPort=8002 --set wlsDomain.ipmClusterName= --set wlsDomain.ipmManagedServerPort= --set wlsDomain.captureClusterName= --set wlsDomain.captureManagedServerPort= --set wlsDomain.wccadfClusterName= --set wlsDomain.wccadfManagedServerPort= --set wlsDomain.clusterName=soa_cluster --set wlsDomain.managedServerPort=8001 --set wlsDomain.portletClusterName= --set wlsDomain.portletManagedServerPort= --set wlsDomain.templateEnabled= --set wlsDomain.checkIfSamplesIsAccordingToNewStructure=true --set wlsDomain.wcsitesClusterName=soa_cluster --set wlsDomain.wcsitesManagedServerPort=8001 --set sslType=NONSSL --set tls=NONSSL --set nginx.hostname=certautomin.subnet3ad3phx.paasinfratoophx.oraclevcn.com")).execute();

    //create domain
    new Command().withParams(new CommandParams()
                    .command("cd /home/opc/intg-test/workspace/fmwsamples/ && ./OracleSOASuite/kubernetes/3.3.0/create-soa-domain/domain-home-on-pv/create-domain.sh -i create-domain-inputs.yaml -o script-output-domain-directory")).execute();
    new Command().withParams(new CommandParams()
                    .command("helm upgrade op-501-02250727 /home/opc/intg-test/workspace/fmwsamples/OracleSOASuite/kubernetes/3.3.0/charts/weblogic-operator --namespace soa-opns --reuse-values --set 'domainNamespaces={soa-domain}' --wait")).execute();
    new Command().withParams(new CommandParams()
                        .command("cd /home/opc/intg-test/workspace/fmwsamples/script-output-domain-directory/weblogic-domains/soainfra/ && kubectl apply -f domain.yaml -n soa-domain")).execute();

    try {
      MINUTES.sleep(60);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
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

  public void domain_yaml_util() throws IOException {
    String pv_pvc = "---\n" +
            "version: create-weblogic-sample-domain-inputs-v1\n" +
            "sslEnabled: false\n" +
            "adminPort: 7001\n" +
            "adminServerSSLPort: 7002\n" +
            "adminServerName: AdminServer\n" +
            "domainUID: soainfra\n" +
            "domainType: soa\n" +
            "domainHome: /u01/oracle/user_projects/domains/soainfra\n" +
            "serverStartPolicy: IF_NEEDED\n" +
            "configuredManagedServerCount: 5\n" +
            "initialManagedServerReplicas: 2\n" +
            "soaClusterName: soa_cluster\n" +
            "soaManagedServerNameBase: soa_server\n" +
            "soaManagedServerPort: 8001\n" +
            "soaManagedServerSSLPort: 8002\n" +
            "osbClusterName: osb_cluster\n" +
            "osbManagedServerNameBase: osb_server\n" +
            "osbManagedServerPort: 9001\n" +
            "osbManagedServerSSLPort: 9002\n" +
            "image: fmw-paas-sandbox-cert-docker.dockerhub-phx.oci.oraclecorp.com/oracle/soasuite:12.2.1.4-jdk8-ol7-220216.1814\n" +
            "imagePullPolicy: IfNotPresent\n" +
            "productionModeEnabled: true\n" +
            "weblogicCredentialsSecretName: soainfra-weblogic-credentials\n" +
            "includeServerOutInPodLog: true\n" +
            "logHome: /u01/oracle/user_projects/domains/logs/soainfra\n" +
            "httpAccessLogInLogHome: true\n" +
            "t3ChannelPort: 30012\n" +
            "exposeAdminT3Channel: false\n" +
            "adminNodePort: 30701\n" +
            "exposeAdminNodePort: false\n" +
            "namespace: soa-domain\n" +
            "javaOptions: -Dweblogic.StdoutDebugEnabled=false -Dweblogic.ssl.Enabled=true -Dweblogic.security.SSL.ignoreHostnameVerification=true\n" +
            "persistentVolumeClaimName: soainfra-soa-domain-pvc\n" +
            "domainPVMountPath: /u01/oracle/user_projects\n" +
            "createDomainScriptsMountPath: /u01/weblogic\n" +
            "createDomainScriptName: create-domain-job.sh\n" +
            "createDomainFilesDir: wlst\n" +
            "rcuSchemaPrefix: soainfra\n" +
            "rcuDatabaseURL: oracledb.soa-domain:1521/oracledbpdb.us.oracle.com\n" +
            "rcuCredentialsSecret: soainfra-rcu-credentials\n" +
            "persistentStore: jdbc\n" +
            "imagePullSecretName: phxregcred\n" +
            "t3PublicAddress: 100.111.151.24\n" +
            "loadBalancerHostName: 100.111.151.24\n" +
            "loadBalancerPortNumber: 32115\n" +
            "loadBalancerProtocol: https";
    File file=new File("/home/opc/intg-test/workspace/fmwsamples/OracleSOASuite/kubernetes/3.3.0/create-soa-domain/domain-home-on-pv/create-domain-inputs.yaml");
    DataOutputStream outstream= new DataOutputStream(new FileOutputStream(file,false));
    outstream.write(pv_pvc.getBytes());
    outstream.close();
  }

  public void db_yaml_util() throws IOException {
    String pv_pvc = "apiVersion: v1\n" +
            "kind: Service\n" +
            "metadata:\n" +
            "  name: oracledb\n" +
            "  labels:\n" +
            "    app: oracledb\n" +
            "  namespace: soa-domain\n" +
            "spec:\n" +
            "  ports:\n" +
            "    - port: 1521\n" +
            "      name: server-port\n" +
            "    - port: 5500\n" +
            "      name: em-port\n" +
            "  clusterIP: None\n" +
            "  selector:\n" +
            "    app: oracledb\n" +
            "---\n" +
            "#apiVersion: apps/v1beta1\n" +
            "apiVersion: apps/v1\n" +
            "kind: StatefulSet\n" +
            "metadata:\n" +
            "  name: oracledb\n" +
            "  namespace: soa-domain\n" +
            "spec:\n" +
            "  selector:\n" +
            "    matchLabels:\n" +
            "      app: oracledb\n" +
            "  serviceName: \"oracledb\"\n" +
            "  replicas: 1\n" +
            "  template:\n" +
            "    metadata:\n" +
            "      labels:\n" +
            "        app: oracledb\n" +
            "    spec:\n" +
            "      terminationGracePeriodSeconds: 30\n" +
            "      imagePullSecrets:\n" +
            "        - name: regcred\n" +
            "      containers:\n" +
            "        - name: oracledb\n" +
            "          image: container-registry.oracle.com/database/enterprise:12.2.0.1-slim\n" +
            "          imagePullPolicy: IfNotPresent\n" +
            "          ports:\n" +
            "            - containerPort: 1521\n" +
            "              name: server-port\n" +
            "            - containerPort: 5500\n" +
            "              name: em-port\n" +
            "          env:\n" +
            "            - name: DB_SID\n" +
            "              value: oracledb\n" +
            "            - name: DB_PASSWD\n" +
            "              value: Oradoc_db1\n" +
            "            - name: DB_PDB\n" +
            "              value: oracledbpdb\n" +
            "            - name: DB_DOMAIN\n" +
            "              value: us.oracle.com\n" +
            "            - name: DB_BUNDLE\n" +
            "              value: basic\n" +
            "          readinessProbe:\n" +
            "            exec:\n" +
            "              command:\n" +
            "                - grep\n" +
            "                - Done ! The database is ready for use .\n" +
            "                - /home/oracle/setup/log/setupDB.log\n" +
            "            initialDelaySeconds: 300\n" +
            "            periodSeconds: 5";
    File file=new File("/home/opc/intg-test/workspace/fmwsamples/OracleSOASuite/kubernetes/3.3.0/create-oracle-db-service/common/oracle.db.yaml");
    DataOutputStream outstream= new DataOutputStream(new FileOutputStream(file,false));
    outstream.write(pv_pvc.getBytes());
    outstream.close();
  }
}