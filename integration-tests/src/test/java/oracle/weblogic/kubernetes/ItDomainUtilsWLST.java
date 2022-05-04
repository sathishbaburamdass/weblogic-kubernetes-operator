// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.DisplayName;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * Util Class to deploy given domain using fmw samples repo
 */

@DisplayName("Create given domain using sample repo")
@IntegrationTest
class ItDomainUtilsWLST {
  static Map<String, String> productIdMap = new HashMap<>();
  static Map<String, String> productDirectoryMap = new HashMap<>();
  static Map<String, String> managedServerBaseNames = new HashMap<>();
  static{
    productDirectoryMap.put("SOA", "OracleSOASuite");
    productDirectoryMap.put("WCP", "OracleWebCenterPortal");
    productDirectoryMap.put("WC-SITES", "OracleWebCenterSites");
    productDirectoryMap.put("OIG", "OracleIdentityGovernance");
    productDirectoryMap.put("OAM", "OracleAccessManagement");
    productDirectoryMap.put("WCC", "OracleWebCenterContent");

    productIdMap.put("SOA", "soa");
    productIdMap.put("WCP", "wcp");
    productIdMap.put("WC-SITES", "wcsites");
    productIdMap.put("OIG", "oim");
    productIdMap.put("OAM", "access");
    productIdMap.put("WCC", "wcc");

    managedServerBaseNames.put("SOA", "soa-server");
    managedServerBaseNames.put("WCC", "ucm-server");
    managedServerBaseNames.put("OAM", "oam-server");
    managedServerBaseNames.put("OIG", "oim-server");
    managedServerBaseNames.put("WCP", "wcpserver");
    managedServerBaseNames.put("WC-SITES", "wcsites-server");
  }

  private static String connectionURL = "";
  private static String dbImage = "";
  private static String productImage = "";

  private static LoggingFacade logger = null;

  private static final long TIMESTAMP = System.currentTimeMillis();
  public static final String domainUid = TestConstants.FMW_DOMAIN_TYPE + "infra";
  public static final String domainNS = TestConstants.FMW_DOMAIN_TYPE + "-ns-" + TIMESTAMP;
  public static final String operatorNS = "opt-ns-" + TIMESTAMP;
  public static String managedServerPrefix = "";
  public static String adminServerPodName = "";
  public static String clusterName = "";
  public static Boolean IS_DOMAIN_DEPLOYED = false;
  public static final int managedServerReplicaCount = 2;
  private static final String wlSecretName = domainUid + "-weblogic-credentials";
  private static final String rcuSecretName = domainUid + "-rcu-credentials";
  private static final String workSpacePath = "/home/opc/intg-test/workspace/fmwsamples/";
  private static final String workSpaceBasePath = "/home/opc/intg-test/workspace/";
  private static final String projectDir = System.getProperty("user.dir");
  private static final String OPT_VERSION = TestConstants.OPERATOR_VERSION;
  private static final String prodID = getProdName(productIdMap,TestConstants.FMW_DOMAIN_TYPE);
  private static final String prodDirectory = productDirectoryMap.get(prodID);
  private static List<String> listOfDirInProdDir;
  private static List<String> operatorDir;


  public static void deployDomainUsingSampleRepo() throws IOException {
    logger = getLogger();
    System.out.println("****----Inside Init All : Domain deployment via Sample Scripts****----");
    System.out.println("IS UPPERSTACK : "+ TestConstants.IS_UPPERSTACK);

    //create ns & cluster bindings
    prepareENV();
    copyRepos();


    //update create-pv-pvc-inputs.yaml
    pv_pvc_util();

    //install operator
    installOperator();

    //prepare db
    prepareDB();
    prepareRCU();

    //create domain
    domain_yaml_util();
    createDomain();
    checkPods();

  }

  public static void pv_pvc_util() throws IOException {
    String nfs_folder = TestConstants.FMW_DOMAIN_TYPE + "-store-" + TIMESTAMP;
    nfs_folder = "/scratch/u01/DockerVolume/domains/" + nfs_folder;
    //mkdir in nfs domain share
    new Command().withParams(new CommandParams()
            .command("mkdir "+nfs_folder)).execute();
    new Command().withParams(new CommandParams()
            .command("chmod -R 777 "+nfs_folder)).execute();

    String pv_pvc = "---\n" +
            "version: create-"+domainUid+"-pv-pvc-inputs-v1\n" +
            "baseName: domain\n" +
            "domainUID: "+domainUid+"\n" +
            "namespace: "+domainNS+"\n" +
            "weblogicDomainStorageType: HOST_PATH\n" +
            "weblogicDomainStoragePath: "+ nfs_folder + "\n" +
            "weblogicDomainStorageReclaimPolicy: Recycle\n" +
            "weblogicDomainStorageSize: 10Gi";
    File file;
    if(listOfDirInProdDir.contains("create-weblogic-domain-pv-pvc")) {
       file = new File(workSpacePath + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml");
    }else {
       file = new File(workSpaceBasePath + "weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml");
    }
    DataOutputStream outstream= new DataOutputStream(new FileOutputStream(file,false));
    outstream.write(pv_pvc.getBytes());
    outstream.close();
    String pvName = domainUid+"-domain-pv.yaml";
    String pvcName = domainUid+"-domain-pvc.yaml";
    if(listOfDirInProdDir.contains("create-weblogic-domain-pv-pvc"))
        new Command().withParams(new CommandParams()
            .command("cd "+workSpacePath+" && ./"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-pv-pvc/create-pv-pvc.sh -i "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml -o script-output-directory && kubectl apply -f script-output-directory/pv-pvcs/"+pvName+" && kubectl apply -f script-output-directory/pv-pvcs/"+pvcName)).execute();
    else
        new Command().withParams(new CommandParams()
              .command("cd "+workSpaceBasePath+" && ./weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc.sh -i weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain-pv-pvc/create-pv-pvc-inputs.yaml -o script-output-directory && kubectl apply -f script-output-directory/pv-pvcs/"+pvName+" && kubectl apply -f script-output-directory/pv-pvcs/"+pvcName)).execute();

  }

  public static void domain_yaml_util() throws IOException{
    logger.info("---Manipulate Domain Yaml File---");
    File file = null;
    List<String> domainYamlOrgiValue = null;
    BufferedReader reader;
    FileWriter writer;
    String content = "";

    if(operatorDir.contains("weblogic-operator")){
        file=new File(workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain/domain-home-on-pv/create-domain-inputs.yaml");
        domainYamlOrgiValue = Files.readAllLines((Paths.get(workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain/domain-home-on-pv/create-domain-inputs.yaml")));
    }else {
        file=new File(workSpaceBasePath+"weblogic-kubernetes-operator/kubernetes/samples/scripts/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain/domain-home-on-pv/create-domain-inputs.yaml");
        domainYamlOrgiValue = Files.readAllLines((Paths.get(workSpaceBasePath+"weblogic-kubernetes-operator/kubernetes/samples/scripts/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain/domain-home-on-pv/create-domain-inputs.yaml")));
    }

      reader = new BufferedReader(new FileReader(file));
      String line = reader.readLine();

      while (line != null)
      {
        content = content + line + System.lineSeparator();
        line = reader.readLine();
      }

      //logger.info(content);
      //start - find and replace domain yaml values


      Map<String,String> replaceDomainYamlMap = new HashMap<>();
      replaceDomainYamlMap.put("rcuCredentialsSecret: ","rcuCredentialsSecret: "+rcuSecretName);
      replaceDomainYamlMap.put("weblogicCredentialsSecretName: ","weblogicCredentialsSecretName: "+wlSecretName);
      replaceDomainYamlMap.put("rcuSchemaPrefix: ","rcuSchemaPrefix: "+domainUid);
      replaceDomainYamlMap.put("namespace: ","namespace: "+domainNS);
      replaceDomainYamlMap.put("initialManagedServerReplicas: ","initialManagedServerReplicas: "+managedServerReplicaCount);
      replaceDomainYamlMap.put("rcuDatabaseURL: ","rcuDatabaseURL: "+connectionURL);
      replaceDomainYamlMap.put("persistentVolumeClaimName: ","persistentVolumeClaimName: "+domainUid+"-domain-pvc");
      replaceDomainYamlMap.put("image: ","image: "+productImage);

      Pattern pattern;
      Matcher matcher;
      for(Map.Entry<String,String> e : replaceDomainYamlMap.entrySet()){
        String domainYamlProp = e.getKey();
        pattern = Pattern.compile(domainYamlProp+"?");

        for(String domainYamlByLine : domainYamlOrgiValue){
          matcher = pattern.matcher(domainYamlByLine);
          if(matcher.find()){
            content = content.replace(domainYamlByLine,e.getValue());
          }

        }
      }
      writer = new FileWriter(file);
      writer.write(content);
      reader.close();
      writer.close();

  }

  public static void copyRepos() throws IOException {
    new Command()
            .withParams(new CommandParams()
                    .command("rm -rf /home/opc/intg-test/workspace && mkdir -p /home/opc/intg-test/workspace/FMW-DockerImages && mkdir -p /home/opc/intg-test/workspace/weblogic-kubernetes-operator && mkdir -p /home/opc/intg-test/workspace/k8spipeline && chmod -R 777 /home/opc/intg-test/workspace"))
            .execute();

    new Command()
            .withParams(new CommandParams()
                    .command("GIT_SSH_COMMAND='ssh -i /home/opc/intg-test/id_rsa_github -o IdentitiesOnly=yes' git clone git@orahub.oci.oraclecorp.com:paascicd/FMW-DockerImages.git /home/opc/intg-test/workspace/FMW-DockerImages"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("git clone --branch release/3.3 https://github.com/oracle/weblogic-kubernetes-operator.git /home/opc/intg-test/workspace/weblogic-kubernetes-operator"))
            .execute();
    new Command()
            .withParams(new CommandParams()
                    .command("cd /home/opc/intg-test/workspace && mv -f FMW-DockerImages fmwsamples_bkup && mkdir /home/opc/intg-test/workspace/fmwsamples && cd fmwsamples && mkdir -p "+prodDirectory+"/kubernetes/"+OPT_VERSION+""))
            .execute();
    try {
      new Command().withParams(new CommandParams()
              .command("cd /home/opc/intg-test/workspace/fmwsamples && cp -rf /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/README.md /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/charts /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/common /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-kubernetes-secrets /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-oracle-db-service /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-rcu-credentials /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-rcu-schema /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-" + TestConstants.FMW_DOMAIN_TYPE + "-domain /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-weblogic-domain-credentials /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-weblogic-domain-pv-pvc /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/delete-domain /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/domain-lifecycle /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/elasticsearch-and-kibana /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/imagetool-scripts /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/logging-services /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/monitoring-service /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/rest /home/opc/intg-test/workspace/fmwsamples_bkup/" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/scaling " + prodDirectory + "/kubernetes/" + OPT_VERSION + "/")).execute();
    }catch (Exception e){
      logger.info(e.getMessage());
    }finally {
      new Command().withParams(new CommandParams()
              .command("cd "+workSpacePath+prodDirectory+"/kubernetes/" + OPT_VERSION+" && ls > dirs.txt && ls charts/ > optDir.txt")).execute();
      listOfDirInProdDir = Files.readAllLines((Paths.get(workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/dirs.txt")));
      operatorDir = Files.readAllLines((Paths.get(workSpacePath+prodDirectory+"/kubernetes/"+OPT_VERSION+"/optDir.txt")));
      logger.info("List of Directories in PROD SAMPLES : "+listOfDirInProdDir);
      if(!operatorDir.contains("weblogic-operator")){
        new Command().withParams(new CommandParams()
                .command("cd "+workSpacePath+" && cp -rf "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-"+TestConstants.FMW_DOMAIN_TYPE+"-domain "+workSpaceBasePath+"weblogic-kubernetes-operator/kubernetes/samples/scripts/")).execute();
      }
    }
  }
  
  public static void prepareENV(){
    new Command().withParams(new CommandParams()
            .command("kubectl create ns "+operatorNS+" && kubectl create ns "+domainNS+" && kubectl create clusterrolebinding crb-default-sa-"+operatorNS+" --clusterrole=cluster-admin --serviceaccount="+operatorNS+":default")).execute();
    connectionURL = "oracledb."+domainNS+":1521/oracledbpdb.us.oracle.com";
    dbImage = "container-registry.oracle.com/database/enterprise:12.2.0.1-slim";
    productImage = TestConstants.FMWINFRA_IMAGE_NAME+":"+TestConstants.FMWINFRA_IMAGE_TAG;
    managedServerPrefix = domainUid+"-"+managedServerBaseNames.get(prodID);
    adminServerPodName = domainUid + "-adminserver";
    clusterName = managedServerBaseNames.get(prodID).replace("-","_").replace("server","cluster");
    logger.info("--print domain variables---");
    logger.info(managedServerPrefix);
    logger.info(adminServerPodName);
    logger.info(clusterName);
  }

  public static void prepareDB(){

    File file=new File(projectDir+"/integration-tests/src/resources/configfiles/upperstack/oracle-db.yaml");
    BufferedReader reader = null;
    FileWriter writer = null;
    String content = "";
    try{
      reader = new BufferedReader(new FileReader(file));
      String line = reader.readLine();

      while (line != null)
      {
        content = content + line + System.lineSeparator();
        line = reader.readLine();
      }
      content = content.replace("%DB_NAME%","oracledb");
      content = content.replace("%DB_IMAGE%",dbImage);
      content = content.replace("%DB_PASSWORD%","Oradoc_db1");
      content = content.replace("%DB_NAMESPACE%",domainNS);
      content = content.replace("%DB_SECRET%","regcred");
      content = content.replace("%DB_PDB_NAME%","oracledbpdb");
      content = content.replace("%DB_DOMAIN_NAME%","us.oracle.com");
      content = content.replace("%DB_IF_READY_PRINT_DONE_OR_COMPLETE%","Done ! The database is ready for use .");
      content = content.replace("%DB_LOG_LOCATION%","/home/oracle/setup/log/setupDB.log");
      writer = new FileWriter(file);
      writer.write(content);
      reader.close();
      writer.close();
    }catch(IOException e){
      e.printStackTrace();
    }

    new Command().withParams(new CommandParams()
            .command("cd "+projectDir+"/integration-tests/src/resources/configfiles/upperstack/ && kubectl apply -f oracle-db.yaml -n "+domainNS)).execute();
    new Command().withParams(new CommandParams()
            .command("while [[ $(kubectl get pods oracledb-0 -n "+domainNS+" -o 'jsonpath={..status.conditions[?(@.type==\"Ready\")].status}') != \"True\" ]]; do echo \"waiting for DB pod to be ready\" && sleep 10; done")).execute();

  }

  public static void prepareRCU(){
    if(listOfDirInProdDir.contains("create-weblogic-domain-credentials") && listOfDirInProdDir.contains("create-rcu-credentials")){
      new Command().withParams(new CommandParams()
              .command("cd "+workSpacePath+" && "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-weblogic-domain-credentials/create-weblogic-credentials.sh -u weblogic -p welcome1 -n "+domainNS+" -d "+domainUid+" && "+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-rcu-credentials/create-rcu-credentials.sh -u "+domainUid+" -p Welcome1 -a sys -q Oradoc_db1 -d "+domainUid+" -n "+domainNS)).execute();
    }else{
      new Command().withParams(new CommandParams()
              .command("cd "+workSpaceBasePath+ " && weblogic-kubernetes-operator/kubernetes/samples/scripts/create-weblogic-domain-credentials/create-weblogic-credentials.sh -u weblogic -p welcome1 -n "+domainNS+" -d "+domainUid+" && weblogic-kubernetes-operator/kubernetes/samples/scripts/create-rcu-credentials/create-rcu-credentials.sh -u "+domainUid+" -p Welcome1 -a sys -q Oradoc_db1 -d "+domainUid+" -n "+domainNS)).execute();
    }
    //create weblogic & rcu creds
    //prepare rcu based on the product
    if(TestConstants.FMW_DOMAIN_TYPE.matches("soa") && listOfDirInProdDir.contains("create-rcu-schema")){
      new Command().withParams(new CommandParams()
              .command("cd "+workSpacePath+" && ./"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-rcu-schema/create-rcu-schema.sh -s "+domainUid+" -t "+TestConstants.FMW_DOMAIN_TYPE+" -d "+connectionURL+" -i "+productImage+" -n "+domainNS+" -q Oradoc_db1 -r Welcome1 -c SOA_PROFILE_TYPE=SMALL,HEALTHCARE_INTEGRATION=NO -l 1000")).execute();
    }else if(listOfDirInProdDir.contains("create-rcu-schema")){
      new Command().withParams(new CommandParams()
              .command("cd "+workSpacePath+" && ./"+prodDirectory+"/kubernetes/"+OPT_VERSION+"/create-rcu-schema/create-rcu-schema.sh -s "+domainUid+" -t "+TestConstants.FMW_DOMAIN_TYPE+" -d "+connectionURL+" -i "+productImage+" -n "+domainNS+" -q Oradoc_db1 -r Welcome1 -l 2000")).execute();
    }else {
      File file=new File(projectDir+"/integration-tests/src/resources/configfiles/upperstack/fmwk8s-rcu-configmap.yaml");
      BufferedReader reader = null;
      FileWriter writer = null;
      String content = "";
      try{
        reader = new BufferedReader(new FileReader(file));
        String line = reader.readLine();

        while (line != null)
        {
          content = content + line + System.lineSeparator();
          line = reader.readLine();
        }
        content = content.replace("%CONNECTION_STRING%",connectionURL);
        content = content.replace("%RCUPREFIX%",domainUid);
        content = content.replace("%SYS_PASSWORD%","Oradoc_db1");
        content = content.replace("%PASSWORD%","Welcome1");
        content = content.replace("%PRODUCT_ID%",TestConstants.FMW_DOMAIN_TYPE);
        writer = new FileWriter(file);
        writer.write(content);
        reader.close();
        writer.close();
      }catch(IOException e){
        e.printStackTrace();
      }

      file=new File(projectDir+"/integration-tests/src/resources/configfiles/upperstack/fmwk8s-rcu-pod.yaml");
      content = "";
      try{
        reader = new BufferedReader(new FileReader(file));
        String line = reader.readLine();

        while (line != null)
        {
          content = content + line + System.lineSeparator();
          line = reader.readLine();
        }
        content = content.replace("%DB_SECRET%","phxregcred");
        content = content.replace("%PRODUCT_ID%",TestConstants.FMW_DOMAIN_TYPE);
        content = content.replace("%PRODUCT_IMAGE%",productImage);
        writer = new FileWriter(file);
        writer.write(content);
        reader.close();
        writer.close();
      }catch(IOException e){
        e.printStackTrace();
      }

      new Command().withParams(new CommandParams()
              .command("cd "+projectDir+"/integration-tests/src/resources/configfiles/upperstack/ && kubectl apply -f fmwk8s-rcu-configmap.yaml -n "+domainNS+" && kubectl apply -f fmwk8s-rcu-pod.yaml -n "+domainNS)).execute();
    }

  }

  public static void installOperator(){
    if(operatorDir.contains("weblogic-operator")) {
      new Command().withParams(new CommandParams()
              .command("helm install op-intg-test " + workSpacePath + prodDirectory + "/kubernetes/" + OPT_VERSION + "/charts/weblogic-operator --namespace " + operatorNS + " --set serviceAccount=default --set 'domainNamespaces={}' --set image=ghcr.io/oracle/weblogic-kubernetes-operator:" + OPT_VERSION + " --wait")).execute();
    }else {
      new Command().withParams(new CommandParams()
              .command("helm install op-intg-test " + workSpaceBasePath + "weblogic-kubernetes-operator/kubernetes/charts/weblogic-operator --namespace " + operatorNS + " --set serviceAccount=default --set 'domainNamespaces={}' --set image=ghcr.io/oracle/weblogic-kubernetes-operator:" + OPT_VERSION + " --wait")).execute();
    }
  }

  public static void createDomain(){
    if(operatorDir.contains("weblogic-operator")) {
        new Command().withParams(new CommandParams()
                .command("cd " + workSpacePath + " && ./" + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-" + TestConstants.FMW_DOMAIN_TYPE + "-domain/domain-home-on-pv/create-domain.sh -i  " + prodDirectory + "/kubernetes/" + OPT_VERSION + "/create-" + TestConstants.FMW_DOMAIN_TYPE + "-domain/domain-home-on-pv/create-domain-inputs.yaml -o script-output-domain-directory")).execute();
        new Command().withParams(new CommandParams()
                .command("helm upgrade op-intg-test " + workSpacePath + prodDirectory + "/kubernetes/" + OPT_VERSION + "/charts/weblogic-operator --namespace " + operatorNS + " --reuse-values --set 'domainNamespaces={" + domainNS + "}' --wait")).execute();
        new Command().withParams(new CommandParams()
                .command("cd " + workSpacePath + "script-output-domain-directory/weblogic-domains/" + domainUid + "/ && kubectl apply -f domain.yaml -n " + domainNS)).execute();
    }else {
        new Command().withParams(new CommandParams()
                .command("cd " + workSpaceBasePath + " && ./weblogic-kubernetes-operator/kubernetes/samples/scripts/create-" + TestConstants.FMW_DOMAIN_TYPE + "-domain/domain-home-on-pv/create-domain.sh -i  weblogic-kubernetes-operator/kubernetes/samples/scripts/create-" + TestConstants.FMW_DOMAIN_TYPE + "-domain/domain-home-on-pv/create-domain-inputs.yaml -o script-output-domain-directory")).execute();
        new Command().withParams(new CommandParams()
                .command("helm upgrade op-intg-test " + workSpaceBasePath + "weblogic-kubernetes-operator/kubernetes/charts/weblogic-operator --namespace " + operatorNS + " --reuse-values --set 'domainNamespaces={" + domainNS + "}' --wait")).execute();
        new Command().withParams(new CommandParams()
              .command("cd " + workSpaceBasePath + "script-output-domain-directory/weblogic-domains/" + domainUid + "/ && kubectl apply -f domain.yaml -n " + domainNS)).execute();
    }
  }
  public static void checkPods(){
    checkPodReady(adminServerPodName, domainUid, domainNS);
    for(int i=1;i<=managedServerReplicaCount;i++){
      checkPodReady(managedServerPrefix+i, domainUid, domainNS);
    }
  }

  public static <K,V> K getProdName(Map<K,V> map,V val){
    return map.entrySet().stream()
            .filter(entry->val.equals(entry.getValue()))
            .findFirst().map(Map.Entry::getKey)
            .orElse(null);
  }
}